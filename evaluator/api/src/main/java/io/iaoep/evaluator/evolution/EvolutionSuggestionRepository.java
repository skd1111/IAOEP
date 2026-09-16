package io.iaoep.evaluator.evolution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvolutionSuggestionRepository extends JpaRepository<EvolutionSuggestion, UUID> {

    List<EvolutionSuggestion> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<EvolutionSuggestion> findByProjectIdAndStatusOrderByCreatedAtDesc(
            UUID projectId, EvolutionSuggestion.Status status);

    List<EvolutionSuggestion> findByProjectIdAndRiskLevelOrderByExpectedRoiDesc(
            UUID projectId, EvolutionSuggestion.RiskLevel riskLevel);

    long countByStatus(EvolutionSuggestion.Status status);
}
