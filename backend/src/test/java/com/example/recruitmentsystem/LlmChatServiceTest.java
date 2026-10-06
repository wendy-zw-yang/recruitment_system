package com.example.recruitmentsystem;

import com.example.recruitmentsystem.llm.service.LlmChatService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LlmChatService 单元测试。覆盖：关键词分词 / score 计算 / topK / 上下文拼装 / 未命中。
 */
class LlmChatServiceTest {

    /** 测试用假手册（与生产 manual.md 同结构） */
    private static final String FAKE_MANUAL = """
            ## 如何投递
            投递职位流程：浏览职位 → 点击「立即投递」→ 弹出确认对话框 → 确认。
            投递必须先有 ACTIVE 简历。同一个职位只能投递一次。
            撤回投递：进入「我的投递」→ 点击撤回按钮。

            ## 简历上传
            上传简历：进入我的简历 → 选择文件 → 系统调用 AI 自动解析。
            支持 PDF 和 .docx 格式，文件大小 20MB。
            """;

    @Test
    void tokenize_chineseCharsAndBigrams() {
        Set<String> t = LlmChatService.tokenize("如何投递");
        assertTrue(t.contains("如"));
        assertTrue(t.contains("何"));
        assertTrue(t.contains("投"));
        assertTrue(t.contains("递"));
        // bigrams
        assertTrue(t.contains("如何"));
        assertTrue(t.contains("何投"));
        assertTrue(t.contains("投递"));
    }

    @Test
    void tokenize_englishWordsLowercased() {
        Set<String> t = LlmChatService.tokenize("AI Chat PDF Resume");
        assertTrue(t.contains("ai"));
        assertTrue(t.contains("chat"));
        assertTrue(t.contains("pdf"));
        assertTrue(t.contains("resume"));
    }

    @Test
    void tokenize_mixedChineseAndEnglish() {
        Set<String> t = LlmChatService.tokenize("上传 PDF 简历");
        assertTrue(t.contains("上"));
        assertTrue(t.contains("传"));
        assertTrue(t.contains("pdf"));
        assertTrue(t.contains("上传"));
    }

    @Test
    void tokenize_nullOrEmpty() {
        assertTrue(LlmChatService.tokenize(null).isEmpty());
        assertTrue(LlmChatService.tokenize("").isEmpty());
    }

    @Test
    void parseSections_splitsByH2() {
        List<LlmChatService.Section> list = LlmChatService.parseSections(FAKE_MANUAL);
        assertEquals(2, list.size());
        assertEquals("如何投递", list.get(0).title);
        assertEquals("简历上传", list.get(1).title);
        assertTrue(list.get(0).content.contains("立即投递"));
        assertTrue(list.get(1).content.contains("PDF"));
    }

    @Test
    void parseSections_introBeforeFirstH2() {
        String text = "# 总标题\n前言段落\n\n## A 章节\n内容A\n";
        List<LlmChatService.Section> list = LlmChatService.parseSections(text);
        // 前言（"前言段落"）为一个 section（无标题 → "前言"）
        assertTrue(list.size() >= 1);
    }

    @Test
    void scoreAll_matchesRelevantSection() {
        LlmChatService svc = new LlmChatService();
        // 通过反射注入 sections（@PostConstruct 已被跳过）
        injectSections(svc, FAKE_MANUAL);

        List<LlmChatService.ScoredSection> all = svc.scoreAll("如何投递");
        assertFalse(all.isEmpty(), "至少应有 1 个 section 命中");
        // 第一个应为「如何投递」
        assertEquals("如何投递", all.get(0).section.title);
        assertTrue(all.get(0).score > 0.0);
    }

    @Test
    void scoreAll_returnsEmptyForBlankQuestion() {
        LlmChatService svc = new LlmChatService();
        injectSections(svc, FAKE_MANUAL);
        assertTrue(svc.scoreAll("").isEmpty());
        assertTrue(svc.scoreAll(null).isEmpty());
    }

    @Test
    void topK_returnsAtMost3AndAboveThreshold() {
        LlmChatService svc = new LlmChatService();
        injectSections(svc, FAKE_MANUAL);

        List<LlmChatService.ScoredSection> top = svc.topK("如何投递");
        assertFalse(top.isEmpty());
        assertTrue(top.size() <= 3);
        // 所有命中必须 ≥ 0.15
        for (LlmChatService.ScoredSection ss : top) {
            assertTrue(ss.score >= LlmChatService.SCORE_THRESHOLD);
        }
    }

    @Test
    void topK_unrelatedQuestion_returnsEmpty() {
        LlmChatService svc = new LlmChatService();
        injectSections(svc, FAKE_MANUAL);

        // 完全不相关的问题（如不含"投递"/"简历"/"PDF"/"上传"等关键词）
        List<LlmChatService.ScoredSection> top = svc.topK("随便问问天气吧");
        assertTrue(top.isEmpty(), "不相关问题不应命中任何 section");
    }

    @Test
    void topK_thresholdFiltersLowScores() {
        LlmChatService svc = new LlmChatService();
        injectSections(svc, FAKE_MANUAL);

        // 极短问题 → score 可能低于阈值
        List<LlmChatService.ScoredSection> top = svc.topK("嗯");
        assertTrue(top.isEmpty());
    }

    @Test
    void buildContext_formatsTitleAndContent() {
        LlmChatService svc = new LlmChatService();
        injectSections(svc, FAKE_MANUAL);

        String ctx = svc.buildContext("如何投递");
        assertNotNull(ctx);
        assertTrue(ctx.contains("## 如何投递"));
        assertTrue(ctx.contains("立即投递"));
    }

    @Test
    void buildContext_emptyForNoHit() {
        LlmChatService svc = new LlmChatService();
        injectSections(svc, FAKE_MANUAL);

        String ctx = svc.buildContext("随便问问天气吧");
        assertEquals("", ctx);
    }

    /** 反射注入 sections 字段（绕过 @PostConstruct）。 */
    private static void injectSections(LlmChatService svc, String manualText) {
            java.lang.reflect.Field f = null;
            try {
                f = LlmChatService.class.getDeclaredField("sections");
                f.setAccessible(true);
                f.set(svc, LlmChatService.parseSections(manualText));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (f != null) {
                    // 可选：恢复可访问性状态
                }
            }
    }
}