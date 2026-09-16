package io.iaoep.evaluator.evolution.dto;

import io.iaoep.evaluator.evolution.EvolutionSuggestion;
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
public class EvolutionSuggestionResponse {

    private UUID id;
    private UUID projectId;
    private UUID jobId;
    private EvolutionSuggestion.Type type;
    private String target;
    private String description;
    private Map<String, Object> diff;
    private Double expectedRoi;
    private EvolutionSuggestion.RiskLevel riskLevel;
    private EvolutionSuggestion.Status status;
    private UUID abTestId;
    private Instant createdAt;
    private String createdBy;

    public static EvolutionSuggestionResponse from(EvolutionSuggestion s) {
        return EvolutionSuggestionResponse.builder()
                .id(s.getId())
                .projectId(s.getProjectId())
                .jobId(s.getJobId())
                .type(s.getType())
                .target(s.getTarget())
                .description(s.getDescription())
                .diff(s.getDiff())
                .expectedRoi(s.getExpectedRoi())
                .riskLevel(s.getRiskLevel())
                .status(s.getStatus())
                .abTestId(s.getAbTestId())
                .createdAt(s.getCreatedAt())
                .createdBy(s.getCreatedBy())
                .build();
    }
}
