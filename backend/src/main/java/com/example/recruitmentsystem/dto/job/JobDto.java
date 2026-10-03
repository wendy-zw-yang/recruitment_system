package com.example.recruitmentsystem.dto.job;

import com.example.recruitmentsystem.entity.Job;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职位详情 / 列表通用 DTO。
 *
 * <p>包含 companyName / industryName / cityName 等冗余字段，避免前端再 join。</p>
 */
@Data
public class JobDto {

    private Long id;
    private Long hrUserId;
    private String companyName;
    private Long industryId;
    private String industryName;
    private Long cityId;
    private String cityName;
    private String province;
    private String title;
    private Integer salaryMin;
    private Integer salaryMax;
    private String description;
    private String requirements;
    private String keywords;
    /** DRAFT / ONLINE / OFFLINE / DELETED */
    private String status;
    /** PENDING / APPROVED / REJECTED */
    private String auditStatus;
    private String auditNote;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 是否已被当前候选人收藏（仅候选人端列表需要） */
    private Boolean favorited;

    public static JobDto from(Job j) {
        JobDto dto = new JobDto();
        dto.id = j.getId();
        dto.hrUserId = j.getHrUserId();
        dto.industryId = j.getIndustryId();
        dto.cityId = j.getCityId();
        dto.province = j.getProvince();
        dto.title = j.getTitle();
        dto.salaryMin = j.getSalaryMin();
        dto.salaryMax = j.getSalaryMax();
        dto.description = j.getDescription();
        dto.requirements = j.getRequirements();
        dto.keywords = j.getKeywords();
        dto.status = j.getStatus();
        dto.auditStatus = j.getAuditStatus();
        dto.auditNote = j.getAuditNote();
        dto.publishedAt = j.getPublishedAt();
        dto.createdAt = j.getCreatedAt();
        dto.updatedAt = j.getUpdatedAt();
        return dto;
    }
}
