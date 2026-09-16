package io.iaoep.evaluator.regression.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRegressionRequest {

    @NotNull
    private UUID baselineJobId;

    @NotNull
    private UUID candidateJobId;

    /** 阻断阈值, 默认 0.05 (5%) */
    private Double threshold;
}
