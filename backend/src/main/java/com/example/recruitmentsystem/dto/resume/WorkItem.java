package com.example.recruitmentsystem.dto.resume;

import lombok.Data;

import java.util.List;

/**
 * 工作经历条目。
 *
 * <p>日期字段统一用 {@code "YYYY-MM"} 字符串（见 {@link EducationItem} 说明）。</p>
 */
@Data
public class WorkItem {

    private String company;

    private String position;

    private String startDate;

    private String endDate;

    private String description;

    private List<String> tags;
}
