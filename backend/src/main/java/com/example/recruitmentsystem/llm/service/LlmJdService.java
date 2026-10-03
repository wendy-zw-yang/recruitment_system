package com.example.recruitmentsystem.llm.service;

import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.llm.prompt.JdPolishPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI-3 JD 润色（润色模式：基于 HR 已有内容优化）。
 *
 * <p>失败由 Controller 层捕获并 toast；解析失败时返回空字段而非抛错，让 HR 自行填写。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmJdService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public static class PolishedJd {
        public String description;
        public String requirements;
        public String keywords;

        public PolishedJd() {}

        public PolishedJd(String description, String requirements, String keywords) {
            this.description = description;
            this.requirements = requirements;
            this.keywords = keywords;
        }
    }

    /**
     * 润色所有内容字段。任一字段可空：LLM 会基于上下文推断或返回空串。
     */
    public PolishedJd polish(String title,
                             String industryName,
                             String cityName,
                             String salaryRange,
                             String description,
                             String requirements,
                             String keywords,
                             Long callerUserId) {
        JsonNode raw = llmClient.callJson("AI-3",
                JdPolishPrompt.SYSTEM_PROMPT,
                JdPolishPrompt.userPrompt(title, industryName, cityName, salaryRange,
                        description, requirements, keywords),
                callerUserId);
        return parse(raw);
    }

    private PolishedJd parse(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            log.warn("[LlmJdService] 大模型返回非对象，返回空字段");
            return new PolishedJd("", "", "");
        }
        ObjectNode node = (ObjectNode) raw;
        return new PolishedJd(
                textOrEmpty(node, "description"),
                textOrEmpty(node, "requirements"),
                textOrEmpty(node, "keywords")
        );
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || !v.isTextual() ? "" : v.asText();
    }
}
