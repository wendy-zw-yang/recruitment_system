package com.example.recruitmentsystem.service.impl;

import com.example.recruitmentsystem.config.MailProperties;
import com.example.recruitmentsystem.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送实现。
 *
 * <p>{@code app.mail.enabled=false}（默认）：仅 INFO 日志输出，便于演示态查看验证码。
 * 设为 true 时通过 {@link JavaMailSender} 真发，需保证 SMTP 配置完整。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final MailProperties mailProperties;
    private final JavaMailSender mailSender;

    @Override
    public void send(String to, String subject, String body) {
        if (!mailProperties.isEnabled()) {
            log.info("[MailService:DEV] to={} subject={} body={}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (mailProperties.getFromAddress() != null && !mailProperties.getFromAddress().isBlank()) {
            message.setFrom(mailProperties.getFromAddress());
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("[MailService:SENT] to={} subject={}", to, subject);
    }
}
