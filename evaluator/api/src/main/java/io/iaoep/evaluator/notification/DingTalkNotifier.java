package io.iaoep.evaluator.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 钉钉群机器人 Webhook 通知器.
 *
 * <p>webhook URL 格式: <pre>https://oapi.dingtalk.com/robot/send?access_token=xxx</pre>
 *
 * <p>payload 格式: {@code {"msgtype": "markdown", "markdown": {"title": "...", "text": "..."}}}.
 */
@Slf4j
@Component
public class DingTalkNotifier extends GenericWebhookNotifier {

    @Override
    public String type() { return "dingtalk"; }

    @Override
    protected String getPayloadField() { return "markdown"; }
}
