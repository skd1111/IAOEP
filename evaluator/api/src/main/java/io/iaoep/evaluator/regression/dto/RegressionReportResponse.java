package io.iaoep.evaluator.regression;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegressionReportResponse {

    private UUID id;
    private UUID projectId;
    private UUID baselineJobId;
    private UUID candidateJobId;
    private String baselineVersion;
    private String candidateVersion;
    private RegressionReport.Status status;
    private Double overallDelta;
    private Double threshold;
    private Map<String, Object> result;
    private Instant createdAt;
    private String createdBy;

    public static RegressionReportResponse from(RegressionReport r) {
        return RegressionReportResponse.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .baselineJobId(r.getBaselineJobId())
                .candidateJobId(r.getCandidateJobId())
                .baselineVersion(r.getBaselineVersion())
                .candidateVersion(r.getCandidateVersion())
                .status(r.getStatus())
                .overallDelta(r.getOverallDelta())
                .threshold(r.getThreshold())
                .result(r.getResult())
                .createdAt(r.getCreatedAt())
                .createdBy(r.getCreatedBy())
                .build();
    }
}
