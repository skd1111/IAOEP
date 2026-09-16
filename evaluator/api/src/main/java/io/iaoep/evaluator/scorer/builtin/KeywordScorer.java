package io.iaoep.evaluator.scorer.builtin;

import io.iaoep.evaluator.scorer.Scorer;
import io.iaoep.evaluator.scorer.ScorerException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 关键词 Scorer — 检查 trace 输出是否包含 / 不包含特定关键词.
 *
 * <p>Config:
 * <pre>
 * {
 *   "must_include": ["IAOEP", "可观测性"],   // 必含
 *   "must_exclude": ["错误", "fail"],        // 必不含
 *   "case_sensitive": false                  // 默认 false
 * }
 * </pre>
 *
 * <p>评分:
 * <ul>
 *   <li>每条规则独立评分 (0 或 1)</li>
 *   <li>总分 = 通过的规则数 / 总规则数</li>
 * </ul>
 */
@Component
public class KeywordScorer implements Scorer {

    @Override
    public String name() { return "keyword"; }

    @Override
    public String description() { return "检查 trace 输出是否包含 / 不包含特定关键词"; }

    @Override
    public double score(Map<String, Object> trace, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<String> mustInclude = (List<String>) config.getOrDefault("must_include", List.of());
        @SuppressWarnings("unchecked")
        List<String> mustExclude = (List<String>) config.getOrDefault("must_exclude", List.of());
        boolean caseSensitive = Boolean.TRUE.equals(config.get("case_sensitive"));

        String output = extractOutput(trace);
        String normalized = caseSensitive ? output : output.toLowerCase();

        int totalRules = mustInclude.size() + mustExclude.size();
        if (totalRules == 0) {
            throw new ScorerException("keyword scorer requires must_include or must_exclude");
        }

        int passed = 0;

        // must_include: 所有关键词都必须出现
        boolean allIncluded = mustInclude.stream()
                .map(kw -> caseSensitive ? kw : kw.toLowerCase())
                .allMatch(normalized::contains);
        if (allIncluded) passed += mustInclude.size();

        // must_exclude: 所有关键词都不能出现
        boolean noneExcluded = mustExclude.stream()
                .map(kw -> caseSensitive ? kw : kw.toLowerCase())
                .noneMatch(normalized::contains);
        if (noneExcluded) passed += mustExclude.size();

        return (double) passed / totalRules;
    }

    /** 从 trace 提取所有 span 的输出文本 */
    private String extractOutput(Map<String, Object> trace) {
        StringBuilder sb = new StringBuilder();
        Object spans = trace.get("spans");
        if (spans instanceof List<?> list) {
            for (Object s : list) {
                if (s instanceof Map<?, ?> m && m.get("agent_output") instanceof String str) {
                    sb.append(str).append("\n");
                }
            }
        }
        return sb.toString();
    }
}
