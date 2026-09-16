package io.iaoep.evaluator.evaluation;

import io.iaoep.evaluator.evaluation.dto.CreateEvaluationRequest;
import io.iaoep.evaluator.evaluation.dto.EvaluationJobResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iaoep/projects/{projectId}/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService service;

    @GetMapping
    public List<EvaluationJobResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @GetMapping("/{jobId}")
    public EvaluationJobResponse get(@PathVariable UUID projectId,
                                     @PathVariable UUID jobId) {
        return service.get(jobId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluationJobResponse create(@PathVariable UUID projectId,
                                        @Valid @RequestBody CreateEvaluationRequest req,
                                        @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return service.create(projectId, req, userId);
    }

    @DeleteMapping("/{jobId}")
    public EvaluationJobResponse cancel(@PathVariable UUID projectId,
                                         @PathVariable UUID jobId) {
        return service.cancel(jobId);
    }
}
