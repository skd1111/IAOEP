package iaoep

import (
	"context"
	"crypto/rand"
	"fmt"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/trace"
)

// ObservedAgent 装饰一个 Agent 方法, 自动产生 agent.run Span.
//
// 用法:
//
//	result, err := iaoep.ObservedAgent(ctx, "customer-service", "general",
//	    func(ctx context.Context) (string, error) {
//	        return callLLM(ctx, query)
//	    })
//
// 参数:
//   - name:   Agent 名 (必填, 作为 agent.name 属性)
//   - skill:  Skill 名 (可选)
//   - fn:     实际函数
func ObservedAgent[T any](ctx context.Context, name, skill string, fn func(context.Context) (T, error)) (T, error) {
	tracer := Tracer().(trace.Tracer)
	ctx, span := tracer.Start(ctx, "agent.run",
		trace.WithSpanKind(trace.SpanKindInternal),
	)
	defer span.End()

	span.SetAttributes(attribute.String("agent.name", name))
	if skill != "" {
		span.SetAttributes(attribute.String("agent.skill.name", skill))
	}

	result, err := fn(ctx)
	if err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return result, err
	}
	span.SetStatus(codes.Ok, "")
	return result, nil
}

// ObservedLLMCall 装饰一个 LLM 调用方法, 产生 llm.call Span.
//
// 用法:
//
//	resp, err := iaoep.ObservedLLMCall(ctx, "openai", "qwen3-turbo", 120, 80,
//	    func(ctx context.Context) (string, error) {
//	        return openaiClient.Chat(model, messages)
//	    })
//
// 参数:
//   - system:        LLM 供应商 (openai / dashscope / glm / deepseek)
//   - model:        模型名
//   - inputTokens:  输入 token 数 (可选, 0 = 跳过)
//   - outputTokens: 输出 token 数 (可选, 0 = 跳过)
//   - fn:            实际 LLM 调用
func ObservedLLMCall[T any](ctx context.Context, system, model string,
	inputTokens, outputTokens int,
	fn func(context.Context) (T, error)) (T, error) {

	tracer := Tracer().(trace.Tracer)
	ctx, span := tracer.Start(ctx, "llm.call",
		trace.WithSpanKind(trace.SpanKindClient),
	)
	defer span.End()

	span.SetAttributes(
		attribute.String("gen_ai.system", system),
		attribute.String("gen_ai.request.model", model),
	)
	if inputTokens > 0 {
		span.SetAttributes(attribute.Int("gen_ai.usage.input_tokens", inputTokens))
	}
	if outputTokens > 0 {
		span.SetAttributes(attribute.Int("gen_ai.usage.output_tokens", outputTokens))
	}

	result, err := fn(ctx)
	if err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return result, err
	}
	span.SetStatus(codes.Ok, "")
	return result, nil
}

// ObservedTool 装饰一个工具调用方法, 产生 tool.execute Span.
func ObservedTool[T any](ctx context.Context, name string,
	fn func(context.Context) (T, error)) (T, error) {

	tracer := Tracer().(trace.Tracer)
	ctx, span := tracer.Start(ctx, "tool.execute",
		trace.WithSpanKind(trace.SpanKindInternal),
	)
	defer span.End()

	span.SetAttributes(
		attribute.String("tool.name", name),
		attribute.String("tool.call.id", randomUUID()),
	)

	result, err := fn(ctx)
	if err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return result, err
	}
	span.SetStatus(codes.Ok, "")
	return result, nil
}

// ObservedABTest 装饰一个方法, 根据 trace_id 分配 baseline/candidate (Phase 4-5).
//
// 用法:
//
//	result, err := iaoep.ObservedABTest(ctx, "customer-service-ab",
//	    func(ctx context.Context) (string, error) {
//	        group := iaoep.ABTestGroupFromContext(ctx)
//	        // 根据 group 选不同实现
//	        return callLLM(ctx, modelForGroup(group))
//	    })
func ObservedABTest[T any](ctx context.Context, abTestName string,
	fn func(context.Context) (T, error)) (T, error) {

	group := "baseline"   // 简化: 实际从 evaluator API 调 /assign

	tracer := Tracer().(trace.Tracer)
	ctx, span := tracer.Start(ctx, "ab.test",
		trace.WithSpanKind(trace.SpanKindInternal),
	)
	defer span.End()

	span.SetAttributes(
		attribute.String("ab_test_name", abTestName),
		attribute.String("ab_test_group", group),
	)

	ctx = contextWithABGroup(ctx, group)
	result, err := fn(ctx)
	if err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return result, err
	}
	span.SetStatus(codes.Ok, "")
	return result, nil
}

// ABTestGroupFromContext 从 context 读当前 A/B Test group.
func ABTestGroupFromContext(ctx context.Context) string {
	if v := ctx.Value(abTestGroupKey); v != nil {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return "baseline"   // 兜底
}

// ---------- private helpers ----------

type contextKey string

const abTestGroupKey contextKey = "iaoep.ab_test_group"

func contextWithABGroup(ctx context.Context, group string) context.Context {
	return context.WithValue(ctx, abTestGroupKey, group)
}

// randomUUID 生成 UUID v4 (使用 crypto/rand).
func randomUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40 // version 4
	b[8] = (b[8] & 0x3f) | 0x80 // variant 10
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
