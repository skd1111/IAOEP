package io.iaoep.evaluator.scorer;

/**
 * Scorer 执行异常 — 配置错误 / 数据缺失 / 计算失败.
 */
public class ScorerException extends RuntimeException {

    public ScorerException(String message) {
        super(message);
    }

    public ScorerException(String message, Throwable cause) {
        super(message, cause);
    }
}
