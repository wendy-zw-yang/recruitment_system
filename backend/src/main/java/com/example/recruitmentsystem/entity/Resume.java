package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历主体。每位候选人通过应用层约束仅一份 {@code is_archived=false}（ACTIVE）简历。
 *
 * <p>对应表：{@code resume}。</p>
 *
 * <p>教育 / 工作 / 项目以 JSON 数组存储；技能以文本字段存储。</p>
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long candidateId;

    /** 真实姓名（简历基本信息） */
    private String basicName;

    private String basicPhone;

    private String basicEmail;

    /** 教育经历 JSON 数组 */
    private String education;

    /** 工作经历 JSON 数组 */
    private String work;

    /** 项目经历 JSON 数组 */
    private String projects;

    /** 技能（自由输入，逗号或换行分隔） */
    private String skills;

    private String selfIntro;

    /** false = ACTIVE / true = ARCHIVED */
    @TableField("is_archived")
    private Boolean archived;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
