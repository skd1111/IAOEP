package io.iaoep.evaluator.evolutionlog;

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
 * Evolution Log — 自进化全量审计记录.
 *
 * <p>记录所有 EvolutionSuggestion 的应用 / 回滚, 用于合规审计 + 效果回溯.</p>
 */
@Entity
@Table(name = "evolution_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionLog {

    public enum Outcome {
        SUCCESS,       // 应用后, 后续评测/线上指标验证有效
        FAILED,        // 应用后, 后续评测/线上指标恶化
        REVERTED       // 主动回滚
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** 关联的 EvolutionSuggestion */
    @Column(name = "suggestion_id")
    private UUID suggestionId;

    /** 类型: prompt_change / model_swap / route_change / tool_replace / rollback */
    @Column(nullable = false, length = 50)
    private String type;

    /** 目标对象 (e.g. "CustomerServiceAgent.chat") */
    @Column(nullable = false, length = 255)
    private String target;

    /** 应用前配置 (JSON) */
    @Type(JsonBinaryType.class)
    @Column(name = "before_config", columnDefinition = "jsonb")
    private Map<String, Object> beforeConfig;

    /** 应用后配置 (JSON) */
    @Type(JsonBinaryType.class)
    @Column(name = "after_config", columnDefinition = "jsonb")
    private Map<String, Object> afterConfig;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "applied_by", nullable = false, length = 255)
    private String appliedBy;

    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    @Column(name = "rolled_back_by", length = 255)
    private String rolledBackBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Outcome outcome = Outcome.SUCCESS;

    /** 备注 (原因 / 影响等) */
    @Column(columnDefinition = "text")
    private String note;
}
