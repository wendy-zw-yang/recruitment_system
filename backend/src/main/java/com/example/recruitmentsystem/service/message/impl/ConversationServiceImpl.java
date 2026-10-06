package com.example.recruitmentsystem.service.message.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.message.ConversationDetailResponse;
import com.example.recruitmentsystem.dto.message.ConversationDto;
import com.example.recruitmentsystem.dto.message.MessageAttachmentDto;
import com.example.recruitmentsystem.dto.message.MessageDto;
import com.example.recruitmentsystem.dto.message.MessageListResponse;
import com.example.recruitmentsystem.dto.message.UnreadStatsResponse;
import com.example.recruitmentsystem.entity.Conversation;
import com.example.recruitmentsystem.entity.Job;
import com.example.recruitmentsystem.entity.Message;
import com.example.recruitmentsystem.entity.MessageAttachment;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.ConversationMapper;
import com.example.recruitmentsystem.mapper.JobMapper;
import com.example.recruitmentsystem.mapper.MessageAttachmentMapper;
import com.example.recruitmentsystem.mapper.MessageMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.message.ConversationService;
import com.example.recruitmentsystem.service.message.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话业务实现。
 *
 * <p>核心约束：</p>
 * <ul>
 *   <li>{@code (hrUserId, candidateId, isDeleted)} 联合唯一 → 同一对 HR ↔ 候选人永远只有一个有效会话</li>
 *   <li>撤回投递 → 同一会话追加 SYSTEM 消息；不创建新会话 → 一个 HR 不会因同一候选人投了多职位出现多个聊天窗口</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final MessageAttachmentMapper attachmentMapper;
    private final UserMapper userMapper;
    private final JobMapper jobMapper;
    private final SseEmitterManager sseManager;

    @Override
    @Transactional
    public ConversationDto findOrCreate(Long requesterId, String requesterRole, Long peerId) {
        Long hrUserId;
        Long candidateId;
        if ("HR".equals(requesterRole)) {
            hrUserId = requesterId;
            candidateId = peerId;
        } else if ("CANDIDATE".equals(requesterRole)) {
            hrUserId = peerId;
            candidateId = requesterId;
        } else {
            throw new BusinessException(403, "ADMIN 不能发起会话");
        }
        validateUserIsRole(hrUserId, "HR", "对方用户不是 HR");
        validateUserIsRole(candidateId, "CANDIDATE", "对方用户不是求职者");

        Conversation existed = conversationMapper.findByHrAndCandidate(hrUserId, candidateId);
        if (existed != null) {
            return enrich(existed, requesterId, requesterRole);
        }

        Conversation c = new Conversation();
        c.setHrUserId(hrUserId);
        c.setCandidateId(candidateId);
        c.setLastMessageAt(null);
        conversationMapper.insert(c);
        log.info("[ConversationService] new conversation id={} hr={} candidate={}", c.getId(), hrUserId, candidateId);
        return enrich(c, requesterId, requesterRole);
    }

    @Override
    public List<ConversationDto> listMine(Long requesterId, String requesterRole) {
        if (!"HR".equals(requesterRole) && !"CANDIDATE".equals(requesterRole)) {
            throw new BusinessException(403, "ADMIN 不能查看会话");
        }
        List<Conversation> list = conversationMapper.listMine(requesterId);
        List<ConversationDto> result = new ArrayList<>(list.size());
        for (Conversation c : list) {
            result.add(enrich(c, requesterId, requesterRole));
        }
        return result;
    }

    @Override
    @Transactional
    public ConversationDetailResponse open(Long requesterId, String requesterRole, Long conversationId, int recentLimit) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!isParticipant(conv, requesterId, requesterRole)) {
            throw new BusinessException(403, "无权限访问该会话");
        }
        // 最近 N 条（按 ASC 顺序）
        List<Message> recent = messageMapper.selectRecentByConversation(conversationId, Math.max(1, Math.min(recentLimit, 100)));
        Collections.reverse(recent); // ASC

        // 自动标记对方消息已读
        String myRole = "HR".equals(requesterRole) ? Message.ROLE_HR : Message.ROLE_CANDIDATE;
        int updated = messageMapper.markPeerMessagesRead(conversationId, myRole);

        // 拼装 DTO
        List<MessageDto> messageDtos = new ArrayList<>(recent.size());
        Long lastReadMessageId = null;
        for (Message m : recent) {
            MessageDto dto = MessageDto.from(m);
            messageDtos.add(dto);
            if (myRole.equals(m.getSenderRole()) && m.getReadFlag() != null && m.getReadFlag() == 1) {
                lastReadMessageId = m.getId();
            }
        }
        attachAttachments(messageDtos);
        attachJobTitles(messageDtos);

        // 推 read_receipt 事件给对方
        if (updated > 0) {
            Long peerId = "HR".equals(requesterRole) ? conv.getCandidateId() : conv.getHrUserId();
            List<Long> readMessageIds = new ArrayList<>();
            for (Message m : recent) {
                if (!myRole.equals(m.getSenderRole()) && m.getReadFlag() != null && m.getReadFlag() == 1) {
                    readMessageIds.add(m.getId());
                }
            }
            Map<String, Object> event = new HashMap<>();
            event.put("conversationId", conversationId);
            event.put("readerRole", myRole);
            event.put("readerId", requesterId);
            event.put("messageIds", readMessageIds);
            sseManager.pushToUser(peerId, "read_receipt", event);
        }

        ConversationDetailResponse resp = new ConversationDetailResponse();
        resp.setConversation(enrich(conv, requesterId, requesterRole));
        resp.setMessages(messageDtos);
        resp.setLastReadMessageId(lastReadMessageId);
        return resp;
    }

    @Override
    public MessageListResponse listMessages(Long requesterId, String requesterRole, Long conversationId,
                                            int pageNum, int pageSize) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!isParticipant(conv, requesterId, requesterRole)) {
            throw new BusinessException(403, "无权限访问该会话");
        }
        int offset = (pageNum - 1) * pageSize;
        List<Message> rows = messageMapper.listByConversationDesc(conversationId, offset, pageSize);
        Long total = (long) messageMapper.selectRecentByConversation(conversationId, Integer.MAX_VALUE).size();

        List<MessageDto> dtos = new ArrayList<>(rows.size());
        for (Message m : rows) {
            dtos.add(MessageDto.from(m));
        }
        attachAttachments(dtos);
        attachJobTitles(dtos);

        MessageListResponse resp = new MessageListResponse();
        resp.setPageNum(pageNum);
        resp.setPageSize(pageSize);
        resp.setTotal(total);
        resp.setRecords(dtos);
        return resp;
    }

    @Override
    public UnreadStatsResponse unreadStats(Long requesterId, String requesterRole) {
        if (!"HR".equals(requesterRole) && !"CANDIDATE".equals(requesterRole)) {
            UnreadStatsResponse empty = new UnreadStatsResponse();
            empty.setTotal(0);
            empty.setByConversation(Collections.emptyList());
            return empty;
        }
        int total = messageMapper.countMyUnread(requesterId, requesterRole);
        // 不实现 byConversation 拆分（首页只显示总数）
        UnreadStatsResponse resp = new UnreadStatsResponse();
        resp.setTotal(total);
        resp.setByConversation(Collections.emptyList());
        return resp;
    }

    // ============ 内部 ============

    private boolean isParticipant(Conversation conv, Long userId, String role) {
        if ("HR".equals(role)) {
            return userId.equals(conv.getHrUserId());
        }
        if ("CANDIDATE".equals(role)) {
            return userId.equals(conv.getCandidateId());
        }
        return false;
    }

    private void validateUserIsRole(Long userId, String expectedRole, String errMsg) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(404, "对方用户不存在");
        }
        if (!expectedRole.equals(u.getRoleCode())) {
            throw new BusinessException(400, errMsg);
        }
        if (!"ENABLED".equals(u.getStatus())) {
            throw new BusinessException(400, "对方账号已停用");
        }
    }

    /**
     * 填充会话 DTO（peer 信息 / 最后消息预览 / 未读数）。
     */
    private ConversationDto enrich(Conversation c, Long requesterId, String requesterRole) {
        ConversationDto dto = ConversationDto.from(c);
        Long peerId = "HR".equals(requesterRole) ? c.getCandidateId() : c.getHrUserId();
        User peer = userMapper.selectById(peerId);
        if (peer != null) {
            dto.setPeerId(peerId);
            dto.setPeerName(peer.getUsername() != null ? peer.getUsername() : peer.getEmail());
            dto.setPeerRole(peer.getRoleCode());
        }
        // 最后一条消息预览
        List<Message> recent = messageMapper.selectRecentByConversation(c.getId(), 1);
        if (!recent.isEmpty()) {
            Message last = recent.get(0);
            dto.setLastMessagePreview(Message.ROLE_SYSTEM.equals(last.getSenderRole())
                    ? "[系统通知] " + previewText(last.getContent())
                    : previewText(last.getContent()));
        }
        // 未读数
        String myRole = "HR".equals(requesterRole) ? Message.ROLE_HR : Message.ROLE_CANDIDATE;
        dto.setUnreadCount(messageMapper.countUnread(c.getId(), myRole));
        return dto;
    }

    private String previewText(String s) {
        if (s == null) return "";
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private void attachAttachments(List<MessageDto> dtos) {
        if (dtos.isEmpty()) return;
        List<Long> messageIds = new ArrayList<>(dtos.size());
        for (MessageDto d : dtos) messageIds.add(d.getId());
        List<MessageAttachment> atts = attachmentMapper.selectByMessageIds(messageIds);
        if (atts.isEmpty()) return;
        Map<Long, List<MessageAttachmentDto>> grouped = new HashMap<>();
        for (MessageAttachment a : atts) {
            MessageAttachmentDto ad = MessageAttachmentDto.from(a);
            ad.setDownloadUrl("/api/messages/attachment/" + a.getId() + "/download");
            grouped.computeIfAbsent(a.getMessageId(), k -> new ArrayList<>()).add(ad);
        }
        for (MessageDto d : dtos) {
            List<MessageAttachmentDto> list = grouped.get(d.getId());
            d.setAttachments(list != null ? list : Collections.emptyList());
        }
    }

    private void attachJobTitles(List<MessageDto> dtos) {
        Map<Long, String> titleCache = new HashMap<>();
        for (MessageDto d : dtos) {
            if (d.getJobId() == null) continue;
            String title = titleCache.computeIfAbsent(d.getJobId(), id -> {
                Job job = jobMapper.selectById(id);
                return job != null ? job.getTitle() : null;
            });
            d.setJobTitle(title);
        }
    }
}
