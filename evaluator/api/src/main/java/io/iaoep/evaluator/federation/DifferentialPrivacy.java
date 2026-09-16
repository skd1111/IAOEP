package io.iaoep.evaluator.federation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 差分隐私 (Differential Privacy) 工具.
 *
 * <p>v1.0 GA: 双机制支持:
 * <ul>
 *   <li><b>Laplace mechanism</b> (Phase 7): 纯 ε-DP, 简单但噪声大</li>
 *   <li><b>Gaussian mechanism</b> (Phase 7.9): (ε, δ)-DP, 噪声小但需 δ 放宽</li>
 * </ul>
 *
 * <p>Laplace 公式:
 * <pre>
 *   noised = true + Lap(0, sensitivity / ε)
 * </pre>
 *
 * <p>Gaussian 公式 (Phase 7.9):
 * <pre>
 *   noised = true + N(0, σ²)
 *   where σ ≥ sensitivity * sqrt(2 * ln(1.25 / δ)) / ε
 *   标准 σ = sensitivity * sqrt(2 * ln(1.25 / δ)) / ε
 * </pre>
 *
 * <p>何时用哪种?
 * <ul>
 *   <li>Laplace: 简单 / δ=0 / 噪声较大 (推荐 ε ≥ 1)</li>
 *   <li>Gaussian: 大数据集 / δ 放宽 (e.g. δ=1e-5) / 噪声较小</li>
 * </ul>
 *
 * <p>常用参数 (Laplace):
 * <ul>
 *   <li>ε = 0.1: 强隐私 (噪声较大)</li>
 *   <li>ε = 1.0: 中等隐私 (默认)</li>
 *   <li>ε = 10.0: 弱隐私 (噪声很小)</li>
 * </ul>
 */
public final class DifferentialPrivacy {

    private DifferentialPrivacy() {}

    /** 噪声机制 */
    public enum Mechanism {
        LAPLACE, GAUSSIAN
    }

    /**
     * 给单个值加 Laplace 噪声 (Phase 7).
     */
    public static double addNoise(double trueValue, double sensitivity, double epsilon) {
        if (epsilon <= 0) throw new IllegalArgumentException("epsilon must be > 0");
        double scale = sensitivity / epsilon;
        double noise = sampleLaplace(0.0, scale);
        return trueValue + noise;
    }

    /**
     * 给单个值加 Gaussian 噪声 (Phase 7.9).
     *
     * @param trueValue   真实统计值
     * @param sensitivity 敏感度
     * @param epsilon     隐私预算 ε
     * @param delta        失败概率 δ (e.g. 1e-5)
     * @return 加噪声后的值
     */
    public static double addGaussianNoise(double trueValue, double sensitivity, double epsilon, double delta) {
        if (epsilon <= 0) throw new IllegalArgumentException("epsilon must be > 0");
        if (delta <= 0 || delta >= 1) throw new IllegalArgumentException("delta must be in (0, 1)");

        // σ = sensitivity * sqrt(2 * ln(1.25 / δ)) / ε
        double sigma = sensitivity * Math.sqrt(2 * Math.log(1.25 / delta)) / epsilon;
        double noise = sampleGaussian(0.0, sigma);
        return trueValue + noise;
    }

    /**
     * 给计数加 Laplace 噪声 (敏感度固定为 1).
     */
    public static long addNoiseCount(long trueCount, double epsilon) {
        return Math.max(0, Math.round(addNoise(trueCount, 1.0, epsilon)));
    }

    /**
     * 给平均值加 Laplace 噪声 (敏感度 = 1.0 / n).
     */
    public static double addNoiseMean(double trueMean, int n, double epsilon) {
        if (n <= 0) return trueMean;
        double sensitivity = 1.0 / n;
        return addNoise(trueMean, sensitivity, epsilon);
    }

    /**
     * 给平均值加 Gaussian 噪声 (敏感度 = 1.0 / n, Phase 7.9).
     */
    public static double addGaussianNoiseMean(double trueMean, int n, double epsilon, double delta) {
        if (n <= 0) return trueMean;
        double sensitivity = 1.0 / n;
        return addGaussianNoise(trueMean, sensitivity, epsilon, delta);
    }

    /**
     * 通用: 加噪声 (指定机制).
     */
    public static double addNoise(double trueValue, double sensitivity, double epsilon,
                                   Mechanism mechanism, double delta) {
        return switch (mechanism) {
            case LAPLACE  -> addNoise(trueValue, sensitivity, epsilon);
            case GAUSSIAN -> addGaussianNoise(trueValue, sensitivity, epsilon, delta);
        };
    }

    // ---------------------------------------------------------------
    // 分布采样 (private)
    // ---------------------------------------------------------------

    private static double sampleLaplace(double mu, double b) {
        double u = ThreadLocalRandom.current().nextDouble() - 0.5;
        double sign = u < 0 ? -1 : 1;
        return mu - b * sign * Math.log(1 - 2 * Math.abs(u));
    }

    /**
     * Gaussian 采样 (Box-Muller transform).
     */
    private static double sampleGaussian(double mu, double sigma) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double u1 = 1.0 - rnd.nextDouble();    // avoid log(0)
        double u2 = 1.0 - rnd.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        return mu + sigma * z;
    }
}
