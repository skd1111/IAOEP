package io.iaoep.sdk.abtest;

import org.slf4j.MDC;

/**
 * 当前线程的 A/B 测试分组 (baseline / candidate).
 *
 * <p>用 ThreadLocal + SLF4J MDC 双重存储, 让下游 SDK / 业务代码都能读.</p>
 *
 * <p>用法:
 * <pre>
 * String group = ABTestContext.currentGroup();   // "baseline" / "candidate" / null
 * String abTestName = ABTestContext.currentABTestName();
 * </pre>
 */
public final class ABTestContext {

    private static final ThreadLocal<String> CURRENT_GROUP = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_NAME  = new ThreadLocal<>();

    private ABTestContext() {}

    public static void set(String abTestName, String group) {
        CURRENT_NAME.set(abTestName);
        CURRENT_GROUP.set(group);
        // 注入 SLF4J MDC (Logback 透传到日志)
        MDC.put("ab_test_name", abTestName);
        MDC.put("ab_test_group", group);
    }

    public static void clear() {
        CURRENT_NAME.remove();
        CURRENT_GROUP.remove();
        MDC.remove("ab_test_name");
        MDC.remove("ab_test_group");
    }

    public static String currentGroup() {
        return CURRENT_GROUP.get();
    }

    public static String currentABTestName() {
        return CURRENT_NAME.get();
    }
}
