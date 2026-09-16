package io.iaoep.sdk.autoconfigure;

import io.iaoep.sdk.aspect.ObservedAgentAspect;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * IAOEP SDK Auto-Configuration。
 *
 * <p>注册:
 * <ul>
 *   <li>{@link OpenTelemetry} Bean (基于 OTLP exporter)</li>
 *   <li>{@link ObservedAgentAspect} Bean (AOP 拦截)</li>
 * </ul>
 *
 * <p>配置 (application.yml):
 * <pre>
 * iaoep:
 *   sdk:
 *     enabled: true                    # 默认 true
 *     otlp-endpoint: http://localhost:4318
 *     service-name: my-agent
 *     tenant-id: default
 *     sampling-ratio: 1.0
 * </pre>
 *
 * <p>环境变量 (备选):
 * <ul>
 *   <li>{@code IAOEP_EXPORTER_OTLP_ENDPOINT}</li>
 *   <li>{@code IAOEP_SERVICE_NAME}</li>
 *   <li>{@code IAOEP_TENANT_ID}</li>
 *   <li>{@code IAOEP_SAMPLING_RATIO}</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "iaoep.sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IaoepSdkAutoConfiguration {

    @Value("${iaoep.sdk.otlp-endpoint:${IAOEP_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}}")
    private String otlpEndpoint;

    @Value("${iaoep.sdk.service-name:${IAOEP_SERVICE_NAME:iaoep-agent}}")
    private String serviceName;

    @Value("${iaoep.sdk.tenant-id:${IAOEP_TENANT_ID:default}}")
    private String tenantId;

    @Value("${iaoep.sdk.sampling-ratio:${IAOEP_SAMPLING_RATIO:1.0}}")
    private double samplingRatio;

    /**
     * 注册 OpenTelemetry SDK (OTLP HTTP exporter)。
     */
    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetry openTelemetry() {
        log.info("IAOEP SDK initializing: endpoint={}, service={}, tenant={}", otlpEndpoint, serviceName, tenantId);

        // Resource attributes (OTel GenAI SemConv + IAOEP 扩展)
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put(AttributeKey.stringKey("service.name"), serviceName)
                        .put(AttributeKey.stringKey("iaoep.tenant_id"), tenantId)
                        .build()));

        // OTLP HTTP exporter (默认, 与 Ingest Gateway HTTP 端口对接)
        SpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(otlpEndpoint + "/v1/traces")
                .setTimeout(Duration.ofSeconds(5))
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter)
                        .setScheduleDelay(Duration.ofSeconds(1))
                        .build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    /**
     * 注册 AOP 拦截器 Bean (Spring 会自动扫描 @Aspect)。
     */
    @Bean
    public ObservedAgentAspect observedAgentAspect(OpenTelemetry openTelemetry) {
        return new ObservedAgentAspect(openTelemetry);
    }
}
