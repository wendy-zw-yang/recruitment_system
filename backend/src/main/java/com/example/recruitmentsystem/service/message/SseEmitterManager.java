package com.example.recruitmentsystem.service.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 站内消息 SSE 长连接管理器（§5 消息中心）。
 *
 * <p>单例 Bean；负责：</p>
 * <ul>
 *   <li>注册：用户登录后建立 SSE 连接（{@code /api/sse/subscribe}），用 {@code userId → emitter} 维护</li>
 *   <li>推送：业务层调用 {@link #pushToUser} 把事件投递给在线用户的 emitter</li>
 *   <li>心跳：每 25 秒发 {@code ": ping"} 防 nginx 60 秒超时切断</li>
 *   <li>清理：用户断连 / emitter 完成时自动从 map 移除</li>
 * </ul>
 *
 * <p>复用 AI-4 v0.6.2 模式（{@code emitter.onError / onTimeout / onCompletion}）吸收 async 异常，
 * 避免 Spring ErrorPageFilter 输出 "Cannot render error page" 警告。</p>
 *
 * <p>不处理序列化：事件 payload 由调用方序列化（{@code ObjectMapper} 注入）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager {

    /** SSE 超时：0 = 永不过期（依赖客户端断连自然结束） */
    private static final long SSE_TIMEOUT_MS = 0L;

    /** 心跳间隔：25 秒（nginx 默认 60 秒超时） */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 25L;

    private final ObjectMapper objectMapper;

    /** userId → 该用户所有活跃 emitter（一般 1 个；多 tab 会有多个） */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private ScheduledExecutorService heartbeatExecutor;

    @PostConstruct
    void init() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("[SseEmitterManager] 已启动心跳（每 {}s）", HEARTBEAT_INTERVAL_SECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        emitters.values().forEach(list -> list.forEach(e -> {
            try { e.complete(); } catch (Exception ignore) { /* ignore */ }
        }));
        emitters.clear();
    }

    /**
     * 注册一个新的 emitter 给指定用户。
     *
     * <p>调用方应在 {@code SseEmitter} 上注册 {@code onError / onTimeout / onCompletion} 回调，
     * 但无需手动从 map 移除——{@link #autoUnregister} 已在回调里处理。</p>
     */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list =
                emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        autoUnregister(userId, emitter);
        log.info("[SseEmitterManager] register userId={} active={}", userId, list.size());
        return emitter;
    }

    /**
     * 推一个事件给指定用户。事件格式：{@code event: <eventName>\ndata: <json>\n\n}。
     *
     * @param userId    目标用户
     * @param eventName 事件名（前端 EventSource 监听）
     * @param payload   数据 payload（任意 POJO；序列化为 JSON）
     * @return true = 已成功写至少一个 emitter；false = 用户离线 / emitter 全失效
     */
    public boolean pushToUser(Long userId, String eventName, Object payload) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            log.debug("[SseEmitterManager] push skipped (offline) userId={} event={}", userId, eventName);
            return false;
        }
        String data;
        try {
            data = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception jsonErr) {
            log.warn("[SseEmitterManager] payload serialize failed event={}: {}", eventName, jsonErr.getMessage());
            return false;
        }
        int sent = 0;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                sent++;
            } catch (IOException | IllegalStateException e) {
                // emitter 已关闭 / 客户端断连；从 map 移除
                log.debug("[SseEmitterManager] push failed (closing) userId={}: {}", userId, e.getMessage());
                removeEmitter(userId, emitter);
            }
        }
        return sent > 0;
    }

    /** 当前在线用户数（用于监控） */
    public int onlineUserCount() {
        return emitters.size();
    }

    // ============ 内部 ============

    private void autoUnregister(Long userId, SseEmitter emitter) {
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            log.debug("[SseEmitterManager] timeout userId={}", userId);
            removeEmitter(userId, emitter);
        });
        emitter.onError((ex) -> {
            log.debug("[SseEmitterManager] error userId={}: {}", userId, ex.getMessage());
            removeEmitter(userId, emitter);
        });
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }

    private void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            Long userId = entry.getKey();
            List<SseEmitter> list = entry.getValue();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    log.debug("[SseEmitterManager] heartbeat failed userId={}: {}", userId, e.getMessage());
                    removeEmitter(userId, emitter);
                }
            }
        }
    }
}
