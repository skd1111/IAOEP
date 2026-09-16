package io.iaoep.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * IAOEP Storage Worker — Kafka Consumer → ClickHouse 写入服务。
 *
 * <p>职责:
 * <ul>
 *   <li>订阅 Kafka topic {@code iaoep.traces}</li>
 *   <li>反序列化 IAOEP StandardSpan JSON</li>
 *   <li>批量写入 ClickHouse 表 {@code iaoep.traces}</li>
 *   <li>暴露 Prometheus metrics + health check</li>
 * </ul>
 *
 * <p>设计要点:
 * <ul>
 *   <li>批量写入: 累积 1000 条或 5 秒 flush 一次 (减少 ClickHouse 写入次数)</li>
 *   <li>失败重试: 写入失败重试 3 次,仍失败则跳过 (避免卡住消费)</li>
 *   <li>优雅关闭: 关闭前 flush 所有缓冲数据</li>
 * </ul>
 */
@EnableKafka
@SpringBootApplication
public class StorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageApplication.class, args);
    }
}
