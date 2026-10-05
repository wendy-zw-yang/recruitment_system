package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递记录。
 *
 * <p>对应表 {@code application}（详见 {@code docs/DataBase/schema.sql}）。</p>
 *
 * <p>状态机（8 态，详见 {@code docs/系统设计/详细设计/投递.md §4.3.2}）：</p>
 * <ul>
 *   <li>PENDING_REVIEW → VIEWED_BY_HR → RESUME_PASSED → INTERVIEWING → OFFERED → HIRED（主路径）</li>
 *   <li>任意节点 → REJECTED</li>
 *   <li>任意非终态 → WITHDRAWN（仅候选人）</li>
 *   <li>终态：HIRED / REJECTED / WITHDRAWN</li>
 * </ul>
 *
 * <p>关键字段：</p>
 * <ul>
 *   <li>{@code resumeSnapshotId}：投递瞬间拷贝的 ACTIVE 简历 id，候选人后续修改不影响</li>
 *   <li>{@code aiScore}：AI-2 评分（0-100），NULL 表示「待评分」</li>
 *   <li>{@code aiReason}：AI 评分理由</li>
 * </ul>
 */
@Data
@TableName("application")
public class Application {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 候选人 users.id */
    private Long candidateId;

    /** 职位 job.id */
    private Long jobId;

    /** 投递时的 ACTIVE 简历快照 id（外键 resume.id） */
    private Long resumeSnapshotId;

    /**
     * 状态（8 态）：
     * PENDING_REVIEW / VIEWED_BY_HR / RESUME_PASSED / INTERVIEWING / OFFERED / HIRED / REJECTED / WITHDRAWN
     */
    private String status;

    /** AI-2 评分（0-100），NULL 表示「待评分」 */
    private Integer aiScore;

    /** AI-2 评分理由（≤ 512 字） */
    private String aiReason;

    /** 投递时间 */
    private LocalDateTime appliedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
