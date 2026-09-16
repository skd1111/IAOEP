package io.iaoep.evaluator.federation;

import io.iaoep.evaluator.federation.dto.FederationReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Federation REST API — 跨租户聚合查询 + 触发聚合.
 */
@RestController
@RequestMapping("/api/v1/iaoep/federation")
@RequiredArgsConstructor
public class FederationController {

    private final FederationService service;

    /**
     * 触发一次跨租户聚合.
     *
     * <p>Body:
     * <pre>
     * {
     *   "name": "accuracy",
     *   "dimension": "model=qwen3-max",
     *   "tenantValues": {"tenantA": 0.92, "tenantB": 0.85, "tenantC": 0.91},
     *   "windowStart": "2027-04-01T00:00:00Z",
     *   "windowEnd":   "2027-04-15T00:00:00Z",
     *   "epsilon": 1.0
     * }
     * </pre>
     */
    @PostMapping("/aggregate")
    @ResponseStatus(HttpStatus.CREATED)
    public FederationReportResponse aggregate(@RequestBody AggregateRequest req) {
        FederationAggregate agg = service.aggregate(
                req.name,
                req.dimension,
                req.tenantValues,
                req.windowStart,
                req.windowEnd,
                req.epsilon
        );
        return FederationReportResponse.from(agg, /* includeTruth */ false);
    }

    /**
     * 查询历史聚合 (仅返回 DP 噪声值, 不暴露真实值).
     */
    @GetMapping("/aggregates")
    public List<FederationReportResponse> queryAggregates(
            @RequestParam String name,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return service.query(name, from, to, /* includeTruth */ false);
    }

    /**
     * Phase 7.7: 按 dimension 前缀查询 (e.g. "model=qwen3-turbo" 匹配所有 skill).
     */
    @GetMapping("/aggregates/by-dimension")
    public List<FederationReportResponse> queryByDimension(
            @RequestParam String dimensionPrefix) {
        return service.queryByDimensionPrefix(dimensionPrefix, /* includeTruth */ false);
    }

    /**
     * Phase 7.7: 多维交叉表 (model × skill).
     *
     * GET /api/v1/iaoep/federation/matrix?name=model_accuracy&since=2027-04-01T00:00:00Z
     * 返回: { "qwen3-turbo": { "code-review": 0.851, ... }, ... }
     */
    @GetMapping("/matrix")
    public java.util.Map<String, java.util.Map<String, Double>> getMatrix(
            @RequestParam String name,
            @RequestParam Instant since) {
        return service.getMatrix(name, since);
    }

    // ---------------------------------------------------------------
    // Request DTO
    // ---------------------------------------------------------------

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AggregateRequest {
        private String name;
        private String dimension;
        private Map<String, Double> tenantValues;
        private Instant windowStart;
        private Instant windowEnd;
        private Double epsilon;
    }
}
