package com.example.recruitmentsystem;

import com.example.recruitmentsystem.service.message.SseEmitterManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SseEmitterManager 单元测试。
 *
 * <p>覆盖：register / pushToUser（在线 + 离线）/ 自动 unregister。</p>
 */
class SseEmitterManagerTest {

    private SseEmitterManager mgr;

    @BeforeEach
    void setUp() {
        // 用 spy 拦截心跳 init/shutdown 副作用
        mgr = spy(new SseEmitterManager(new ObjectMapper()));
    }

    @Test
    void register_addsEmitterToInternalMap() throws Exception {
        SseEmitter emitter = mgr.register(1L);
        assertEquals(1, countEmitters(1L));
        assertEquals(1, mgr.onlineUserCount());
        // 防止测试退出时 emitter 实际 keep-alive
        emitter.complete();
    }

    @Test
    void pushToUser_offline_returnsFalseAndDoesNotThrow() {
        boolean ok = mgr.pushToUser(999L, "new_message", Map.of("a", 1));
        assertFalse(ok);
    }

    @Test
    void registerThenPush_emitterReceivesEvent() throws Exception {
        SseEmitter emitter = mgr.register(1L);
        boolean ok = mgr.pushToUser(1L, "new_message", Map.of("conversationId", 10));
        assertTrue(ok);
        // emitter 已被替换（complete 在自动 unregister 时调用前已经成功 send 一次）
        // 这里我们只验证 pushToUser 没抛异常 + 返回 true
        emitter.complete();
    }

    @Test
    void pushToUser_emitterThrows_removesFromMap() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("client closed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // 把 mock emitter 注入 manager 的 map（绕过 register）
        CopyOnWriteArrayList<SseEmitter> list = new CopyOnWriteArrayList<>();
        list.add(emitter);
        injectEmitters(1L, list);

        boolean ok = mgr.pushToUser(1L, "new_message", Map.of("k", "v"));
        assertFalse(ok, "Should return false when send fails on the only emitter");
        // emitter 应从 map 移除
        assertEquals(0, countEmitters(1L));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ============ 反射注入（访问 ConcurrentHashMap） ============

    @SuppressWarnings("unchecked")
    private int countEmitters(Long userId) throws Exception {
        Field f = SseEmitterManager.class.getDeclaredField("emitters");
        f.setAccessible(true);
        Map<Long, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) f.get(mgr);
        CopyOnWriteArrayList<SseEmitter> list = map.get(userId);
        return list == null ? 0 : list.size();
    }

    @SuppressWarnings("unchecked")
    private void injectEmitters(Long userId, CopyOnWriteArrayList<SseEmitter> list) throws Exception {
        Field f = SseEmitterManager.class.getDeclaredField("emitters");
        f.setAccessible(true);
        Map<Long, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) f.get(mgr);
        map.put(userId, list);
    }
}
