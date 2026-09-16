package io.iaoep.evaluator.evolution;

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
 * Evolution Suggestion — Analyst Agent 生成的优化建议.
 *
 * <p>状态机: {@code proposed → approved / rejected → deployed / reverted}.
 *
 * <p>Phase 1 V1 已建表占位, 此处填实体.</p>
 */
@Entity
@Table(name = "evolution_suggestions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionSuggestion {

    public enum Type {
        PROMPT_CHANGE,    // 修改 prompt
        MODEL_SWAP,       // 切换模型
        ROUTE_CHANGE,     // 路由调整
        TOOL_REPLACE,     // 替换工具
        ROLLBACK,         // 回滚到上一版本
        PARAM_TUNE        // 参数微调 (temperature / top_p)
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    public enum Status {
        PROPOSED,    // 待审批
        APPROVED,    // 已通过 (等待 A/B 测试或应用)
        REJECTED,    // 已被拒绝
        DEPLOYED,    // 已应用
        REVERTED     // 已回滚
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "job_id")
    private UUID jobId;        // 关联触发它的 EvaluationJob (可选)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Type type;

    @Column(nullable = false, length = 255)
    private String target;       // 目标 (e.g. "CustomerServiceAgent.chat()")

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    /** 建议的具体变更 (JSON: before / after / file / line 等) */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> diff;

    /** AI 估算的预期 ROI (improvement rate, 0.0-1.0) */
    @Column(name = "expected_roi")
    private Double expectedRoi;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.PROPOSED;

    @Column(name = "ab_test_id")
    private UUID abTestId;     // 关联触发的 A/B 测试 (可选)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
