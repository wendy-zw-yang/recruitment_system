package com.example.recruitmentsystem.dto.resume;

import lombok.Data;

/**
 * 教育经历条目。
 *
 * <p>日期字段统一用 {@code "YYYY-MM"} 字符串而非 {@link java.time.LocalDate}，
 * 因为 LLM 返回的简历日期多为年-月精度，{@code LocalDate} 会导致 Jackson 反序列化失败
 * 进而把整条教育经历丢掉。展示时按字符串原样渲染即可。</p>
 */
@Data
public class EducationItem {

    private String school;

    private String major;

    private String degree;

    private String startDate;

    private String endDate;

    private String description;
}
