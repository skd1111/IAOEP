package io.iaoep.evaluator.scorer.builtin;

import io.iaoep.evaluator.scorer.Scorer;
import io.iaoep.evaluator.scorer.ScorerException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 延迟 Scorer — 检查 trace 的 P50 / P95 / P99 是否在阈值内.
 *
 * <p>Config:
 * <pre>
 * {
 *   "p50_threshold_ms": 500,    // P50 应 ≤ 500ms
 *   "p95_threshold_ms": 2000,   // P95 应 ≤ 2000ms
 *   "p99_threshold_ms": 5000    // P99 应 ≤ 5000ms
 * }
 * </pre>
 *
 * <p>评分:
 * <ul>
 *   <li>三个分位都达标 → 1.0</li>
 *   <li>每个分位独立评分 (0 或 1), 加权平均</li>
 *   <li>默认权重 p50=0.3 / p95=0.4 / p99=0.3</li>
 * </ul>
 */
@Component
public class LatencyScorer implements Scorer {

    @Override
    public String name() { return "latency"; }

    @Override
    public String description() { return "检查 trace 延迟分位数 (P50/P95/P99) 是否在阈值内"; }

    @Override
    public double score(Map<String, Object> trace, Map<String, Object> config) {
        List<Integer> durations = extractDurations(trace);
        if (durations.isEmpty()) {
            throw new ScorerException("no spans found in trace");
        }
        Collections.sort(durations);

        int p50Threshold = getInt(config, "p50_threshold_ms", Integer.MAX_VALUE);
        int p95Threshold = getInt(config, "p95_threshold_ms", Integer.MAX_VALUE);
        int p99Threshold = getInt(config, "p99_threshold_ms", Integer.MAX_VALUE);

        double p50Score = durations.get(p50Index(durations.size())) <= p50Threshold ? 1.0 : 0.0;
        double p95Score = durations.get(p95Index(durations.size())) <= p95Threshold ? 1.0 : 0.0;
        double p99Score = durations.get(p99Index(durations.size())) <= p99Threshold ? 1.0 : 0.0;

        return p50Score * 0.3 + p95Score * 0.4 + p99Score * 0.3;
    }

    private List<Integer> extractDurations(Map<String, Object> trace) {
        List<Integer> out = new ArrayList<>();
        Object spans = trace.get("spans");
        if (spans instanceof List<?> list) {
            for (Object s : list) {
                if (s instanceof Map<?, ?> m && m.get("duration_ms") instanceof Number n) {
                    out.add(n.intValue());
                }
            }
        }
        return out;
    }

    private int p50Index(int size) {
        return Math.min((int) Math.ceil(size * 0.5) - 1, size - 1);
    }

    private int p95Index(int size) {
        return Math.min((int) Math.ceil(size * 0.95) - 1, size - 1);
    }

    private int p99Index(int size) {
        return Math.min((int) Math.ceil(size * 0.99) - 1, size - 1);
    }

    private int getInt(Map<String, Object> config, String key, int def) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }
}
