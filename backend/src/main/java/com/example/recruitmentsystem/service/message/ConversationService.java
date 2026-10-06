package com.example.recruitmentsystem.service.message;

import com.example.recruitmentsystem.dto.message.ConversationDetailResponse;
import com.example.recruitmentsystem.dto.message.ConversationDto;
import com.example.recruitmentsystem.dto.message.MessageListResponse;
import com.example.recruitmentsystem.dto.message.UnreadStatsResponse;

import java.util.List;

/**
 * §5 消息中心 - 会话 Service 接口。
 */
public interface ConversationService {

    /**
     * 创建或获取会话（按 (hr, candidate) 二元组）。
     *
     * @param requesterId 当前用户 ID
     * @param requesterRole 当前用户角色（CANDIDATE / HR）
     * @param peerId 对方用户 ID
     * @return 会话 DTO
     */
    ConversationDto findOrCreate(Long requesterId, String requesterRole, Long peerId);

    /**
     * 我的会话列表（按 last_message_at DESC）。
     */
    List<ConversationDto> listMine(Long requesterId, String requesterRole);

    /**
     * 进入会话详情：返回会话 + 消息流 + 自动标记对方消息已读 + 推 read_receipt 事件给对方。
     *
     * @param requesterId 当前用户 ID
     * @param requesterRole 当前用户角色
     * @param conversationId 会话 ID
     * @param recentLimit 消息流上限（默认 50）
     */
    ConversationDetailResponse open(Long requesterId, String requesterRole, Long conversationId, int recentLimit);

    /**
     * 分页拉取会话消息（按 created_at DESC 倒序）。
     */
    MessageListResponse listMessages(Long requesterId, String requesterRole, Long conversationId,
                                     int pageNum, int pageSize);

    /**
     * 未读消息总数（用于 HR 首页 / 候选人首页 stats）。
     */
    UnreadStatsResponse unreadStats(Long requesterId, String requesterRole);
}
