package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LlmClient.extractJson 单元测试。覆盖 5 种 LLM 返回形态。
 *
 * <p>不依赖 Spring 上下文，纯静态方法测试。</p>
 */
class LlmClientJsonExtractTest {

    @Test
    void extractJson_parsesPureJson() {
        String text = "{\"description\":\"做后端\",\"requirements\":\"会 Java\",\"keywords\":\"Java\"}";
        JsonNode n = LlmClient.extractJson(text, "AI-3");
        assertNotNull(n);
        assertEquals("做后端", n.get("description").asText());
        assertEquals("Java", n.get("keywords").asText());
    }

    @Test
    void extractJson_stripsThinkBlock() {
        String text = "<think>The user wants me to polish... Let me analyze metadata...</think>\n" +
                "{\"description\":\"负责后端开发\",\"requirements\":\"3 年 Java 经验\",\"keywords\":\"Java,Spring\"}";
        JsonNode n = LlmClient.extractJson(text, "AI-3");
        assertNotNull(n);
        assertEquals("负责后端开发", n.get("description").asText());
        assertEquals("Java,Spring", n.get("keywords").asText());
    }

    @Test
    void extractJson_stripsMarkdownFence() {
        String text = "```json\n{\"description\":\"D\",\"requirements\":\"R\",\"keywords\":\"K\"}\n```";
        JsonNode n = LlmClient.extractJson(text, "AI-3");
        assertNotNull(n);
        assertEquals("D", n.get("description").asText());
    }

    @Test
    void extractJson_handlesThinkBlockAndMarkdownFence() {
        String text = "<think>reasoning</think>\n```json\n" +
                "{\"description\":\"D\",\"requirements\":\"R\",\"keywords\":\"K\"}\n```";
        JsonNode n = LlmClient.extractJson(text, "AI-3");
        assertNotNull(n);
        assertEquals("K", n.get("keywords").asText());
    }

    @Test
    void extractJson_handlesLeadingAndTrailingText() {
        String text = "好的，我来帮您润色。以下是润色后的内容：\n" +
                "{\"description\":\"D\",\"requirements\":\"R\",\"keywords\":\"K\"}\n" +
                "希望对您有帮助！";
        JsonNode n = LlmClient.extractJson(text, "AI-3");
        assertNotNull(n);
        assertEquals("D", n.get("description").asText());
    }

    @Test
    void extractJson_handlesArrayRoot() {
        String text = "[{\"x\":1},{\"x\":2}]";
        JsonNode n = LlmClient.extractJson(text, "AI-1");
        assertNotNull(n);
        assertTrue(n.isArray());
        assertEquals(2, n.size());
    }

    @Test
    void extractJson_throwsOnEmpty() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> LlmClient.extractJson("", "AI-3"));
        assertEquals(500, e.getCode());
        assertTrue(e.getMessage().contains("为空"));
    }

    @Test
    void extractJson_throwsOnNull() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> LlmClient.extractJson(null, "AI-3"));
        assertEquals(500, e.getCode());
    }

    @Test
    void extractJson_throwsOnThinkBlockOnly() {
        String text = "<think>just thinking, no answer</think>";
        BusinessException e = assertThrows(BusinessException.class,
                () -> LlmClient.extractJson(text, "AI-3"));
        assertEquals(500, e.getCode());
        assertTrue(e.getMessage().contains("思考块"));
    }

    @Test
    void extractJson_throwsOnGarbage() {
        String text = "This is plain English with no JSON anywhere.";
        BusinessException e = assertThrows(BusinessException.class,
                () -> LlmClient.extractJson(text, "AI-3"));
        assertEquals(500, e.getCode());
        assertTrue(e.getMessage().contains("解析失败") || e.getMessage().contains("原始"));
    }

    @Test
    void extractJson_includesOriginalPreviewInError() {
        String text = "no json at all, just some random text content here";
        BusinessException e = assertThrows(BusinessException.class,
                () -> LlmClient.extractJson(text, "AI-3"));
        assertTrue(e.getMessage().contains("no json at all"), "错误信息应包含原始响应内容");
    }
}
