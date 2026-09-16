package io.iaoep.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @ObservedLLMCall} — 标注一个 LLM 调用方法,自动产生 OTLP Span。
 *
 * <p>用法:
 * <pre>
 * &#64;Service
 * public class LlmClient {
 *     &#64;ObservedLLMCall(system = "dashscope", modelParam = "modelName")
 *     public ChatResponse call(String modelName, List&lt;Message&gt; messages) {
 *         // ...
 *     }
 * }
 * </pre>
 *
 * <p>Span 名称: {@code llm.call}
 *
 * <p>Span 属性:
 * <ul>
 *   <li>{@code gen_ai.system = "dashscope"}</li>
 *   <li>{@code gen_ai.request.model = <modelName 取自方法参数>}</li>
 *   <li>{@code gen_ai.usage.input_tokens = <取自方法参数>} (可选)</li>
 *   <li>{@code gen_ai.usage.output_tokens = <取自方法参数>} (可选)</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObservedLLMCall {

    /** LLM 供应商 (openai / dashscope / glm / deepseek / anthropic) */
    String system();

    /** 从哪个方法参数取模型名 (Spring SpEL, e.g. {@code "#modelName"}) */
    String modelParam() default "";

    /** 从哪个方法参数取输入 token 数 (e.g. {@code "#inputTokens"}) */
    String inputTokensParam() default "";

    /** 从哪个方法参数取输出 token 数 (e.g. {@code "#outputTokens"}) */
    String outputTokensParam() default "";
}
