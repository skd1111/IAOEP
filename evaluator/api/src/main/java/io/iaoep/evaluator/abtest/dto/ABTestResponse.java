package io.iaoep.evaluator.abtest.dto;

import io.iaoep.evaluator.abtest.ABTest;
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
public class ABTestResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private UUID suggestionId;
    private String baselineVersion;
    private String candidateVersion;
    private Map<String, Double> trafficSplit;
    private ABTest.Status status;
    private Double baselineScore;
    private Double candidateScore;
    private ABTest.Status decision;
    private String decisionReason;
    private Instant startedAt;
    private Instant decidedAt;
    private Instant createdAt;
    private String createdBy;

    public static ABTestResponse from(ABTest t) {
        return ABTestResponse.builder()
                .id(t.getId())
                .projectId(t.getProjectId())
                .name(t.getName())
                .suggestionId(t.getSuggestionId())
                .baselineVersion(t.getBaselineVersion())
                .candidateVersion(t.getCandidateVersion())
                .trafficSplit(t.getTrafficSplit())
                .status(t.getStatus())
                .baselineScore(t.getBaselineScore())
                .candidateScore(t.getCandidateScore())
                .decision(t.getDecision())
                .decisionReason(t.getDecisionReason())
                .startedAt(t.getStartedAt())
                .decidedAt(t.getDecidedAt())
                .createdAt(t.getCreatedAt())
                .createdBy(t.getCreatedBy())
                .build();
    }
}
