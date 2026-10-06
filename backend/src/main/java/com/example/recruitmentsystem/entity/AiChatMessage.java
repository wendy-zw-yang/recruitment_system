package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI-4 智能客服历史消息。
 *
 * <p>对应表 {@code ai_chat_message}（详见 {@code docs/DataBase/schema.sql} §18b）。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>{@code sessionId} 由前端生成（UUID），用于同一浏览器窗口内跨页面刷新保持对话上下文</li>
 *   <li>{@code role}：USER = 用户提问；ASSISTANT = AI 回复</li>
 *   <li>不与 §5 消息中心（HR ↔ 候选人）的 {@code conversation/message} 共享表（语义不同）</li>
 * </ul>
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户（CANDIDATE / HR，ADMIN 拒收） */
    private Long userId;

    /** 会话 ID（前端 UUID） */
    private String sessionId;

    /** USER / ASSISTANT */
    private String role;

    /** 消息内容 */
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}