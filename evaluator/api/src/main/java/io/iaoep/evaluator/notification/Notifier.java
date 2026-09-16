package io.iaoep.evaluator.notification;

import java.util.Map;

/**
 * 通知发送器抽象接口.
 *
 * <p>每种 IM / 协作平台 (企业微信 / 钉钉 / 飞书) 实现此接口.
 * Phase 2: 内置 3 个实现 (wechat_work / dingtalk / feishu),
 * Phase 3: 可扩展 Email / Slack / Webhook 自定义.
 */
public interface Notifier {

    /** 通知类型 (e.g. "wechat_work" / "dingtalk" / "feishu") */
    String type();

    /**
     * 发送通知.
     *
     * @param webhookUrl 目标 webhook URL (从 NotificationChannel.webhookUrl 读)
     * @param title       消息标题 (Markdown)
     * @param content     消息内容 (Markdown)
     * @param metadata    额外元数据 (e.g. projectId / evaluationId)
     * @return 发送结果 (true 成功)
     */
    boolean send(String webhookUrl, String title, String content, Map<String, Object> metadata);
}
