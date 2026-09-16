package io.iaoep.evaluator.dataset.dto;

import io.iaoep.evaluator.dataset.GoldenDataset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private String version;
    private Boolean isActive;
    private Integer caseCount;
    private List<Map<String, Object>> cases;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    public static DatasetResponse from(GoldenDataset d) {
        return DatasetResponse.builder()
                .id(d.getId())
                .projectId(d.getProjectId())
                .name(d.getName())
                .version(d.getVersion())
                .isActive(d.getIsActive())
                .caseCount(d.getCases() != null ? d.getCases().size() : 0)
                .cases(d.getCases())
                .metadata(d.getMetadata())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .createdBy(d.getCreatedBy())
                .build();
    }
}
