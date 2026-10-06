package com.example.recruitmentsystem.service.chat;

import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.config.LlmConfig;
import com.example.recruitmentsystem.dto.chat.AiChatMessageDto;
import com.example.recruitmentsystem.dto.chat.ChatHistoryResponse;
import com.example.recruitmentsystem.entity.AiChatMessage;
import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.llm.prompt.ChatPrompt;
import com.example.recruitmentsystem.llm.service.LlmChatService;
import com.example.recruitmentsystem.mapper.AiChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI-4 智能客服业务编排。
 *
 * <p>v0.6.2 改进：</p>
 * <ul>
 *   <li>新增 "ready / search_done / llm_start" 三个 SSE 事件，让前端在 streamCall 阻塞等 LLM 时有即时反馈（解决"长时间不回应"的体感问题）</li>
 *   <li>每个阶段打 INFO 日志（含耗时），便于排查"卡哪一步"</li>
 *   <li>streamChat 仍然永不抛异常（保留 v0.6.1 修复）</li>
 * </ul>
 *
 * <p>v0.6.1 关键修复（保留）：</p>
 * <ul>
 *   <li><b>streamChat 永不再抛异常</b>（包括预校验、saveMessage、LLM 调用）</li>
 *   <li>三层 try/catch 嵌套：外层 catch(Throwable) 兜底；中层 BusinessException；内层 LLM 流</li>
 *   <li>不再依赖 GlobalExceptionHandler 写入 text/event-stream 响应</li>
 * </ul>
 *
 * <p>三条流路径：(A) 命中 → LLM 流式 + 持久化；(B) 未命中 → 固定提示词；(C) 任何异常 → SSE error。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** 未命中关键词检索时返回的固定提示（不调 LLM，节省 token） */
    public static final String CANNED_NO_HIT =
            "抱歉，这个问题不在我能回答的范围内；请联系管理员。";

    /** 写入失败时的兜底提示（避免泄漏 DB 细节） */
    public static final String USER_MSG_SAVE_FAIL =
            "系统繁忙，请稍后再试";

    private final LlmClient llmClient;
    private final LlmChatService llmChatService;
    private final LlmConfig llmConfig;
    private final AiChatMessageMapper chatMapper;

    /**
     * 流式回答用户问题。
     *
     * <p><b>关键不变量</b>：本方法永远不会抛出任何异常。所有错误路径均通过 SSE error 事件 + emitter.complete() 关闭。</p>
     *
     * <p>v0.6.2 阶段化事件（前端可显示"客服正在思考…"等状态）：</p>
     * <ol>
     *   <li>权限校验：ADMIN 角色 → emit error + complete + return</li>
     *   <li>参数校验：空 / 超长 → emit error + complete + return</li>
     *   <li>emit "ready"：sessionId 已就绪</li>
     *   <li>写 USER 消息：DB 异常 → emit error + complete + return</li>
     *   <li>emit "search_done"：关键词检索结果（hits 数）</li>
     *   <li>emit "llm_start"：开始调 LLM</li>
     *   <li>LLM 流式 / 固定提示词路径</li>
     * </ol>
     */
    public void streamChat(Long userId, String sessionId, String question, SseEmitter emitter) {
        long t0 = System.currentTimeMillis();
        try {
            doStreamChat(userId, sessionId, question, emitter);
        } catch (Throwable t) {
            // 兜底：捕获所有（Error + RuntimeException + checked），绝不外抛
            log.error("[AiChatService] fatal stream error userId={} sid={} elapsed={}ms",
                    userId, sessionId, System.currentTimeMillis() - t0, t);
            safeEmitErrorAndComplete(emitter, "系统繁忙，请稍后再试");
        }
    }

    /** 实际业务逻辑（异常由外层 streamChat 兜底） */
    private void doStreamChat(Long userId, String sessionId, String question, SseEmitter emitter) {
        // 1. 权限校验（ADMIN 拒收）
        String role = CurrentUserContext.getRole();
        if ("ADMIN".equals(role)) {
            safeEmitErrorAndComplete(emitter, "管理员无 AI 客服权限");
            return;
        }
        // 2. 参数校验
        if (question == null || question.isBlank()) {
            safeEmitErrorAndComplete(emitter, "问题不能为空");
            return;
        }
        if (question.length() > 2000) {
            safeEmitErrorAndComplete(emitter, "问题过长（≤2000 字符）");
            return;
        }
        String sid = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;

        log.info("[AiChatService] stream START userId={} sid={} qLen={}", userId, sid, question.length());

        // 3. emit "ready"：连接就绪 + sessionId 告知前端
        safeEmitReady(emitter, sid);

        // 4. 写 USER 消息（DB 异常由外层兜底）
        AiChatMessage userMsg;
        try {
            userMsg = saveMessage(userId, sid, "USER", question);
            log.info("[AiChatService] USER saved msgId={} userId={}", userMsg.getId(), userId);
        } catch (Exception saveEx) {
            log.error("[AiChatService] save USER failed userId={} sid={}", userId, sid, saveEx);
            safeEmitErrorAndComplete(emitter, USER_MSG_SAVE_FAIL);
            return;
        }

        // 5. 关键词检索
        List<LlmChatService.ScoredSection> top;
        try {
            top = llmChatService.topK(question);
        } catch (Exception searchEx) {
            log.error("[AiChatService] keyword search failed userId={} sid={}", userId, sid, searchEx);
            safeEmitErrorAndComplete(emitter, USER_MSG_SAVE_FAIL);
            return;
        }
        boolean hasHit = top != null && !top.isEmpty();
        // 6. emit "search_done"：告诉前端检索结果
        safeEmitSearchDone(emitter, hasHit ? top.size() : 0);

        try {
            if (!hasHit) {
                // 路径 B：固定提示词
                log.info("[AiChatService] path B (canned) userId={} sid={}", userId, sid);
                safeEmitDelta(emitter, CANNED_NO_HIT);
                AiChatMessage asst = saveMessage(userId, sid, "ASSISTANT", CANNED_NO_HIT);
                log.info("[AiChatService] ASSISTANT saved (canned) msgId={}", asst.getId());
                safeEmitDone(emitter, sid, asst.getId());
                safeComplete(emitter);
                return;
            }

            // 路径 A：LLM 流式
            log.info("[AiChatService] path A (LLM) userId={} sid={} model={}",
                    userId, sid, llmConfig.getChatModel());

            String matchedContext = llmChatService.buildContext(question);
            String userPromptText = ChatPrompt.userPrompt(question, matchedContext);
            StringBuilder full = new StringBuilder();

            // 7. emit "llm_start"
            safeEmitLlmStart(emitter);

            long t0llm = System.currentTimeMillis();
            String responseText = llmClient.streamCall(
                    "AI-4",
                    ChatPrompt.SYSTEM_PROMPT,
                    userPromptText,
                    userId,
                    llmConfig.getChatMaxTokens(),
                    llmConfig.getChatModel(),
                    chunk -> {
                        full.append(chunk);
                        safeEmitDelta(emitter, chunk);
                    });
            long llmElapsed = System.currentTimeMillis() - t0llm;
            log.info("[AiChatService] LLM stream done userId={} sid={} elapsed={}ms respLen={}",
                    userId, sid, llmElapsed, responseText == null ? 0 : responseText.length());

            // 流完成：写 ASSISTANT 消息
            AiChatMessage asst = saveMessage(userId, sid, "ASSISTANT",
                    responseText == null || responseText.isEmpty() ? full.toString() : responseText);
            log.info("[AiChatService] ASSISTANT saved msgId={} userId={}", asst.getId(), userId);
            safeEmitDone(emitter, sid, asst.getId());
            safeComplete(emitter);
        } catch (BusinessException e) {
            log.warn("[AiChatService] 业务异常 userId={} sid={} msg={}", userId, sid, e.getMessage());
            safeEmitErrorAndComplete(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("[AiChatService] 流式调用异常 userId={} sid={}", userId, sid, e);
            safeEmitErrorAndComplete(emitter, "客服暂不可用，请稍后再试");
        }
    }

    /**
     * 拉取当前用户指定 session 的最近 N 条历史（按 created_at ASC）。
     *
     * @param userId    当前用户 ID
     * @param sessionId 会话 UUID
     * @param limit     上限（≤100，默认 20）
     */
    public ChatHistoryResponse getRecentHistory(Long userId, String sessionId, int limit) {
        if (userId == null) throw new BusinessException(401, "未登录");
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(400, "sessionId 不能为空");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<AiChatMessage> rows = chatMapper.selectRecentBySession(userId, sessionId, safeLimit);
        // 倒序查出来的按 DESC；前端希望 ASC（早 → 晚）
        List<AiChatMessage> asc = new ArrayList<>(rows);
        Collections.reverse(asc);
        List<AiChatMessageDto> dtos = new ArrayList<>(asc.size());
        for (AiChatMessage m : asc) {
            AiChatMessageDto dto = new AiChatMessageDto();
            dto.setId(m.getId());
            dto.setRole(m.getRole());
            dto.setContent(m.getContent());
            dto.setCreatedAt(m.getCreatedAt());
            dtos.add(dto);
        }
        ChatHistoryResponse resp = new ChatHistoryResponse();
        resp.setSessionId(sessionId);
        resp.setMessages(dtos);
        return resp;
    }

    /**
     * 软删除当前用户指定 session 的所有消息。
     *
     * @return 受影响行数
     */
    public int clearSession(Long userId, String sessionId) {
        if (userId == null) throw new BusinessException(401, "未登录");
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(400, "sessionId 不能为空");
        }
        return chatMapper.softDeleteBySession(userId, sessionId);
    }

    // ============ 私有 ============

    private AiChatMessage saveMessage(Long userId, String sessionId, String role, String content) {
        AiChatMessage m = new AiChatMessage();
        m.setUserId(userId);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        chatMapper.insert(m);
        return m;
    }

    // ============ SSE 安全写入辅助（不再抛异常） ============

    /**
     * 安全写 SSE ready 事件。v0.6.2：连接建立后立即告知前端 sessionId。
     */
    private void safeEmitReady(SseEmitter emitter, String sessionId) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("ready")
                    .data(Map.of("sessionId", sessionId == null ? "" : sessionId)));
        } catch (Exception e) {
            log.warn("[AiChatService] SSE ready 写出失败：{}", e.getMessage());
        }
    }

    /**
     * 安全写 SSE search_done 事件。v0.6.2：告诉前端关键词检索命中数。
     */
    private void safeEmitSearchDone(SseEmitter emitter, int hits) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("search_done")
                    .data(Map.of("hits", hits)));
        } catch (Exception e) {
            log.warn("[AiChatService] SSE search_done 写出失败：{}", e.getMessage());
        }
    }

    /**
     * 安全写 SSE llm_start 事件。v0.6.2：开始调 LLM，让前端显示"客服正在生成…"。
     */
    private void safeEmitLlmStart(SseEmitter emitter) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("llm_start").data(Map.of()));
        } catch (Exception e) {
            log.warn("[AiChatService] SSE llm_start 写出失败：{}", e.getMessage());
        }
    }

    /**
     * 安全写 SSE delta 事件。失败仅记日志，不抛异常。
     */
    private void safeEmitDelta(SseEmitter emitter, String text) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("delta")
                    .data(Map.of("text", text == null ? "" : text)));
        } catch (Exception e) {
            log.warn("[AiChatService] SSE delta 写出失败：{}", e.getMessage());
        }
    }

    /**
     * 安全写 SSE done 事件。失败仅记日志，不抛异常。
     */
    private void safeEmitDone(SseEmitter emitter, String sessionId, Long messageId) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(Map.of("sessionId", sessionId, "messageId", messageId == null ? -1 : messageId)));
        } catch (Exception e) {
            log.warn("[AiChatService] SSE done 事件写出失败：{}", e.getMessage());
        }
    }

    /**
     * 安全写 SSE error 事件 + 关闭 emitter。失败仅记日志，不抛异常。
     *
     * <p>替代之前的 emitError（会抛 BusinessException）+ completeWithError（async dispatcher 会触发
     * GlobalExceptionHandler 写 text/event-stream 失败）。</p>
     */
    private void safeEmitErrorAndComplete(SseEmitter emitter, String message) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", message == null ? "系统繁忙，请稍后再试" : message)));
        } catch (Exception e) {
            log.warn("[AiChatService] SSE error 事件写出失败：{}", e.getMessage());
        } finally {
            safeComplete(emitter);
        }
    }

    /**
     * 安全关闭 emitter。任何异常均吞掉。
     */
    private void safeComplete(SseEmitter emitter) {
        if (emitter == null) return;
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("[AiChatService] emitter.complete 失败（可能已关闭）：{}", e.getMessage());
        }
    }
}