package io.iaoep.evaluator.regression;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regression Report — 两个 Agent 版本 (baseline / candidate) 的评测对比结果.
 *
 * <p>存储于 PostgreSQL, 维度对比详情存 result JSONB.
 */
@Entity
@Table(name = "regression_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegressionReport {

    public enum Status {
        PASSED,     // candidate ≥ baseline - threshold
        FAILED,     // 任一维度下降 > threshold
        INVALID     // baseline 或 candidate 缺失
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "baseline_job_id", nullable = false)
    private UUID baselineJobId;

    @Column(name = "candidate_job_id", nullable = false)
    private UUID candidateJobId;

    @Column(name = "baseline_version", length = 100)
    private String baselineVersion;

    @Column(name = "candidate_version", length = 100)
    private String candidateVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status;

    /** 全局分差 (candidate.overall - baseline.overall), 正数 = 变好 */
    @Column(name = "overall_delta")
    private Double overallDelta;

    /** 阻断阈值 (默认 0.05, 任意维度下降超过此值 → FAILED) */
    @Column(name = "threshold")
    @Builder.Default
    private Double threshold = 0.05;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;        // 完整对比 JSON

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
