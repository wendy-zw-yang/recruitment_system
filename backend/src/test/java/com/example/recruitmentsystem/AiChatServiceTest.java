package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.config.LlmConfig;
import com.example.recruitmentsystem.dto.chat.ChatHistoryResponse;
import com.example.recruitmentsystem.entity.AiChatMessage;
import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.llm.service.LlmChatService;
import com.example.recruitmentsystem.mapper.AiChatMessageMapper;
import com.example.recruitmentsystem.service.chat.AiChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test SseEmitter that captures all events for verification.
 *
 * <p>Why not mock() + doAnswer: SseEmitter.send has overloads (SseEventBuilder,
 * Object, MediaType) and Mockito cannot disambiguate matchers cleanly. A custom
 * subclass is simpler and more robust.</p>
 */
class CapturingSseEmitter extends SseEmitter {
    final List<Object> sentPayloads = new ArrayList<>();
    final List<Runnable> completions = new ArrayList<>();

    CapturingSseEmitter() {
        super(0L);
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
        // builder is DataWithMediaTypeSseEventBuilder (Spring impl)
        // call build() via reflection to get the actual events
        try {
            Method build = builder.getClass().getMethod("build");
            Object events = build.invoke(builder);
            if (events instanceof Set) {
                for (Object e : (Set<?>) events) {
                    Method getData = e.getClass().getMethod("getData");
                    Object data = getData.invoke(e);
                    sentPayloads.add(data);
                }
            } else {
                sentPayloads.add(builder);
            }
        } catch (Exception reflectErr) {
            sentPayloads.add(builder);
        }
    }

    @Override
    public void complete() {
        completions.add(() -> {});
    }
}

/**
 * AiChatService unit test.
 * Covers: write USER msg / LLM path / canned path / ADMIN reject / history fetch / clear.
 *
 * <p>SseEmitter mocking: Mockito spy + doAnswer to capture events.
 * Spring's SseEmitter.send(SseEventBuilder) eventually calls send(Object, MediaType),
 * so overriding that captures all events.</p>
 */
class AiChatServiceTest {

    private LlmClient llmClient;
    private LlmChatService llmChatService;
    private LlmConfig llmConfig;
    private AiChatMessageMapper chatMapper;
    private CapturingSseEmitter emitter;
    private List<Object> sentPayloads;
    private List<Runnable> completions;
    private AiChatService svc;

