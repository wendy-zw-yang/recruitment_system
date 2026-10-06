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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    /** v0.6：用于流式调用（AI-4）。复用同一 HttpClient 以节省连接池。 */
    private final HttpClient httpClient;
    private final Duration httpTimeout;

    public LlmClient(LlmConfig llmConfig, AiCallLogMapper aiCallLogMapper, ObjectMapper objectMapper) {
        this.llmConfig = llmConfig;
        this.aiCallLogMapper = aiCallLogMapper;
        this.objectMapper = objectMapper;

        this.httpTimeout = Duration.ofSeconds(llmConfig.getTimeoutSeconds() == null ? 30 : llmConfig.getTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder().connectTimeout(httpTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.httpClient);
        factory.setReadTimeout(httpTimeout);
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

    /**
     * v0.6 AI-4 流式调用 LLM。返回完整响应文本（用于持久化到 ai_call_log）。
     *
     * <p>协议：OpenAI Chat Completions + {@code stream=true}，逐行 {@code data: {json}} 解析。
     * 兼容通义千问 Qwen / MiniMax 等 OpenAI-兼容服务。</p>
     *
     * <p>每个 chunk 调一次 {@code chunkHandler.accept(chunk)}；调用方负责把 chunk 转发到 SSE。
     * 流完成 → 写 ai_call_log（status=SUCCESS，response=完整内容）。</p>
     *
     * <p>失败处理：
     * <ul>
     *   <li>配置缺失 → 抛 BusinessException(400)</li>
     *   <li>HTTP 非 2xx → 抛 BusinessException(500)</li>
     *   <li>超时（{@link #httpTimeout}） → 抛 BusinessException(500)</li>
     *   <li>流中断 → 抛 BusinessException(500)；已发出的 chunk 已在调用方处理，本方法不重发</li>
     * </ul>
     * </p>
     *
     * @param functionCode  AI-4（写入 ai_call_log）
     * @param systemPrompt  系统提示词
     * @param userPrompt    用户提示词
     * @param callerUserId  调用方 user.id
     * @param maxTokens     单次响应最大 token（null 不传）
     * @param modelOverride 模型覆盖（null 用 {@code llmConfig.getModel()}；传 {@code llmConfig.getChatModel()} 让 AI-4 用更快模型）
     * @param chunkHandler  chunk 回调；参数 = 文本增量
     * @return 完整响应文本（已拼接所有 chunk）
     * @throws BusinessException 配置缺失 / HTTP 失败 / 超时 / 流解析异常
     */
    public String streamCall(String functionCode,
                             String systemPrompt,
                             String userPrompt,
                             Long callerUserId,
                             Integer maxTokens,
                             String modelOverride,
                             java.util.function.Consumer<String> chunkHandler) {
        validateConfig();
        String combined = systemPrompt + "\n\n" + userPrompt;

        LlmRequest request = new LlmRequest();
        String model = (modelOverride == null || modelOverride.isBlank())
                ? llmConfig.getModel()
                : modelOverride;
        request.setModel(model);
        request.setMessages(List.of(
                new LlmRequest.Message("system", systemPrompt),
                new LlmRequest.Message("user", userPrompt)
        ));
        request.setStream(true);
        if (maxTokens != null && maxTokens > 0) {
            request.setMaxTokens(maxTokens);
        }
        // v0.6.3：API 强制要求 response_format.type（"text" 或 "json_object"）
        // 此前注释错误写"AI-4 不强制"，实际是必填，缺则 HTTP 400
        request.setResponseFormat(new LlmRequest.ResponseFormat("text"));

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new BusinessException(500, "流式请求序列化失败：" + e.getMessage());
        }

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(llmConfig.getApiUrl()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + llmConfig.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .timeout(httpTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long start = System.currentTimeMillis();
        StringBuilder full = new StringBuilder();
        String status = "SUCCESS";
        String errorMessage = null;
        Integer promptTokens = null;
        Integer completionTokens = null;

        try {
            HttpResponse<java.util.stream.Stream<String>> resp = httpClient.send(
                    httpReq, HttpResponse.BodyHandlers.ofLines());

            int code = resp.statusCode();
            if (code / 100 != 2) {
                status = "FAIL";
                // v0.6.3：读取 API 错误响应体，存入 ai_call_log 便于诊断 HTTP 400 等
                StringBuilder bodyBuf = new StringBuilder();
                try (java.util.stream.Stream<String> errLines = resp.body()) {
                    for (java.util.Iterator<String> it = errLines.iterator(); it.hasNext(); ) {
                        String l = it.next();
                        if (l != null && !l.isBlank()) bodyBuf.append(l).append('\n');
                        if (bodyBuf.length() > 400) break; // 错误响应通常很短，截断即可
                    }
                } catch (Exception bodyReadErr) {
                    log.debug("[LlmClient] 读错误响应失败：{}", bodyReadErr.getMessage());
                }
                String body = bodyBuf.toString().trim();
                errorMessage = truncate("HTTP " + code + (body.isEmpty() ? "" : " | body: " + body), 500);
                throw new BusinessException(500, "大模型调用失败：" + errorMessage);
            }

            java.util.stream.Stream<String> lines = resp.body();
            java.util.Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (line == null || line.isBlank()) continue;
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if ("[DONE]".equals(data)) break;

                JsonNode node;
                try {
                    node = objectMapper.readTree(data);
                } catch (Exception parseErr) {
                    log.warn("[LlmClient] {} SSE 行解析失败：{}", functionCode, parseErr.getMessage());
                    continue;
                }
                JsonNode choices = node.path("choices");
                if (!choices.isArray() || choices.isEmpty()) continue;
                JsonNode delta = choices.path(0).path("delta");
                String content = delta.path("content").asText("");
                if (!content.isEmpty()) {
                    full.append(content);
                    try {
                        chunkHandler.accept(content);
                    } catch (Exception chunkErr) {
                        log.warn("[LlmClient] {} chunkHandler 抛错（已忽略）：{}",
                                functionCode, chunkErr.getMessage());
                    }
                }
                // 部分服务在最后一条带 usage
                JsonNode usage = node.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    Integer pt = usage.path("prompt_tokens").asInt(0);
                    Integer ct = usage.path("completion_tokens").asInt(0);
                    if (pt > 0) promptTokens = pt;
                    if (ct > 0) completionTokens = ct;
                }
            }
            return full.toString();
        } catch (BusinessException e) {
            status = "FAIL";
            errorMessage = e.getMessage();
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            status = "TIMEOUT";
            errorMessage = truncate(e.getMessage(), 500);
            throw new BusinessException(500, "大模型调用超时");
        } catch (java.io.IOException e) {
            status = "FAIL";
            errorMessage = truncate(e.getMessage(), 500);
            log.error("[LlmClient] 流式调用 IO 异常 function={}", functionCode, e);
            throw new BusinessException(500, "大模型调用失败：" + errorMessage);
        } catch (InterruptedException e) {
            status = "FAIL";
            errorMessage = "interrupted";
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "大模型调用被中断");
        } finally {
            persistLog(functionCode, combined, full.toString(), start, status,
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
