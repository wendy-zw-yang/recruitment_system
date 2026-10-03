package com.example.recruitmentsystem.service;

/**
 * 简历附件文本抽取。Python 子进程优先（pdfplumber + python-docx），失败回退 Java。
 *
 * <p>详见 {@code docs/技术约束.md §11} / {@code docs/系统设计/详细设计/简历.md §2.3.5}。</p>
 */
public interface ResumeTextExtractService {

    /**
     * 从 PDF / .docx 抽取纯文本。
     *
     * @param absolutePath 文件绝对路径
     * @return 抽取出的纯文本（去除多余空白）
     */
    String extractText(String absolutePath);
}
