package io.iaoep.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * IAOEP StandardSpan — Ingest Gateway 推送到 Kafka 的标准格式。
 *
 * <p>Schema 与 {@code docker-compose/clickhouse/init.sql} 中 {@code iaoep.traces} 表字段对齐。
 * 字段命名与 Kafka JSON 消息一致 (snake_case)。</p>
 *
 * <p>设计取舍: 用 {@code @JsonIgnoreProperties(ignoreUnknown = true)} 容错,
 * 即使 Ingest Gateway 增加新字段也不会导致 Consumer 崩溃。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StandardSpan {

    // 标识
    private String tenantId;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String spanName;
    private String spanKind;

    // 时间
    private Long startTimeUnixNano;
    private Long endTimeUnixNano;
    private Integer durationMs;

    // Agent
    private String agentName;
    private String sessionId;
    private String userId;
    private String skillName;

    // LLM
    private String llmSystem;
    private String llmModel;
    private Long llmInputTokens;
    private Long llmOutputTokens;

    // Tool
    private String toolName;
    private String toolCallId;
    private String toolErrorType;

    // 成本
    private Double costCny;

    // 状态
    private String status;
    private String errorMessage;

    // 灵活扩展
    private Map<String, String> tags;
    private Map<String, String> attributes;

    // ============================================================
    // JSON 字段映射 (Java camelCase → JSON snake_case)
    // ============================================================
    @JsonProperty("tenant_id")
    public String getTenantId() { return tenantId; }
    @JsonProperty("trace_id")
    public String getTraceId() { return traceId; }
    @JsonProperty("span_id")
    public String getSpanId() { return spanId; }
    @JsonProperty("parent_span_id")
    public String getParentSpanId() { return parentSpanId; }
    @JsonProperty("span_name")
    public String getSpanName() { return spanName; }
    @JsonProperty("span_kind")
    public String getSpanKind() { return spanKind; }

    @JsonProperty("start_time_unix_nano")
    public Long getStartTimeUnixNano() { return startTimeUnixNano; }
    @JsonProperty("end_time_unix_nano")
    public Long getEndTimeUnixNano() { return endTimeUnixNano; }
    @JsonProperty("duration_ms")
    public Integer getDurationMs() { return durationMs; }

    @JsonProperty("agent_name")
    public String getAgentName() { return agentName; }
    @JsonProperty("session_id")
    public String getSessionId() { return sessionId; }
    @JsonProperty("user_id")
    public String getUserId() { return userId; }
    @JsonProperty("skill_name")
    public String getSkillName() { return skillName; }

    @JsonProperty("llm_system")
    public String getLlmSystem() { return llmSystem; }
    @JsonProperty("llm_model")
    public String getLlmModel() { return llmModel; }
    @JsonProperty("llm_input_tokens")
    public Long getLlmInputTokens() { return llmInputTokens; }
    @JsonProperty("llm_output_tokens")
    public Long getLlmOutputTokens() { return llmOutputTokens; }

    @JsonProperty("tool_name")
    public String getToolName() { return toolName; }
    @JsonProperty("tool_call_id")
    public String getToolCallId() { return toolCallId; }
    @JsonProperty("tool_error_type")
    public String getToolErrorType() { return toolErrorType; }

    @JsonProperty("cost_cny")
    public Double getCostCny() { return costCny; }

    @JsonProperty("status")
    public String getStatus() { return status; }
    @JsonProperty("error_message")
    public String getErrorMessage() { return errorMessage; }
}
