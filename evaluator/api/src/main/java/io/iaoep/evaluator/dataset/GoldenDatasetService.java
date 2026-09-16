package io.iaoep.evaluator.dataset;

import io.iaoep.evaluator.dataset.dto.CreateDatasetRequest;
import io.iaoep.evaluator.dataset.dto.DatasetResponse;
import io.iaoep.evaluator.dataset.dto.UpdateDatasetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoldenDatasetService {

    private final GoldenDatasetRepository repository;

    /**
     * 列出项目的所有 dataset (按更新时间倒序).
     */
    @Transactional(readOnly = true)
    public List<DatasetResponse> list(UUID projectId) {
        return repository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(DatasetResponse::from)
                .toList();
    }

    /**
     * 同名 dataset 的所有版本.
     */
    @Transactional(readOnly = true)
    public List<DatasetResponse> listVersions(UUID projectId, String name) {
        return repository.findByProjectIdAndNameOrderByVersionDesc(projectId, name).stream()
                .map(DatasetResponse::from)
                .toList();
    }

    /**
     * 按 ID 获取.
     */
    @Transactional(readOnly = true)
    public DatasetResponse get(UUID id) {
        return repository.findById(id)
                .map(DatasetResponse::from)
                .orElseThrow(() -> new NoSuchElementException("dataset not found: " + id));
    }

    /**
     * 创建新 dataset. 同 (project, name, version) 唯一.
     */
    @Transactional
    public DatasetResponse create(UUID projectId, CreateDatasetRequest req, String createdBy) {
        if (repository.findByProjectIdAndNameAndVersion(projectId, req.getName(), req.getVersion()).isPresent()) {
            throw new IllegalArgumentException(
                    "dataset exists: name=" + req.getName() + " version=" + req.getVersion());
        }

        GoldenDataset dataset = GoldenDataset.builder()
                .projectId(projectId)
                .name(req.getName())
                .version(req.getVersion())
                .isActive(req.getIsActive() == null || req.getIsActive())
                .cases(req.getCases())
                .metadata(req.getMetadata())
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // 如果激活, 同名其他版本自动取消
        if (dataset.getIsActive()) {
            dataset = repository.save(dataset);
            repository.deactivateOtherVersions(projectId, dataset.getName(), dataset.getId());
            return DatasetResponse.from(dataset);
        }
        return DatasetResponse.from(repository.save(dataset));
    }

    /**
     * 更新 dataset (cases / metadata).
     */
    @Transactional
    public DatasetResponse update(UUID id, UpdateDatasetRequest req) {
        GoldenDataset dataset = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("dataset not found: " + id));

        if (req.getCases() != null) {
            dataset.setCases(req.getCases());
        }
        if (req.getMetadata() != null) {
            dataset.setMetadata(req.getMetadata());
        }
        if (Boolean.TRUE.equals(req.getActivate())) {
            dataset.setIsActive(true);
        }

        dataset = repository.save(dataset);
        if (Boolean.TRUE.equals(req.getActivate())) {
            repository.deactivateOtherVersions(dataset.getProjectId(), dataset.getName(), dataset.getId());
        }
        return DatasetResponse.from(dataset);
    }

    /**
     * 删除 dataset (硬删, 不级联).
     */
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("dataset not found: " + id);
        }
        repository.deleteById(id);
    }
}
