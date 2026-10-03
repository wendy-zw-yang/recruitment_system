package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.llm.service.LlmJdService;
import com.example.recruitmentsystem.llm.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * LlmJdService 单元测试。覆盖：解析成功 / 字段缺失容错 / mock LLM 抛错。
 */
class LlmJdServiceTest {

    @Test
    void polish_returnsAllFieldsOnSuccess() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        when(mock.callJson(eq("AI-3"), anyString(), anyString(), anyLong()))
                .thenReturn(om.readTree("""
                        {
                          "description": "负责后端开发、参与架构设计、review 代码",
                          "requirements": "3 年 Java 经验、熟悉 Spring Boot 和 MySQL、有高并发经验",
                          "keywords": "Java, Spring Boot, MySQL, Redis, Kafka"
                        }
                        """));

        LlmJdService svc = new LlmJdService(mock, om);
        LlmJdService.PolishedJd r = svc.polish(
                "Java 工程师", "互联网/IT", "深圳", "25-40K",
                "负责后端开发", "3 年经验", "Java, Spring Boot",
                1L);

        assertNotNull(r.description);
        assertNotNull(r.requirements);
        assertNotNull(r.keywords);
        assertTrue(r.description.contains("后端"));
        assertTrue(r.requirements.contains("Java"));
        assertTrue(r.keywords.split(",").length >= 3);
    }

    @Test
    void polish_toleratesEmptyFields() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        when(mock.callJson(eq("AI-3"), anyString(), anyString(), anyLong()))
                .thenReturn(om.readTree("""
                        { "description": "", "requirements": "", "keywords": "" }
                        """));

        LlmJdService svc = new LlmJdService(mock, om);
        LlmJdService.PolishedJd r = svc.polish(
                "Java", "互联网/IT", "北京", "20-30K",
                "", "", "",
                1L);

        assertEquals("", r.description);
        assertEquals("", r.requirements);
        assertEquals("", r.keywords);
    }

    @Test
    void polish_returnsEmptyWhenResponseNotObject() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        when(mock.callJson(eq("AI-3"), anyString(), anyString(), anyLong()))
                .thenReturn(om.readTree("\"not an object\""));

        LlmJdService svc = new LlmJdService(mock, om);
        LlmJdService.PolishedJd r = svc.polish(
                "Java", null, null, null,
                "desc", "req", "kw",
                1L);

        assertEquals("", r.description);
        assertEquals("", r.requirements);
        assertEquals("", r.keywords);
    }

    @Test
    void polish_propagatesBusinessException() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        when(mock.callJson(eq("AI-3"), anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException(500, "mock AI fail"));

        LlmJdService svc = new LlmJdService(mock, om);
        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.polish("Java", "IT", "北京", null, "d", "r", "k", 1L));
        assertEquals(500, e.getCode());
        assertTrue(e.getMessage().contains("mock AI fail"));
    }
}
