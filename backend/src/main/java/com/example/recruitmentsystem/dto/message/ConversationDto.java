package com.example.recruitmentsystem.dto.message;

import com.example.recruitmentsystem.entity.Conversation;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表 / 详情 DTO。
 *
 * <p>{@code peerName / peerRole / jobTitle} 由 Service 层 JOIN users / job 填充（详见
 * {@link com.example.recruitmentsystem.service.message.ConversationService}）。</p>
 */
@Data
public class ConversationDto {

    private Long id;

    private Long hrUserId;

    private Long candidateId;

    /** 对方用户 ID（HR 视角 = candidateId；候选人视角 = hrUserId） */
    private Long peerId;

    /** 对方姓名（users.username） */
    private String peerName;

    /** 对方角色：CANDIDATE / HR */
    private String peerRole;

    /** 最近一条消息的预览文本（可空） */
    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    /** 未读消息数（当前用户视角，对方发来的） */
    private Integer unreadCount;

    private LocalDateTime createdAt;

    public static ConversationDto from(Conversation c) {
        ConversationDto dto = new ConversationDto();
        dto.setId(c.getId());
        dto.setHrUserId(c.getHrUserId());
        dto.setCandidateId(c.getCandidateId());
        dto.setLastMessageAt(c.getLastMessageAt());
        dto.setCreatedAt(c.getCreatedAt());
        return dto;
    }
}
