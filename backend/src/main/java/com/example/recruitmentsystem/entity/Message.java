package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息。
 *
 * <p>对应表 {@code message}（详见 {@code docs/DataBase/schema.sql §16}）。</p>
 *
 * <p>{@code senderRole} 枚举：</p>
 * <ul>
 *   <li>{@code CANDIDATE} — 候选人发送</li>
 *   <li>{@code HR} — HR 发送</li>
 *   <li>{@code SYSTEM} — 系统消息（如候选人撤回投递通知 HR）；{@code senderId=0}</li>
 * </ul>
 */
@Data
@TableName("message")
public class Message {

    public static final String ROLE_CANDIDATE = "CANDIDATE";
    public static final String ROLE_HR = "HR";
    public static final String ROLE_SYSTEM = "SYSTEM";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** 发送人 users.id；SYSTEM 角色时该值为 0 */
    private Long senderId;

    /** CANDIDATE / HR / SYSTEM */
    private String senderRole;

    /** 关联职位（可空） */
    private Long jobId;

    /** 消息内容 */
    private String content;

    /** 0 未读 / 1 已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
