package com.example.recruitmentsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI 集成的智能招聘系统 - 启动类。
 *
 * <p>Spring Boot 3.5.14 入口。启动后默认激活 {@code local} profile，
 * 读取 {@code application.yml} 与 {@code application-local.yml}。</p>
 *
 * <p>详细配置约定见 {@code docs/技术约束.md §5}，
 * 协作规范见项目根 {@code AGENTS.md}。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
