package io.iaoep.evaluator.evolution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        log.info("AnalystScheduler triggering...");
        // Phase 3 简化: 只跑默认 project (或第一个 project)
        // 实际应该遍历所有 project_id, 每个跑一次
        // 这里省略 project 列表查询, 等待 PR 21 接入 EvolutionLog 后再做完整调度
        log.info("AnalystScheduler placeholder - see PR 21 for full integration");
    }
}
