package io.iaoep.evaluator.abtest;

import io.iaoep.evaluator.abtest.dto.ABTestResponse;
import io.iaoep.evaluator.abtest.dto.CreateABTestRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iaoep/projects/{projectId}/ab-tests")
@RequiredArgsConstructor
public class ABTestController {

    private final ABTestService service;

    @GetMapping
    public List<ABTestResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @GetMapping("/{abId}")
    public ABTestResponse get(@PathVariable UUID projectId, @PathVariable UUID abId) {
        return service.get(abId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ABTestResponse create(@PathVariable UUID projectId,
                                 @Valid @RequestBody CreateABTestRequest req,
                                 @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return service.create(projectId, req, userId);
    }

    @PostMapping("/{abId}/start")
    public ABTestResponse start(@PathVariable UUID projectId, @PathVariable UUID abId) {
        return service.start(abId);
    }

    /**
     * 决策 (传入 baseline + candidate 两个 EvaluationJob ID).
     */
    @PostMapping("/{abId}/decide")
    public ABTestResponse decide(@PathVariable UUID projectId,
                                 @PathVariable UUID abId,
                                 @RequestBody Map<String, Object> body) {
        UUID baselineJobId = UUID.fromString((String) body.get("baselineJobId"));
        UUID candidateJobId = UUID.fromString((String) body.get("candidateJobId"));
        Double threshold = body.get("threshold") != null ? ((Number) body.get("threshold")).doubleValue() : null;
        return service.decide(abId, baselineJobId, candidateJobId, threshold);
    }

    @PostMapping("/{abId}/apply")
    public ABTestResponse apply(@PathVariable UUID projectId, @PathVariable UUID abId) {
        return service.apply(abId);
    }

    @PostMapping("/{abId}/rollback")
    public ABTestResponse rollback(@PathVariable UUID projectId, @PathVariable UUID abId) {
        return service.rollback(abId);
    }

    /**
     * 流量分配决策 (下游 SDK 调用).
     * GET /api/v1/iaoep/projects/{id}/ab-tests/{abId}/assign
     * → "baseline" 或 "candidate"
     */
    @GetMapping("/{abId}/assign")
    public Map<String, String> assignGroup(@PathVariable UUID projectId, @PathVariable UUID abId) {
        String group = service.assignGroup(abId);
        return Map.of("group", group != null ? group : "baseline",
                "abTestId", abId.toString());
    }
}
