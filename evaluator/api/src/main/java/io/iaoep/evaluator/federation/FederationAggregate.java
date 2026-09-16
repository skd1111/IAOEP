package io.iaoep.evaluator.federation;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Federation Aggregate — 跨租户聚合统计.
 *
 * <p>每条记录 = 一个聚合指标 (e.g. "tenantA+tenantB 的平均 accuracy") + 差分隐私参数.</p>
 */
@Entity
@Table(name = "federation_aggregates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FederationAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 聚合名 (e.g. "model_accuracy", "latency_p99", "success_rate") */
    @Column(nullable = false, length = 100)
    private String name;

    /** 维度 (e.g. "llm_model=qwen3-max", "skill=code-review") */
    @Column(nullable = false, length = 200)
    private String dimension;

    /** 真实聚合值 (不加噪声) */
    @Column(name = "true_value", nullable = false)
    private Double trueValue;

    /** 加 DP 噪声后的值 (供外部查询) */
    @Column(name = "noisy_value", nullable = false)
    private Double noisyValue;

    /** 样本数 (参与的租户 / trace 数) */
    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    /** 参与的租户列表 (脱敏, 仅租户 ID hash) */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> tenantHashes;

    /** 差分隐私参数 */
    @Column(nullable = false)
    private Double epsilon;

    @Column(nullable = false)
    private Double sensitivity;

    /** 时间窗口 (聚合覆盖的时间范围) */
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
