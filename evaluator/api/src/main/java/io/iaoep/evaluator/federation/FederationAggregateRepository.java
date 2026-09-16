package io.iaoep.evaluator.federation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FederationAggregateRepository extends JpaRepository<FederationAggregate, UUID> {

    List<FederationAggregate> findByNameAndWindowStartBetweenOrderByWindowStartDesc(
            String name, Instant from, Instant to);

    List<FederationAggregate> findByNameOrderByWindowStartDesc(String name);

    List<FederationAggregate> findByDimensionOrderByWindowStartDesc(String dimension);

    /**
     * Phase 7.7: 按 dimension 前缀查询 (e.g. "model=qwen3-turbo" 匹配 "model=qwen3-turbo,skill=...")
     */
    @Query("SELECT a FROM FederationAggregate a WHERE a.dimension LIKE :prefix% ORDER BY a.windowStart DESC")
    List<FederationAggregate> findByDimensionStartingWith(String prefix);
}
