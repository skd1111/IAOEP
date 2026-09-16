package io.iaoep.sdk.aspect;

import io.iaoep.sdk.annotation.ObservedAgent;
import io.iaoep.sdk.annotation.ObservedLLMCall;
import io.iaoep.sdk.annotation.ObservedTool;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * IAOEP AOP 拦截器 — 拦截带 {@code @ObservedAgent / @ObservedLLMCall / @ObservedTool}
 * 注解的方法,自动创建 OpenTelemetry Span。
 *
 * <p>Span 命名遵循 OTel GenAI SemConv:
 * <ul>
 *   <li>{@code @ObservedAgent} → span name = "agent.run"</li>
 *   <li>{@code @ObservedLLMCall} → span name = "llm.call"</li>
 *   <li>{@code @ObservedTool} → span name = "tool.execute"</li>
 * </ul>
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class ObservedAgentAspect {

    private final OpenTelemetry openTelemetry;

    // ====================== @ObservedAgent ======================

    @Around("@annotation(observedAgent)")
    public Object aroundObservedAgent(ProceedingJoinPoint joinPoint, ObservedAgent observedAgent) throws Throwable {
        Tracer tracer = openTelemetry.getTracer("io.iaoep.sdk", "0.1.0");
        Span span = tracer.spanBuilder("agent.run")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("agent.name", observedAgent.name())
                .startSpan();

        if (!observedAgent.skill().isEmpty()) {
            span.setAttribute("agent.skill.name", observedAgent.skill());
        }

        return executeWithSpan(joinPoint, span, observedAgent.captureInput(),
                observedAgent.captureOutput(), observedAgent.maxPayloadLength());
    }

    // ====================== @ObservedLLMCall ======================

    @Around("@annotation(observedLLMCall)")
    public Object aroundObservedLLMCall(ProceedingJoinPoint joinPoint, ObservedLLMCall observedLLMCall) throws Throwable {
        Tracer tracer = openTelemetry.getTracer("io.iaoep.sdk", "0.1.0");
        Span span = tracer.spanBuilder("llm.call")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.system", observedLLMCall.system())
                .startSpan();

        // 用 SpEL 解析参数表达式 (e.g. "#modelName" → 取 method param "modelName")
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        EvaluationContext context = buildContext(joinPoint, method);

        String modelName = evalString(observedLLMCall.modelParam(), context);
        if (modelName != null && !modelName.isEmpty()) {
            span.setAttribute("gen_ai.request.model", modelName);
        }

        Long inputTokens = evalLong(observedLLMCall.inputTokensParam(), context);
        if (inputTokens != null) {
            span.setAttribute("gen_ai.usage.input_tokens", inputTokens);
        }

        Long outputTokens = evalLong(observedLLMCall.outputTokensParam(), context);
        if (outputTokens != null) {
            span.setAttribute("gen_ai.usage.output_tokens", outputTokens);
        }

        return executeWithSpan(joinPoint, span, false, false, 0);
    }

    // ====================== @ObservedTool ======================

    @Around("@annotation(observedTool)")
    public Object aroundObservedTool(ProceedingJoinPoint joinPoint, ObservedTool observedTool) throws Throwable {
        Tracer tracer = openTelemetry.getTracer("io.iaoep.sdk", "0.1.0");
        Span span = tracer.spanBuilder("tool.execute")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("tool.name", observedTool.name())
                .setAttribute("tool.call.id", UUID.randomUUID().toString())
                .startSpan();

        return executeWithSpan(joinPoint, span, observedTool.captureArguments(),
                false, observedTool.maxPayloadLength());
    }

    // ====================== 通用执行逻辑 ======================

    private Object executeWithSpan(ProceedingJoinPoint joinPoint, Span span,
                                    boolean captureInput, boolean captureOutput, int maxLength) throws Throwable {
        Object result;
        try (Scope scope = span.makeCurrent()) {
            if (captureInput) {
                span.setAttribute("agent.input", truncate(toJson(joinPoint.getArgs()), maxLength));
            }
            result = joinPoint.proceed();
            if (captureOutput) {
                span.setAttribute("agent.output", truncate(toJson(result), maxLength));
            }
            span.setStatus(StatusCode.OK);
        } catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            span.recordException(t);
            throw t;
        } finally {
            span.end();
        }
        return result;
    }

    // ====================== SpEL 工具 ======================

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private EvaluationContext buildContext(ProceedingJoinPoint joinPoint, Method method) {
        EvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = getParamNames(method);
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        return context;
    }

    private String[] getParamNames(Method method) {
        // 简化: 用 arg0, arg1... (JDK 反射拿不到参数名,除非加 -parameters)
        // 建议用户编译时加 -parameters, 或在这里用 Spring 的 ParameterNameDiscoverer
        org.springframework.core.ParameterNameDiscoverer pnd = new org.springframework.core.DefaultParameterNameDiscoverer();
        String[] names = pnd.getParameterNames(method);
        if (names != null) return names;
        // 兜底
        String[] fallback = new String[method.getParameterCount()];
        for (int i = 0; i < fallback.length; i++) fallback[i] = "arg" + i;
        return fallback;
    }

    private String evalString(String spel, EvaluationContext context) {
        if (spel == null || spel.isEmpty()) return null;
        try {
            Expression expr = PARSER.parseExpression(spel);
            Object value = expr.getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL: {}", spel, e);
            return null;
        }
    }

    private Long evalLong(String spel, EvaluationContext context) {
        if (spel == null || spel.isEmpty()) return null;
        try {
            Expression expr = PARSER.parseExpression(spel);
            Object value = expr.getValue(context);
            if (value == null) return null;
            if (value instanceof Number n) return n.longValue();
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    // ====================== 工具 ======================

    private String toJson(Object obj) {
        if (obj == null) return "null";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (max <= 0 || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
