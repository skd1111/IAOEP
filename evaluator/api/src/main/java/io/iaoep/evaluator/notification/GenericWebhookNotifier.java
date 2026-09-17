package io.iaoep.evaluator.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    private final RestClient restClient = RestClient.builder().build();

    protected abstract String getPayloadField();    // "markdown" / "text" / "content"

    @Override
    public boolean send(String webhookUrl, String title, String content, Map<String, Object> metadata) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("[{}] webhook URL not configured, skipping", type());
            return false;
        }
        try {
            Map<String, Object> payload = Map.of(
                    getPayloadField(), Map.of(
                            "title", title,
                            "content", content
                    )
            );
            restClient.post()
                    .uri(webhookUrl)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[{}] webhook sent: {}", type(), title);
            return true;
        } catch (Exception e) {
            log.warn("[{}] webhook send failed: {}", type(), e.getMessage());
            return false;
        }
    }
}
