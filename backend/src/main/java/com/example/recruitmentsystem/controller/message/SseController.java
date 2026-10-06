package com.example.recruitmentsystem.controller.message;

import com.example.recruitmentsystem.common.annotation.LoginRequired;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.service.message.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * §5 消息中心 - SSE 长连接订阅（接收 new_message / read_receipt 等事件）。
 *
 * <p>复用 AI-4 v0.6.2 模式：超时 0L（永不过期）+ {@code emitter.onError/onTimeout/onCompletion}
 * 回调吸收 async 异常，避免 Spring ErrorPageFilter 警告。</p>
 */
@Slf4j
@LoginRequired
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterManager sseManager;

    /**
     * 订阅消息事件。前端用 {@code EventSource} 自动重连。
     *
     * <p>事件类型：</p>
     * <ul>
     *   <li>{@code new_message} — 新消息（含 conversationId + message 全字段）</li>
     *   <li>{@code read_receipt} — 对方已读（含 conversationId + readerId + messageIds）</li>
     * </ul>
     *
     * <p>心跳：服务端每 25 秒发 {@code ": ping"} 防 nginx 60 秒超时切断。</p>
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        Long userId = CurrentUserContext.getUserId();
        SseEmitter emitter = sseManager.register(userId);
        // 主动发一条 ready 事件（客户端可借此确认连接建立）
        try {
            emitter.send(SseEmitter.event().name("ready").data("{\"userId\":" + userId + "}"));
        } catch (Exception ignore) {
            // emitter 可能已被关闭；register 内已注册 onError 回调清理
        }
        return emitter;
    }
}
