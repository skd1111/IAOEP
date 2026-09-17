package io.iaoep.storage.sink;

import com.clickhouse.jdbc.ClickHouseConnection;
import com.clickhouse.jdbc.ClickHouseDataSource;
import io.iaoep.storage.config.StorageProperties;
import io.iaoep.storage.model.StandardSpan;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ClickHouse 批量写入器 — 累积 N 条或每 X 秒 flush 一次。
 *
 * <p>设计要点:
 * <ul>
 *   <li><b>批量写入</b>: 减少 ClickHouse 网络往返</li>
 *   <li><b>定时 flush</b>: 即使流量低也能及时落库</li>
 *   <li><b>线程安全</b>: 用 {@link ReentrantLock} 保护缓冲</li>
 *   <li><b>优雅关闭</b>: shutdown 时 flush 所有缓冲数据</li>
 *   <li><b>失败重试</b>: 写入失败重试 N 次,仍失败则跳过 (避免卡住消费)</li>
 * </ul>
 *
 * <p>写入 SQL:
 * <pre>
 * INSERT INTO iaoep.traces (tenant_id, trace_id, ...) VALUES (?, ?, ...)
 * </pre>
 */
@Slf4j
@Component
public class ClickHouseSink {

    private final StorageProperties props;
    private final MeterRegistry meterRegistry;

    private ClickHouseDataSource dataSource;
    private ClickHouseConnection connection;
    private PreparedStatement batchStatement;

    private final List<StandardSpan> buffer = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger pendingFlushes = new AtomicInteger(0);

    private final Counter spansWrittenCounter;
    private final Counter writeErrorsCounter;
    private final Timer writeTimer;
    private volatile Thread flushScheduler;

    public ClickHouseSink(StorageProperties props, MeterRegistry meterRegistry) {
        this.props = props;
        this.meterRegistry = meterRegistry;
        this.spansWrittenCounter = Counter.builder("iaoep.spans.written")
                .description("Total spans successfully written to ClickHouse")
                .register(meterRegistry);
        this.writeErrorsCounter = Counter.builder("iaoep.spans.write_errors")
                .description("Total span write errors")
                .register(meterRegistry);
        this.writeTimer = Timer.builder("iaoep.spans.write_latency")
                .description("Latency of ClickHouse batch writes")
                .register(meterRegistry);
    }

    @PostConstruct
    public void init() throws Exception {
        log.info("ClickHouseSink connecting to {}", props.getClickhouse().getUrl());
        var chProps = new java.util.Properties();
        chProps.setProperty("user", props.getClickhouse().getUser());
        chProps.setProperty("password", props.getClickhouse().getPassword());
        chProps.setProperty("compress", "true");
        chProps.setProperty("decompress", "true");

        this.dataSource = new ClickHouseDataSource(props.getClickhouse().getUrl(), chProps);
        this.connection = dataSource.getConnection();

        // 预编译批量 INSERT (参数占位符 = size)
        String sql = buildInsertSql(props.getBatch().getSize());
        this.batchStatement = connection.prepareStatement(sql);
        log.info("ClickHouseSink ready: bufferSize={}", props.getBatch().getSize());

        startFlushScheduler();
    }

    @PreDestroy
    public void shutdown() {
        log.info("ClickHouseSink shutting down, flushing remaining {} spans", buffer.size());
        if (flushScheduler != null) {
            flushScheduler.interrupt();
        }
        try {
            flush();
        } catch (Exception e) {
            log.error("Final flush failed", e);
        }
        try {
            if (batchStatement != null) batchStatement.close();
            if (connection != null) connection.close();
            if (dataSource != null) dataSource.close();
        } catch (Exception e) {
            log.warn("Error closing ClickHouse resources", e);
        }
    }

