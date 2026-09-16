package io.iaoep.evaluator.approval;

import io.iaoep.evaluator.evolution.EvolutionService;
import io.iaoep.evaluator.evolution.EvolutionSuggestion;
import io.iaoep.evaluator.evolution.EvolutionSuggestionRepository;
import io.iaoep.evaluator.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Approval Service — 三级风险分级审批 + 7 天 SLA.
 *
 * <p>审批流:
 * <ol>
 *   <li>{@link #approve} / {@link #reject}: 单次审批</li>
 *   <li>对高风险 (双签): 调用两次 {@link #sign} 才生效</li>
 *   <li>{@link #expireOverdue}: 每天 cron 扫 7 天未审的 suggestion, 自动关闭</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRepository approvalRepo;
    private final EvolutionSuggestionRepository suggestionRepo;
    private final EvolutionService evolutionService;
    private final NotificationService notificationService;

    @Value("${iaoep.evaluator.approval.sla-days:7}")
    private int slaDays;

    @Value("${iaoep.evaluator.approval.webhook-url:}")
    private String webhookUrl;

    @Value("${iaoep.evaluator.approval.type:dingtalk}")
    private String notificationType;

    /**
     * 单签审批 (low/medium 风险).
     * 高风险应该用 {@link #sign} 双签.
     */
    @Transactional
    public Approval approve(UUID suggestionId, String userId, String comment) {
        return recordDecision(suggestionId, userId, Approval.Decision.APPROVED, comment, false, 1);
    }

    @Transactional
    public Approval reject(UUID suggestionId, String userId, String comment) {
        return recordDecision(suggestionId, userId, Approval.Decision.REJECTED, comment, false, 1);
    }

    /**
     * 高风险双签: 同一 suggestion 需 2 个不同用户签字.
     */
    @Transactional
    public Approval sign(UUID suggestionId, String userId, String comment) {
        EvolutionSuggestion suggestion = suggestionRepo.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("suggestion not found"));

        if (suggestion.getRiskLevel() != EvolutionSuggestion.RiskLevel.HIGH) {
            throw new IllegalStateException("sign() 只用于高风险 suggestion, 当前: " + suggestion.getRiskLevel());
        }

        // 检查现有签字
        long signedCount = approvalRepo.countBySuggestionIdAndDecision(suggestionId, Approval.Decision.APPROVED);
        if (signedCount >= 2) {
            throw new IllegalStateException("已双签完成, 无需再签");
        }
        if (signedCount == 1) {
            // 检查是否是不同用户
            List<Approval> existing = approvalRepo.findBySuggestionIdAndIsHighRiskTrue(suggestionId);
            if (!existing.isEmpty() && existing.get(0).getDecidedBy().equals(userId)) {
                throw new IllegalStateException("高风险双签需 2 个不同用户签字, 当前已由 " + existing.get(0).getDecidedBy() + " 签过");
            }
        }

        return recordDecision(suggestionId, userId, Approval.Decision.APPROVED, comment, true, (int) (signedCount + 1));
    }

    private Approval recordDecision(UUID suggestionId, String userId,
                                    Approval.Decision decision, String comment,
                                    boolean isHighRisk, int sigIndex) {
        EvolutionSuggestion suggestion = suggestionRepo.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("suggestion not found: " + suggestionId));

        // 检查 suggestion 状态
        if (suggestion.getStatus() == EvolutionSuggestion.Status.DEPLOYED
                || suggestion.getStatus() == EvolutionSuggestion.Status.REVERTED) {
            throw new IllegalStateException("suggestion 已部署/回滚, 不能再次审批: " + suggestion.getStatus());
        }

        Approval approval = Approval.builder()
                .projectId(suggestion.getProjectId())
                .suggestionId(suggestionId)
                .decision(decision)
                .comment(comment)
                .decidedBy(userId)
                .decidedAt(Instant.now())
                .isHighRisk(isHighRisk)
                .signatureIndex(sigIndex)
                .build();
        approvalRepo.save(approval);

        // 更新 suggestion 状态
        if (decision == Approval.Decision.REJECTED) {
            evolutionService.reject(suggestionId, userId, comment);
        } else if (decision == Approval.Decision.APPROVED) {
            // 高风险需要 2 个签字才生效
            if (isHighRisk) {
                long signed = approvalRepo.countBySuggestionIdAndDecision(suggestionId, Approval.Decision.APPROVED);
                if (signed >= 2) {
                    evolutionService.approve(suggestionId, userId);
                } else {
                    log.info("高风险 suggestion {} 等待第二签字 (已签 {} 次)", suggestionId, signed);
                }
            } else {
                evolutionService.approve(suggestionId, userId);
            }
        }

        // 通知
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            try {
                String title = String.format("[IAOEP] 建议审批: %s - %s",
                        suggestion.getTarget(), decision);
                String content = String.format("""
                        ## 建议审批结果

                        - **Suggestion**: `%s`
                        - **Type**: %s
                        - **Risk**: %s
                        - **Decision**: %s
                        - **By**: %s
                        - **Comment**: %s

                        [查看详情](/suggestions/%s)
                        """,
                        suggestionId, suggestion.getType(), suggestion.getRiskLevel(),
                        decision, userId, comment != null ? comment : "(无)",
                        suggestionId);
                // 简化为复用 evaluator notification, 实际应该独立通知 channel
                log.info("notify approval: {} {} {}", title, webhookUrl, notificationType);
            } catch (Exception e) {
                log.warn("approval notification failed: {}", e.getMessage());
            }
        }

        return approval;
    }

    /**
     * 7 天 SLA: 每天扫一次, 自动关闭超时未审的 suggestion.
     */
    @Scheduled(cron = "${iaoep.evaluator.approval.sla-cron:0 0 3 * * ?}")
    @Transactional
    public void expireOverdue() {
        Instant cutoff = Instant.now().minus(slaDays, ChronoUnit.DAYS);
        List<EvolutionSuggestion> suggestions = suggestionRepo.findAll();
        int expired = 0;
        for (EvolutionSuggestion s : suggestions) {
            if (s.getStatus() != EvolutionSuggestion.Status.PROPOSED) continue;
            if (s.getCreatedAt().isBefore(cutoff)) {
                s.setStatus(EvolutionSuggestion.Status.REJECTED);
                suggestionRepo.save(s);
                expired++;
                log.warn("Suggestion {} 超期未审, 自动关闭", s.getId());
            }
        }
        if (expired > 0) {
            log.info("SLA 检查完成, 自动关闭 {} 个超时 suggestion", expired);
        }
    }

    @Transactional(readOnly = true)
    public List<Approval> list(UUID projectId) {
        return approvalRepo.findByProjectIdOrderByDecidedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public List<Approval> listBySuggestion(UUID suggestionId) {
        return approvalRepo.findBySuggestionIdOrderByDecidedAtAsc(suggestionId);
    }
}
