package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.llm.LlmClient;
import com.example.recruitmentsystem.llm.service.LlmScoreService;
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
 * LlmScoreService 单元测试。覆盖：正常解析 / 字段缺失 / 分数越界 / 返回非对象。
 */
class LlmScoreServiceTest {

    @Test
    void scoreSync_returnsValidJson() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        when(mock.callJson(eq("AI-2"), anyString(), anyString(), anyLong()))
                .thenReturn(om.readTree("""
                        { "score": 82, "reason": "技能匹配度高，3 年 Java 经验契合要求；短板：缺少高并发项目经验" }
                        """));

        LlmScoreService svc = new LlmScoreService(mock, om);
        LlmScoreService.ScoreResult r = svc.scoreSync(
                "{\"skills\":[\"Java\",\"Spring\"]}",
                "Java 工程师", "后端开发", "3 年 Java",
                1L);

        assertEquals(82, r.score);
        assertNotNull(r.reason);
        assertTrue(r.reason.contains("技能"));
    }

    @Test
    void scoreSync_throwsWhenScoreMissing() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        when(mock.callJson(eq("AI-2"), anyString(), anyString(), anyLong()))
                .thenReturn(om.readTree("""
                        { "reason": "no score field" }
                        """));

        LlmScoreService svc = new LlmScoreService(mock, om);
        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.scoreSync("{}", "t", "d", "r", 1L));
        assertEquals(500, e.getCode());
        assertTrue(e.getMessage().contains("score"));
    }

    @Test
    void scoreSync_throwsWhenScoreOutOfRange() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        when(mock.callJson(eq("AI-2"), anyString(), anyString(), anyLong()))
                .thenReturn(om.readTree("""
                        { "score": 150, "reason": "x" }
                        """));

        LlmScoreService svc = new LlmScoreService(mock, om);
        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.scoreSync("{}", "t", "d", "r", 1L));
        assertEquals(500, e.getCode());
        assertTrue(e.getMessage().contains("越界"));
    }

    @Test
    void scoreSync_throwsWhenResponseNotObject() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        when(mock.callJson(eq("AI-2"), anyString(), anyString(), anyLong()))
                .thenReturn(om.readTree("\"a string, not object\""));

        LlmScoreService svc = new LlmScoreService(mock, om);
        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.scoreSync("{}", "t", "d", "r", 1L));
        assertEquals(500, e.getCode());
        assertTrue(e.getMessage().contains("非对象"));
    }

    @Test
    void scoreSync_throwsWhenResumeJsonEmpty() throws Exception {
        LlmClient mock = Mockito.mock(LlmClient.class);
        ObjectMapper om = new ObjectMapper();
        // 空简历也应能调用成功（AI 自行评估），但 prompt 中会写「（空）」
        // 此用例验证：空字符串不导致 NPE / IllegalArgument
        when(mock.callJson(eq("AI-2"), anyString(), anyString(), anyLong()))
                .thenReturn(om.readTree("""
                        { "score": 30, "reason": "简历信息过少，匹配度有限" }
                        """));

        LlmScoreService svc = new LlmScoreService(mock, om);
        LlmScoreService.ScoreResult r = svc.scoreSync("", "t", "d", "r", 1L);
        assertEquals(30, r.score);
        assertNotNull(r.reason);
    }
}
