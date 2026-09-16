package io.iaoep.evaluator.evaluation.dto;

import io.iaoep.evaluator.evaluation.EvaluationJob;
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
public class EvaluationJobResponse {

    private UUID id;
    private UUID projectId;
    private UUID datasetId;
    private String agentVersion;
    private Map<String, Object> judgeConfig;
    private EvaluationJob.Status status;
    private Double progress;
    private Integer totalCases;
    private Integer completedCases;
    private Map<String, Object> result;
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String createdBy;

    public static EvaluationJobResponse from(EvaluationJob j) {
        return EvaluationJobResponse.builder()
                .id(j.getId())
                .projectId(j.getProjectId())
                .datasetId(j.getDatasetId())
                .agentVersion(j.getAgentVersion())
                .judgeConfig(j.getJudgeConfig())
                .status(j.getStatus())
                .progress(j.getProgress())
                .totalCases(j.getTotalCases())
                .completedCases(j.getCompletedCases())
                .result(j.getResult())
                .errorMessage(j.getErrorMessage())
                .createdAt(j.getCreatedAt())
                .startedAt(j.getStartedAt())
                .completedAt(j.getCompletedAt())
                .createdBy(j.getCreatedBy())
                .build();
    }
}
