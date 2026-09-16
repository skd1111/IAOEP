package io.iaoep.evaluator.dataset;

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
 * Golden Dataset — 评测数据集.
 *
 * <p>每个 dataset 由 {@code (project_id, name, version)} 唯一标识.
 * 同名 dataset 支持多版本, 但同一时间只有 {@code is_active=true} 的版本生效.</p>
 *
 * <p>cases 字段是 JSONB, 存储 List&lt;Map&lt;String, Object&gt;&gt;,
 * 每个 case 包含 {@code input} / {@code expected_output} / {@code rubric} / {@code tags}.</p>
 */
@Entity
@Table(name = "golden_datasets",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "name", "version"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoldenDataset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 评测用例列表, 每个用例结构:
     * <pre>
     * {
     *   "input": { ... },                // 必填, Agent 输入
     *   "expected_output": "..." | { ... }, // 可选, 期望输出
     *   "rubric": "...",                  // 可选, 评分规则
     *   "tags": ["..."]                   // 可选, 标签
     * }
     * </pre>
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> cases;

    /** 自由扩展字段 (如创建者、来源、备注等) */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
