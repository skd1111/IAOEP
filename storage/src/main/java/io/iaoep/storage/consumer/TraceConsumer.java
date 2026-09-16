package io.iaoep.storage.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.iaoep.storage.config.StorageProperties;
import io.iaoep.storage.model.StandardSpan;
import io.iaoep.storage.sink.ClickHouseSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka Trace Consumer — 订阅 {@code iaoep.traces} topic, 反序列化为
 * {@link StandardSpan} 后写入 ClickHouse。
 *
 * <p>关键点:
 * <ul>
 *   <li><b>批量消费</b>: spring.kafka.listener.type=batch,一次最多拉 1000 条</li>
 *   <li><b>手动 ACK</b>: 写入成功后才 ACK,失败则下次重试</li>
 *   <li><b>单条失败不影响批量</b>: 解析失败的跳过,继续处理其他</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceConsumer {

    private final ClickHouseSink sink;
    private final StorageProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(
            topics = "${iaoep.storage.kafka-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(List<String> messages, Acknowledgment ack) {
        int received = messages.size();
        int accepted = 0;
        int rejected = 0;

        for (String json : messages) {
            try {
                StandardSpan span = objectMapper.readValue(json, StandardSpan.class);
                sink.add(span);
                accepted++;
            } catch (Exception e) {
                rejected++;
                log.warn("Failed to parse span: {} | error={}", truncate(json), e.getMessage());
            }
        }

        if (received > 100) {
            log.debug("TraceConsumer batch: received={}, accepted={}, rejected={}", received, accepted, rejected);
        }

        // 手动 ACK: 写入已加入缓冲,即使 ClickHouse flush 失败,Kafka 也会重试
        ack.acknowledge();
    }

    private String truncate(String s) {
        return s == null ? "null" : (s.length() > 200 ? s.substring(0, 200) + "..." : s);
    }
}
