package io.iaoep.evaluator.scorer;

import io.iaoep.evaluator.scorer.builtin.CostScorer;
import io.iaoep.evaluator.scorer.builtin.JsonFormatScorer;
import io.iaoep.evaluator.scorer.builtin.KeywordScorer;
import io.iaoep.evaluator.scorer.builtin.LatencyScorer;
import io.iaoep.evaluator.scorer.builtin.TokenScorer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scorer 单元测试 (不依赖 Spring 容器, 直接 new + 注册).
 */
class ScorerExecutorTest {

    private final ScorerRegistry registry = new ScorerRegistry() {
        @Override
        void init() {
            // 手动注册, 跳过 Spring
            register(new LatencyScorer());
            register(new CostScorer());
            register(new TokenScorer());
            register(new KeywordScorer());
            register(new JsonFormatScorer());
        }
        void register(Scorer s) {
            // 通过反射注入 (测试环境简化)
            try {
                java.lang.reflect.Field f = ScorerRegistry.class.getDeclaredField("byName");
                f.setAccessible(true);
                ((java.util.Map<String, Scorer>) f.get(this)).put(s.name(), s);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };

    private final ScorerExecutor executor = new ScorerExecutor(registry);

    // ========== LatencyScorer ==========

    @Test
    void latencyAllPass() {
        var scorer = registry.get("latency");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("span_name", "agent.run", "duration_ms", 100),
                Map.of("span_name", "llm.call", "duration_ms", 200),
                Map.of("span_name", "tool.execute", "duration_ms", 300)
        ));
        double score = scorer.score(trace, Map.of(
                "p50_threshold_ms", 500,
                "p95_threshold_ms", 1000,
                "p99_threshold_ms", 2000
        ));
        assertEquals(1.0, score, 0.001);
    }

    @Test
    void latencyP95Fail() {
        var scorer = registry.get("latency");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("span_name", "agent.run", "duration_ms", 100),
                Map.of("span_name", "llm.call", "duration_ms", 3000)  // 超过 p95
        ));
        double score = scorer.score(trace, Map.of(
                "p50_threshold_ms", 1000,
                "p95_threshold_ms", 1000,
                "p99_threshold_ms", 5000
        ));
        assertEquals(0.7, score, 0.001);  // p50 + p99 通过, p95 不通过
    }

    // ========== CostScorer ==========

    @Test
    void costZeroIsPerfect() {
        var scorer = registry.get("cost");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("span_name", "llm.call", "cost_cny", 0.0)
        ));
        assertEquals(1.0, scorer.score(trace, Map.of("max_cost_cny", 0.1)), 0.001);
    }

    @Test
    void costExceedIsZero() {
        var scorer = registry.get("cost");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("span_name", "llm.call", "cost_cny", 0.2)
        ));
        assertEquals(0.0, scorer.score(trace, Map.of("max_cost_cny", 0.1)), 0.001);
    }

    @Test
    void costLinear() {
        var scorer = registry.get("cost");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("span_name", "llm.call", "cost_cny", 0.05)
        ));
        assertEquals(0.5, scorer.score(trace, Map.of("max_cost_cny", 0.1)), 0.001);
    }

    // ========== KeywordScorer ==========

    @Test
    void keywordAllPass() {
        var scorer = registry.get("keyword");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("agent_output", "IAOEP 是可观测性平台")
        ));
        double score = scorer.score(trace, Map.of(
                "must_include", List.of("IAOEP", "可观测性")
        ));
        assertEquals(1.0, score, 0.001);
    }

    @Test
    void keywordFail() {
        var scorer = registry.get("keyword");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("agent_output", "IAOEP 是平台")
        ));
        double score = scorer.score(trace, Map.of(
                "must_include", List.of("IAOEP", "可观测性")
        ));
        assertEquals(0.5, score, 0.001);  // IAOEP 通过, 可观测性 不通过
    }

    @Test
    void keywordExclude() {
        var scorer = registry.get("keyword");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("agent_output", "this is correct")
        ));
        double score = scorer.score(trace, Map.of(
                "must_exclude", List.of("error", "fail")
        ));
        assertEquals(1.0, score, 0.001);
    }

    // ========== JsonFormatScorer ==========

    @Test
    void jsonValid() {
        var scorer = registry.get("json_format");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("agent_output", "{\"answer\": \"42\"}")
        ));
        double score = scorer.score(trace, Map.of(
                "schema", Map.of(
                        "type", "object",
                        "required", List.of("answer"),
                        "properties", Map.of("answer", Map.of("type", "string"))
                )
        ));
        assertEquals(1.0, score, 0.001);
    }

    @Test
    void jsonMissingField() {
        var scorer = registry.get("json_format");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("agent_output", "{\"foo\": 1}")
        ));
        double score = scorer.score(trace, Map.of(
                "schema", Map.of(
                        "type", "object",
                        "required", List.of("answer")
                )
        ));
        assertEquals(0.0, score, 0.001);
    }

    @Test
    void jsonInvalidJson() {
        var scorer = registry.get("json_format");
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("agent_output", "this is not json")
        ));
        double score = scorer.score(trace, Map.of(
                "schema", Map.of("type", "object")
        ));
        assertEquals(0.0, score, 0.001);
    }

    // ========== ScorerExecutor (聚合) ==========

    @Test
    void executorWeighted() {
        var config = List.of(
                new ScorerExecutor.ScorerConfig("cost", Map.of("max_cost_cny", 0.1), 1.0),
                new ScorerExecutor.ScorerConfig("latency", Map.of("p95_threshold_ms", 1000), 1.0)
        );
        Map<String, Object> trace = Map.of("spans", List.of(
                Map.of("span_name", "llm.call", "duration_ms", 100, "cost_cny", 0.05)
        ));
        ScorerExecutor.ExecutionResult result = executor.execute(trace, config);

        // cost: 0.05 / 0.1 → 0.5; latency: 100 ≤ 1000 → 1.0
        // 平均: (0.5 + 1.0) / 2 = 0.75
        assertEquals(0.75, result.total(), 0.001);
        assertEquals(2, result.details().size());
    }

    @Test
    void executorFailureScore() {
        var config = List.of(
                new ScorerExecutor.ScorerConfig("nonexistent_scorer", Map.of(), 1.0)
        );
        assertThrows(IllegalArgumentException.class,
                () -> executor.execute(Map.of(), config));
    }
}
