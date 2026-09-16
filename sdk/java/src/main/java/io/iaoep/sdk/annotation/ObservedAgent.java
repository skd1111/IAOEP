package io.iaoep.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @ObservedAgent} — 标注一个 Agent 方法,自动产生 OTLP Span。
 *
 * <p>用法:
 * <pre>
 * &#64;Service
 * public class CustomerServiceAgent {
 *     &#64;ObservedAgent(name = "customer-service", skill = "general")
 *     public String chat(String query) {
 *         return chatModel.call(query);
 *     }
 * }
 * </pre>
 *
 * <p>产生的 Span 属性:
 * <ul>
 *   <li>{@code agent.name = "customer-service"}</li>
 *   <li>{@code agent.skill.name = "general"}</li>
 *   <li>(如 captureInput) {@code agent.input = "<JSON>"}</li>
 *   <li>(如 captureOutput) {@code agent.output = "<JSON>"}</li>
 * </ul>
 *
 * <p>Span 名称: {@code agent.run}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObservedAgent {

    /** Agent 名称 (必填,会作为 Span 的 agent.name 属性) */
    String name();

    /** 关联 Skill 名 (可选) */
    String skill() default "";

    /** 是否捕获方法参数 (默认 false, 避免 PII 泄露) */
    boolean captureInput() default false;

    /** 是否捕获返回值 (默认 false, 避免 PII 泄露) */
    boolean captureOutput() default false;

    /** 输入参数最大序列化长度 (字符, 防止巨型 payload) */
    int maxPayloadLength() default 4096;
}
