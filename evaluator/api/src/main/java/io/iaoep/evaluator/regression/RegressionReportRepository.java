package io.iaoep.evaluator.regression;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegressionReportRepository extends JpaRepository<RegressionReport, UUID> {

    List<RegressionReport> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<RegressionReport> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, RegressionReport.Status status);
}
