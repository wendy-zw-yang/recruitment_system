package com.example.recruitmentsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 客户端配置。绑定 {@code application.yml} 中 {@code llm} 节点。
 *
 * <p>所有字段均支持环境变量覆盖，便于在不同部署环境切换服务商。</p>
 *
 * <p>详见 {@code docs/技术约束.md §6} / {@code docs/系统设计/详细设计/AI集成.md §6.3.2}。</p>
 */
@Data
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    /** OpenAI Chat Completions 端点 */
    private String apiUrl;

    /** Bearer Token */
    private String apiKey;

    /** 模型名（如 MiniMax-M3 / qwen-plus） */
    private String model;

    /** 调用超时（秒） */
    private Integer timeoutSeconds = 30;

    /** 调用结果本地缓存 TTL（秒）。0 表示不缓存。 */
    private Integer cacheTtlSeconds = 3600;
}
