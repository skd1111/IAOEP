package io.iaoep.evaluator.dataset;

import io.iaoep.evaluator.dataset.dto.CreateDatasetRequest;
import io.iaoep.evaluator.dataset.dto.DatasetResponse;
import io.iaoep.evaluator.dataset.dto.UpdateDatasetRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Golden Dataset REST API.
 *
 * <p>端点:
 * <ul>
 *   <li>{@code GET    /api/v1/iaoep/projects/{id}/datasets}                          — 列出</li>
 *   <li>{@code POST   /api/v1/iaoep/projects/{id}/datasets}                          — 创建</li>
 *   <li>{@code GET    /api/v1/iaoep/projects/{id}/datasets/{dsId}}                  — 详情</li>
 *   <li>{@code PUT    /api/v1/iaoep/projects/{id}/datasets/{dsId}}                  — 更新</li>
 *   <li>{@code DELETE /api/v1/iaoep/projects/{id}/datasets/{dsId}}                  — 删除</li>
 *   <li>{@code GET    /api/v1/iaoep/projects/{id}/datasets/by-name/{name}/versions}  — 同名版本列表</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/iaoep/projects/{projectId}/datasets")
@RequiredArgsConstructor
public class GoldenDatasetController {

    private final GoldenDatasetService service;

    @GetMapping
    public List<DatasetResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @GetMapping("/by-name/{name}/versions")
    public List<DatasetResponse> listVersions(@PathVariable UUID projectId,
                                              @PathVariable String name) {
        return service.listVersions(projectId, name);
    }

    @GetMapping("/{dsId}")
    public DatasetResponse get(@PathVariable UUID projectId,
                               @PathVariable UUID dsId) {
        return service.get(dsId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetResponse create(@PathVariable UUID projectId,
                                  @Valid @RequestBody CreateDatasetRequest req,
                                  @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return service.create(projectId, req, userId);
    }

    @PutMapping("/{dsId}")
    public DatasetResponse update(@PathVariable UUID projectId,
                                  @PathVariable UUID dsId,
                                  @Valid @RequestBody UpdateDatasetRequest req) {
        return service.update(dsId, req);
    }

    @DeleteMapping("/{dsId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                       @PathVariable UUID dsId) {
        service.delete(dsId);
        return ResponseEntity.noContent().build();
    }
}
