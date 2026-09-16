package com.example.democustomer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo Customer Service Agent — 端到端集成示例。
 *
 * <p>启动后:
 * <ul>
 *   <li>Spring AI Agent 监听 POST /chat</li>
 *   <li>所有调用自动通过 IAOEP SDK 产生 OTLP trace</li>
 *   <li>trace 发送到 {@code http://iaoep-ingest:4318}</li>
 *   <li>在 IAOEP Web Dashboard 实时查看</li>
 * </ul>
 */
@SpringBootApplication
public class DemoCustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoCustomerApplication.class, args);
    }
}
