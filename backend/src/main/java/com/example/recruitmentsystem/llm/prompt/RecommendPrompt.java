package com.example.recruitmentsystem.llm.prompt;

/**
 * v0.7.3 AI 推荐打分 Prompt。
 *
 * <p>输入：候选人简历 JSON（来自 {@code resume.structured_json}）+ 偏好 4 维
 * + 10 条职位（id / title / cityName / province / keywords 摘录）。</p>
 *
 * <p>输出（JSON 数组）：每个元素包含 {@code jobId}（Long）与 {@code score}
 * （0-100 整数，越高越相关）。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>用 {@code json_object} response_format 强制 LLM 输出 JSON</li>
 *   <li>prompt 精简，避免 resume 字段嵌套过深</li>
 *   <li>限制职位描述字符数（仅 title + keywords + 城市），控制 token 消耗</li>
 * </ul>
 */
public final class RecommendPrompt {

    private RecommendPrompt() {
    }

    /**
     * 系统提示词：定义角色 + 输出 JSON Schema。
     */
    public static final String SYSTEM_PROMPT =
            "你是一名求职推荐助手。给定一名候选人的简历 JSON 与求职偏好，以及一组候选职位的简要信息，"
                    + "请按候选人与职位的契合度从高到低打分（0-100 整数）。\n"
                    + "\n"
                    + "评分要点（仅参考，不要机械执行）：\n"
                    + "1. 技能关键词重合度（如 Java / Spring Boot / Vue）\n"
                    + "2. 工作 / 项目经历与职位要求的契合度\n"
                    + "3. 行业 / 城市 / 期望职位关键词的契合度\n"
                    + "4. 学历 / 经验的吻合度（JD 提到则加分）\n"
                    + "\n"
                    + "输出 JSON 数组，每个元素含两个字段：\n"
                    + "  - jobId：Long 类型（必须严格匹配传入的 job.id）\n"
                    + "  - score：0-100 整数\n"
                    + "\n"
                    + "严格约束：\n"
                    + "- 数组长度必须等于传入的职位数量\n"
                    + "- jobId 必须原样出现，不得遗漏或新增\n"
                    + "- score 范围 0-100；不要写 null / 字符串 / 小数\n"
                    + "- 不要在数组外加任何其他字段或解释文字\n"
                    + "\n"
                    + "只返回 JSON 数组本体，不要 markdown code fence。";

    /**
     * 用户提示词：拼装实际业务数据（候选人简历 + 偏好 + 职位列表）。
     *
     * @param resumeJson 候选人简历结构化 JSON（{@code resume.structured_json}）；可为 null（无简历）
     * @param positionText 期望职位关键词（前端输入，可空）
     * @param industryName 期望行业名（可空；由 service JOIN dict_industry 取）
     * @param province 期望省份（可空）
     * @param cityName 期望城市名（可空）
     * @param jobsContent 候选职位列表（已格式化为 Markdown 列表，每条含 jobId/title/city/keywords）
     * @return 完整 user prompt
     */
    public static String userPrompt(String resumeJson,
                                   String positionText,
                                   String industryName,
                                   String province,
                                   String cityName,
                                   String jobsContent) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("# 候选人简历（JSON）\n");
        if (resumeJson == null || resumeJson.isBlank()) {
            sb.append("（无简历）\n\n");
        } else {
            sb.append(resumeJson).append("\n\n");
        }

        sb.append("# 求职偏好\n");
        sb.append("- 期望职位关键词：").append(nullToDash(positionText)).append('\n');
        sb.append("- 期望行业：").append(nullToDash(industryName)).append('\n');
        sb.append("- 期望省份：").append(nullToDash(province)).append('\n');
        sb.append("- 期望城市：").append(nullToDash(cityName)).append('\n');
        sb.append("\n");

        sb.append("# 候选职位列表（").append(countJobs(jobsContent)).append(" 条）\n");
        sb.append(jobsContent).append('\n');

        sb.append("\n请按契合度从高到低输出 JSON 数组（不要 markdown fence）：\n");
        sb.append("- 元素 1：{\"jobId\": <id>, \"score\": <0-100>}\n");
        sb.append("- 元素 2：...\n");
        sb.append("- 总数必须等于职位列表条目数");
        return sb.toString();
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "（无）" : s;
    }

    /** 简单计算职位列表条目数（用于提示词"候选职位列表（N 条）"展示） */
    private static int countJobs(String jobsContent) {
        if (jobsContent == null) return 0;
        int n = 0;
        int i = jobsContent.indexOf("- jobId=");
        while (i >= 0) {
            n++;
            i = jobsContent.indexOf("- jobId=", i + 1);
        }
        return n;
    }
}