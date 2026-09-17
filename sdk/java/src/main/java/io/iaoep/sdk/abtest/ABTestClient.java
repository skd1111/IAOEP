package io.iaoep.sdk.abtest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A/B Test Client — 调 IAOEP Evaluator 的 /assign 端点, 决定当前请求分配到 baseline 还是 candidate.
 *
 * <p>Phase 5: 支持按 trace_id 细粒度分配 (同一 trace 的所有 span 落到同一 group).
 * 缓存 key: {@code abTestName + traceId}, 比 user_id 更细粒度 (一次请求的所有子 span 一致).</p>
 */
@Slf4j
@ConfigurationProperties(prefix = "iaoep.sdk.abtest")
public class ABTestClient {

    private final RestClient client;

    @Value("${iaoep.sdk.abtest.evaluator-base-url:http://localhost:8082}")
    private String evaluatorBaseUrl;

    /** 缓存 key → (group, expireAt) */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(String group, long expireAtMs) {}

    public ABTestClient() {
        this.client = RestClient.builder().build();
    }

    /**
     * 决定当前请求分配到哪个 group (Phase 5: 按 trace_id 细粒度).
     *
     * @param abTestName  A/B 测试名 (与 Evaluator 的 ab_test.name 对应)
     * @param projectId   项目 ID
     * @param traceId     trace ID (同一 trace 所有 span 一致)
     * @return "baseline" 或 "candidate"
     */
    public String assignByTraceId(String abTestName, UUID projectId, String traceId) {
        return doAssign(abTestName, projectId, "trace:" + traceId);
    }

    /**
     * 决定当前请求分配到哪个 group (按 userId, 旧 API 保留).
     */
    public String assign(String abTestName, UUID projectId, String userId) {
        return doAssign(abTestName, projectId, "user:" + (userId != null ? userId : "anonymous"));
    }

    private String doAssign(String abTestName, UUID projectId, String stickyKey) {
        String cacheKey = abTestName + ":" + stickyKey;
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expireAtMs > now) {
            return cached.group;
        }

        try {
            AssignResponse resp = client()
                    .get()
                    .uri(evaluatorBaseUrl + "/api/v1/iaoep/projects/{projectId}/ab-tests/{abId}/assign",
                            projectId, abTestName)
                    .retrieve()
                    .body(AssignResponse.class);
            String group = resp != null && resp.group != null ? resp.group : "baseline";

            cache.put(cacheKey, new CacheEntry(group, now + Duration.ofSeconds(30).toMillis()));
            return group;
        } catch (Exception e) {
            log.debug("ABTest assign failed, fallback to baseline: {}", e.getMessage());
            return "baseline";   // 失败兜底: 默认 baseline
        }
    }

    private RestClient client() {
        return RestClient.builder().baseUrl(evaluatorBaseUrl).build();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssignResponse {
        private String group;
        @JsonProperty("abTestId")
        private String abTestId;
    }
}
