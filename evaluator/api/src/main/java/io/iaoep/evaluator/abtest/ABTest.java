package io.iaoep.evaluator.abtest;

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
 * A/B Test — 两个 Agent 版本的流量分流测试.
 *
 * <p>状态机:
 * <pre>
 *   pending → running → analyzing → (passed | failed | inconclusive)
 *                              ↓
 *                          deployed | reverted
 * </pre>
 *
 * <p>Phase 3 简化为:
 * <ul>
 *   <li>HTTP Header 注入: {@code X-IAOEP-AB-Test-Group: baseline | candidate}</li>
 *   <li>下游 Agent 服务 (SDK 拦截) 读 header 决定用哪个版本</li>
 *   <li>本服务管理分流比例 (默认 50/50) 和评测结果对比</li>
 * </ul>
 */
@Entity
@Table(name = "ab_tests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ABTest {

    public enum Status {
        PENDING,        // 待启动
        RUNNING,        // 流量已分流
        ANALYZING,      // 收集中, 等样本量
        PASSED,         // 新版胜出
        FAILED,         // 新版劣化
        INCONCLUSIVE,    // 不显著
        DEPLOYED,       // 已应用 (新版切为默认)
        REVERTED         // 已回滚
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(length = 255)
    private String name;

    /** 关联的 EvolutionSuggestion (触发的建议) */
    @Column(name = "suggestion_id")
    private UUID suggestionId;

    @Column(name = "baseline_version", nullable = false, length = 100)
    private String baselineVersion;

    @Column(name = "candidate_version", nullable = false, length = 100)
    private String candidateVersion;

    /** 流量分配 (默认 50/50, JSON: {"baseline": 0.5, "candidate": 0.5}) */
    @Type(JsonBinaryType.class)
    @Column(name = "traffic_split", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Double> trafficSplit = Map.of("baseline", 0.5, "candidate", 0.5);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.PENDING;

    /** Baseline 版本跑出来的总分 (跑评测后填充) */
    @Column(name = "baseline_score")
    private Double baselineScore;

    /** Candidate 版本跑出来的总分 */
    @Column(name = "candidate_score")
    private Double candidateScore;

    /** 决策: passed (新版胜出) / failed (新版劣化) / inconclusive (不显著) */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Status decision;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
