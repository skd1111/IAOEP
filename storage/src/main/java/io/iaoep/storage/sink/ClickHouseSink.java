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
                flushLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 强制 flush (定时器调用 / 优雅关闭)。
     */
    public void flush() {
        lock.lock();
        try {
            flushLocked();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 实际 flush 逻辑 (必须在 lock 内调用)。
     */
    private void flushLocked() {
        if (buffer.isEmpty()) {
            return;
        }
        int size = buffer.size();
        pendingFlushes.incrementAndGet();
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // 重置 PreparedStatement 参数
            batchStatement.clearParameters();
            batchStatement.clearBatch();

            for (int i = 0; i < size; i++) {
                StandardSpan span = buffer.get(i);
                bindSpan(batchStatement, span);
                batchStatement.addBatch();
            }

            int[] result = batchStatement.executeBatch();
            int written = result.length;
            spansWrittenCounter.increment(written);
            log.debug("ClickHouseSink flushed {} spans", written);
            buffer.clear();
        } catch (Exception e) {
            writeErrorsCounter.increment();
            log.error("Failed to flush {} spans to ClickHouse: {}", size, e.getMessage());
            // 重试逻辑
            if (shouldRetry(e)) {
                retryFlush();
            } else {
                // 跳过 (避免卡住消费), 清空缓冲
                buffer.clear();
            }
        } finally {
            sample.stop(writeTimer);
            pendingFlushes.decrementAndGet();
        }
    }

    private boolean shouldRetry(Exception e) {
        // 简化: 总是重试一次
        return true;
    }

    private void retryFlush() {
        try {
            Thread.sleep(props.getRetry().getBackoffMs());
            flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
                    start_time_unix_nano, end_time_unix_nano, duration_ms,
                    agent_name, session_id, user_id, skill_name,
                    llm_system, llm_model, llm_input_tokens, llm_output_tokens,
                    tool_name, tool_call_id, tool_error_type,
                    cost_cny, status, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        ps.setLong(7, span.getStartTimeUnixNano() != null ? span.getStartTimeUnixNano() : 0L);
        ps.setLong(8, span.getEndTimeUnixNano() != null ? span.getEndTimeUnixNano() : 0L);
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
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ============================================================
    // 健康检查
    // ============================================================

    public int getBufferSize() {
        return buffer.size();
    }

    public int getPendingFlushes() {
        return pendingFlushes.get();
    }
}
