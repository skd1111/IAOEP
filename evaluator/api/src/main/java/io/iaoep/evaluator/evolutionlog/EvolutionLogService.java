package io.iaoep.evaluator.evolutionlog;

import io.iaoep.evaluator.evolution.EvolutionSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionLogService {

    private final EvolutionLogRepository repo;

    /**
     * 记录一次应用 (PR 19 A/B 测试通过后, 或低风险自动应用).
     */
    @Transactional
    public EvolutionLog logApplied(EvolutionSuggestion suggestion,
                                    Map<String, Object> beforeConfig,
                                    Map<String, Object> afterConfig,
                                    String userId) {
        EvolutionLog log = EvolutionLog.builder()
                .projectId(suggestion.getProjectId())
                .suggestionId(suggestion.getId())
                .type(suggestion.getType().name())
                .target(suggestion.getTarget())
                .beforeConfig(beforeConfig)
                .afterConfig(afterConfig)
                .appliedAt(Instant.now())
                .appliedBy(userId)
                .outcome(EvolutionLog.Outcome.SUCCESS)
                .build();
        log.info("Evolution applied: {} by {} (suggestion={})", suggestion.getTarget(), userId, suggestion.getId());
        return repo.save(log);
    }

    /**
     * 记录回滚.
     */
    @Transactional
    public EvolutionLog logRolledBack(UUID evolutionLogId, String userId, String note) {
        EvolutionLog log = repo.findById(evolutionLogId)
                .orElseThrow(() -> new IllegalArgumentException("evolution log not found: " + evolutionLogId));
        log.setRolledBackAt(Instant.now());
        log.setRolledBackBy(userId);
        log.setOutcome(EvolutionLog.Outcome.REVERTED);
        if (note != null) log.setNote(note);
        log.warn("Evolution rolled back: {} by {}", log.getTarget(), userId);
        return repo.save(log);
    }

    @Transactional(readOnly = true)
    public List<EvolutionLog> listByProject(UUID projectId) {
        return repo.findByProjectIdOrderByAppliedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public EvolutionLog get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("evolution log not found: " + id));
    }
}
