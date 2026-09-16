package io.iaoep.evaluator.scorer.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.iaoep.evaluator.scorer.Scorer;
import io.iaoep.evaluator.scorer.ScorerException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * JSON 格式 Scorer — 验证 trace 输出是否符合 JSON Schema.
 *
 * <p>Config:
 * <pre>
 * {
 *   "schema": {                  // JSON Schema 字符串
 *     "type": "object",
 *     "required": ["answer"],
 *     "properties": {
 *       "answer": { "type": "string" }
 *     }
 *   },
 *   "scope": "agent_output"      // 取哪个字段做校验, 默认 agent_output
 * }
 * </pre>
 *
 * <p>评分: 严格匹配返回 1.0, 否则 0.0.
 *
 * <p>注: 简化实现 — 用 Jackson 解析 + 基础结构校验.
 * 完整 JSON Schema 校验 (Phase 2 后续可用 json-schema-validator).
 */
@Component
public class JsonFormatScorer implements Scorer {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() { return "json_format"; }

    @Override
    public String description() { return "验证 trace 输出是否符合 JSON Schema (简化版)"; }

    @Override
    public double score(Map<String, Object> trace, Map<String, Object> config) {
        Object schemaObj = config.get("schema");
        if (schemaObj == null) {
            throw new ScorerException("missing config: schema");
        }
        String scope = (String) config.getOrDefault("scope", "agent_output");

        // 提取输出文本
        String outputText = extractOutputText(trace, scope);
        if (outputText == null || outputText.isBlank()) {
            return 0.0;
        }

        // 1. 必须是合法 JSON
        JsonNode parsed;
        try {
            parsed = mapper.readTree(outputText);
        } catch (Exception e) {
            return 0.0;  // 不是合法 JSON
        }
        if (!parsed.isObject()) {
            return 0.0;  // 不是 object
        }

        // 2. 校验 schema (基础校验)
        JsonNode schemaNode = mapper.valueToTree(schemaObj);
        JsonNode required = schemaNode.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode field : required) {
                if (!parsed.has(field.asText())) {
                    return 0.0;  // 缺少必填字段
                }
            }
        }

        // 3. 校验 properties 类型
        JsonNode properties = schemaNode.get("properties");
        if (properties != null && properties.isObject()) {
            IteratorSupport.fields(properties).forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode expectedType = entry.getValue().get("type");
                if (expectedType == null) return;
                JsonNode actual = parsed.get(fieldName);
                if (actual == null) return;
                if (!matchesType(actual, expectedType.asText())) {
                    throw new ScorerException(
                            "field '" + fieldName + "' type mismatch: expected " + expectedType);
                }
            });
        }

        return 1.0;
    }

    private String extractOutputText(Map<String, Object> trace, String scope) {
        Object spans = trace.get("spans");
        if (spans instanceof java.util.List<?> list) {
            for (Object s : list) {
                if (s instanceof Map<?, ?> m && m.get(scope) instanceof String str) {
                    return str;
                }
            }
        }
        return null;
    }

    private boolean matchesType(JsonNode node, String expectedType) {
        return switch (expectedType) {
            case "string" -> node.isTextual();
            case "number" -> node.isNumber();
            case "integer" -> node.isIntegralNumber();
            case "boolean" -> node.isBoolean();
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "null" -> node.isNull();
            default -> true;  // 未知类型放行
        };
    }

    /** 简易 helper: fields() iterator (避免导入整个 json-schema-validator) */
    private static class IteratorSupport {
        static java.util.Iterator<Map.Entry<String, JsonNode>> fields(JsonNode obj) {
            return obj.fields();
        }
    }
}
