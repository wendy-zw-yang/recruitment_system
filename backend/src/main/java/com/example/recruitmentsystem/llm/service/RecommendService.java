package com.example.recruitmentsystem.llm.service;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.llm.prompt.RecommendPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.7.3 AI 推荐打分服务。
 *
 * <p>封装 LLM 调用 + JSON 解析，输出 {@code Map<jobId, score>}。
 * 失败 / 异常一律静默 fallback（返回空 map），由调用方决定如何使用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /**
     * 调 LLM 给 top-N 职位打分。
     *
     * @param userId     候选人 userId（用于 ai_call_log + SSE 推送）
     * @param resumeJson 简历 JSON（{@code resume.structured_json}）；可为 null
     * @param positionText 期望职位关键词；可空
     * @param industryName 期望行业名（service 已 JOIN 字典）；可空
     * @param province 期望省份；可空
     * @param cityName 期望城市名；可空
     * @param jobsContent 候选职位 Markdown 列表（由调用方格式化）
     * @return {@code Map<Long jobId, Integer score>}；LLM 失败 / 解析失败时返回空 map
     */
    public Map<Long, Integer> rank(String userIdForLog,
                                   String resumeJson,
                                   String positionText,
                                   String industryName,
                                   String province,
                                   String cityName,
                                   String jobsContent) {
        if (jobsContent == null || jobsContent.isBlank()) return new HashMap<>();
        try {
            String userPrompt = RecommendPrompt.userPrompt(
                    resumeJson, positionText, industryName, province, cityName, jobsContent);
            JsonNode root = llmClient.callJson("AI-2", RecommendPrompt.SYSTEM_PROMPT, userPrompt, null);
            // 期望：root 是 array，每个元素含 jobId / score
            Map<Long, Integer> out = new LinkedHashMap<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    JsonNode jid = item.get("jobId");
                    JsonNode sc = item.get("score");
                    if (jid == null || !jid.canConvertToLong()) continue;
                    if (sc == null || !sc.isInt()) continue;
                    int score = Math.max(0, Math.min(100, sc.asInt()));
                    out.put(jid.asLong(), score);
                }
            } else if (root.isObject() && root.has("scores")) {
                // 容错：LLM 可能包了一层 {"scores":[...]}
                JsonNode arr = root.get("scores");
                if (arr.isArray()) {
                    for (JsonNode item : arr) {
                        JsonNode jid = item.get("jobId");
                        JsonNode sc = item.get("score");
                        if (jid == null || !jid.canConvertToLong()) continue;
                        if (sc == null || !sc.isInt()) continue;
                        out.put(jid.asLong(), Math.max(0, Math.min(100, sc.asInt())));
                    }
                }
            }
            log.info("[RecommendService] AI 打分完成 user={} jobs={} returned={}",
                    userIdForLog, countJobs(jobsContent), out.size());
            return out;
        } catch (BusinessException be) {
            log.warn("[RecommendService] AI 打分失败 user={} : {}", userIdForLog, be.getMessage());
            return new HashMap<>();
        } catch (Exception e) {
            log.error("[RecommendService] AI 打分异常 user={}", userIdForLog, e);
            return new HashMap<>();
        }
    }

    /** 简单计算职位列表条目数（用于日志） */
    private static int countJobs(String jobsContent) {
        if (jobsContent == null) return 0;
        int n = 0;
        int i = jobsContent.indexOf("- jobId=");
        while (i >= 0) {
            n++;
            i = jobsContent.indexOf("- jobId=", i + 1);
        }
        return n;
    }
}