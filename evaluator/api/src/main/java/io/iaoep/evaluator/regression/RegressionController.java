package io.iaoep.evaluator.regression;

import io.iaoep.evaluator.regression.dto.CreateRegressionRequest;
import io.iaoep.evaluator.regression.dto.RegressionReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iaoep/projects/{projectId}/regression")
@RequiredArgsConstructor
public class RegressionController {

    private final RegressionService service;

    @GetMapping
    public List<RegressionReportResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @GetMapping("/{regId}")
    public RegressionReportResponse get(@PathVariable UUID projectId,
                                       @PathVariable UUID regId) {
        return service.get(regId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegressionReportResponse compare(@PathVariable UUID projectId,
                                           @Valid @RequestBody CreateRegressionRequest req,
                                           @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return service.compare(projectId, req, userId);
    }
}
