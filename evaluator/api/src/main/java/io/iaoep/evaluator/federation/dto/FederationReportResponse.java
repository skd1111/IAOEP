package io.iaoep.evaluator.federation.dto;

import io.iaoep.evaluator.federation.FederationAggregate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 跨租户评测报告 (对外暴露, 始终含 DP 噪声值).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FederationReportResponse {

    private String name;                              // e.g. "model_accuracy"
    private String dimension;                         // e.g. "model=qwen3-max"
    private Double noisyValue;                        // DP-noised value (供外部使用)
    private Double trueValue;                         // 真实值 (仅 Owner 可见)
    private Integer sampleSize;                       // 参与租户数
    private Integer tenantCount;
    private Double epsilon;                           // 隐私预算
    private Double sensitivity;
    private Instant windowStart;
    private Instant windowEnd;
    private List<UUID> contributorTenants;           // 参与租户 ID (已脱敏 hash)

    public static FederationReportResponse from(FederationAggregate a, boolean includeTruth) {
        return FederationReportResponse.builder()
                .name(a.getName())
                .dimension(a.getDimension())
                .noisyValue(a.getNoisyValue())
                .trueValue(includeTruth ? a.getTrueValue() : null)
                .sampleSize(a.getSampleSize())
                .tenantCount(a.getTenantHashes() != null ? a.getTenantHashes().size() : 0)
                .epsilon(a.getEpsilon())
                .sensitivity(a.getSensitivity())
                .windowStart(a.getWindowStart())
                .windowEnd(a.getWindowEnd())
                .contributorTenants(a.getTenantHashes() == null ? List.of()
                        : a.getTenantHashes().keySet().stream().map(UUID::fromString).toList())
                .build();
    }
}
