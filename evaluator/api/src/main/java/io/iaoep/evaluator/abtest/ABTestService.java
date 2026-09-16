package io.iaoep.evaluator.abtest;

import io.iaoep.evaluator.abtest.dto.ABTestResponse;
import io.iaoep.evaluator.abtest.dto.CreateABTestRequest;
import io.iaoep.evaluator.evaluation.EvaluationJob;
import io.iaoep.evaluator.evaluation.EvaluationJobRepository;
import io.iaoep.evaluator.regression.RegressionService;
import io.iaoep.evaluator.regression.dto.CreateRegressionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A/B Test Service — 创建测试 + 流量分流 + 评测对比 + 决策.
 *
 * <p>流程:
 * <ol>
 *   <li>{@link #create}: 创建 A/B 测试, 关联 EvaluationJob</li>
 *   <li>{@link #start}: 启动, 标记 RUNNING, 通知下游 Agent 服务分流</li>
 *   <li>(自动跑) baseline + candidate 两个 EvaluationJob</li>
 *   <li>{@link #decide}: 对比两个 job 的 overall, 决策 passed/failed/inconclusive</li>
 *   <li>{@link #apply} / {@link #rollback}: 应用新版或回滚</li>
 * </ol>
 *
 * <p>Phase 3 简化: 不真正做流量分配 (需要 Agent SDK 配合), 只管理测试元数据 + 决策.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ABTestService {

    private final ABTestRepository repo;
    private final EvaluationJobRepository jobRepo;
    private final RegressionService regressionService;

    @Transactional(readOnly = true)
    public List<ABTestResponse> list(UUID projectId) {
        return repo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(ABTestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ABTestResponse get(UUID id) {
        return ABTestResponse.from(repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ab test not found: " + id)));
    }

    @Transactional
    public ABTestResponse create(UUID projectId, CreateABTestRequest req, String userId) {
        ABTest abTest = ABTest.builder()
                .projectId(projectId)
                .name(req.getName() != null ? req.getName()
                        : String.format("A/B: %s → %s", req.getBaselineVersion(), req.getCandidateVersion()))
                .suggestionId(req.getSuggestionId())
                .baselineVersion(req.getBaselineVersion())
                .candidateVersion(req.getCandidateVersion())
                .trafficSplit(req.getTrafficSplit() != null ? req.getTrafficSplit()
                        : Map.of("baseline", 0.5, "candidate", 0.5))
                .status(ABTest.Status.PENDING)
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();
        return ABTestResponse.from(repo.save(abTest));
    }

    /**
     * 启动 A/B 测试, 标记 RUNNING.
     * 实际流量分流由下游 Agent SDK 配合 (Phase 3 简化: 通过 HTTP Header 注入).
     */
    @Transactional
    public ABTestResponse start(UUID id) {
        ABTest abTest = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ab test not found: " + id));
        if (abTest.getStatus() != ABTest.Status.PENDING) {
            throw new IllegalStateException("ab test not pending: " + abTest.getStatus());
        }
        abTest.setStatus(ABTest.Status.RUNNING);
        abTest.setStartedAt(Instant.now());
        return ABTestResponse.from(repo.save(abTest));
    }

    /**
     * 决策 A/B 测试 — 复用 RegressionService 对比 baseline / candidate 评测结果.
     *
     * @param baselineJobId  baseline 版本评测任务 ID
     * @param candidateJobId candidate 版本评测任务 ID
     */
    @Transactional
    public ABTestResponse decide(UUID abTestId, UUID baselineJobId, UUID candidateJobId, Double threshold) {
        ABTest abTest = repo.findById(abTestId)
                .orElseThrow(() -> new IllegalArgumentException("ab test not found: " + abTestId));

        // 1. 拉两个 job 的 overall
        EvaluationJob baseline = jobRepo.findById(baselineJobId)
                .orElseThrow(() -> new IllegalArgumentException("baseline job not found"));
        EvaluationJob candidate = jobRepo.findById(candidateJobId)
                .orElseThrow(() -> new IllegalArgumentException("candidate job not found"));

        if (baseline.getResult() == null || candidate.getResult() == null) {
            throw new IllegalStateException("both jobs must be COMPLETED");
        }

        double baseScore = asDouble(baseline.getResult().get("overall"));
        double candScore = asDouble(candidate.getResult().get("overall"));
        double delta = candScore - baseScore;

        abTest.setBaselineScore(baseScore);
        abTest.setCandidateScore(candScore);

        double thr = threshold != null ? threshold : 0.05;
        abTest.setStatus(ABTest.Status.ANALYZING);

        // 2. 决策 (简化: 不做显著性检验, 只看 delta 是否超阈值)
        ABTest.Status decision;
        String reason;
        if (delta >= thr) {
            decision = ABTest.Status.PASSED;
            reason = String.format("candidate 优于 baseline: %.3f vs %.3f (delta=+%+.3f)", candScore, baseScore, delta);
        } else if (delta <= -thr) {
            decision = ABTest.Status.FAILED;
            reason = String.format("candidate 劣化: %.3f vs %.3f (delta=%.3f)", candScore, baseScore, delta);
        } else {
            decision = ABTest.Status.INCONCLUSIVE;
            reason = String.format("差异不显著 (delta=%.3f, threshold=%.3f), 建议继续观察", delta, thr);
        }

        abTest.setDecision(decision);
        abTest.setDecisionReason(reason);
        abTest.setDecidedAt(Instant.now());
        abTest.setStatus(decision);

        log.info("A/B test decided: {} ({})", abTestId, reason);
        return ABTestResponse.from(repo.save(abTest));
    }

    /**
     * 应用候选版本 (切为默认).
     */
    @Transactional
    public ABTestResponse apply(UUID id) {
        ABTest abTest = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ab test not found: " + id));
        if (abTest.getDecision() != ABTest.Status.PASSED) {
            throw new IllegalStateException(
                    "can only apply PASSED tests, current decision: " + abTest.getDecision());
        }
        abTest.setStatus(ABTest.Status.DEPLOYED);
        log.info("A/B test applied: {}", id);
        return ABTestResponse.from(repo.save(abTest));
    }

    /**
     * 回滚 (候选版本有 bug, 切回 baseline).
     */
    @Transactional
    public ABTestResponse rollback(UUID id) {
        ABTest abTest = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ab test not found: " + id));
        if (abTest.getStatus() == ABTest.Status.DEPLOYED) {
            abTest.setStatus(ABTest.Status.REVERTED);
            log.warn("A/B test reverted: {}", id);
        } else if (abTest.getStatus() == ABTest.Status.FAILED || abTest.getStatus() == ABTest.Status.INCONCLUSIVE) {
            // 不应用, 状态已经是最终
            log.info("A/B test {} already finalized as {}, no rollback needed", id, abTest.getStatus());
        } else {
            throw new IllegalStateException(
                    "can only rollback DEPLOYED tests, current status: " + abTest.getStatus());
        }
        return ABTestResponse.from(repo.save(abTest));
    }

    /**
     * 流量分配决策 (下游 SDK 调用).
     *
     * @return "baseline" 或 "candidate"
     */
    public String assignGroup(UUID abTestId) {
        ABTest abTest = repo.findById(abTestId)
                .orElseThrow(() -> new IllegalArgumentException("ab test not found: " + abTestId));
        if (abTest.getStatus() != ABTest.Status.RUNNING) {
            return null;   // 不分流
        }
        Map<String, Double> split = abTest.getTrafficSplit() != null
                ? abTest.getTrafficSplit() : Map.of("baseline", 0.5, "candidate", 0.5);
        double baselineRatio = split.getOrDefault("baseline", 0.5);

        // 简化: 用 trace_id 哈希决定
        // 真实应该按用户 / session 维度, 一致性体验
        double r = Math.random();
        return r < baselineRatio ? "baseline" : "candidate";
    }

    private double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
