package com.example.democustomer.controller;

import com.example.democustomer.agent.CustomerServiceAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Chat 端点 — 用户发送 query,Agent 返回回答。
 *
 * <p>所有调用会被 {@code @ObservedAgent} 自动 trace。</p>
 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final CustomerServiceAgent agent;

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        String userId = body.getOrDefault("userId", "demo-user");

        long start = System.currentTimeMillis();
        String response = agent.chat(query, userId);
        long latency = System.currentTimeMillis() - start;

        return Map.of(
                "query", query,
                "userId", userId,
                "response", response,
                "latencyMs", latency
        );
    }
}
