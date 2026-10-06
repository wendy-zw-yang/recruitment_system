package com.example.recruitmentsystem.llm.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-4 关键词检索 + 上下文拼装。
 *
 * <p>v0.6 设计要点：</p>
 * <ul>
 *   <li>启动时一次性读取 {@code resources/ai/manual.md}，按 {@code ##} 切分为 ~26 个 section</li>
 *   <li>每个 section 预 tokenize（char + bigram + English word）</li>
 *   <li>查询时对每个 section 计算 Jaccard-like 相似度；top-k 由调用方决定（默认 3）</li>
 *   <li>阈值过滤：score &lt; 0.15 的 section 视为未命中</li>
 *   <li>全量检索不区分角色（求职 / HR FAQ 共享）</li>
 * </ul>
 *
 * <p>性能：每个 section 已预 tokenize 为 Set，查询时 O(N) 次 set 交集。N ≤ 30 完全够用。</p>
 */
@Slf4j
@Service
public class LlmChatService {

    /** 检索 top-k（设计文档默认 3，详见 AI集成.md §6.4.5） */
    public static final int DEFAULT_TOP_K = 3;

    /** 阈值：score 低于此值则视为未命中（设计文档默认 0.15） */
    public static final double SCORE_THRESHOLD = 0.15;

    /** 英文 / 数字词提取（兼容 ASII 字符） */
    private static final Pattern EN_WORD = Pattern.compile("[A-Za-z][A-Za-z0-9_]+");

    /** 单个 ## 章节 */
    public static class Section {
        public final String title;
        public final String content;
        public final Set<String> tokens;

        public Section(String title, String content, Set<String> tokens) {
            this.title = title;
            this.content = content;
            this.tokens = tokens;
        }
    }

    /** 评分结果 */
    public static class ScoredSection {
        public final Section section;
        public final double score;

        public ScoredSection(Section section, double score) {
            this.section = section;
            this.score = score;
        }
    }

    private List<Section> sections = Collections.emptyList();

    @PostConstruct
    public void init() {
        try {
            this.sections = loadSections("ai/manual.md");
            log.info("[LlmChatService] 使用手册已加载，共 {} 个 section", sections.size());
        } catch (Exception e) {
            log.error("[LlmChatService] 使用手册加载失败，AI-4 将无法引用手册内容", e);
            this.sections = Collections.emptyList();
        }
    }

    /**
     * 计算每个 section 与 question 的相似度，按 score DESC 排序后返回前 {@link #DEFAULT_TOP_K} 个。
     *
     * <p>score 计算公式：|section.tokens ∩ q.tokens| / max(|q.tokens|, 1)（归一化到 [0, 1]）</p>
     *
     * @param question 用户问题（原始字符串，可含中英混合）
     * @return 评分降序的 section 列表（含 0 分项，方便上层过滤）
     */
    public List<ScoredSection> scoreAll(String question) {
        if (question == null || question.isBlank()) return Collections.emptyList();
        Set<String> qTokens = tokenize(question);
        if (qTokens.isEmpty()) return Collections.emptyList();

        int qSize = qTokens.size();
        List<ScoredSection> out = new ArrayList<>(sections.size());
        for (Section s : sections) {
            int overlap = countOverlap(s.tokens, qTokens);
            double score = (double) overlap / (double) qSize;
            if (overlap > 0) {
                out.add(new ScoredSection(s, score));
            }
        }
        out.sort((a, b) -> Double.compare(b.score, a.score));
        return out;
    }

    /**
     * 取 top-k（默认 3）命中；score &lt; {@link #SCORE_THRESHOLD} 的不返回。
     *
     * @return top-k section 列表（可能为空）
     */
    public List<ScoredSection> topK(String question, int k) {
        List<ScoredSection> all = scoreAll(question);
        List<ScoredSection> out = new ArrayList<>(k);
        for (ScoredSection ss : all) {
            if (ss.score < SCORE_THRESHOLD) break;
            out.add(ss);
            if (out.size() >= k) break;
        }
        return out;
    }

    public List<ScoredSection> topK(String question) {
        return topK(question, DEFAULT_TOP_K);
    }

    /**
     * 把 top-k section 拼成参考上下文（注入 prompt）。
     */
    public String buildContext(String question) {
        List<ScoredSection> top = topK(question);
        if (top.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            ScoredSection ss = top.get(i);
            if (i > 0) sb.append("\n\n---\n\n");
            sb.append("## ").append(ss.section.title).append('\n');
            sb.append(ss.section.content);
        }
        return sb.toString();
    }

    /**
     * 把整篇手册文本按 {@code ##} 切分为 section。第一个 section 可能没有标题（intro），
     * 视为 "前言" 章节。
     */
    static List<Section> loadSections(String classpathPath) throws IOException {
        ClassPathResource res = new ClassPathResource(classpathPath);
        if (!res.exists()) {
            throw new IOException("Classpath 资源不存在: " + classpathPath);
        }
        String text = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return parseSections(text);
    }

    public static List<Section> parseSections(String text) {
        List<Section> list = new ArrayList<>();
        String[] lines = text.split("\\r?\\n", -1);
        StringBuilder current = new StringBuilder();
        String currentTitle = null;
        for (String line : lines) {
            if (line.startsWith("## ") && !line.startsWith("### ")) {
                if (currentTitle != null || current.length() > 0) {
                    list.add(makeSection(currentTitle, current.toString()));
                }
                currentTitle = line.substring(3).trim();
                current.setLength(0);
            } else {
                if (currentTitle != null || !line.isBlank()) {
                    current.append(line).append('\n');
                }
            }
        }
        if (currentTitle != null || current.length() > 0) {
            list.add(makeSection(currentTitle, current.toString()));
        }
        return list;
    }

    private static Section makeSection(String title, String content) {
        String t = title == null ? "前言" : title;
        return new Section(t, content.trim(), tokenize(content));
    }

    /**
     * 分词：英文 / 数字按 \b[A-Za-z][A-Za-z0-9_]+ ；中文按字符；
     * 中文连续 bigram 也加入（提升 "如何投递" 这种短语匹配）。
     */
    public static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) return tokens;
        Matcher m = EN_WORD.matcher(text);
        while (m.find()) {
            tokens.add(m.group().toLowerCase(Locale.ROOT));
        }
        StringBuilder cn = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            // CJK Unified Ideographs 基本区 + 扩展 A
            if (cp >= 0x4E00 && cp <= 0x9FA5) {
                tokens.add(String.valueOf((char) cp));
                cn.append((char) cp);
            }
        }
        String cs = cn.toString();
        for (int i = 0; i < cs.length() - 1; i++) {
            tokens.add(cs.substring(i, i + 2));
        }
        return tokens;
    }

    private static int countOverlap(Set<String> a, Set<String> b) {
        if (a.size() > b.size()) {
            // 让小的迭代
            Set<String> tmp = a; a = b; b = tmp;
        }
        int cnt = 0;
        for (String t : a) {
            if (b.contains(t)) cnt++;
        }
        return cnt;
    }
}