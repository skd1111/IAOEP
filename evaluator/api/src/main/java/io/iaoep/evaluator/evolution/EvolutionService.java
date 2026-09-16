package io.iaoep.evaluator.evolution;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evolution Suggestion 业务服务 (CRUD + 审批).
 *
 * <p>审批流 (PR 20 详细实现, 这里先做基础 approve/reject):</p>
 * <ul>
 *   <li>LOW: 自动 approve (跳过人工), Phase 3 + PR 20 完整实现</li>
 *   <li>MEDIUM: 单审, 任意 Maintainer 可批</li>
 *   <li>HIGH: 双审, 2 个 Maintainer 都签才生效</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EvolutionService {

    private final EvolutionSuggestionRepository repo;

    @Transactional(readOnly = true)
    public List<EvolutionSuggestion> list(UUID projectId) {
        return repo.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public List<EvolutionSuggestion> listByStatus(UUID projectId, EvolutionSuggestion.Status status) {
        return repo.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status);
    }

    @Transactional(readOnly = true)
    public EvolutionSuggestion get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("suggestion not found: " + id));
    }

    /**
     * 审批通过 (中/高风险需要单审/双审, 完整逻辑 PR 20).
     * 当前简化: 直接 APPROVED.
     */
    @Transactional
    public EvolutionSuggestion approve(UUID id, String userId) {
        EvolutionSuggestion suggestion = get(id);
        suggestion.setStatus(EvolutionSuggestion.Status.APPROVED);
        return repo.save(suggestion);
    }

    /**
     * 拒绝建议.
     */
    @Transactional
    public EvolutionSuggestion reject(UUID id, String userId, String reason) {
        EvolutionSuggestion suggestion = get(id);
        suggestion.setStatus(EvolutionSuggestion.Status.REJECTED);
        if (reason != null && suggestion.getDiff() != null) {
            suggestion.getDiff().put("rejection_reason", reason);
        }
        return repo.save(suggestion);
    }

    /**
     * 标记已部署 (PR 19 A/B 测试通过后调用).
     */
    @Transactional
    public EvolutionSuggestion markDeployed(UUID id) {
        EvolutionSuggestion suggestion = get(id);
        suggestion.setStatus(EvolutionSuggestion.Status.DEPLOYED);
        return repo.save(suggestion);
    }

    /**
     * 标记已回滚.
     */
    @Transactional
    public EvolutionSuggestion markReverted(UUID id) {
        EvolutionSuggestion suggestion = get(id);
        suggestion.setStatus(EvolutionSuggestion.Status.REVERTED);
        return repo.save(suggestion);
    }
}
