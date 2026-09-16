package io.iaoep.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @ObservedTool} — 标注一个工具调用方法,自动产生 OTLP Span。
 *
 * <p>用法:
 * <pre>
 * &#64;Component
 * public class DateTimeTools {
 *     &#64;ObservedTool(name = "get_current_time")
 *     public String getCurrentDateTime() {
 *         return LocalDateTime.now().toString();
 *     }
 * }
 * </pre>
 *
 * <p>Span 名称: {@code tool.execute}
 *
 * <p>Span 属性:
 * <ul>
 *   <li>{@code tool.name = "get_current_time"}</li>
 *   <li>{@code tool.call.id = <UUID>}</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObservedTool {

    /** 工具名 (必填) */
    String name();

    /** 是否捕获参数 (默认 false) */
    boolean captureArguments() default false;

    /** 最大参数序列化长度 */
    int maxPayloadLength() default 2048;
}
