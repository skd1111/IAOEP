package io.iaoep.evaluator.scorer.builtin;

import io.iaoep.evaluator.scorer.Scorer;
import io.iaoep.evaluator.scorer.ScorerException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 成本 Scorer — 检查 trace 总成本是否在阈值内.
 *
 * <p>Config:
 * <pre>
 * {
 *   "max_cost_cny": 0.10  // 单次 trace 成本应 ≤ 0.10 CNY
 * }
 * </pre>
 *
 * <p>评分: linear, 0 cost → 1.0, max_cost → 0.0.
 */
@Component
public class CostScorer implements Scorer {

    @Override
    public String name() { return "cost"; }

    @Override
    public String description() { return "检查 trace 总成本 (CNY) 是否在阈值内"; }

    @Override
    public double score(Map<String, Object> trace, Map<String, Object> config) {
        Double maxCost = getDouble(config, "max_cost_cny");
        if (maxCost == null || maxCost <= 0) {
            throw new ScorerException("missing or invalid config: max_cost_cny");
        }

        double totalCost = 0.0;
        Object spans = trace.get("spans");
        if (spans instanceof List<?> list) {
            for (Object s : list) {
                if (s instanceof Map<?, ?> m && m.get("cost_cny") instanceof Number n) {
                    totalCost += n.doubleValue();
                }
            }
        }

        if (totalCost <= 0) return 1.0;          // 无成本 = 满分
        if (totalCost >= maxCost) return 0.0;    // 超出 = 零分
        return 1.0 - (totalCost / maxCost);       // 线性递减
    }

    private Double getDouble(Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }
}
