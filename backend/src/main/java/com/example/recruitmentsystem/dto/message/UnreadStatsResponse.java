package com.example.recruitmentsystem.dto.message;

import lombok.Data;

import java.util.List;

/**
 * 未读消息统计（HR 首页 stats 用）。
 */
@Data
public class UnreadStatsResponse {

    /** 当前用户所有会话的未读消息总数 */
    private Integer total;

    /** 按会话拆分（候选展示，可选） */
    private List<ConversationUnreadItem> byConversation;

    @Data
    public static class ConversationUnreadItem {
        private Long conversationId;
        private Integer unreadCount;
    }
}
