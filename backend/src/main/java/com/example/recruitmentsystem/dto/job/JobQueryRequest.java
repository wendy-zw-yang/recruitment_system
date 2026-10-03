package com.example.recruitmentsystem.dto.job;

import lombok.Data;

/**
 * 候选人端职位列表查询条件。
 */
@Data
public class JobQueryRequest {

    private String keyword;

    private Long industryId;

    private Long cityId;

    /** newest / salary */
    private String sort;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
