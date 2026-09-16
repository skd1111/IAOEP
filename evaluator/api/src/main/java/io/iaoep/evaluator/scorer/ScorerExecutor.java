package io.iaoep.evaluator.scorer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scorer 执行器 — 批量运行多个 Scorer, 返回聚合结果.
 *
 * <p>输入:
 * <pre>
 * {
 *   "scorers": [
 *     { "name": "latency", "config": { "p95_threshold_ms": 2000 }, "weight": 1.0 },
 *     { "name": "cost",    "config": { "max_cost_cny": 0.05 },     "weight": 0.5 }
 *   ]
 * }
 * </pre>
 *
 * <p>输出:
 * <pre>
 * {
 *   "total": 0.85,
 *   "details": {
 *     "latency": { "score": 0.9, "weight": 1.0 },
 *     "cost":    { "score": 0.7, "weight": 0.5 }
 *   }
 * }
 * </pre>
 *
 * <p>加权聚合: {@code total = sum(score * weight) / sum(weight)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScorerExecutor {

    private final ScorerRegistry registry;

    /** 单条 Scorer 配置 (用于构建执行计划) */
    public record ScorerConfig(String name, Map<String, Object> config, double weight) {
        public ScorerConfig(String name, Map<String, Object> config, double weight) {
            this.name = name;
            this.config = config != null ? config : Map.of();
            this.weight = weight == 0 ? 1.0 : weight;
        }
    }

    /** 单条 Scorer 执行结果 */
    public record ScorerResult(String name, double score, double weight, String error) {
        public static ScorerResult success(String name, double score, double weight) {
            return new ScorerResult(name, score, weight, null);
        }
        public static ScorerResult failure(String name, double weight, String error) {
            return new ScorerResult(name, 0.0, weight, error);
        }
    }

    /** 聚合执行结果 */
    public record ExecutionResult(double total, Map<String, ScorerResult> details) {}

    /**
     * 执行多个 Scorer 并聚合.
     */
    public ExecutionResult execute(Map<String, Object> trace, List<ScorerConfig> configs) {
        Map<String, ScorerResult> details = new HashMap<>();
        double weightedSum = 0.0;
        double weightTotal = 0.0;

        for (ScorerConfig config : configs) {
            Scorer scorer = registry.find(config.name())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown scorer: " + config.name()));

            ScorerResult result;
            try {
                scorer.validateConfig(config.config());
                double score = scorer.score(trace, config.config());
                if (score < 0.0 || score > 1.0) {
                    throw new ScorerException(
                            "Scorer returned out-of-range score: " + score);
                }
                result = ScorerResult.success(config.name(), score, config.weight());
            } catch (Exception e) {
                log.warn("Scorer '{}' failed: {}", config.name(), e.getMessage());
                result = ScorerResult.failure(config.name(), config.weight(), e.getMessage());
            }

            details.put(config.name(), result);
            weightedSum += result.score() * result.weight();
            weightTotal += result.weight();
        }

        double total = weightTotal > 0 ? weightedSum / weightTotal : 0.0;
        return new ExecutionResult(total, details);
    }
}
