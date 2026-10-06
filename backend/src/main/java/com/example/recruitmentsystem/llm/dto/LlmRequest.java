package com.example.recruitmentsystem.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI Chat Completions 协议请求体。
 *
 * <p>对应 {@code https://api.openai.com/v1/chat/completions}（MiniMax / 通义千问兼容）。</p>
 */
public class LlmRequest {

    private String model;
    private List<Message> messages;

    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

    /** 可选；流式输出（SSE）时为 true，详见 §6.4.5 AI-4。 */
    private Boolean stream = false;

    /**
     * 可选；单次响应最大 token 数。AI-4 用以限制客服回答长度（避免过度生成拉慢速度）。
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public ResponseFormat getResponseFormat() { return responseFormat; }
    public void setResponseFormat(ResponseFormat responseFormat) { this.responseFormat = responseFormat; }
    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public static class Message {
        private String role;
        private String content;

        public Message() {}
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class ResponseFormat {
        private String type;
        public ResponseFormat() {}
        public ResponseFormat(String type) { this.type = type; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
