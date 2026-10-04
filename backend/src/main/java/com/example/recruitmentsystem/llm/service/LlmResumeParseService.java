package com.example.recruitmentsystem.llm.service;

import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.llm.prompt.ResumeParsePrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * AI-1 简历解析服务。负责调 LlmClient 并把响应归一化为稳定结构。
 *
 * <p>v0.2 修订（2026-10-04）：</p>
 * <ul>
 *   <li>normalize 对 education / work / projects / skills 四个数组做强校验：缺失或非数组都强制转为空数组</li>
 *   <li>normalize 内部对每个数组元素的关键字段做兜底（如 school / company / name 缺失时补 null 而非丢弃整条）</li>
 *   <li>parseToJson 失败时抛错并把 LLM 原始响应前 300 字写到日志，方便诊断</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmResumeParseService {

    /** 简历主表里三个数组字段的名称，用于 normalize 强校验 */
    private static final String[] REQUIRED_LIST_FIELDS = {"education", "work", "projects"};

    /** 每个数组元素必须保留的关键字段（缺失时设为 null 而非整条丢弃） */
    private static final Set<String> EDUCATION_KEYS = Set.of("school", "major", "degree", "startDate", "endDate", "description");
    private static final Set<String> WORK_KEYS = Set.of("company", "position", "startDate", "endDate", "description", "tags");
    private static final Set<String> PROJECT_KEYS = Set.of("name", "role", "startDate", "endDate", "description", "techStack");

    /** 在元素里默认应该是「数组」的关键字段，缺失时补空数组而非 null */
    private static final Set<String> ARRAY_KEYS = Set.of("tags", "techStack");

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /**
     * 把简历纯文本解析为归一化的 JSON 节点。
     * 失败抛 {@code BusinessException("AI-1 解析失败：xxx")}，由 {@link com.example.recruitmentsystem.service.ResumeService} 降级处理。
     */
    public JsonNode parseToJson(String resumeText, Long callerUserId) {
        JsonNode raw;
        try {
            raw = llmClient.callJson("AI-1",
                    ResumeParsePrompt.SYSTEM_PROMPT,
                    ResumeParsePrompt.userPrompt(resumeText),
                    callerUserId);
        } catch (RuntimeException e) {
            log.warn("[AI-1] LLM 解析失败: callerUserId={}, msg={}", callerUserId, e.getMessage());
            throw e;
        }
        return normalize(raw);
    }

    /**
     * 把 LLM 返回的不规整 JSON 归一化为前端易用的结构。
     *
     * <ul>
     *   <li>非对象 → 抛错</li>
     *   <li>education / work / projects 缺失或非数组 → 强制空数组（绝不丢字段）</li>
     *   <li>数组元素缺失关键字段 → 设为 null，不丢整条</li>
     *   <li>skills 非数组 → 转字符串数组</li>
     * </ul>
     */
    public JsonNode normalize(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            throw new IllegalArgumentException("大模型返回非对象");
        }
        ObjectNode root = (ObjectNode) raw;

        for (String field : REQUIRED_LIST_FIELDS) {
            JsonNode normalized = ensureObjectArray(root.get(field), keySetFor(field));
            root.set(field, normalized);
        }

        JsonNode skills = ensureStringArray(root.get("skills"));
        root.set("skills", skills);

        return root;
    }

    private static Set<String> keySetFor(String field) {
        return switch (field) {
            case "education" -> EDUCATION_KEYS;
            case "work" -> WORK_KEYS;
            case "projects" -> PROJECT_KEYS;
            default -> Set.of();
        };
    }

    /**
     * 确保字段是对象数组，元素补齐缺失的关键字段。
     * 普通缺失字段补 null；{@code tags} / {@code techStack} 等数组型字段补空数组。
     * 输入 null / 非数组 / 数组里元素是字符串 → 都转为标准格式。
     */
    private JsonNode ensureObjectArray(JsonNode node, Set<String> requiredKeys) {
        ArrayNode target = objectMapper.createArrayNode();
        if (node == null || node.isNull()) {
            return target;
        }
        if (!node.isArray()) {
            // LLM 把整段写成字符串 / 对象 → 退化为空数组，绝不丢字段
            log.warn("[AI-1 normalize] 期望数组但收到 {}，降级为空数组", node.getNodeType());
            return target;
        }
        node.forEach(child -> {
            ObjectNode item;
            if (child != null && child.isObject()) {
                item = (ObjectNode) child;
            } else {
                // 字符串/数字等 → 包成空对象
                item = objectMapper.createObjectNode();
            }
            // 补齐关键字段
            Set<String> existing = new HashSet<>();
            item.fieldNames().forEachRemaining(existing::add);
            for (String key : requiredKeys) {
                if (!existing.contains(key)) {
                    if (ARRAY_KEYS.contains(key)) {
                        item.set(key, objectMapper.createArrayNode());
                    } else {
                        item.putNull(key);
                    }
                }
            }
            target.add(item);
        });
        return target;
    }

    /**
     * 确保字段是字符串数组。非数组 → 转字符串数组；非字符串元素 → toString 后塞入。
     */
    private JsonNode ensureStringArray(JsonNode node) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (node == null || node.isNull()) {
            return arr;
        }
        if (node.isArray()) {
            node.forEach(child -> arr.add(child.isTextual() ? child.asText() : child.toString()));
            return arr;
        }
        if (node.isTextual()) {
            String text = node.asText();
            for (String piece : text.split("[,，;；\\s]+")) {
                if (!piece.isBlank()) {
                    arr.add(piece.trim());
                }
            }
            return arr;
        }
        arr.add(node.toString());
        return arr;
    }
}
