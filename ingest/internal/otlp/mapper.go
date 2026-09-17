// Package otlp 负责 OTLP Span → IAOEP 标准字段映射。
//
// 参考:
//   - OTel GenAI Semantic Conventions 1.0+
//   - docs/agentops-platform-design.md § 6 数据模型
//
// 关键 Span 类型:
//   - agent.run      (顶层,含 total tokens / cost / status)
//   - agent.turn.*   (每个 turn)
//   - llm.call       (含 model / tokens / cost)
//   - tool.execute   (含 tool name / args / result / error)
package otlp

import (
	"strconv"

	coltracepb "go.opentelemetry.io/proto/otlp/collector/trace/v1"
	tracepb "go.opentelemetry.io/proto/otlp/trace/v1"
)

// StandardSpan 是 IAOEP 标准化的 Span 格式,推送到 Kafka 后
// 由 Storage Worker 写入 ClickHouse `iaoep.traces` 表。
type StandardSpan struct {
	// 标识
	TenantID   string `json:"tenant_id"`
	TraceID    string `json:"trace_id"`
	SpanID     string `json:"span_id"`
	ParentSpanID string `json:"parent_span_id,omitempty"`
	SpanName   string `json:"span_name"`
	SpanKind   string `json:"span_kind"`

	// 时间 (纳秒)
	StartTimeUnixNano int64 `json:"start_time_unix_nano"`
	EndTimeUnixNano   int64 `json:"end_time_unix_nano"`
	DurationMs        int32  `json:"duration_ms"`

	// Agent 字段
	AgentName string `json:"agent_name,omitempty"`
	SessionID string `json:"session_id,omitempty"`
	UserID    string `json:"user_id,omitempty"`
	SkillName string `json:"skill_name,omitempty"`

	// LLM 字段
	LLMSystem       string `json:"llm_system,omitempty"`
	LLMModel        string `json:"llm_model,omitempty"`
	LLMInputTokens  uint32 `json:"llm_input_tokens,omitempty"`
	LLMOutputTokens uint32 `json:"llm_output_tokens,omitempty"`

	// Tool 字段
	ToolName       string `json:"tool_name,omitempty"`
	ToolCallID     string `json:"tool_call_id,omitempty"`
	ToolErrorType  string `json:"tool_error_type,omitempty"`

	// 成本 (默认 CNY)
	CostCNY float64 `json:"cost_cny,omitempty"`

	// 状态
	Status       string `json:"status"`
	ErrorMessage string `json:"error_message,omitempty"`

	// Phase 5: A/B Test (SDK 注入 OTLP attribute 透传)
	ABTestName  string `json:"ab_test_name,omitempty"`
	ABTestGroup string `json:"ab_test_group,omitempty"`

	// 灵活扩展
	Tags       map[string]string `json:"tags,omitempty"`
	Attributes map[string]string `json:"attributes,omitempty"`
}

// MapSpans 把 OTLP ExportTraceServiceRequest 转换为 []StandardSpan。
//
// 处理流程:
//   1. 遍历 resource_spans → scope_spans → spans
//   2. 从 resource.attributes 提取 tenant_id / service.name
//   3. 从 span.attributes 提取 IAOEP 标准字段 (gen_ai.* / agent.* / tool.*)
//   4. 派生字段: duration_ms / status / cost (若 spans 含 cost)
func MapSpans(req *coltracepb.ExportTraceServiceRequest, defaultTenantID string) []StandardSpan {
	var result []StandardSpan

	for _, rs := range req.GetResourceSpans() {
		// 从 resource.attributes 提取 tenant_id 和 service.name
		tenantID := defaultTenantID
		serviceName := ""
		if rs.GetResource() != nil {
			for _, kv := range rs.GetResource().GetAttributes() {
				switch kv.GetKey() {
				case "tenant.id", "iaoep.tenant_id":
					tenantID = kv.GetValue().GetStringValue()
				case "service.name":
					serviceName = kv.GetValue().GetStringValue()
				}
			}
		}

		for _, ss := range rs.GetScopeSpans() {
			for _, span := range ss.GetSpans() {
				result = append(result, mapSpan(span, tenantID, serviceName))
			}
		}
	}

	return result
}

