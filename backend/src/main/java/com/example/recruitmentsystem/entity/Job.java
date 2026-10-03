package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职位 / JD。
 *
 * <p>状态机：</p>
 * <ul>
 *   <li>status: DRAFT → ONLINE → OFFLINE（DELETED 仅软删）</li>
 *   <li>auditStatus: PENDING → APPROVED / REJECTED</li>
 *   <li>上线前置条件：auditStatus == APPROVED</li>
 * </ul>
 *
 * <p>对应表：{@code job}（详见 {@code docs/DataBase/schema.sql}）。</p>
 */
@Data
@TableName("job")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hrUserId;

    private Long companyId;

    private String title;

    /** 行业 → {@code dict_industry.id} */
    private Long industryId;

    /** 城市 → {@code dict_city.id} */
    private Long cityId;

    /** 省份 / 直辖市（冗余，便于按省份聚合筛选） */
    private String province;

    @TableField("salary_min")
    private Integer salaryMin;

    @TableField("salary_max")
    private Integer salaryMax;

    private String description;

    private String requirements;

    /** 关键词（逗号分隔，AI 润色输出 / HR 手动维护） */
    private String keywords;

    /** DRAFT / ONLINE / OFFLINE / DELETED */
    private String status;

    /** PENDING / APPROVED / REJECTED */
    @TableField("audit_status")
    private String auditStatus;

    @TableField("audit_note")
    private String auditNote;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
