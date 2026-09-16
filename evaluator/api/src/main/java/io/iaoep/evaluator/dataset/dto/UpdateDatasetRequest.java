package io.iaoep.evaluator.dataset.dto;

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
public class UpdateDatasetRequest {

    /** 可选: 修改 cases (完整替换) */
    private List<Map<String, Object>> cases;

    /** 可选: 修改 metadata */
    private Map<String, Object> metadata;

    /** 可选: 设为当前激活版本 (同 name 其他版本自动取消激活) */
    private Boolean activate;
}
