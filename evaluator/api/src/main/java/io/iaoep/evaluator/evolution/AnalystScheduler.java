package io.iaoep.evaluator.evolution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Analyst Scheduler — 周期触发 Analyst.
 *
 * <p>默认每天凌晨 2 点跑, 可通过配置调整:</p>
 * <pre>
 * iaoep:
 *   evaluator:
 *     analyst:
 *       enabled: true
 *       cron: "0 0 2 * * ?"   # 默认每天 02:00
 *       lookback-jobs: 20     # 最近多少个 job
 * </pre>
 *
 * <p>Phase 3 简化: 对所有 project 跑 (实际生产应按 project 配置或租户隔离).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iaoep.evaluator.analyst.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AnalystScheduler {

    private final AnalystService analystService;

    @Value("${iaoep.evaluator.analyst.lookback-jobs:20}")
    private int lookbackJobs;

    /**
     * 默认 cron: 每天凌晨 2 点 (可通过 iaoep.evaluator.analyst.cron 覆盖).
     * 这里用 fixedDelay 演示 (Phase 3 简化为 1 天间隔, 可改为 cron).
     */
    @Scheduled(fixedDelayString = "${iaoep.evaluator.analyst.fixed-delay-ms:86400000}",
               initialDelay = 10000)
    public void runAnalyst() {
        log.info("AnalystScheduler triggering (lookbackJobs={})...", lookbackJobs);
        try {
            // 使用默认 project ID (实际生产应遍历所有 project)
            UUID defaultProjectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            int suggestionsGenerated = analystService.analyze(defaultProjectId, lookbackJobs);
            log.info("AnalystScheduler completed: {} suggestions generated", suggestionsGenerated);
        } catch (Exception e) {
            log.error("AnalystScheduler failed: {}", e.getMessage(), e);
        }
    }
}
