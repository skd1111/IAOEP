package io.iaoep.evaluator.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    List<Approval> findBySuggestionIdOrderByDecidedAtAsc(UUID suggestionId);

    List<Approval> findByProjectIdOrderByDecidedAtDesc(UUID projectId);

    long countBySuggestionIdAndDecision(UUID suggestionId, Approval.Decision decision);

    List<Approval> findBySuggestionIdAndIsHighRiskTrue(UUID suggestionId);
}
