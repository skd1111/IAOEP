package io.iaoep.evaluator.abtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateABTestRequest {

    @NotNull
    private UUID datasetId;       // 评测数据集 ID

    @NotBlank
    private String baselineVersion;

    @NotBlank
    private String candidateVersion;

    /** 关联的 EvolutionSuggestion (可选) */
    private UUID suggestionId;

    /** 流量分配 (默认 50/50, JSON: {"baseline": 0.5, "candidate": 0.5}) */
    private Map<String, Double> trafficSplit;
}
