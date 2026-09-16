package io.iaoep.evaluator.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 飞书群机器人 Webhook 通知器.
 *
 * <p>webhook URL 格式: <pre>https://open.feishu.cn/open-apis/bot/v2/hook/xxx</pre>
 *
 * <p>payload 格式: {@code {"msg_type": "interactive", "card": {...}}} (富文本)
 * Phase 2 简化: 用 markdown 文本.
 */
@Slf4j
@Component
public class FeishuNotifier extends GenericWebhookNotifier {

    @Override
    public String type() { return "feishu"; }

    @Override
    protected String getPayloadField() { return "content"; }
}
