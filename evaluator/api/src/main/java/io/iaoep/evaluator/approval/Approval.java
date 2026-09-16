package io.iaoep.evaluator.approval;

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
 * Approval — 对 EvolutionSuggestion 的一次审批记录.
 *
 * <p>三级风险 + 审批次数:
 * <ul>
 *   <li>LOW: 单签 (Maintainer 签一次即生效) — 自动应用</li>
 *   <li>MEDIUM: 单签 + 通知 (Maintainer 主动 review)</li>
 *   <li>HIGH: 双签 SOP (2 个不同 Maintainer 都签才生效)</li>
 * </ul>
 *
 * <p>7 天 SLA: 超期未审 → 自动 expired.</p>
 */
@Entity
@Table(name = "approvals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Approval {

    public enum Decision {
        APPROVED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "suggestion_id", nullable = false)
    private UUID suggestionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Decision decision;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "decided_by", nullable = false, length = 255)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    /** 高风险标识 (双签 SOP 用) */
    @Column(name = "is_high_risk", nullable = false)
    @Builder.Default
    private Boolean isHighRisk = false;

    /** 双签中第几个签字 (1 / 2), 高风险时使用 */
    @Column(name = "signature_index")
    private Integer signatureIndex;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
