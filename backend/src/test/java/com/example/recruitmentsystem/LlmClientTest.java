package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.config.LlmConfig;
import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.mapper.AiCallLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * LlmClient 单元测试。
 *
 * <p>用 WireMock-free 方案：构造一个 LlmConfig，验证未配置 Key 时抛错（典型失败模式）。
 * HTTP 调用层走真实调用（指向 localhost:0 即拒绝连接），验证网络失败路径写 TIMEOUT 日志。</p>
 */
@SpringBootTest
class LlmClientTest {

    @Autowired private LlmConfig llmConfig;
    @Autowired private AiCallLogMapper aiCallLogMapper;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void rejectsMissingApiKey() {
        LlmConfig bad = new LlmConfig();
        bad.setApiUrl("http://localhost:9999/chat/completions");
        bad.setApiKey("");
        bad.setModel("MiniMax-M3");
        LlmClient client = new LlmClient(bad, aiCallLogMapper, objectMapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.callJson("AI-1", "sys", "user", 1L));
        assertEquals(400, ex.getCode());
        assertEquals("大模型配置不完整", ex.getMessage());
    }

    @Test
    void rejectsMissingModel() {
        LlmConfig bad = new LlmConfig();
        bad.setApiUrl("http://localhost:9999/chat/completions");
        bad.setApiKey("sk-test");
        bad.setModel("");
        LlmClient client = new LlmClient(bad, aiCallLogMapper, objectMapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.callJson("AI-1", "sys", "user", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void networkFailure_recordsTimeoutLog() {
        LlmConfig cfg = new LlmConfig();
        cfg.setApiUrl("http://127.0.0.1:1/chat/completions");
        cfg.setApiKey("sk-test");
        cfg.setModel("MiniMax-M3");
        cfg.setTimeoutSeconds(2);
        cfg.setCacheTtlSeconds(0);
        LlmClient client = new LlmClient(cfg, aiCallLogMapper, objectMapper);

        long before = aiCallLogMapper.selectCount(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.callJson("AI-1", "sys", "user", null));
        long after = aiCallLogMapper.selectCount(null);

        assertEquals(500, ex.getCode());
        assertEquals(after, before + 1, "应写入一条 ai_call_log");
    }
}
