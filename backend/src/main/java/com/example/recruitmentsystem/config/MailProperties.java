package com.example.recruitmentsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用层邮件开关与发件人信息。
 *
 * <p>{@code enabled=false}（默认）→ {@link com.example.recruitmentsystem.service.MailService}
 * 仅打印到 console，用于演示态；{@code enabled=true} → 走真实 SMTP。</p>
 *
 * <p>详见 {@code docs/需求分析/功能性需求分析.md §9.3}（决策 ④ / ⑥）。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** 演示态 false / 生产态 true */
    private boolean enabled = false;

    /** 发件邮箱 */
    private String fromAddress;

    /** 发件人显示名 */
    private String fromName = "招聘系统";
}
