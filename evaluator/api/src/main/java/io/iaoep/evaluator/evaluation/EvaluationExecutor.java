package io.iaoep.evaluator.evaluation;

import io.iaoep.evaluator.dataset.GoldenDataset;
import io.iaoep.evaluator.dataset.GoldenDatasetRepository;
import io.iaoep.evaluator.judge.LLMJudgeClient;
import io.iaoep.evaluator.judge.LLMJudgeClient.ScoreResult;
import io.iaoep.evaluator.notification.NotificationService;
import io.iaoep.evaluator.scorer.ScorerExecutor;
import io.iaoep.evaluator.scorer.ScorerExecutor.ScorerConfig;
import io.iaoep.evaluator.scorer.ScorerExecutor.ExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * 评测执行器 — 跑一个 EvaluationJob.
 *
 * <p>执行流程:
 * <ol>
 *   <li>加载 Golden Dataset cases</li>
 *   <li>对每个 case: 模拟重放 (Phase 2 简化为拿生产 trace 的 agent_output 对比)
 *       — 真实场景需要调用 Agent 服务重放</li>
 *   <li>跑 Rule Scorer (PR 13)</li>
 *   <li>跑 LLM Judge (PR 14)</li>
 *   <li>聚合成 report</li>
 *   <li>写回 DB + 触发通知 (PR 17)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationExecutor {

    private final EvaluationJobRepository jobRepo;
    private final GoldenDatasetRepository datasetRepo;
    private final ScorerExecutor scorerExecutor;
    private final LLMJudgeClient judgeClient;
    private final NotificationService notificationService;

    /** 评测完成后通知 (Phase 3 接入 NotificationChannel, Phase 2 简化用环境变量) */
    @Value("${iaoep.evaluator.notification.evaluation.webhook-url:}")
    private String evaluationWebhookUrl;

    @Value("${iaoep.evaluator.notification.evaluation.type:dingtalk}")
    private String evaluationNotificationType;

    /**
     * 异步执行评测任务.
     * Spring @Async + 默认 ThreadPoolTaskExecutor.
     */
    @Async
    public void executeAsync(UUID jobId) {
        log.info("Starting evaluation job: {}", jobId);
        execute(jobId);
    }

    /**
     * 同步执行 (供测试用).
     */
    public EvaluationJob execute(UUID jobId) {
        EvaluationJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("job not found: " + jobId));

        if (job.getStatus() != EvaluationJob.Status.PENDING) {
            throw new IllegalStateException("job not pending: " + job.getStatus());
        }

        // 1. 标记为 RUNNING
        job.setStatus(EvaluationJob.Status.RUNNING);
        job.setStartedAt(Instant.now());
        jobRepo.save(job);

        try {
            // 2. 加载 Dataset
            GoldenDataset dataset = datasetRepo.findById(job.getDatasetId())
                    .orElseThrow(() -> new IllegalStateException("dataset not found: " + job.getDatasetId()));
            List<Map<String, Object>> cases = dataset.getCases();
            job.setTotalCases(cases.size());
            jobRepo.save(job);

            // 3. 默认 Scorer 配置 (可被 judgeConfig.scrapers 覆盖)
            List<ScorerConfig> defaultScorers = List.of(
                    new ScorerConfig("latency", Map.of(
                            "p50_threshold_ms", 500,
                            "p95_threshold_ms", 2000,
                            "p99_threshold_ms", 5000
                    ), 0.3),
                    new ScorerConfig("cost", Map.of("max_cost_cny", 0.10), 0.3),
                    new ScorerConfig("token", Map.of(
                            "max_input_tokens", 5000,
                            "max_output_tokens", 2000
                    ), 0.4)
            );

            List<String> dimensions = extractDimensions(job);

            // 4. 累计所有 case 的评分
            List<Map<String, Object>> caseResults = new ArrayList<>();
            List<Double> overallScores = new ArrayList<>();
            int completed = 0;

            for (Map<String, Object> caseDef : cases) {
                try {
                    Map<String, Object> trace = mockTrace(caseDef);

                    // Rule Scorer
                    ExecutionResult scorerResult = scorerExecutor.execute(trace, defaultScorers);

                    // LLM Judge
                    String rubric = (String) caseDef.getOrDefault("rubric", "通用评测");
                    Object input = caseDef.get("input");
                    String rubricText = rubric + "\n\n输入: " + (input != null ? input.toString() : "");

                    ScoreResult judgeResult;
                    try {
                        judgeResult = judgeClient.score(trace, rubricText, dimensions);
                    } catch (Exception e) {
                        log.warn("LLM Judge failed for case: {}", e.getMessage());
                        judgeResult = new ScoreResult();
                        judgeResult.setOverall(scorerResult.total());
                        judgeResult.setDimensions(List.of());
                        judgeResult.setRawResponse("JUDGE_FAILED: " + e.getMessage());
                        judgeResult.setElapsedMs(0);
                    }

                    double overall = 0.5 * scorerResult.total() + 0.5 * judgeResult.getOverall();
                    overallScores.add(overall);

                    caseResults.add(Map.of(
                            "input", caseDef.get("input"),
                            "expected_output", caseDef.get("expected_output"),
                            "scorer_result", Map.of(
                                    "total", scorerResult.total(),
                                    "details", scorerResult.details().entrySet().stream()
                                            .collect(java.util.stream.Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    e -> Map.of("score", e.getValue().score(), "weight", e.getValue().weight())
                                            ))
                            ),
                            "judge_result", Map.of(
                                    "overall", judgeResult.getOverall(),
                                    "dimensions", judgeResult.getDimensions() == null ? List.of()
                                            : judgeResult.getDimensions().stream()
                                                    .map(d -> Map.of("name", d.getName(), "score", d.getScore(), "reason", d.getReason()))
                                                    .toList()
                            ),
                            "overall", overall,
                            "passed", overall >= 0.7
                    ));
                } catch (Exception e) {
                    log.error("Case failed: {}", e.getMessage(), e);
                    caseResults.add(Map.of(
                            "input", caseDef.get("input"),
                            "error", e.getMessage(),
                            "passed", false
                    ));
                }
                completed++;
                job.setCompletedCases(completed);
                job.setProgress(cases.isEmpty() ? 1.0 : (double) completed / cases.size());
                jobRepo.save(job);
            }

            // 5. 聚合 report
            double meanOverall = overallScores.isEmpty() ? 0.0
                    : overallScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            Map<String, Object> report = Map.of(
                    "overall", meanOverall,
                    "total_cases", cases.size(),
                    "completed_cases", completed,
                    "rule_scorer_weight", 0.5,
                    "llm_judge_weight", 0.5,
                    "case_results", caseResults
            );

            job.setResult(report);
            job.setStatus(EvaluationJob.Status.COMPLETED);
            job.setProgress(1.0);
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);

            // 通知 (PR 17): 评测完成
            if (evaluationWebhookUrl != null && !evaluationWebhookUrl.isBlank()) {
                try {
                    notificationService.notifyEvaluationCompleted(
                            job, evaluationWebhookUrl, evaluationNotificationType);
                } catch (Exception e) {
                    log.warn("notification failed: {}", e.getMessage());
                }
            }

            log.info("Evaluation job completed: {} (overall={})", jobId, meanOverall);
            return job;

        } catch (Exception e) {
            log.error("Evaluation job failed: {}", jobId, e);
            job.setStatus(EvaluationJob.Status.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
            return job;
        }
    }

    private List<String> extractDimensions(EvaluationJob job) {
        Object dimsObj = job.getJudgeConfig() == null ? null : job.getJudgeConfig().get("dimensions");
        if (dimsObj instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of("accuracy", "style", "safety");
    }

    /**
     * 模拟从 Golden Dataset case 生成 trace.
     * Phase 2 简化: 直接把 case.input 当作 agent_input, case.expected_output 当作 agent_output.
     * 真实场景需要: 重放 input 到 Agent 服务, 拿到实际输出.
     */
    private Map<String, Object> mockTrace(Map<String, Object> caseDef) {
        Map<String, Object> span = new HashMap<>();
        span.put("span_name", "agent.run");
        span.put("agent_input", String.valueOf(caseDef.getOrDefault("input", "")));
        span.put("agent_output", String.valueOf(caseDef.getOrDefault("expected_output", "")));
        span.put("duration_ms", 250);
        span.put("cost_cny", 0.02);
        span.put("llm_input_tokens", 100);
        span.put("llm_output_tokens", 80);
        return Map.of("spans", List.of(span));
    }
}
