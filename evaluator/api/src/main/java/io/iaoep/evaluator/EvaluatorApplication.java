package io.iaoep.evaluator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IAOEP Evaluator API 入口.
 *
 * <p>Phase 2 核心服务:
 * <ul>
 *   <li>Golden Dataset 管理 (PR 12)</li>
 *   <li>Rule Scorer (PR 13)</li>
 *   <li>LLM-as-Judge 调用 (PR 14)</li>
 *   <li>评测任务调度 (PR 15)</li>
 *   <li>回归检测 (PR 16)</li>
 *   <li>通知集成 (PR 17)</li>
 * </ul>
 */
@EnableScheduling
@SpringBootApplication
public class EvaluatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvaluatorApplication.class, args);
    }
}
