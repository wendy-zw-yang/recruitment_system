package com.example.recruitmentsystem.dto.chat;

import lombok.Data;

import java.util.List;

/**
 * AI-4 历史消息响应。
 *
 * <p>返回当前用户的指定 session 最近 N 条消息（按 created_at ASC 升序）。</p>
 */
@Data
public class ChatHistoryResponse {

    /** 当前会话 ID（与请求一致） */
    private String sessionId;

    /** 消息列表（按时间从早到晚） */
    private List<AiChatMessageDto> messages;
}