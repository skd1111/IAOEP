package io.iaoep.evaluator.abtest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ABTestRepository extends JpaRepository<ABTest, UUID> {

    List<ABTest> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<ABTest> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, ABTest.Status status);
}