// mapSpan 把单个 OTLP Span 转换为 StandardSpan。
func mapSpan(span *tracepb.Span, tenantID, serviceName string) StandardSpan {
	out := StandardSpan{
		TenantID:   tenantID,
		TraceID:    formatHex(span.GetTraceId()),
		SpanID:     formatHex(span.GetSpanId()),
		ParentSpanID: formatHex(span.GetParentSpanId()),
		SpanName:   span.GetName(),
		SpanKind:   spanKindString(span.GetKind()),
		StartTimeUnixNano: int64(span.GetStartTimeUnixNano()),
		EndTimeUnixNano:   int64(span.GetEndTimeUnixNano()),
		DurationMs: durationMs(span.GetStartTimeUnixNano(), span.GetEndTimeUnixNano()),
		Attributes: map[string]string{},
	}

	// 默认 agent_name = service.name (若 Span name 是 agent.*)
	if serviceName != "" {
		out.AgentName = serviceName
	}

	// 从 attributes 提取 IAOEP 字段
	for _, kv := range span.GetAttributes() {
		key := kv.GetKey()
		value := attrValueToString(kv.GetValue())
		out.Attributes[key] = value

		switch key {
		// Agent
		case "agent.name":
			out.AgentName = value
		case "session.id", "gen_ai.conversation.id":
			out.SessionID = value
		case "user.id", "iaoep.user_id":
			out.UserID = value
		case "agent.skill.name", "iaoep.skill_name":
			out.SkillName = value

		// LLM
		case "gen_ai.system":
			out.LLMSystem = value
		case "gen_ai.request.model":
			out.LLMModel = value
		case "gen_ai.usage.input_tokens":
			out.LLMInputTokens = uint32Value(value)
		case "gen_ai.usage.output_tokens":
			out.LLMOutputTokens = uint32Value(value)

		// Tool
		case "tool.name":
			out.ToolName = value
		case "tool.call.id":
			out.ToolCallID = value
		case "tool.error.type":
			out.ToolErrorType = value

		// 成本
		case "gen_ai.cost.cny":
			out.CostCNY = floatValue(value)
		case "gen_ai.cost.usd":
			out.CostCNY = floatValue(value) * 7.2 // USD → CNY 近似汇率

		// Phase 5: A/B Test
		case "ab_test_name", "iaoep.ab_test_name":
			out.ABTestName = value
		case "ab_test_group", "iaoep.ab_test_group":
			out.ABTestGroup = value
		}
	}

	// Status
	switch span.GetStatus().GetCode() {
	case 1: // STATUS_CODE_OK
		out.Status = "success"
	case 2: // STATUS_CODE_ERROR
		out.Status = "error"
		out.ErrorMessage = span.GetStatus().GetMessage()
	default:
		out.Status = "unset"
	}
	if out.Status == "unset" && span.GetEndTimeUnixNano() == 0 {
		out.Status = "timeout"
	}

	return out
}

// ---------- helpers ----------

// formatHex 把 protobuf bytes 转为十六进制字符串 (OTLP trace_id / span_id 是 8/16 字节二进制)
func formatHex(b []byte) string {
	if len(b) == 0 {
		return ""
	}
	const hex = "0123456789abcdef"
	out := make([]byte, len(b)*2)
	for i, v := range b {
		out[i*2] = hex[v>>4]
		out[i*2+1] = hex[v&0x0f]
	}
	return string(out)
}

// spanKindString 把 OTLP SpanKind 转为字符串
func spanKindString(k tracepb.Span_SpanKind) string {
	switch k {
	case tracepb.Span_SPAN_KIND_INTERNAL:
		return "INTERNAL"
	case tracepb.Span_SPAN_KIND_CLIENT:
		return "CLIENT"
	case tracepb.Span_SPAN_KIND_SERVER:
		return "SERVER"
	case tracepb.Span_SPAN_KIND_PRODUCER:
		return "PRODUCER"
	case tracepb.Span_SPAN_KIND_CONSUMER:
		return "CONSUMER"
	default:
		return "UNSPECIFIED"
	}
}

// attrValueToString 把 OTLP AnyValue 转为字符串
func attrValueToString(v *tracepb.AnyValue) string {
	if v == nil {
		return ""
	}
	switch v.GetValue().(type) {
	case *tracepb.AnyValue_StringValue:
		return v.GetStringValue()
	case *tracepb.AnyValue_BoolValue:
		return strconv.FormatBool(v.GetBoolValue())
	case *tracepb.AnyValue_IntValue:
		return strconv.FormatInt(v.GetIntValue(), 10)
	case *tracepb.AnyValue_DoubleValue:
		return strconv.FormatFloat(v.GetDoubleValue(), 'f', -1, 64)
	case *tracepb.AnyValue_ArrayValue:
		// 简化: 返回 JSON 数组字符串
		return v.GetArrayValue().String()
	case *tracepb.AnyValue_KvlistValue:
		return v.GetKvlistValue().String()
	default:
		return ""
	}
}

func uint32Value(s string) uint32 {
	if v, err := strconv.ParseUint(s, 10, 32); err == nil {
		return uint32(v)
	}
	return 0
}

func floatValue(s string) float64 {
	if v, err := strconv.ParseFloat(s, 64); err == nil {
		return v
	}
	return 0
}

func durationMs(startNs, endNs uint64) int32 {
	if endNs <= startNs {
		return 0
	}
	return int32((endNs - startNs) / 1_000_000)
}
