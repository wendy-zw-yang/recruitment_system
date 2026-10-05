package com.example.recruitmentsystem.llm.prompt;

/**
 * AI-2 简历 ↔ JD 匹配评分 prompt 模板。
 *
 * <p>详见 {@code docs/系统设计/详细设计/AI集成.md §6.4.3}。</p>
 *
 * <p>输入：候选人结构化简历 JSON + 职位描述 / 要求。输出：0-100 分 + 中文理由。</p>
 *
 * <p>v0.1 设计要点：</p>
 * <ul>
 *   <li>score 必须是 0-100 整数（强制上下界）</li>
 *   <li>reason 200 字内，中文</li>
 *   <li>仅返回 JSON 对象，不要 Markdown / 解释文字 / 思考块</li>
 *   <li>不允许捏造事实；缺失信息按 0 分项评估</li>
 * </ul>
 */
public final class ScorePrompt {

    private ScorePrompt() {}

    public static final String SYSTEM_PROMPT = """
            你是一名严格的招聘匹配评分专家。基于候选人的结构化简历与职位 JD，给出 0-100 的匹配分与 200 字内的中文评分理由。

            评分维度（总和 100%）：
            1. 技能匹配度（40%）：JD 要求的技能 vs 候选人 skills / work.tags / project.techStack
            2. 经验相关性（30%）：JD 行业 / 职位类型 vs 候选人最近 1-2 段工作经历
            3. 学历 / 行业契合（20%）：JD 学历要求 vs 候选人最高学历；行业背景相似度
            4. 加分项（10%）：知名公司 / 开源贡献 / 高匹配项目经历

            严格要求：
            1. 必须仅返回一个 JSON 对象，不要包含 Markdown 代码块 / 解释文字 / 思考块
            2. 包含两个字段：score (整数 0-100) / reason (string)
            3. score 必须是 0-100 闭区间内的整数
            4. reason 必须用中文，2-3 句话：先说主要匹配点，再说主要短板
            5. 缺失信息视为 0 分项，不得编造事实

            JSON Schema:
            {
              "score": 0,
              "reason": "string"
            }
            """;

    public static String userPrompt(String resumeJson, String jobDescription, String jobRequirements, String jobTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("请评估以下候选人简历与职位的匹配度。\n\n");

        sb.append("=== 职位 ===\n");
        sb.append("标题：").append(nullToDash(jobTitle)).append('\n');
        sb.append("【岗位职责】\n").append(nullToDash(jobDescription)).append("\n\n");
        sb.append("【任职要求】\n").append(nullToDash(jobRequirements)).append("\n\n");

        sb.append("=== 候选人简历（结构化 JSON）===\n");
        sb.append(nullToDash(resumeJson));
        return sb.toString();
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "（空）" : s;
    }
}
