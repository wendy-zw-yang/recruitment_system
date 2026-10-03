package com.example.recruitmentsystem.llm.service;

import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.llm.prompt.ResumeParsePrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI-1 简历解析服务。负责调 LlmClient 并把响应归一化为稳定结构（教育/工作/项目缺失字段容错）。
 */
@Service
@RequiredArgsConstructor
public class LlmResumeParseService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /**
     * 把简历纯文本解析为归一化的 JSON 节点。
     * 失败抛 {@code BusinessException("大模型返回字段缺失：xxx")}，由 {@link com.example.recruitmentsystem.service.ResumeService} 降级处理。
     */
    public JsonNode parseToJson(String resumeText, Long callerUserId) {
        JsonNode raw = llmClient.callJson("AI-1",
                ResumeParsePrompt.SYSTEM_PROMPT,
                ResumeParsePrompt.userPrompt(resumeText),
                callerUserId);
        return normalize(raw);
    }

    /** 把 LLM 返回的不规整 JSON 归一化为前端易用的结构（缺失数组补 []、缺失字段保留）。 */
    public JsonNode normalize(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            throw new IllegalArgumentException("大模型返回非对象");
        }
        ObjectNode root = (ObjectNode) raw;
        ensureArray(root, "education");
        ensureArray(root, "work");
        ensureArray(root, "projects");
        ensureStringArray(root, "skills");
        return root;
    }

    private void ensureArray(ObjectNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            root.set(field, objectMapper.createArrayNode());
        } else if (!node.isArray()) {
            ArrayNode arr = objectMapper.createArrayNode();
            arr.add(node);
            root.set(field, arr);
        }
    }

    private void ensureStringArray(ObjectNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            root.set(field, objectMapper.createArrayNode());
        } else if (node.isArray()) {
            ArrayNode arr = objectMapper.createArrayNode();
            node.forEach(child -> arr.add(child.isTextual() ? child.asText() : child.toString()));
            root.set(field, arr);
        } else if (node.isTextual()) {
            ArrayNode arr = objectMapper.createArrayNode();
            String text = node.asText();
            for (String piece : text.split("[,，;；\\s]+")) {
                if (!piece.isBlank()) {
                    arr.add(piece.trim());
                }
            }
            root.set(field, arr);
        } else {
            ArrayNode arr = objectMapper.createArrayNode();
            arr.add(node.toString());
            root.set(field, arr);
        }
    }
}
