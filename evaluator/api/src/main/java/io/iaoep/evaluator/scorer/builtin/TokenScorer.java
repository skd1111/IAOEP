package io.iaoep.evaluator.scorer.builtin;

import io.iaoep.evaluator.scorer.Scorer;
import io.iaoep.evaluator.scorer.ScorerException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Token Scorer — 检查 trace 总 token 用量是否在阈值内.
 *
 * <p>Config:
 * <pre>
 * {
 *   "max_input_tokens": 5000,
 *   "max_output_tokens": 2000
 * }
 * </pre>
 *
 * <p>评分: 各 LLM span 累加 input + output tokens, 与阈值对比.
 * 输入权重 0.4, 输出权重 0.6 (鼓励简洁回复).
 */
@Component
public class TokenScorer implements Scorer {

    @Override
    public String name() { return "token"; }

    @Override
    public String description() { return "检查 trace 总 token 用量是否在阈值内"; }

    @Override
    public double score(Map<String, Object> trace, Map<String, Object> config) {
        long maxInput = getLong(config, "max_input_tokens", Long.MAX_VALUE);
        long maxOutput = getLong(config, "max_output_tokens", Long.MAX_VALUE);

        long totalInput = 0;
        long totalOutput = 0;
        Object spans = trace.get("spans");
        if (spans instanceof List<?> list) {
            for (Object s : list) {
                if (s instanceof Map<?, ?> m && "llm.call".equals(m.get("span_name"))) {
                    if (m.get("llm_input_tokens") instanceof Number n) totalInput += n.longValue();
                    if (m.get("llm_output_tokens") instanceof Number n) totalOutput += n.longValue();
                }
            }
        }

        double inputScore = totalInput <= maxInput ? 1.0 : Math.max(0, 1.0 - (totalInput - maxInput) / (double) maxInput);
        double outputScore = totalOutput <= maxOutput ? 1.0 : Math.max(0, 1.0 - (totalOutput - maxOutput) / (double) maxOutput);
        return inputScore * 0.4 + outputScore * 0.6;
    }

    private long getLong(Map<String, Object> config, String key, long def) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.longValue();
        return def;
    }
}
