package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员操作日志。UC-34 / UC-35 / UC-36 / UC-38 / UC-39 / UC-40 所有写操作必写。
 * （UC-37 简历审核 v0.2 取消，对应审计项不存在）
 *
 * <p>对应表：{@code audit_log}。</p>
 *
 * <p>详见 {@code docs/系统设计/详细设计/管理员.md §7.3.5}。</p>
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;

    /** USER_ENABLE / USER_DISABLE / USER_RESET_PASSWORD / USER_ROLE_CHANGE / JOB_APPROVE / JOB_REJECT / COMPANY_VERIFY / COMPANY_REJECT / DICT_* */
    private String actionType;

    private Long targetId;

    /** USER / JOB / COMPANY / DICT_INDUSTRY / DICT_CITY / DICT_SKILL */
    private String targetType;

    private String beforeValue;

    private String afterValue;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