    @BeforeEach
    void setUp() throws Exception {
        llmClient = mock(LlmClient.class);
        llmChatService = mock(LlmChatService.class);
        llmConfig = new LlmConfig();
        llmConfig.setModel("test-model"); // getChatModel() fallback returns this
        llmConfig.setChatMaxTokens(600);
        chatMapper = mock(AiChatMessageMapper.class);

        emitter = new CapturingSseEmitter();
        sentPayloads = emitter.sentPayloads;
        completions = emitter.completions;

        CurrentUserContext.set(1L, "CANDIDATE");

        svc = new AiChatService(llmClient, llmChatService, llmConfig, chatMapper);

        // chatMapper.insert mock auto-increment id
        doAnswer(inv -> {
            AiChatMessage m = inv.getArgument(0);
            m.setId(System.nanoTime());
            return 1;
        }).when(chatMapper).insert(any(AiChatMessage.class));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void streamChat_adminRole_emitsErrorAndCompletes_noThrow() {
        CurrentUserContext.clear();
        CurrentUserContext.set(99L, "ADMIN");
        // v0.6.1: service never throws; uses SSE error event + complete instead
        svc.streamChat(99L, "sid-1", "how to apply", emitter);
        // Should emit error event + complete (no exception)
        assertTrue(sentPayloads.size() >= 1, "ADMIN reject should emit error event");
        assertFalse(completions.isEmpty(), "Should call emitter.complete()");
        // Should NOT call LLM
        verify(llmClient, never()).streamCall(anyString(), anyString(), anyString(),
                anyLong(), any(), anyString(), any());
    }

    @Test
    void streamChat_blankQuestion_emitsErrorAndCompletes_noThrow() {
        svc.streamChat(1L, "sid-1", "", emitter);
        assertTrue(sentPayloads.size() >= 1, "Blank question should emit error event");
        assertFalse(completions.isEmpty(), "Should call emitter.complete()");
        verify(llmClient, never()).streamCall(anyString(), anyString(), anyString(),
                anyLong(), any(), anyString(), any());
    }

    @Test
    void streamChat_questionTooLong_emitsErrorAndCompletes_noThrow() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2001; i++) sb.append('x');
        svc.streamChat(1L, "sid-1", sb.toString(), emitter);
        assertTrue(sentPayloads.size() >= 1, "Too-long question should emit error event");
        assertFalse(completions.isEmpty(), "Should call emitter.complete()");
        verify(llmClient, never()).streamCall(anyString(), anyString(), anyString(),
                anyLong(), any(), anyString(), any());
    }

    @Test
    void streamChat_noKeywordHit_emitsFixedCannedResponse() throws Exception {
        when(llmChatService.topK(anyString())).thenReturn(Collections.emptyList());

        svc.streamChat(1L, "sid-canned", "tell me about weather", emitter);

        // Should not call LLM
        verify(llmClient, never()).streamCall(anyString(), anyString(), anyString(),
                anyLong(), any(), anyString(), any());
        // Write 2 messages (USER + canned ASSISTANT)
        verify(chatMapper, times(2)).insert(any(AiChatMessage.class));
        // At least 1 delta + 1 done
        assertTrue(sentPayloads.size() >= 2, "Should send at least delta + done");
        assertFalse(completions.isEmpty(), "emitter.complete() should be called");
    }

    @Test
    void streamChat_keywordHit_callsLlmStreamAndEmitsDeltas() throws Exception {
        LlmChatService.Section s = new LlmChatService.Section("how-to-apply", "apply flow", java.util.Set.of("apply"));
        when(llmChatService.topK(anyString())).thenReturn(java.util.List.of(new LlmChatService.ScoredSection(s, 0.5)));
        when(llmChatService.buildContext(anyString())).thenReturn("## how-to-apply\napply flow");
        when(llmClient.streamCall(eq("AI-4"), anyString(), anyString(), anyLong(), any(), anyString(), any(Consumer.class)))
                .thenAnswer(inv -> {
                    Consumer<String> handler = inv.getArgument(6);
                    handler.accept("Hi");
                    handler.accept(", ");
                    handler.accept("I am HuiHui.");
                    return "Hi, I am HuiHui.";
                });

        svc.streamChat(1L, "sid-llm", "how to apply", emitter);

        // LLM called once
        verify(llmClient, times(1)).streamCall(eq("AI-4"), anyString(), anyString(),
                anyLong(), any(), anyString(), any());
        // Write 2 messages (USER + ASSISTANT)
        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(chatMapper, times(2)).insert(captor.capture());
        List<AiChatMessage> saved = captor.getAllValues();
        assertEquals("USER", saved.get(0).getRole());
        assertEquals("ASSISTANT", saved.get(1).getRole());
        assertEquals("Hi, I am HuiHui.", saved.get(1).getContent());
        // At least 3 delta + 1 done = >= 4 send calls
        assertTrue(sentPayloads.size() >= 4,
                "Should have >=3 delta + 1 done events (>=4 send() calls)");
    }

    @Test
    void streamChat_llmFailure_emitsErrorEventAndCompletes() throws Exception {
        when(llmChatService.topK(anyString())).thenReturn(java.util.List.of(
                new LlmChatService.ScoredSection(
                        new LlmChatService.Section("X", "Y", java.util.Set.of("x")), 0.5)));
        when(llmChatService.buildContext(anyString())).thenReturn("ctx");
        when(llmClient.streamCall(anyString(), anyString(), anyString(), anyLong(),
                any(), anyString(), any(Consumer.class)))
                .thenThrow(new BusinessException(500, "LLM config incomplete"));

        // Should not throw
        svc.streamChat(1L, "sid-fail", "test question", emitter);

        // At least 1 send call (error event)
        assertTrue(sentPayloads.size() >= 1);
        // emitter.complete() should be called (v0.6.1: no longer uses completeWithError())
        assertFalse(emitter.completions.isEmpty(), "emitter.complete() should be called");
    }

    @Test
    void streamChat_sessionIdBlank_autoGeneratesUuid() {
        when(llmChatService.topK(anyString())).thenReturn(Collections.emptyList());

        svc.streamChat(1L, "", "weather question", emitter);

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(chatMapper, atLeastOnce()).insert(captor.capture());
        String sid = captor.getValue().getSessionId();
        assertNotNull(sid);
        assertFalse(sid.isBlank(), "sessionId should auto-generate when blank");
        assertEquals(36, sid.length(), "Should be UUID");
    }

    @Test
    void getRecentHistory_ordersAscByTime() {
        AiChatMessage m1 = mkMsg(1L, 1L, "sid-x", "USER", "question 1");
        m1.setCreatedAt(java.time.LocalDateTime.of(2026, 10, 6, 10, 0, 0));
        AiChatMessage m2 = mkMsg(2L, 1L, "sid-x", "ASSISTANT", "answer 1");
        m2.setCreatedAt(java.time.LocalDateTime.of(2026, 10, 6, 10, 0, 5));
        AiChatMessage m3 = mkMsg(3L, 1L, "sid-x", "USER", "question 2");
        m3.setCreatedAt(java.time.LocalDateTime.of(2026, 10, 6, 10, 1, 0));
        when(chatMapper.selectRecentBySession(eq(1L), eq("sid-x"), anyInt()))
                .thenReturn(java.util.Arrays.asList(m3, m2, m1));

        ChatHistoryResponse resp = svc.getRecentHistory(1L, "sid-x", 20);
        assertEquals("sid-x", resp.getSessionId());
        assertEquals(3, resp.getMessages().size());
        // Service internal reverse => ASC: m1, m2, m3
        assertEquals("USER", resp.getMessages().get(0).getRole());
        assertEquals("question 1", resp.getMessages().get(0).getContent());
        assertEquals("USER", resp.getMessages().get(2).getRole());
        assertEquals("question 2", resp.getMessages().get(2).getContent());
    }

    @Test
    void getRecentHistory_capsLimit() {
        when(chatMapper.selectRecentBySession(anyLong(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        ChatHistoryResponse resp = svc.getRecentHistory(1L, "sid-x", 200);
        assertEquals(0, resp.getMessages().size());
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(chatMapper).selectRecentBySession(anyLong(), anyString(), captor.capture());
        assertEquals(100, captor.getValue());
    }

    @Test
    void getRecentHistory_blankSessionId_throws() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.getRecentHistory(1L, "", 20));
        assertEquals(400, e.getCode());
    }

    @Test
    void clearSession_delegatesToMapper() {
        when(chatMapper.softDeleteBySession(1L, "sid-x")).thenReturn(5);
        int n = svc.clearSession(1L, "sid-x");
        assertEquals(5, n);
    }

    /**
     * v0.6.1 key regression: saveMessage throws DB exception, service NEVER throws,
     * must emit SSE error event + complete (so frontend typing indicator clears).
     */
    @Test
    void streamChat_saveMessageFailure_emitsErrorAndCompletes_noThrow() {
        doAnswer(inv -> {
            throw new org.springframework.jdbc.BadSqlGrammarException(
                    "insert", "INSERT INTO ai_chat_message ...",
                    new java.sql.SQLSyntaxErrorException("Table doesn't exist"));
        }).when(chatMapper).insert(any(AiChatMessage.class));

        try {
            svc.streamChat(1L, "sid-dbfail", "how to apply", emitter);
        } catch (Throwable t) {
            throw new AssertionError("streamChat should never throw, but threw: " + t.getMessage(), t);
        }
        assertTrue(sentPayloads.size() >= 1, "DB fail should emit error event");
        assertFalse(completions.isEmpty(), "DB fail should call emitter.complete()");
        verify(llmClient, never()).streamCall(anyString(), anyString(), anyString(),
                anyLong(), any(), anyString(), any());
    }

    private static AiChatMessage mkMsg(Long id, Long userId, String sid, String role, String content) {
        AiChatMessage m = new AiChatMessage();
        m.setId(id);
        m.setUserId(userId);
        m.setSessionId(sid);
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}