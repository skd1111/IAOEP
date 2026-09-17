package io.iaoep.evaluator.decision;

import io.iaoep.evaluator.evolution.EvolutionSuggestion;
import io.iaoep.evaluator.evolution.EvolutionSuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Decision Agent — 在线异常检测 + 自动建议回滚.
 *
 * <p>每分钟 (Phase 3 简化) 扫一次:
 * <ul>
 *   <li>从 ClickHouse 查过去 5 分钟 error_rate (Phase 3 简化为读 env / 模拟)</li>
 *   <li>超过阈值 → 生成 ROLLBACK 类 EvolutionSuggestion (高风险, 需双签)</li>
 *   <li>通知 (微信/钉钉)</li>
 * </ul>
 *
 * <p>Phase 3 简化: error_rate 通过环境变量读 (实际应该读 ClickHouse).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionAgent {

    private final EvolutionSuggestionRepository suggestionRepo;
    private final JdbcTemplate jdbcTemplate;

    @Value("${iaoep.evaluator.decision.error-threshold:0.10}")
    private double errorThreshold;

    @Value("${iaoep.evaluator.decision.enabled:true}")
    private boolean enabled;

    /**
     * 每分钟扫一次 (Phase 3 简化).
     * 真实生产: ClickHouse SQL `SELECT countIf(status='error')/count(*) FROM iaoep.traces WHERE start_time > now() - 300`.
     */
    @Scheduled(fixedDelayString = "${iaoep.evaluator.decision.scan-interval-ms:60000}",
               initialDelay = 30000)
    public void scan() {
        if (!enabled) return;

        // 从 ClickHouse 查过去 5 分钟错误率
        double errorRate = queryErrorRate();

        if (errorRate > errorThreshold) {
            log.warn("[Decision] error_rate={} > threshold={}, 生成回滚建议",
                    errorRate, errorThreshold);

            EvolutionSuggestion suggestion = EvolutionSuggestion.builder()
                    .projectId(UUID.randomUUID())  // Phase 3 简化, 实际应该是指定 project
                    .type(EvolutionSuggestion.Type.ROLLBACK)
                    .target("AUTO: high error rate detected")
                    .description(String.format(
                            "检测到过去 5 分钟错误率 %.2f%% 超过阈值 %.2f%%, 建议回滚最近一次部署",
                            errorRate * 100, errorThreshold * 100))
                    .riskLevel(EvolutionSuggestion.RiskLevel.HIGH)
                    .status(EvolutionSuggestion.Status.PROPOSED)
                    .expectedRoi(0.0)  // 回滚不是优化, 是止损
                    .diff(java.util.Map.of("reason", "auto-rollback-suggestion",
                            "error_rate", errorRate,
                            "threshold", errorThreshold))
                    .createdAt(Instant.now())
                    .createdBy("decision-agent")
                    .build();
            suggestionRepo.save(suggestion);

            // Phase 3 简化: 不真发通知 (复用 NotificationService)
            log.warn("[Decision] 已生成回滚建议: {}", suggestion.getId());
        } else {
            log.debug("[Decision] scan OK: error_rate={}", errorRate);
        }
    }

    /**
     * 从 ClickHouse 查过去 5 分钟的错误率.
     * 如果 ClickHouse 不可用, 返回 0.0 (安全兆底).
     */
    private double queryErrorRate() {
        try {
            Double result = jdbcTemplate.queryForObject(
                    "SELECT countIf(status = 'error') * 1.0 / nullIf(count(*), 0) " +
                    "FROM iaoep.traces WHERE start_time > now() - INTERVAL 5 MINUTE",
                    Double.class);
            return result != null ? result : 0.0;
        } catch (Exception e) {
            log.debug("[Decision] ClickHouse query failed, fallback to 0.0: {}", e.getMessage());
            return 0.0;
        }
    }
}
