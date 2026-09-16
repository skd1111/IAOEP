package io.iaoep.evaluator.judge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Java 端 LLM Judge HTTP 客户端 — 调用 Python sidecar 的 FastAPI 服务.
 *
 * <p>架构:
 * <pre>
 *   Evaluator Java Service
 *        |
 *        | HTTP POST /score (FastAPI)
 *        v
 *   iaoep-judge (Python sidecar)
 *        |
 *        | HTTP (OpenAI 兼容协议)
 *        v
 *   千问 / GLM / DeepSeek / OpenAI ...
 * </pre>
 *
 * <p>配置 (application.yml):
 * <pre>
 * iaoep:
 *   evaluator:
 *     judge:
 *       base-url: http://iaoep-judge:9000
 *       timeout-seconds: 30
 *       voting-count: 1
 * </pre>
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "iaoep.evaluator.judge")
public class LLMJudgeClient {

    /** 从 Python sidecar 端点配置 */
    @Value("${iaoep.evaluator.judge.base-url:http://iaoep-judge:9000}")
    private String baseUrl;

    @Value("${iaoep.evaluator.judge.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${iaoep.evaluator.judge.voting-count:1}")
    private int votingCount;

    /** LLM 配置 (传给 sidecar, 由 sidecar 调用真实 LLM) */
    @Value("${iaoep.evaluator.judge.llm.model:qwen3-max}")
    private String llmModel;

    @Value("${iaoep.evaluator.judge.llm.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String llmBaseUrl;

    @Value("${iaoep.evaluator.judge.llm.api-key:}")
    private String llmApiKey;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .build();
        }
        return client;
    }

    /**
     * 调用 LLM Judge 评分.
     *
     * @param trace     一条 IAOEP trace (含 spans)
     * @param rubric    评分规则 (人类给定)
     * @param dimensions 要评分的维度列表 (默认 ["accuracy","style","safety"])
     * @return 评分结果
     */
    public ScoreResult score(Map<String, Object> trace, String rubric, List<String> dimensions) {
        JudgeConfig cfg = JudgeConfig.builder()
                .llmModel(llmModel)
                .llmBaseUrl(llmBaseUrl)
                .llmApiKey(llmApiKey)
                .timeoutSeconds(timeoutSeconds)
                .votingCount(votingCount)
                .build();

        ScoreRequest req = ScoreRequest.builder()
                .trace(trace)
                .rubric(rubric)
                .dimensions(dimensions != null ? dimensions : List.of("accuracy", "style", "safety"))
                .config(cfg)
                .build();

        try {
            return client()
                    .post()
                    .uri("/score")
                    .body(req)
                    .retrieve()
                    .body(ScoreResult.class);
        } catch (Exception e) {
            log.error("LLM Judge call failed: {}", e.getMessage());
            throw new RuntimeException("LLM Judge call failed: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // DTOs (与 Python sidecar 的 FastAPI 模型对齐)
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JudgeConfig {
        @JsonProperty("llm_model")
        private String llmModel;
        @JsonProperty("llm_base_url")
        private String llmBaseUrl;
        @JsonProperty("llm_api_key")
        private String llmApiKey;
        @JsonProperty("timeout_seconds")
        private int timeoutSeconds;
        @JsonProperty("voting_count")
        private int votingCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreRequest {
        private Map<String, Object> trace;
        private String rubric;
        private List<String> dimensions;
        private JudgeConfig config;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreResult {
        private double overall;
        private List<DimensionResult> dimensions;
        @JsonProperty("raw_response")
        private String rawResponse;
        @JsonProperty("elapsed_ms")
        private int elapsedMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DimensionResult {
        private String name;
        private double score;
        private String reason;
    }
}
