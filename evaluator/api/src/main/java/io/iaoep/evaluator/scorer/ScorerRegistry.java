package io.iaoep.evaluator.scorer;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scorer 注册中心 — 自动发现 Spring 容器中所有 {@link Scorer} 实现.
 *
 * <p>使用方式:
 * <pre>
 * &#64;Autowired
 * private ScorerRegistry registry;
 *
 * Scorer latencyScorer = registry.get("latency");
 * double score = latencyScorer.score(trace, config);
 * </pre>
 *
 * <p>扩展: 在新 Scorer 类上加 {@code @Component} + 实现 {@link Scorer}, 启动时自动注册.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScorerRegistry {

    private final List<Scorer> scorers;

    private final Map<String, Scorer> byName = new HashMap<>();

    @PostConstruct
    void init() {
        for (Scorer scorer : scorers) {
            String name = scorer.name();
            if (byName.put(name, scorer) != null) {
                log.warn("Scorer name collision: '{}', overriding", name);
            }
            log.info("Registered Scorer: {} - {}", name, scorer.description());
        }
        log.info("Total scorers registered: {}", byName.size());
    }

    public Optional<Scorer> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Scorer get(String name) {
        Scorer scorer = byName.get(name);
        if (scorer == null) {
            throw new IllegalArgumentException("Unknown scorer: " + name);
        }
        return scorer;
    }

    public List<String> listNames() {
        return List.copyOf(byName.keySet());
    }
}
