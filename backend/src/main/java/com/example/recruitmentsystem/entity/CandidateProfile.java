package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 求职者扩展资料 + 求职偏好（与 users 1:1）。
 *
 * <p>对应表：{@code candidate_profile}。</p>
 */
@Data
@TableName("candidate_profile")
public class CandidateProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String expectedPosition;

    /** 期望行业 → {@code dict_industry.id} */
    private Long expectedIndustryId;

    /** 期望城市 → {@code dict_city.id} */
    private Long expectedCityId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
