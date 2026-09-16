package io.iaoep.sdk.abtest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @ABTest} — 标注一个方法,SDK 自动决定走 baseline / candidate 版本.
 *
 * <p>用法:
 * <pre>
 * &#64;Service
 * public class CustomerAgent {
 *     &#64;ABTest(name = "customer-service-ab", groups = {"baseline", "candidate"})
 *     public String chat(String query) {
 *         return chatModel.call(query);  // 实际实现 (两个版本都一样)
 *     }
 * }
 * </pre>
 *
 * <p>SDK 行为:
 * <ol>
 *   <li>调用 {@code /api/v1/iaoep/projects/{id}/ab-tests/{name}/assign} 拿到 group</li>
 *   <li>把 group 写入 ThreadLocal ({@link ABTestContext})</li>
 *   <li>被装饰方法执行时, 下游 LLM/工具调用可读 {@link ABTestContext#currentGroup()}</li>
 * </ol>
 *
 * <p>注: Phase 4 简化: 不真正切换方法实现, 仅设置 ThreadLocal 供下游 SDK 读.
 * Phase 4.5: 支持同一 bean 的 baseline / candidate 两个版本切换.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ABTest {

    /** A/B 测试名 (对应 Evaluator 的 ab_test.name) */
    String name();

    /** 分组名 (默认 ["baseline", "candidate"]) */
    String[] groups() default {"baseline", "candidate"};

    /** 流量分配缓存时间 (秒, 默认 30) — 减少 evaluator 调用 */
    int cacheSeconds() default 30;
}
