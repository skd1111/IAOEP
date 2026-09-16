package io.iaoep.evaluator.evaluation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEvaluationRequest {

    @NotNull
    private UUID datasetId;

    /** Agent 版本标识 (e.g. "v1.0", git commit hash) */
    private String agentVersion;

    /** 自定义 rubric (覆盖 dataset 中每个 case 的 rubric) */
    private String rubric;

    /** 要评分的维度, 默认 ["accuracy", "style", "safety"] */
    private List<String> dimensions;

    /** 完整 judge 配置 (覆盖 application.yml 默认) */
    private Map<String, Object> judgeConfig;
}
