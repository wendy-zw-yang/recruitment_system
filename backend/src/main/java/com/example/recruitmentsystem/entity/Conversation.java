package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息会话（HR ↔ 候选人）。
 *
 * <p>对应表 {@code conversation}（详见 {@code docs/DataBase/schema.sql §15}）。</p>
 *
 * <p>核心约束：(hrUserId, candidateId, isDeleted) 联合唯一 → 同一 HR 与同一候选人
 * 永远只对应一个有效会话（{@code isDeleted=0}）。撤回投递不删除会话，
 * 仅在该会话中追加 SYSTEM 角色消息，因此一个 HR 不会因候选人投了多个职位而出现多个聊天窗口。</p>
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 users.id（HR） */
    private Long hrUserId;

    /** 关联 users.id（CANDIDATE） */
    private Long candidateId;

    /** 最后消息时间（NULL = 会话刚创建但无消息） */
    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