    /**
     * 添加一条 Span 到缓冲。缓冲满自动触发 flush。
     */
    public void add(StandardSpan span) {
        lock.lock();
        try {
            buffer.add(span);
            if (buffer.size() >= props.getBatch().getSize()) {
                flushOnce(); // 失败时异常传播, 由 flush() 或调用方处理
            }
        } catch (Exception e) {
            log.warn("Auto-flush in add() failed: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 强制 flush (定时器调用 / 优雅关闭)。
     * 包含重试: 失败后释放锁 → sleep → 重新获取锁 → 再尝试一次。
     */
    public void flush() {
        lock.lock();
        try {
            flushOnce();
            return; // 成功
        } catch (Exception e) {
            writeErrorsCounter.increment();
            log.error("Failed to flush spans to ClickHouse: {}", e.getMessage());
            // 释放锁后重试, 避免持锁 sleep
            lock.unlock();
            try {
                try {
                    Thread.sleep(props.getRetry().getBackoffMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                lock.lock();
                try {
                    flushOnce(); // 重试一次
                } catch (Exception retryEx) {
                    writeErrorsCounter.increment();
                    log.error("Retry flush failed, discarding {} spans: {}", buffer.size(), retryEx.getMessage());
                    buffer.clear();
                } finally {
                    lock.unlock();
                }
            } catch (Exception outerEx) {
                // sleep 之前或 lock 之后异常, 安全丢弃
                log.warn("Retry flush outer error: {}", outerEx.getMessage());
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 单次 flush 尝试 (必须在 lock 内调用, 失败时抛异常但不释放锁)。
     */
    private void flushOnce() throws Exception {
        if (buffer.isEmpty()) {
            return;
        }
        int size = buffer.size();
        pendingFlushes.incrementAndGet();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            batchStatement.clearParameters();
            batchStatement.clearBatch();
            for (int i = 0; i < size; i++) {
                bindSpan(batchStatement, buffer.get(i));
                batchStatement.addBatch();
            }
            int[] result = batchStatement.executeBatch();
            spansWrittenCounter.increment(result.length);
            log.debug("ClickHouseSink flushed {} spans", result.length);
            buffer.clear();
        } finally {
            sample.stop(writeTimer);
            pendingFlushes.decrementAndGet();
        }
    }

    /**
     * 启动定时 flush 线程。
     */
    private void startFlushScheduler() {
        flushScheduler = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(props.getBatch().getFlushIntervalMs());
                    flush();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Scheduled flush error", e);
                }
            }
        }, "clickhouse-flush-scheduler");
        flushScheduler.setDaemon(true);
        flushScheduler.start();
    }

    /**
     * 构建批量 INSERT SQL。
     */
    private String buildInsertSql(int batchSize) {
        // 单条 INSERT,JDBC 通过 addBatch + executeBatch 实现批量
        return """
                INSERT INTO iaoep.traces (
                    tenant_id, trace_id, span_id, parent_span_id, span_name, span_kind,
                    start_time, end_time, duration_ms,
                    agent_name, session_id, user_id, skill_name,
                    llm_system, llm_model, llm_input_tokens, llm_output_tokens,
                    tool_name, tool_call_id, tool_error_type,
                    cost_cny, status, error_message,
                    ab_test_name, ab_test_group,
                    tags, attributes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }

    /**
     * 绑定 Span 字段到 PreparedStatement (按 buildInsertSql 中的列顺序)。
     */
    private void bindSpan(PreparedStatement ps, StandardSpan span) throws java.sql.SQLException {
        ps.setString(1, nullToEmpty(span.getTenantId()));
        ps.setString(2, nullToEmpty(span.getTraceId()));
        ps.setString(3, nullToEmpty(span.getSpanId()));
        ps.setString(4, nullToEmpty(span.getParentSpanId()));
        ps.setString(5, nullToEmpty(span.getSpanName()));
        ps.setString(6, nullToEmpty(span.getSpanKind()));
        // 时间: 纳秒时间戳 → java.sql.Timestamp (ClickHouse DateTime64(9))
        ps.setTimestamp(7, nanosToTimestamp(span.getStartTimeUnixNano()));
        ps.setTimestamp(8, nanosToTimestamp(span.getEndTimeUnixNano()));
        ps.setInt(9, span.getDurationMs() != null ? span.getDurationMs() : 0);
        ps.setString(10, nullToEmpty(span.getAgentName()));
        ps.setString(11, nullToEmpty(span.getSessionId()));
        ps.setString(12, nullToEmpty(span.getUserId()));
        ps.setString(13, nullToEmpty(span.getSkillName()));
        ps.setString(14, nullToEmpty(span.getLlmSystem()));
        ps.setString(15, nullToEmpty(span.getLlmModel()));
        ps.setLong(16, span.getLlmInputTokens() != null ? span.getLlmInputTokens() : 0L);
        ps.setLong(17, span.getLlmOutputTokens() != null ? span.getLlmOutputTokens() : 0L);
        ps.setString(18, nullToEmpty(span.getToolName()));
        ps.setString(19, nullToEmpty(span.getToolCallId()));
        ps.setString(20, nullToEmpty(span.getToolErrorType()));
        ps.setDouble(21, span.getCostCny() != null ? span.getCostCny() : 0.0);
        ps.setString(22, nullToEmpty(span.getStatus()));
        ps.setString(23, nullToEmpty(span.getErrorMessage()));
        // Phase 5: A/B Test 字段
        ps.setString(24, nullToEmpty(span.getAbTestName()));
        ps.setString(25, nullToEmpty(span.getAbTestGroup()));
        // 灵活扩展 Map 字段
        ps.setObject(26, span.getTags() != null ? span.getTags() : java.util.Map.of());
        ps.setObject(27, span.getAttributes() != null ? span.getAttributes() : java.util.Map.of());
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 纳秒时间戳 → java.sql.Timestamp (ClickHouse DateTime64(9) 兼容).
     */
    private java.sql.Timestamp nanosToTimestamp(Long nanos) {
        if (nanos == null || nanos == 0L) {
            return new java.sql.Timestamp(0);
        }
        long seconds = nanos / 1_000_000_000L;
        long nanoAdjustment = nanos % 1_000_000_000L;
        return java.sql.Timestamp.from(java.time.Instant.ofEpochSecond(seconds, nanoAdjustment));
    }

    // ============================================================
    // 健康检查
    // ============================================================

    /**
     * 等待缓冲清空 (数据实际落库) 后再返回, 供 Consumer 在 ACK 前调用。
     *
     * @param timeoutMs 最大等待时间 (毫秒)
     * @return true=缓冲已清空, false=超时
     */
    public boolean waitForFlush(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            lock.lock();
            try {
                if (buffer.isEmpty()) {
                    return true;
                }
            } finally {
                lock.unlock();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public int getBufferSize() {
        return buffer.size();
    }

    public int getPendingFlushes() {
        return pendingFlushes.get();
    }
}
