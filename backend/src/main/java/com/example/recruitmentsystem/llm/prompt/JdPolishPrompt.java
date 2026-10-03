package com.example.recruitmentsystem.llm.prompt;

/**
 * AI-3 JD 润色 prompt 模板（润色模式：基于 HR 已有内容优化，而非从零生成）。
 *
 * <p>详见 {@code docs/系统设计/详细设计/AI集成.md §6.4.4}。</p>
 */
public final class JdPolishPrompt {

    private JdPolishPrompt() {}

    public static final String SYSTEM_PROMPT = """
            你是一名专业的招聘内容编辑，擅长润色 HR 已有的招聘描述。

            工作方式：
            1. 以 HR 提供的现有 description / requirements / keywords 为基础进行润色、补全、规范化，不要凭空捏造内容。
            2. 若某字段为空，可结合 title / industry / city / salary 上下文生成合理内容，但保持简洁专业。
            3. description（岗位职责）不少于 3 条；requirements（任职要求）不少于 3 条。
            4. keywords 控制在 10 个以内，逗号分隔，例如：Java, Spring Boot, MySQL, Redis。
            5. 若 LLM 调用方对某字段完全无内容且上下文也无法推断，可返回空字符串。

            仅返回 JSON 对象，不要包含 Markdown 代码块或解释文字。

            JSON Schema:
            {
              "description": string,
              "requirements": string,
              "keywords": string
            }
            """;

    public static String userPrompt(String title,
                                   String industryName,
                                   String cityName,
                                   String salaryRange,
                                   String description,
                                   String requirements,
                                   String keywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("请润色以下招聘内容。\n\n");
        sb.append("=== 元数据（上下文，不修改）===\n");
        sb.append("职位标题：").append(nullToDash(title)).append('\n');
        if (notBlank(industryName)) sb.append("行业：").append(industryName).append('\n');
        if (notBlank(cityName)) sb.append("城市：").append(cityName).append('\n');
        if (notBlank(salaryRange)) sb.append("薪资：").append(salaryRange).append('\n');

        sb.append("\n=== 待润色内容 ===\n");
        sb.append("【岗位职责】\n").append(nullToDash(description)).append('\n');
        sb.append("【任职要求】\n").append(nullToDash(requirements)).append('\n');
        sb.append("【关键词 / 补充说明】\n").append(nullToDash(keywords)).append('\n');
        return sb.toString();
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "（空）" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
