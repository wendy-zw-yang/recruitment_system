package com.example.recruitmentsystem.llm.prompt;

/**
 * AI-1 简历解析 prompt 模板。Java text block 形式定义，运行时由 {@code LlmResumeParseService} 拼装。
 *
 * <p>详见 {@code docs/系统设计/详细设计/AI集成.md §6.4.2}。</p>
 */
public final class ResumeParsePrompt {

    private ResumeParsePrompt() {}

    public static final String SYSTEM_PROMPT = """
            你是一名专业的简历解析助手。请将用户给出的简历纯文本解析为严格的 JSON 对象。

            要求：
            1. 仅返回 JSON 对象，不要包含 Markdown 代码块或解释文字。
            2. 字段缺失时返回 null，不要凭空捏造。
            3. education / work / projects 必须是数组，元素为对象。
            4. skills 必须是字符串数组（即使原文是连续文本也要拆分为数组）。

            JSON Schema:
            {
              "name": string|null,
              "phone": string|null,
              "email": string|null,
              "education": [
                {
                  "school": string,
                  "major": string|null,
                  "degree": string|null,
                  "startDate": string|null,
                  "endDate": string|null,
                  "description": string|null
                }
              ],
              "work": [
                {
                  "company": string,
                  "position": string|null,
                  "startDate": string|null,
                  "endDate": string|null,
                  "description": string|null,
                  "tags": string[]
                }
              ],
              "projects": [
                {
                  "name": string,
                  "role": string|null,
                  "startDate": string|null,
                  "endDate": string|null,
                  "description": string|null,
                  "techStack": string[]
                }
              ],
              "skills": string[],
              "selfIntro": string|null
            }
            """;

    public static String userPrompt(String resumeText) {
        return "请解析以下简历文本：\n\n" + resumeText;
    }
}
