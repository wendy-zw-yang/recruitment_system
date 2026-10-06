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

    /**
     * AI-4 智能客服专用模型。默认同 {@link #model}，但用户可设置为更快的模型（如 {@code qwen-turbo}）
     * 以获得更快首字速度。详见 {@code docs/dev-logs/2026-10-06.md} §v0.6 速度优化。
     */
    private String chatModel;

    /**
     * AI-4 单次响应最大 token 数。用于限制客服回答长度（中文 200 字 ≈ 400 tokens，留 50% buffer）。
     * 调小可缩短响应时间。
     */
    private Integer chatMaxTokens = 600;

    /**
     * 解析 chatModel（缺省时回退到 {@link #model}）。
     */
    public String getChatModel() {
        return (chatModel == null || chatModel.isBlank()) ? model : chatModel;
    }
}
