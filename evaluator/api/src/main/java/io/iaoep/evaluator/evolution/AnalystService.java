package io.iaoep.evaluator.evolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.iaoep.evaluator.evaluation.EvaluationJob;
import io.iaoep.evaluator.evaluation.EvaluationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Analyst Service — 周期跑 AI 分析, 识别失败模式, 生成 EvolutionSuggestion.
 *
 * <p>Phase 2 简化: 不接 ClickHouse (直接复用 Phase 2 EvaluationJob.result),
 * 输入是历史评测任务的 result 字段, AI 找出失败 case 的模式.</p>
 *
 * <p>实际生产应该从 ClickHouse 拉 trace, 这里用 EvaluationJob.result 演示流程.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalystService {

    private final EvolutionSuggestionRepository suggestionRepo;
    private final EvaluationJobRepository jobRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 周期触发: 分析最近 N 个 evaluation job, 生成建议.
     *
     * @param projectId 项目 ID
     * @param lookbackJobs 分析最近多少个 job (默认 20)
     * @return 生成的 suggestion 数量
     */
    @Transactional
    public int analyze(UUID projectId, int lookbackJobs) {
        log.info("Analyst running for project={}, lookback={}", projectId, lookbackJobs);

        // 1. 拉最近的评测任务 (按时间倒序)
        List<EvaluationJob> recentJobs = jobRepo
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .limit(lookbackJobs)
                .toList();

        if (recentJobs.isEmpty()) {
            log.info("No evaluation jobs found, skipping");
            return 0;
        }

        // 2. 找失败的 case (overall < 0.7 OR 含失败 case)
        List<FailedCase> failedCases = new ArrayList<>();
        for (EvaluationJob job : recentJobs) {
            if (job.getResult() == null) continue;
            extractFailedCases(job, failedCases);
        }

        if (failedCases.isEmpty()) {
            log.info("No failed cases found");
            return 0;
        }

        // 3. AI 聚类失败模式 (Phase 2 简化: 用规则聚类, Phase 3 接 LLM)
        //    当前实现: 按 expected_output 包含的错误关键词聚类
        Map<String, List<FailedCase>> clusters = clusterByErrorPattern(failedCases);

        // 4. 为每个聚类生成 EvolutionSuggestion
        int created = 0;
        for (Map.Entry<String, List<FailedCase>> entry : clusters.entrySet()) {
            String pattern = entry.getKey();
            List<FailedCase> cases = entry.getValue();
            if (cases.size() < 2) continue;  // 单次不构成模式

            EvolutionSuggestion suggestion = buildSuggestion(projectId, pattern, cases, recentJobs.get(0).getId());
            suggestionRepo.save(suggestion);
            created++;
        }

        log.info("Analyst generated {} suggestions for project={}", created, projectId);
        return created;
    }

    private void extractFailedCases(EvaluationJob job, List<FailedCase> sink) {
        Object caseResultsObj = job.getResult() == null ? null : job.getResult().get("case_results");
        if (!(caseResultsObj instanceof List<?> list)) return;

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            if (!Boolean.FALSE.equals(map.get("passed"))) continue;

            Object input = map.get("input");
            Object expected = map.get("expected_output");
            Object reason = map.get("error") != null ? map.get("error") : map.get("reason");

            sink.add(new FailedCase(
                    String.valueOf(input),
                    String.valueOf(expected),
                    reason != null ? String.valueOf(reason) : "未知失败",
                    job.getId()
            ));
        }
    }

    /**
     * 按错误模式聚类 (Phase 2 简化版: 提取错误关键词).
     * Phase 3 接入 LLM 做语义聚类.
     */
    private Map<String, List<FailedCase>> clusterByErrorPattern(List<FailedCase> cases) {
        Map<String, List<FailedCase>> clusters = new HashMap<>();
        for (FailedCase c : cases) {
            String pattern = extractErrorPattern(c.reason);
            clusters.computeIfAbsent(pattern, k -> new ArrayList<>()).add(c);
        }
        return clusters;
    }

    private String extractErrorPattern(String reason) {
        // 简化: 按 "Error" / "超时" / "格式错误" 等关键词
        String lower = reason.toLowerCase();
        if (lower.contains("timeout")) return "TIMEOUT";
        if (lower.contains("format"))  return "FORMAT_ERROR";
        if (lower.contains("accuracy")) return "ACCURACY_LOW";
        if (lower.contains("cost"))     return "COST_HIGH";
        if (lower.contains("token"))    return "TOKEN_LIMIT";
        return "OTHER";
    }

    private EvolutionSuggestion buildSuggestion(UUID projectId, String pattern,
                                                List<FailedCase> cases, UUID triggerJobId) {
        // 根据 pattern 生成建议
        EvolutionSuggestion.Type type;
        String target;
        String description;
        EvolutionSuggestion.RiskLevel riskLevel;
        Double expectedRoi;

        switch (pattern) {
            case "TIMEOUT" -> {
                type = EvolutionSuggestion.Type.PARAM_TUNE;
                target = "AgentLoop.maxTurns";
                description = String.format(
                        "检测到 %d 次超时, 建议降低 maxTurns 或减少单次 LLM 调用超时",
                        cases.size());
                riskLevel = EvolutionSuggestion.RiskLevel.LOW;
                expectedRoi = 0.15;
            }
            case "FORMAT_ERROR" -> {
                type = EvolutionSuggestion.Type.PROMPT_CHANGE;
                target = "Agent.chat system prompt";
                description = String.format(
                        "检测到 %d 次格式错误 (LLM 输出不符合 schema), 建议 prompt 增加格式示例",
                        cases.size());
                riskLevel = EvolutionSuggestion.RiskLevel.MEDIUM;
                expectedRoi = 0.25;
            }
            case "ACCURACY_LOW" -> {
                type = EvolutionSuggestion.Type.MODEL_SWAP;
                target = "LLM.model";
                description = String.format(
                        "检测到 %d 次准确率低, 建议切换到更强大的模型 (如 qwen3-max)",
                        cases.size());
                riskLevel = EvolutionSuggestion.RiskLevel.HIGH;
                expectedRoi = 0.30;
            }
            case "COST_HIGH" -> {
                type = EvolutionSuggestion.Type.ROUTE_CHANGE;
                target = "LLM.router";
                description = String.format(
                        "检测到 %d 次成本过高, 建议简单查询路由到 qwen3-turbo",
                        cases.size());
                riskLevel = EvolutionSuggestion.RiskLevel.MEDIUM;
                expectedRoi = 0.20;
            }
            case "TOKEN_LIMIT" -> {
                type = EvolutionSuggestion.Type.PROMPT_CHANGE;
                target = "LLM.maxOutputTokens";
                description = String.format(
                        "检测到 %d 次输出超 token 限制, 建议调整 max_tokens 或截断 prompt",
                        cases.size());
                riskLevel = EvolutionSuggestion.RiskLevel.LOW;
                expectedRoi = 0.10;
            }
            default -> {
                type = EvolutionSuggestion.Type.PARAM_TUNE;
                target = "Agent.config";
                description = String.format("检测到 %d 次其他失败, 建议人工 review", cases.size());
                riskLevel = EvolutionSuggestion.RiskLevel.LOW;
                expectedRoi = 0.05;
            }
        }

        return EvolutionSuggestion.builder()
                .projectId(projectId)
                .jobId(triggerJobId)
                .type(type)
                .target(target)
                .description(description)
                .diff(Map.of(
                        "pattern", pattern,
                        "case_count", cases.size(),
                        "example_inputs", cases.stream().limit(3).map(FailedCase::input).toList()
                ))
                .expectedRoi(expectedRoi)
                .riskLevel(riskLevel)
                .status(EvolutionSuggestion.Status.PROPOSED)
                .createdAt(Instant.now())
                .createdBy("analyst-agent")
                .build();
    }

    private record FailedCase(String input, String expected, String reason, UUID jobId) {}
}
