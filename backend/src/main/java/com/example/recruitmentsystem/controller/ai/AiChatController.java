package com.example.recruitmentsystem.controller.ai;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.LoginRequired;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.chat.ChatHistoryResponse;
import com.example.recruitmentsystem.service.chat.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI-4 智能客服 Controller。
 *
 * <p>v0.6.1：SSE 端点不再依赖 GlobalExceptionHandler（之前在异步通道里写 text/event-stream 失败）。</p>
 *
 * <p>所有错误处理下沉到 {@link AiChatService#streamChat}，保证：
 * <ul>
 *   <li>SSE 通道返回前已 emit error 事件 + complete emitter</li>
 *   <li>service 永不抛异常 → 不会触发 GlobalExceptionHandler 的 text/event-stream 写入失败</li>
 * </ul>
 * </p>
 */
@Slf4j
@LoginRequired
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * SSE 流式响应。
     *
     * <p>SSE 事件协议（v0.6.2 扩展）：</p>
     * <ul>
     *   <li>{@code event: ready       data: {"sessionId": "..."}} — 连接建立；前端可显示"客服正在思考"</li>
     *   <li>{@code event: search_done data: {"hits": N}} — 关键词检索完成；N=0 走固定提示词路径</li>
     *   <li>{@code event: llm_start   data: {}} — LLM 调用开始</li>
     *   <li>{@code event: delta       data: {"text": "..."}} — 每 chunk</li>
     *   <li>{@code event: done        data: {"sessionId": "...", "messageId": N}} — 流完成</li>
     *   <li>{@code event: error       data: {"message": "..."}} — 失败</li>
     * </ul>
     *
     * <p>v0.6.2 关键修复：注册 {@code emitter.onError/onTimeout/onCompletion} 回调，
     * 吸收 async I/O 异常（客户端断开 / 超时），避免 Spring ErrorPageFilter 输出
     * "Cannot render error page" 警告日志。</p>
     *
     * @param question   用户问题（1-2000 字符）
     * @param sessionId  会话 UUID（可选，缺省时自动生成）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String question,
                             @RequestParam(required = false) String sessionId) {
        Long userId = CurrentUserContext.getUserId();
        // 超时 0 = 永不过期；客户端断开 / 调用 emitter.complete() 时自然结束
        SseEmitter emitter = new SseEmitter(0L);

        // v0.6.2：注册 async 回调。吸收客户端断开 / 超时 / 任何 emitter 内部异常，
        // 避免 Spring ErrorPageFilter / GlobalExceptionHandler 在异步通道里写 text/event-stream 失败。
        emitter.onError((ex) -> {
            // 客户端断开是最常见原因；只在 debug 级别记日志避免噪音
            log.debug("[AiChatController] emitter.onError (async): {}", ex.getMessage());
        });
        emitter.onTimeout(() -> log.debug("[AiChatController] emitter.onTimeout"));
        emitter.onCompletion(() -> log.debug("[AiChatController] emitter.onCompletion userId={}", userId));

        // v0.6.2：service.streamChat 永不抛异常（顶层 try/catch 兜底），
        // 此处不写 try/catch，避免让 GlobalExceptionHandler 介入 text/event-stream。
        aiChatService.streamChat(userId, sessionId, question, emitter);
        return emitter;
    }

    /** 最近 N 条历史（默认 20，上限 100）。 */
    @GetMapping("/history")
    public Result<ChatHistoryResponse> history(@RequestParam String sessionId,
                                               @RequestParam(defaultValue = "20") int limit) {
        Long userId = CurrentUserContext.getUserId();
        return Result.success(aiChatService.getRecentHistory(userId, sessionId, limit));
    }

    /** 清空当前 session 历史（软删）。 */
    @DeleteMapping("/history")
    public Result<Integer> clearHistory(@RequestParam String sessionId) {
        Long userId = CurrentUserContext.getUserId();
        int n = aiChatService.clearSession(userId, sessionId);
        return Result.success("已清空 " + n + " 条消息", n);
    }
}