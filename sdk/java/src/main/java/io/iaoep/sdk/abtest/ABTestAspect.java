package io.iaoep.sdk.abtest;

import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.UUID;

/**
 * A/B Test AOP 拦截器 — 拦截 {@code @ABTest} 注解的方法.
 *
 * <p>Phase 5 流程:
 * <ol>
 *   <li>从 OTEL 当前 Span 取 trace_id (优先) 或 user_id (兜底)</li>
 *   <li>调 {@link ABTestClient#assignByTraceId} 拿 group</li>
 *   <li>把 group 写入 {@link ABTestContext}</li>
 *   <li>给当前 OTEL Span 加 {@code ab_test_name} + {@code ab_test_group} 属性</li>
 *   <li>执行原方法 (下游 SDK 自动读 ThreadLocal 决定版本)</li>
 *   <li>清除 ThreadLocal</li>
 * </ol>
 *
 * <p>为什么按 trace_id 分: 同一请求的所有 span (agent.run → llm.call → tool.execute) 应该落到同一 group,
 * 否则会出现 "agent.run 是 baseline, 但 llm.call 是 candidate" 的混合场景, 失去 A/B 测试的对比意义.</p>
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class ABTestAspect {

    private final ABTestClient abTestClient;

    @Around("@annotation(abTest)")
    public Object aroundABTest(ProceedingJoinPoint joinPoint, ABTest abTest) throws Throwable {
        Span currentSpan = Span.current();
        String traceId = currentSpan.getSpanContext().isValid()
                ? currentSpan.getSpanContext().getTraceId()
                : extractUserIdFallback(joinPoint);

        String group = abTestClient.assignByTraceId(abTest.name(), currentProjectId(), traceId);

        log.debug("ABTest {} assigned group={} for traceId={}", abTest.name(), group, traceId);
        ABTestContext.set(abTest.name(), group);

        // 给当前 OTEL Span 加属性 (透传到 Ingest Gateway → ClickHouse)
        currentSpan.setAttribute("ab_test_name", abTest.name());
        currentSpan.setAttribute("ab_test_group", group);

        try {
            return joinPoint.proceed();
        } finally {
            ABTestContext.clear();
        }
    }

    /**
     * trace_id 不可用时, 降级到 user_id (Phase 4 行为, 保留兼容).
     */
    private String extractUserIdFallback(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null) return "anonymous";
        for (Object arg : args) {
            if (arg instanceof String s && !s.isBlank()) return s;
        }
        return "anonymous";
    }

    private UUID currentProjectId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
