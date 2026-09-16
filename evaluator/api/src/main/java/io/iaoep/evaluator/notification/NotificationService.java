package io.iaoep.evaluator.notification;

import io.iaoep.evaluator.evaluation.EvaluationJob;
import io.iaoep.evaluator.regression.RegressionReport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知服务 — 评测完成 / 回归失败时触发.
 *
 * <p>Phase 2 简化: Notifier Registry 内置, 直接调用.
 * Phase 3: 接入 NotificationChannel 数据库 + 项目级 channel 配置.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<Notifier> notifiers;

    private final Map<String, Notifier> byType = new HashMap<>();

    @PostConstruct
    void init() {
        for (Notifier n : notifiers) {
            byType.put(n.type(), n);
        }
        log.info("Registered {} notifiers: {}", byType.size(), byType.keySet());
    }

    /**
     * 评测完成通知.
     */
    public void notifyEvaluationCompleted(EvaluationJob job, String webhookUrl, String type) {
        Optional<Notifier> notifier = Optional.ofNullable(byType.get(type));
        if (notifier.isEmpty()) {
            log.warn("unknown notifier type: {}", type);
            return;
        }

        String title = String.format("[IAOEP] 评测任务完成: %s", job.getAgentVersion());
        String content = String.format("""
                ## 评测任务完成

                - **Job ID**: `%s`
                - **Agent 版本**: `%s`
                - **数据集 ID**: `%s`
                - **进度**: %.0f%% (%d/%d)
                - **总分**: %s
                - **耗时**: %s

                [查看详情](/evaluations/%s)
                """,
                job.getId(),
                job.getAgentVersion(),
                job.getDatasetId(),
                job.getProgress() * 100,
                job.getCompletedCases(),
                job.getTotalCases(),
                job.getResult() != null ? job.getResult().get("overall") : "N/A",
                job.getCompletedAt() != null && job.getStartedAt() != null
                        ? java.time.Duration.between(job.getStartedAt(), job.getCompletedAt()).toSeconds() + "s"
                        : "N/A",
                job.getId());

        Map<String, Object> meta = new HashMap<>();
        meta.put("project_id", job.getProjectId());
        meta.put("evaluation_id", job.getId());
        meta.put("status", job.getStatus().name());

        boolean ok = notifier.get().send(webhookUrl, title, content, meta);
        log.info("notifier {} sent: {} (success={})", type, job.getId(), ok);
    }

    /**
     * 回归检测失败通知.
     */
    public void notifyRegressionFailed(RegressionReport report, String webhookUrl, String type) {
        Optional<Notifier> notifier = Optional.ofNullable(byType.get(type));
        if (notifier.isEmpty()) return;

        String title = String.format("[IAOEP] 回归检测失败: %s → %s",
                report.getBaselineVersion(), report.getCandidateVersion());
        String content = String.format("""
                ## 回归检测失败 ⚠️

                - **Baseline**: `%s` (overall=%s)
                - **Candidate**: `%s` (overall=%s)
                - **Delta**: %+.3f (阈值: %.3f)

                **阻断维度**: %s

                [查看详情](/regression/%s)
                """,
                report.getBaselineVersion(),
                report.getResult() != null ? report.getResult().get("baseline_overall") : "?",
                report.getCandidateVersion(),
                report.getResult() != null ? report.getResult().get("candidate_overall") : "?",
                report.getOverallDelta(),
                report.getThreshold(),
                report.getResult() != null ? report.getResult().get("blocked_dimensions") : "[]",
                report.getId());

        Map<String, Object> meta = new HashMap<>();
        meta.put("project_id", report.getProjectId());
        meta.put("regression_id", report.getId());
        meta.put("status", report.getStatus().name());

        boolean ok = notifier.get().send(webhookUrl, title, content, meta);
        log.info("notifier {} sent regression: {} (success={})", type, report.getId(), ok);
    }
}
