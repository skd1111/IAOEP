package io.iaoep.evaluator.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDatasetRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String version;

    @NotNull
    private List<Map<String, Object>> cases;

    /** 可选: 默认 true */
    private Boolean isActive;

    /** 可选: 默认值 {} */
    private Map<String, Object> metadata;
}
