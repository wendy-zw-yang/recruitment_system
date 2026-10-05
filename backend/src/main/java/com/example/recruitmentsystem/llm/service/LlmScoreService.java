package com.example.recruitmentsystem.llm.service;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.llm.prompt.ScorePrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * AI-2 简历 ↔ JD 匹配评分服务。
 *
 * <p>触发规则（详见 {@code docs/系统设计/详细设计/AI集成.md §6.4.3}）：</p>
 * <ul>
 *   <li>仅在投递瞬间由 {@code ApplicationServiceImpl} 触发一次</li>
 *   <li>HR 浏览投递列表 / 查看详情均不再触发</li>
 *   <li>失败时 ai_score 保持 NULL，前端展示「待评分」</li>
 *   <li>异步执行（{@code @Async}），投递 HTTP 请求立即返回</li>
 * </ul>
 *
 * <p>解析容错：返回非对象 / 字段缺失 / score 越界 → 抛错 → 上层决定保持 NULL。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmScoreService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /** AI-2 function code（写入 ai_call_log.function_code） */
    public static final String FUNCTION_CODE = "AI-2";

    /** 异步评分结果 */
    public static class ScoreResult {
        public int score;
        public String reason;

        public ScoreResult() {}

        public ScoreResult(int score, String reason) {
            this.score = score;
            this.reason = reason == null ? "" : reason;
        }
    }

    /**
     * 异步执行 AI-2 评分。由调用方（如 ApplicationServiceImpl.apply()）通过 {@code @Async} 调用。
     *
     * <p>评分完成后由调用方负责把 {@link ScoreResult} 写回 {@code application.ai_score} /
     * {@code application.ai_reason}。本方法本身不写 DB，便于事务边界控制。</p>
     *
     * @param resumeJson 候选人简历结构化 JSON
     * @param jobDescription 职位描述
     * @param jobRequirements 职位要求
     * @param jobTitle 职位标题
     * @param callerUserId 触发人（候选人 userId，写入 ai_call_log）
     * @return 评分结果（必非 null）
     * @throws BusinessException AI 返回非法 / 解析失败
     */
    @Async("taskExecutor")
    public ScoreResult score(String resumeJson,
                             String jobTitle,
                             String jobDescription,
                             String jobRequirements,
                             Long callerUserId) {
        JsonNode raw = llmClient.callJson(FUNCTION_CODE,
                ScorePrompt.SYSTEM_PROMPT,
                ScorePrompt.userPrompt(resumeJson, jobDescription, jobRequirements, jobTitle),
                callerUserId);
        return parse(raw);
    }

    /**
     * 同步版本（用于单测；调用方也可直接用）。
     *
     * <p>不带 {@code @Async}，直接执行。</p>
     */
    public ScoreResult scoreSync(String resumeJson,
                                 String jobTitle,
                                 String jobDescription,
                                 String jobRequirements,
                                 Long callerUserId) {
        JsonNode raw = llmClient.callJson(FUNCTION_CODE,
                ScorePrompt.SYSTEM_PROMPT,
                ScorePrompt.userPrompt(resumeJson, jobDescription, jobRequirements, jobTitle),
                callerUserId);
        return parse(raw);
    }

    private ScoreResult parse(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            log.warn("[LlmScoreService] AI 返回非对象，丢弃");
            throw new BusinessException(500, "AI 评分返回非对象");
        }
        JsonNode scoreNode = raw.get("score");
        if (scoreNode == null || !scoreNode.isInt()) {
            log.warn("[LlmScoreService] AI 返回缺少 score 字段");
            throw new BusinessException(500, "AI 评分缺少 score 字段");
        }
        int score = scoreNode.asInt();
        if (score < 0 || score > 100) {
            log.warn("[LlmScoreService] AI 返回 score 越界: {}", score);
            throw new BusinessException(500, "AI 评分越界（0-100）");
        }
        String reason = textOrEmpty(raw, "reason");
        return new ScoreResult(score, reason);
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || !v.isTextual() ? "" : v.asText();
    }
}
