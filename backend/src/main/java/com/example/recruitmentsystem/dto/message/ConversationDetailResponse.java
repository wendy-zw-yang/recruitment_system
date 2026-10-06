package com.example.recruitmentsystem.dto.message;

import lombok.Data;

import java.util.List;

/**
 * 会话详情（会话 + 消息流）。
 *
 * <p>由 {@link com.example.recruitmentsystem.service.message.ConversationService#open}
 * 返回；进入会话页时自动已读对方消息。</p>
 */
@Data
public class ConversationDetailResponse {

    private ConversationDto conversation;

    /** 消息列表（按 createdAt ASC 时间正序） */
    private List<MessageDto> messages;

    /** 当前用户在该会话中已读的最新消息 ID（前端可记录以避免重复请求历史） */
    private Long lastReadMessageId;
}
