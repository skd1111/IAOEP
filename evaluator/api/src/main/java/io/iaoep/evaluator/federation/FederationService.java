package io.iaoep.evaluator.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.iaoep.evaluator.federation.dto.FederationReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Federation Service — 跨租户聚合统计 + 差分隐私.
 *
 * <p>Phase 7 简化: 只做"指标聚合 + DP 噪声"两步, 不真正跨租户传输原始数据.</p>
 *
 * <p>流程 (聚合):
 * <ol>
 *   <li>从 ClickHouse / DB 拉所有租户的指标 (e.g. accuracy by model)</li>
 *   <li>按 dimension 分组 (e.g. model=qwen3-max)</li>
 *   <li>对每组计算聚合 (mean / count / p99)</li>
 *   <li>加 DP 噪声 (Laplace mechanism)</li>
 *   <li>租户 ID hash 后存入 (不暴露明文租户)</li>
 *   <li>对外暴露 noisyValue (trueValue 仅 Owner 可见)</li>
 * </ol>
 *
 * <p>Phase 7 简化: 聚合数据来自 Map 注入 (实际生产应查 ClickHouse):
 * <pre>
 *   Map&lt;String, Double&gt; { "tenantA": 0.92, "tenantB": 0.85, ... }
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FederationService {

    private final FederationAggregateRepository repo;

    @Value("${iaoep.evaluator.federation.default-epsilon:1.0}")
    private double defaultEpsilon;

    @Value("${iaoep.evaluator.federation.default-sensitivity:1.0}")
    private double defaultSensitivity;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 聚合跨租户指标 + 加 DP 噪声 + 持久化.
     *
     * @param name           指标名 (e.g. "accuracy", "latency_p99")
     * @param dimension      维度 (e.g. "model=qwen3-max")
     * @param tenantValues   各租户的指标值 (key=tenant_id, value=metric)
     * @param windowStart    时间窗口起点
     * @param windowEnd      时间窗口终点
     * @param epsilon        隐私预算 (默认从配置)
     * @return 聚合报告 (含 DP 噪声值)
     */
    @Transactional
    public FederationAggregate aggregate(
            String name,
            String dimension,
            Map<String, Double> tenantValues,
            Instant windowStart,
            Instant windowEnd,
            Double epsilon
    ) {
        if (tenantValues == null || tenantValues.isEmpty()) {
            throw new IllegalArgumentException("tenantValues 不能为空");
        }

        double eps = epsilon != null ? epsilon : defaultEpsilon;

        // 1. 真实聚合: 加权平均 (权重=1, 简化)
        double trueValue = tenantValues.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        int sampleSize = tenantValues.size();

        // 2. 租户 ID hash (不存明文)
        Map<String, Object> tenantHashes = tenantValues.keySet().stream()
                .collect(Collectors.toMap(
                        tid -> tid,
                        tid -> hashTenantId(tid)
                ));

        // 3. 加 DP 噪声
        double sensitivity = defaultSensitivity / sampleSize;
        double noisyValue = DifferentialPrivacy.addNoiseMean(trueValue, sampleSize, eps);

        // 4. 持久化
        FederationAggregate agg = FederationAggregate.builder()
                .name(name)
                .dimension(dimension)
                .trueValue(trueValue)
                .noisyValue(noisyValue)
                .sampleSize(sampleSize)
                .tenantHashes(tenantHashes)
                .epsilon(eps)
                .sensitivity(sensitivity)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .build();
        return repo.save(agg);
    }

    /**
     * 查询历史聚合.
     */
    @Transactional(readOnly = true)
    public List<FederationReportResponse> query(String name, Instant from, Instant to, boolean includeTruth) {
        return repo.findByNameAndWindowStartBetweenOrderByWindowStartDesc(name, from, to)
                .stream()
                .map(a -> FederationReportResponse.from(a, includeTruth))
                .toList();
    }

    /**
     * Phase 7.7: 多维聚合查询 (按 dimension 前缀匹配).
     *
     * @param dimensionPrefix e.g. "model=qwen3-turbo" 匹配所有 "model=qwen3-turbo,..."
     */
    @Transactional(readOnly = true)
    public List<FederationReportResponse> queryByDimensionPrefix(String dimensionPrefix, boolean includeTruth) {
        return repo.findAll().stream()
                .filter(a -> a.getDimension().startsWith(dimensionPrefix))
                .sorted(Comparator.comparing(FederationAggregate::getWindowStart).reversed())
                .map(a -> FederationReportResponse.from(a, includeTruth))
                .toList();
    }

    /**
     * Phase 7.7: 多维交叉表 (model × skill).
     *
     * @return map[model][skill] = noisy_value (最新)
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Double>> getMatrix(String name, Instant since) {
        Map<String, Map<String, Double>> matrix = new LinkedHashMap<>();
        repo.findAll().stream()
                .filter(a -> a.getName().equals(name))
                .filter(a -> a.getWindowStart().isAfter(since))
                .sorted(Comparator.comparing(FederationAggregate::getWindowStart).reversed())
                .forEach(a -> {
                    Map<String, String> parts = parseDimension(a.getDimension());
                    String model = parts.getOrDefault("model", "unknown");
                    String skill = parts.getOrDefault("skill", "unknown");
                    matrix.computeIfAbsent(model, k -> new LinkedHashMap<>());
                    // 每个 (model, skill) 只取最新一条
                    if (!matrix.get(model).containsKey(skill)) {
                        matrix.get(model).put(skill, a.getNoisyValue());
                    }
                });
        return matrix;
    }

    private Map<String, String> parseDimension(String dim) {
        Map<String, String> map = new HashMap<>();
        for (String part : dim.split(",")) {
            String[] kv = part.split("=");
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }

    /**
     * 租户 ID 不可逆 hash (不暴露明文租户 ID).
     */
    private String hashTenantId(String tenantId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(tenantId.getBytes("UTF-8"));
            // 取前 8 字节 hex
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
