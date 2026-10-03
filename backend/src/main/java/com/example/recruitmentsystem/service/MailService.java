package com.example.recruitmentsystem.service;

/**
 * 邮件发送抽象。{@code app.mail.enabled=true} → 真发；否则仅 console 输出（演示态）。
 */
public interface MailService {

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人邮箱
     * @param subject 主题
     * @param body    正文
     */
    void send(String to, String subject, String body);
}
