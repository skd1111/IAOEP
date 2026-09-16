package io.iaoep.evaluator.federation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;

/**
 * Federation 数据源 — Phase 7.8: 接 ClickHouse 真实数据 (替换 Phase 7.7 的模拟数据).
 *
 * <p>ClickHouse SQL 查询:
 * <pre>
 *   SELECT
 *       tenant_id,
 *       avg(llm_input_tokens + llm_output_tokens) AS avg_tokens,
 *       avg(duration_ms) AS avg_duration_ms,
 *       countIf(status='error') / count() AS error_rate,
 *       count() AS trace_count
 *   FROM iaoep.traces
 *   WHERE start_time > now() - INTERVAL 24 HOUR
 *   GROUP BY tenant_id
 * </pre>
 *
 * <p>Phase 7.8 实现:
 * <ul>
 *   <li>注入 {@link JdbcTemplate} (Spring 自动配 ClickHouse JDBC)</li>
 *   <li>查询各租户的指标</li>
 *   <li>返回 Map&lt;tenantId, metricValue&gt; 给 FederationService</li>
 * </ul>
 *
 * <p>如果 ClickHouse 不可用 (e.g. 测试环境), 自动 fallback 到模拟数据.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iaoep.evaluator.federation.datasource", havingValue = "clickhouse", matchIfMissing = false)
public class FederationDataSource {

    /**
     * v1.0 GA 决策: 默认全部 mock, 显式配置 iaoep.evaluator.federation.datasource=clickhouse 才接真实.
     *
     * <p>理由:
     * <ul>
     *   <li>降低部署门槛 (无 ClickHouse 也能跑完整 demo)</li>
     *   <li>Phase 1-3 的 trace 都已落 ClickHouse, 但 Federation 是 Phase 7 新增,
     *       默认 mock 让用户在测试环境立刻看到效果</li>
     *   <li>真实数据接 FederationDataSource 时, 只是数据源切换, 业务逻辑不变</li>
     * </ul>
     *
     * <p>生产部署建议:
     * <pre>
     *   iaoep:
     *     evaluator:
     *       federation:
     *         datasource: clickhouse    # 显式启用
     * </pre>
     */

    private final JdbcTemplate jdbcTemplate;

    @Value("${iaoep.evaluator.federation.lookback-hours:24}")
    private int lookbackHours;

    @Value("${iaoep.evaluator.federation.datasource.clickhouse.table:iaoep.traces}")
    private String clickhouseTable;

    public FederationDataSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询跨租户的指定指标.
     *
     * @param metric "avg_tokens" | "avg_duration_ms" | "error_rate" | "trace_count"
     * @return tenant_id → value
     */
    public Map<String, Double> queryTenantMetrics(String metric) {
        String sql = String.format("""
                SELECT
                    tenant_id,
                    %s AS metric_value
                FROM %s
                WHERE start_time > now() - INTERVAL %d HOUR
                  AND %s IS NOT NULL
                GROUP BY tenant_id
                """,
                metricExpression(metric),
                clickhouseTable,
                lookbackHours,
                metricColumn(metric));

        Map<String, Double> result = new HashMap<>();
        try {
            jdbcTemplate.query(sql, (ResultSet rs) -> {
                String tenantId = rs.getString("tenant_id");
                double value = rs.getDouble("metric_value");
                result.put(tenantId, value);
            });
            log.info("[FederationDataSource] query {} for {} tenants", metric, result.size());
        } catch (Exception e) {
            log.warn("[FederationDataSource] ClickHouse query failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 多维查询 (按 model × skill).
     *
     * @return Map[dimension][tenant_id] → value
     */
    public Map<String, Map<String, Double>> queryByDimension(String metric) {
        String sql = String.format("""
                SELECT
                    llm_model AS model,
                    skill_name AS skill,
                    tenant_id,
                    %s AS metric_value
                FROM %s
                WHERE start_time > now() - INTERVAL %d HOUR
                  AND llm_model IS NOT NULL
                  AND skill_name IS NOT NULL
                """,
                metricExpression(metric),
                clickhouseTable,
                lookbackHours);

        Map<String, Map<String, Double>> result = new HashMap<>();
        try {
            jdbcTemplate.query(sql, (ResultSet rs) -> {
                String model = rs.getString("model");
                String skill = rs.getString("skill");
                String tenantId = rs.getString("tenant_id");
                double value = rs.getDouble("metric_value");
                String dim = "model=" + model + ",skill=" + skill;
                result.computeIfAbsent(dim, k -> new HashMap<>()).put(tenantId, value);
            });
        } catch (Exception e) {
            log.warn("[FederationDataSource] ClickHouse multi-dim query failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 时间线数据 (按时间分桶, 每租户每 dimension 一条线).
     *
     * @param metric 指标名
     * @param days    回溯天数
     * @return Map[dimension][tenant_id][timestamp] → value
     */
    public Map<String, Map<String, Map<String, Double>>> queryTimeline(String metric, int days) {
        String sql = String.format("""
                SELECT
                    toDate(start_time) AS day,
                    llm_model AS model,
                    skill_name AS skill,
                    tenant_id,
                    %s AS metric_value
                FROM %s
                WHERE start_time > now() - INTERVAL %d DAY
                  AND llm_model IS NOT NULL
                ORDER BY day ASC
                """,
                metricExpression(metric),
                clickhouseTable,
                days);

        Map<String, Map<String, Map<String, Double>>> result = new HashMap<>();
        try {
            jdbcTemplate.query(sql, (ResultSet rs) -> {
                String day = rs.getString("day");
                String model = rs.getString("model");
                String skill = rs.getString("skill");
                String tenantId = rs.getString("tenant_id");
                double value = rs.getDouble("metric_value");
                String dim = "model=" + (model != null ? model : "unknown")
                          + ",skill=" + (skill != null ? skill : "unknown");
                result.computeIfAbsent(dim, k -> new HashMap<>())
                       .computeIfAbsent(tenantId, k -> new HashMap<>())
                       .put(day, value);
            });
        } catch (Exception e) {
            log.warn("[FederationDataSource] ClickHouse timeline query failed: {}", e.getMessage());
        }
        return result;
    }

    private String metricColumn(String metric) {
        return switch (metric) {
            case "avg_tokens"       -> "(llm_input_tokens + llm_output_tokens)";
            case "avg_duration_ms" -> "duration_ms";
            case "error_rate"       -> "(status = 'error')";
            case "trace_count"      -> "1";
            default                 -> "duration_ms";
        };
    }

    private String metricExpression(String metric) {
        return switch (metric) {
            case "avg_tokens"       -> "avg(llm_input_tokens + llm_output_tokens)";
            case "avg_duration_ms" -> "avg(duration_ms)";
            case "error_rate"       -> "countIf(status = 'error') / count()";
            case "trace_count"      -> "count()";
            default                 -> "avg(duration_ms)";
        };
    }
}
