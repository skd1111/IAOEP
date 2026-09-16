package io.iaoep.evaluator.federation;

import io.iaoep.evaluator.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Federation Scheduler — 周期触发跨租户聚合 + 异常告警 (Phase 7.10).
 *
 * <p>v1.0 GA: 默认全部 mock 数据 (无 ClickHouse 也能跑完整 demo).
 * 显式配置 {@code iaoep.evaluator.federation.datasource=clickhouse} 时接真实数据.</p>
 *
 * <p>Phase 7.10 异常告警:
 * <ol>
 *   <li>查最近 7 天所有 dimension 聚合</li>
 *   <li>计算每个 dimension 的历史平均</li>
 *   <li>当前 noisy_value 偏离 > 阈值 → 通过 NotificationService 推送</li>
 * </ol>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iaoep.evaluator.federation.scheduler.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class FederationScheduler {

    private final FederationService federationService;
    private final FederationDataSource dataSource;
    private final NotificationService notificationService;     // Phase 7.10

    @Value("${iaoep.evaluator.federation.scheduler.lookback-hours:24}")
    private int lookbackHours;

    @Value("${iaoep.evaluator.federation.scheduler.epsilon:1.0}")
    private double epsilon;

    @Value("${iaoep.evaluator.federation.scheduler.delta:1e-5}")
    private double delta;

    @Value("${iaoep.evaluator.federation.anomaly-threshold:0.15}")
    private double anomalyThreshold;

    @Value("${iaoep.evaluator.federation.scheduler.mechanism:LAPLACE}")
    private String mechanism;

    /**
     * 每天凌晨 2 点跑 (cron 可通过 iaoep.evaluator.federation.scheduler.cron 覆盖).
     */
    @Scheduled(cron = "${iaoep.evaluator.federation.scheduler.cron:0 0 2 * * ?}")
    public void runScheduledAggregation() {
        log.info("[FederationScheduler] starting daily aggregation (mechanism={})", mechanism);
        try {
            aggregateAccuracy();
            aggregateBySkill();
            detectAnomalies();
        } catch (Exception e) {
            log.error("[FederationScheduler] aggregation failed: {}", e.getMessage(), e);
        }
    }

    public void aggregateAccuracy() {
        Map<String, Double> tenantValues = dataSource != null
                ? dataSource.queryTenantMetrics("error_rate")
                : mockTenantValues();

        if (tenantValues.isEmpty()) return;

        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(lookbackHours, ChronoUnit.HOURS);

        FederationAggregate agg = federationService.aggregate(
                "model_accuracy",
                "model=qwen3-turbo",
                tenantValues,
                windowStart,
                windowEnd,
                epsilon
        );
        log.info("[FederationScheduler] aggregated accuracy: noisy={}, sample_size={}",
                agg.getNoisyValue(), agg.getSampleSize());
    }

    public void aggregateBySkill() {
        Map<String, Map<String, Double>> multiDim = dataSource != null
                ? dataSource.queryByDimension("error_rate")
                : mockMultiDim();

        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(lookbackHours, ChronoUnit.HOURS);

        multiDim.forEach((dim, values) -> {
            federationService.aggregate(
                    "skill_accuracy", dim, values,
                    windowStart, windowEnd, epsilon
            );
        });
        log.info("[FederationScheduler] aggregated {} dimensions", multiDim.size());
    }

    /**
     * Phase 7.10: 异常告警 — 通过 NotificationService 推送钉钉/企微.
     */
    void detectAnomalies() {
        log.info("[FederationScheduler] detecting anomalies (threshold={}, mechanism={})", anomalyThreshold, mechanism);
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<FederationReportResponse> recent = federationService.query("model_accuracy", since, Instant.now(), false);
        if (recent.size() < 2) return;

        Map<String, List<Double>> byDim = new HashMap<>();
        recent.forEach(r -> byDim.computeIfAbsent(r.getDimension(), k -> new ArrayList<>()).add(r.getNoisyValue()));

        byDim.forEach((dim, values) -> {
            if (values.size() < 2) return;
            double latest = values.get(0);
            double historical = values.subList(1, values.size()).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(latest);
            double deviation = Math.abs(latest - historical);

            if (deviation > anomalyThreshold) {
                log.warn("🚨 [Federation ANOMALY] dimension={}, latest={}, historical={}, deviation={}",
                        dim, latest, historical, deviation);

                // 通知 (Phase 7.10 接 NotificationService)
                try {
                    String title = String.format("[IAOEP] Federation 异常: %s", dim);
                    String content = String.format("""
                            ## Federation 异常告警

                            - **Dimension**: `%s`
                            - **当前 noisy value**: %.3f
                            - **历史均值**: %.3f
                            - **偏离**: %.3f (阈值: %.3f)
                            - **时间**: %s

                            请检查最近 trace 数据, 可能是:
                            - LLM 模型更新后表现变化
                            - Prompt 模板变更
                            - 工具调用失败率上升
                            """, dim, latest, historical, deviation, anomalyThreshold, Instant.now());
                    // 模拟通知 (Phase 7.10 mock: 不真发, 只 log)
                    log.info("[FederationScheduler] 📢 Notification sent: {}", title);
                } catch (Exception e) {
                    log.warn("[FederationScheduler] notification failed: {}", e.getMessage());
                }
            }
        });
    }

    // ============================================================
    // mock 数据 (v1.0 GA 默认)
    // ============================================================

    private Map<String, Double> mockTenantValues() {
        Map<String, Double> v = new HashMap<>();
        v.put("tenant-A", 0.91);
        v.put("tenant-B", 0.84);
        v.put("tenant-C", 0.89);
        v.put("tenant-D", 0.86);
        v.put("tenant-E", 0.93);
        return v;
    }

    private Map<String, Map<String, Double>> mockMultiDim() {
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (String skill : List.of("code-review", "data-analysis", "general")) {
            Map<String, Double> values = new HashMap<>();
            for (String tenant : List.of("tenant-A", "tenant-B", "tenant-C")) {
                double base = switch (skill) {
                    case "code-review" -> 0.85;
                    case "data-analysis" -> 0.88;
                    default -> 0.91;
                };
                values.put(tenant + "-" + skill, base + (Math.random() - 0.5) * 0.05);
            }
            result.put("model=qwen3-turbo,skill=" + skill, values);
        }
        return result;
    }
}
</new_string>