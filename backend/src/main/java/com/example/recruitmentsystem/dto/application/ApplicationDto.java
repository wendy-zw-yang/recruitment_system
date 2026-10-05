package com.example.recruitmentsystem.dto.application;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递列表项（候选人侧 + HR 侧共用基础结构）。
 *
 * <p>字段尽量少；详情走 {@link ApplicationDetailDto}。</p>
 */
@Data
public class ApplicationDto {

    private Long id;

    /** 候选人 userId（HR 端可见；候选人端冗余） */
    private Long candidateId;

    /** 候选人姓名（HR 端用） */
    private String candidateName;

    /** 职位 jobId */
    private Long jobId;

    /** 职位标题（HR 端 + 候选人端共用） */
    private String jobTitle;

    /** 公司名 */
    private String companyName;

    /** 状态 */
    private String status;

    /** AI 评分（NULL = 待评分） */
    private Integer aiScore;

    /** AI 评分理由 */
    private String aiReason;

    /** 投递时间 */
    private LocalDateTime appliedAt;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;

    /** 是否可撤回（仅候选人侧用，候选人本人且非终态时为 true） */
    private Boolean withdrawable;

    /** 是否已撤回（HR 端展示标签） */
    private Boolean withdrawn;
}
