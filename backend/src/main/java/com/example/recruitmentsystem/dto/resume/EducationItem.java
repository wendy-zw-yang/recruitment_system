package com.example.recruitmentsystem.dto.resume;

import lombok.Data;

import java.time.LocalDate;

/**
 * 教育经历条目。
 */
@Data
public class EducationItem {

    private String school;

    private String major;

    private String degree;

    private LocalDate startDate;

    private LocalDate endDate;

    private String description;
}
