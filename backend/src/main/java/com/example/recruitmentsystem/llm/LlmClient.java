package com.example.recruitmentsystem.llm;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.config.LlmConfig;
import com.example.recruitmentsystem.entity.AiCallLog;
import com.example.recruitmentsystem.llm.dto.LlmRequest;
import com.example.recruitmentsystem.llm.dto.LlmResponse;
import com.example.recruitmentsystem.mapper.AiCallLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 统一 LLM 调用入口。封装 OpenAI Chat Completions 协议 + JSON 解析 + Caffeine 缓存 + ai_call_log 写入。
 *
 * <p>详见 {@code docs/系统设计/详细设计/AI集成.md §6.3.1}。</p>
 */
@Slf4j
@Component
public class LlmClient {

    private static final int RESPONSE_LOG_BYTES = 4096;

    private final LlmConfig llmConfig;
    private final AiCallLogMapper aiCallLogMapper;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, JsonNode> cache;

    public LlmClient(LlmConfig llmConfig, AiCallLogMapper aiCallLogMapper, ObjectMapper objectMapper) {
        this.llmConfig = llmConfig;
        this.aiCallLogMapper = aiCallLogMapper;
        this.objectMapper = objectMapper;

        Duration timeout = Duration.ofSeconds(llmConfig.getTimeoutSeconds() == null ? 30 : llmConfig.getTimeoutSeconds());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();

        long ttl = llmConfig.getCacheTtlSeconds() == null ? 3600L : llmConfig.getCacheTtlSeconds();
        this.cache = ttl > 0
                ? Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(ttl)).maximumSize(1000).build()
                : null;
    }

    /**
     * 调用 LLM 并返回解析后的 JSON。
     *
     * @param functionCode  AI-1 / AI-2 / AI-3 / AI-4 / AI-5 / AI-6
     * @param systemPrompt  系统提示词（定义角色 + JSON Schema）
     * @param userPrompt    用户提示词（动态业务数据）
     * @param callerUserId  调用方 user.id（可空）
     * @return 解析后的 JSON 节点
     */
    public JsonNode callJson(String functionCode, String systemPrompt, String userPrompt, Long callerUserId) {
        String combined = systemPrompt + "\n\n" + userPrompt;
        String cacheKey = cache == null ? null : sha256(combined);

        if (cache != null) {
            JsonNode cached = cache.getIfPresent(cacheKey);
            if (cached != null) {
                log.debug("[LlmClient] cache hit: function={}", functionCode);
                return cached;
            }
        }

        validateConfig();
        LlmRequest request = new LlmRequest();
        request.setModel(llmConfig.getModel());
        request.setMessages(List.of(
                new LlmRequest.Message("system", systemPrompt),
                new LlmRequest.Message("user", userPrompt)
        ));
        request.setResponseFormat(new LlmRequest.ResponseFormat("json_object"));
        request.setStream(false);

        long start = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMessage = null;
        String responseText = null;
        Integer promptTokens = null;
        Integer completionTokens = null;

        try {
            LlmResponse response = restClient.post()
                    .uri(llmConfig.getApiUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + llmConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(LlmResponse.class);

            if (response == null) {
                throw new BusinessException(500, "大模型返回为空");
            }
            responseText = response.firstContent();
            if (responseText == null || responseText.isBlank()) {
                throw new BusinessException(500, "大模型返回为空");
            }
            if (response.getUsage() != null) {
                promptTokens = response.getUsage().getPromptTokens();
                completionTokens = response.getUsage().getCompletionTokens();
            }
            JsonNode parsed = extractJson(responseText, functionCode);
            if (cache != null) {
                cache.put(cacheKey, parsed);
            }
            return parsed;
        } catch (BusinessException e) {
            status = e.getCode() != null && e.getCode() >= 500 ? "FAIL" : "FAIL";
            errorMessage = e.getMessage();
            throw e;
        } catch (ResourceAccessException e) {
            status = "TIMEOUT";
            errorMessage = truncate(e.getMessage(), 500);
            throw new BusinessException(500, "大模型调用超时");
        } catch (Exception e) {
            status = "FAIL";
            errorMessage = truncate(e.getMessage(), 500);
            log.error("[LlmClient] 调用失败 function={}", functionCode, e);
            throw new BusinessException(500, "大模型调用失败：" + errorMessage);
        } finally {
            persistLog(functionCode, combined, responseText, start, status,
                    errorMessage, callerUserId, promptTokens, completionTokens);
        }
    }

    private void validateConfig() {
        if (isBlank(llmConfig.getApiUrl()) || isBlank(llmConfig.getApiKey()) || isBlank(llmConfig.getModel())) {
            throw new BusinessException(400, "大模型配置不完整");
        }
    }

    private void persistLog(String functionCode, String prompt, String response, long start, String status,
                            String errorMessage, Long callerUserId, Integer promptTokens, Integer completionTokens) {
        try {
            AiCallLog logRow = new AiCallLog();
            logRow.setFunctionCode(functionCode);
            logRow.setPrompt(truncate(prompt, 65535));
            logRow.setResponse(truncate(response, RESPONSE_LOG_BYTES));
            logRow.setLatencyMs((int) (System.currentTimeMillis() - start));
            logRow.setPromptTokens(promptTokens);
            logRow.setCompletionTokens(completionTokens);
            logRow.setStatus(status);
            logRow.setErrorMessage(errorMessage);
            logRow.setCallerUserId(callerUserId);
            aiCallLogMapper.insert(logRow);
        } catch (Exception e) {
            LlmClient.log.warn("[LlmClient] ai_call_log 写入失败", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    /** 匹配 <think>...</think> 块（MiniMax 等推理模型会输出） */
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
    /** 匹配首尾 markdown code fence（单行） */
    private static final Pattern MD_FENCE = Pattern.compile("^```(?:json)?\\s*\\n?|\\n?```\\s*$", Pattern.MULTILINE);

    /**
     * 从 LLM 原始返回中容错抽取 JSON 节点。
     *
     * <p>支持以下返回形态（按尝试顺序）：</p>
     * <ol>
     *   <li>纯 JSON：{@code {...}}</li>
     *   <li>带 <think> 思考块（MiniMax / DeepSeek-R1 等）：先去思考块再解析</li>
     *   <li>带 markdown code fence：{@code ```json\n{...}\n``` }</li>
     *   <li>前后缀带文字：截取首个 {@code {...}} 子串</li>
     *   <li>全部失败：抛错，errorMessage 含原始响应前 200 字</li>
     * </ol>
     */
    public static JsonNode extractJson(String text, String functionCode) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(500, "大模型返回为空");
        }
        String original = text;
        String cleaned = text.trim();

        // 1) 去掉 <think>...</think> 块（MiniMax 风格）
        cleaned = THINK_BLOCK.matcher(cleaned).replaceAll("").trim();
        if (cleaned.isEmpty()) {
            return failed(original, functionCode, "大模型返回仅包含思考块，无实际内容");
        }

        // 2) 直接解析
        JsonNode direct = tryParse(cleaned);
        if (direct != null) return direct;

        // 3) 去 markdown code fence 再试
        String stripped = MD_FENCE.matcher(cleaned).replaceAll("").trim();
        JsonNode fenced = tryParse(stripped);
        if (fenced != null) return fenced;

        // 4) 截取首个 {...}
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first >= 0 && last > first) {
            JsonNode brace = tryParse(cleaned.substring(first, last + 1));
            if (brace != null) return brace;
        }

        // 5) 截取首个 [...]
        int firstArr = cleaned.indexOf('[');
        int lastArr = cleaned.lastIndexOf(']');
        if (firstArr >= 0 && lastArr > firstArr) {
            JsonNode bracket = tryParse(cleaned.substring(firstArr, lastArr + 1));
            if (bracket != null) return bracket;
        }

        return failed(original, functionCode, null);
    }

    private static JsonNode tryParse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new ObjectMapper().readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonNode failed(String original, String functionCode, String hint) {
        String preview = original.length() > 200 ? original.substring(0, 200) + "..." : original;
        String msg = hint != null ? hint : "大模型返回内容解析失败";
        log.warn("[LlmClient] {} 解析失败，原始内容前 200 字：{}", functionCode, preview);
        throw new BusinessException(500, msg + " | 原始：" + preview);
    }
}
