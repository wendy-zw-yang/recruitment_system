package com.example.recruitmentsystem.dto.application;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * HR 端投递列表查询条件。
 *
 * <p>v0.5：jobId 改为可选。不传时查询该 HR 全部在线职位下的投递（用于 HR 首页跨职位聚合）。</p>
 */
@Data
public class HrApplicationListQuery {

    /** 职位 ID（可选；null = 该 HR 全部职位） */
    private Long jobId;

    /** 状态过滤（可选；不传或传 "ACTIVE" = 排除 WITHDRAWN；传 "WITHDRAWN" = 仅看撤回） */
    @Size(max = 32)
    private String status;

    /** 关键词（候选人姓名 / 邮箱模糊匹配，可选） */
    @Size(max = 64)
    private String keyword;

    /** 排序：score_desc（默认，按 AI 评分降序）/ applied_desc（按投递时间） */
    @Size(max = 32)
    private String sort;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
