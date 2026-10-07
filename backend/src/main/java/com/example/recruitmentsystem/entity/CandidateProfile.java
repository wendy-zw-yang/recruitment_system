package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
 *
 * <p>v0.7.3.2：4 个偏好字段加 {@code updateStrategy = ALWAYS}，绕过 MyBatis Plus
 * 默认 {@code NOT_NULL} 策略，确保 {@code null}（用户清空）能被真正写入 DB。
 * Bug 现象：之前候选人清空偏好后端 UPDATE SQL 不含 null 字段，DB 保留旧值，
 * 下次首页 SQL 推荐仍按旧偏好筛选，profile 页面又显示旧值。</p>
 */
@Data
@TableName("candidate_profile")
public class CandidateProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String expectedPosition;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long expectedIndustryId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String expectedProvince;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long expectedCityId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
