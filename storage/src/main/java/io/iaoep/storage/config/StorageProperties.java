package io.iaoep.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IAOEP Storage 业务配置 (绑定 {@code iaoep.storage.*})。
 */
@Data
@ConfigurationProperties(prefix = "iaoep.storage")
public class StorageProperties {

    private String kafkaTopic = "iaoep.traces";
    private ClickHouseConfig clickhouse = new ClickHouseConfig();
    private BatchConfig batch = new BatchConfig();
    private RetryConfig retry = new RetryConfig();

    @Data
    public static class ClickHouseConfig {
        private String url = "jdbc:clickhouse://localhost:8123/iaoep";
        private String user = "iaoep";
        private String password = "iaoep_dev_pwd";
        private String driverClassName = "com.clickhouse.jdbc.ClickHouseDriver";
    }

    @Data
    public static class BatchConfig {
        /** 累积 N 条 flush 一次 */
        private int size = 1000;
        /** 或每 X ms flush 一次 (取先到者) */
        private long flushIntervalMs = 5000;
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long backoffMs = 1000;
    }
}
