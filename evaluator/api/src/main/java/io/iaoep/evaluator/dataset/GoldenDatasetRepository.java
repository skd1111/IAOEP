package io.iaoep.evaluator.dataset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoldenDatasetRepository extends JpaRepository<GoldenDataset, UUID> {

    List<GoldenDataset> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    List<GoldenDataset> findByProjectIdAndNameOrderByVersionDesc(UUID projectId, String name);

    Optional<GoldenDataset> findByProjectIdAndNameAndVersion(UUID projectId, String name, String version);

    Optional<GoldenDataset> findByProjectIdAndNameAndIsActiveTrue(UUID projectId, String name);

    /**
     * 激活指定版本: 同名 dataset 自动取消其他版本的 is_active.
     * 用 @Modifying + JPQL, 一次事务完成.
     */
    @Modifying
    @Query("UPDATE GoldenDataset d SET d.isActive = false " +
           "WHERE d.projectId = :projectId AND d.name = :name AND d.id <> :keepId")
    int deactivateOtherVersions(@Param("projectId") UUID projectId,
                                @Param("name") String name,
                                @Param("keepId") UUID keepId);
}
