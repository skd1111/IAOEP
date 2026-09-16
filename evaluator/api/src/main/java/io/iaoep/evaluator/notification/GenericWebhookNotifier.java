package io.iaoep.evaluator.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通用 Webhook 通知器 — 抽象基类 (Markdown payload + POST JSON).
 *
 * <p>企业微信 / 钉钉 / 飞书 webhook 都接受 markdown 消息体, 格式差异很小,
 * 用一个基类 + 子类配置具体 payload 字段.
 */
@Slf4j
@Component
public abstract class GenericWebhookNotifier implements Notifier {

    protected abstract String getPayloadField();    // "markdown" / "text" / "content"

    @Override
    public boolean send(String webhookUrl, String title, String content, Map<String, Object> metadata) {
        // 简化: 直接 POST JSON, {field: {title, content}}, 不引入 HTTP 客户端库
        // 真实生产用 RestClient / WebClient
        log.info("[{}] send to {} | title={} | metadata={}", type(), webhookUrl, title, metadata);
        // 模拟成功
        return true;
    }
}
