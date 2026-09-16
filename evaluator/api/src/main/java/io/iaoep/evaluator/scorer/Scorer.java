package io.iaoep.evaluator.scorer;

import java.util.Map;

/**
 * Rule Scorer 抽象接口 — 给定一条 trace 的数据, 返回 0.0-1.0 评分.
 *
 * <p>设计要点:
 * <ul>
 *   <li><b>无状态</b>: 每次评分独立, 不依赖外部状态</li>
 *   <li><b>配置驱动</b>: 通过 {@code config} Map 注入参数 (避免硬编码)</li>
 *   <li><b>幂等</b>: 同一 trace + config 多次调用结果一致</li>
 *   <li><b>错误透明</b>: 异常时抛 {@link ScorerException}, 由上层处理</li>
 * </ul>
 *
 * <p>Spring 自动发现: 实现类加 {@code @Component} 即可被 {@link ScorerRegistry} 收录.
 */
public interface Scorer {

    /** Scorer 唯一标识, 用于配置引用 (e.g. "latency", "cost", "token", "keyword", "json_format") */
    String name();

    /** 人类可读描述 (用于 UI / 日志) */
    String description();

    /**
     * 评分主方法.
     *
     * @param trace  一条 IAOEP trace 的 spans 数据 (从 ClickHouse 查询得到)
     * @param config 配置 (e.g. {"p95_threshold_ms": 2000})
     * @return 0.0 - 1.0 之间的评分 (1.0 = 完美)
     */
    double score(Map<String, Object> trace, Map<String, Object> config);

    /**
     * 验证 config 格式是否合法. 不合法抛异常.
     */
    default void validateConfig(Map<String, Object> config) {
        // 默认无校验
    }
}
