package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递状态变更历史。
 *
 * <p>对应表 {@code application_status_history}。每次状态变更（含初次创建）写一行，驱动前端状态时间线。</p>
 *
 * <p>关键字段：</p>
 * <ul>
 *   <li>{@code fromStatus}：原状态（NULL 表示「初次创建投递」）</li>
 *   <li>{@code toStatus}：目标状态</li>
 *   <li>{@code changedBy}：变更人 users.id（HR 推进 / 候选人撤回）</li>
 *   <li>{@code note}：可选备注（如驳回理由）</li>
 * </ul>
 */
@Data
@TableName("application_status_history")
public class ApplicationStatusHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    /** 原状态（NULL = 初次创建） */
    private String fromStatus;

    /** 目标状态 */
    private String toStatus;

    /** 变更人 users.id */
    private Long changedBy;

    /** 备注（如驳回理由） */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
