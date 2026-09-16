package com.example.democustomer.agent;

import com.example.democustomer.tools.DateTimeTools;
import io.iaoep.sdk.abtest.ABTest;
import io.iaoep.sdk.abtest.ABTestContext;
import io.iaoep.sdk.annotation.ObservedAgent;
import io.iaoep.sdk.annotation.ObservedLLMCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Demo Agent — 集成 IAOEP SDK (观测 + A/B Test) 的端到端示例.
 *
 * <p>关键注解:
 * <ul>
 *   <li>{@code @ObservedAgent}: 顶层 Agent 调用, 产生 agent.run Span</li>
 *   <li>{@code @ObservedLLMCall}: LLM 调用, 产生 llm.call Span, 含 token 数</li>
 *   <li>{@code @ObservedTool}: 工具调用, 产生 tool.execute Span</li>
 *   <li>{@code @ABTest} (Phase 4): A/B 测试, baseline / candidate 两个版本自动分流</li>
 * </ul>
 *
 * <p>A/B Test 配置:
 * <ol>
 *   <li>在 IAOEP Evaluator 创建 AB Test: name="customer-service-ab", baseline="qwen3-turbo", candidate="qwen3-max"</li>
 *   <li>SDK 读 header 决定 group, 自动选择模型</li>
 *   <li>trace 显示 group (baseline / candidate), 方便对比</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceAgent {

    private final ChatClient chatClient;
    private final DateTimeTools dateTimeTools;

    /**
     * Phase 4: 加 @ABTest 注解. SDK 自动读 evaluator /assign 端点决定 group.
     * 默认缓存 30 秒 (同一 user 始终分到同一 group, 保证体验一致).
     */
    @ObservedAgent(name = "customer-service", skill = "general")
    @ABTest(name = "customer-service-ab")
    public String chat(String query, String userId) {
        String prompt = """
                你是客服助手。当前用户: %s
                用户问题: %s
                当前时间: %s
                请简洁回答。
                """.formatted(userId, query, dateTimeTools.getCurrentDateTime());

        // Phase 4: 根据 AB Test group 选择不同模型
        String group = ABTestContext.currentGroup();
        String model = "candidate".equals(group) ? "qwen3-max" : "qwen3-turbo";

        log.info("Agent chat: group={}, model={}", group, model);
        return callLlm(model, prompt, 80, 200);
    }

    @ObservedLLMCall(
            system = "openai",
            modelParam = "#modelName",
            inputTokensParam = "#inputTokens",
            outputTokensParam = "#outputTokens"
    )
    public String callLlm(String modelName, String prompt, Integer inputTokens, Integer outputTokens) {
        // 真实场景调用 ChatClient
        return chatClient.prompt(prompt)
                .options(org.springframework.ai.chat.model.ChatOptions.builder().model(modelName).build())
                .call()
                .content();
    }
}
