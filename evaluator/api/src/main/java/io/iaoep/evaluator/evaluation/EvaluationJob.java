package io.iaoep.evaluator.evaluation;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
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
 * Evaluation Job — 一次评测任务的元数据.
 *
 * <p>状态机: {@code pending → running → completed / failed / cancelled}.
 *
 * <p>Phase 1 已建表 (V1__init_schema.sql), 此处填 JPA 实体.</p>
 */
@Entity
@Table(name = "evaluation_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationJob {

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "agent_version", length = 100)
    private String agentVersion;

    /** LLM Judge 配置 (模型 / base-url / voting_count 等) */
    @Type(JsonBinaryType.class)
    @Column(name = "judge_config", columnDefinition = "jsonb")
    private Map<String, Object> judgeConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Double progress = 0.0;

    @Column(name = "total_cases", nullable = false)
    @Builder.Default
    private Integer totalCases = 0;

    @Column(name = "completed_cases", nullable = false)
    @Builder.Default
    private Integer completedCases = 0;

    /** 评测结果 (维度分 + 失败 case 列表 + LLM 反馈) */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
