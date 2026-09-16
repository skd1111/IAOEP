package io.iaoep.evaluator.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 企业微信群机器人 Webhook 通知器.
 *
 * <p>webhook URL 格式: <pre>https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx</pre>
 *
 * <p>payload 格式: {@code {"msgtype": "markdown", "markdown": {"content": "..."}}}.
 */
@Slf4j
@Component
public class WeChatWorkNotifier extends GenericWebhookNotifier {

    @Override
    public String type() { return "wechat_work"; }

    @Override
    protected String getPayloadField() { return "markdown"; }
}
