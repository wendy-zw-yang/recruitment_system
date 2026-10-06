package com.example.recruitmentsystem.dto.message;

import com.example.recruitmentsystem.entity.Message;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 消息 DTO。
 */
@Data
public class MessageDto {

    private Long id;

    private Long conversationId;

    private Long senderId;

    /** CANDIDATE / HR / SYSTEM */
    private String senderRole;

    /** 关联职位（可空） */
    private Long jobId;

    /** 关联职位的标题（用于显示在消息上方） */
    private String jobTitle;

    private String content;

    /** 0 未读 / 1 已读 */
    private Integer readFlag;

    /** 附件列表（可能为空） */
    private List<MessageAttachmentDto> attachments;

    private LocalDateTime createdAt;

    public static MessageDto from(Message m) {
        MessageDto dto = new MessageDto();
        dto.setId(m.getId());
        dto.setConversationId(m.getConversationId());
        dto.setSenderId(m.getSenderId());
        dto.setSenderRole(m.getSenderRole());
        dto.setJobId(m.getJobId());
        dto.setContent(m.getContent());
        dto.setReadFlag(m.getReadFlag());
        dto.setCreatedAt(m.getCreatedAt());
        dto.setAttachments(Collections.emptyList());
        return dto;
    }
}
