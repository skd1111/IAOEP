package io.iaoep.evaluator.evolutionlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvolutionLogRepository extends JpaRepository<EvolutionLog, UUID> {

    List<EvolutionLog> findByProjectIdOrderByAppliedAtDesc(UUID projectId);

    List<EvolutionLog> findBySuggestionIdOrderByAppliedAtDesc(UUID suggestionId);
}
