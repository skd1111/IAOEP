package io.iaoep.evaluator.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationJobRepository extends JpaRepository<EvaluationJob, UUID> {

    List<EvaluationJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<EvaluationJob> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, EvaluationJob.Status status);

    /**
     * 查找待执行的任务 (调度器用).
     * 当前 Phase 1 简化为: 不做分布式锁, 单实例调度.
     */
    List<EvaluationJob> findTop10ByStatusOrderByCreatedAtAsc(EvaluationJob.Status status);
}
