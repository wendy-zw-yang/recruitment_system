package com.example.recruitmentsystem.service.message.impl;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.message.AttachmentUploadRef;
import com.example.recruitmentsystem.dto.message.MessageAttachmentDto;
import com.example.recruitmentsystem.dto.message.MessageDto;
import com.example.recruitmentsystem.dto.message.SendMessageRequest;
import com.example.recruitmentsystem.entity.Conversation;
import com.example.recruitmentsystem.entity.Message;
import com.example.recruitmentsystem.entity.MessageAttachment;
import com.example.recruitmentsystem.mapper.ConversationMapper;
import com.example.recruitmentsystem.mapper.MessageAttachmentMapper;
import com.example.recruitmentsystem.mapper.MessageMapper;
import com.example.recruitmentsystem.service.message.MessageSendService;
import com.example.recruitmentsystem.service.message.MessageService;
import com.example.recruitmentsystem.service.message.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息发送 / 系统消息 实现。
 *
 * <p>合并实现 {@link MessageService}（系统消息 + 撤回触发）和 {@link MessageSendService}（用户主动发消息），
 * 共享底层逻辑（写库 + 更新 last_message_at + SSE 推送）。</p>
 *
 * <p>SSE 推送策略：使用 {@link TransactionSynchronization#afterCommit}，
 * 保证推送时消息已 commit、接收方能查到，避免"接收方收推送但读不到"的竞态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService, MessageSendService {

    /** SYSTEM 消息 senderId 占位（null 表示系统，无真实用户）。
     * v0.7.4.3 修复：原值 0L 会触发 fk_message_sender 外键异常（users.id=0 不存在），
     * 改用 null 走 FK 旁路（schema 已允许 sender_id 为 NULL）。
     * 注意：MessageServiceImpl.sendMessage 调用方传真实 senderId，本字段仅 sendSystemMessage 使用。 */
    private static final Long SYSTEM_SENDER_ID = null;

    private final MessageMapper messageMapper;
    private final MessageAttachmentMapper attachmentMapper;
    private final ConversationMapper conversationMapper;
    private final AttachmentServiceImpl attachmentServiceImpl;
    private final SseEmitterManager sseManager;

    // ============ 用户主动发消息 ============

    @Override
    @Transactional
    public MessageDto sendMessage(Long senderId, String senderRole, SendMessageRequest request) {
        String content = request.getContent();
        List<AttachmentUploadRef> attachments = request.getAttachments();
        boolean hasContent = content != null && !content.isBlank();
        boolean hasAttachment = attachments != null && !attachments.isEmpty();
        if (!hasContent && !hasAttachment) {
            throw new BusinessException(400, "消息内容与附件不能同时为空");
        }
        String role = "HR".equals(senderRole) ? Message.ROLE_HR : Message.ROLE_CANDIDATE;

        Conversation conv = conversationMapper.selectById(request.getConversationId());
        if (conv == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!isParticipant(conv, senderId, role)) {
            throw new BusinessException(403, "无权限在该会话发消息");
        }

        // 先写消息（拿 messageId）
        Message msg = new Message();
        msg.setConversationId(conv.getId());
        msg.setSenderId(senderId);
        msg.setSenderRole(role);
        msg.setJobId(request.getJobId());
        msg.setContent(hasContent ? content : "");
        msg.setReadFlag(0);
        messageMapper.insert(msg);

        // 绑定附件（先落盘已完成的文件 → 入库）
        if (hasAttachment) {
            attachmentServiceImpl.bindToMessage(msg.getId(), attachments);
        }

        // 更新会话 last_message_at
        Conversation update = new Conversation();
        update.setId(conv.getId());
        update.setLastMessageAt(LocalDateTime.now());
        conversationMapper.updateById(update);

        // 拼 DTO
        MessageDto dto = MessageDto.from(msg);
        if (hasAttachment) {
            List<MessageAttachment> atts = attachmentMapper.selectByMessageIds(Collections.singletonList(msg.getId()));
            List<MessageAttachmentDto> attDtos = new ArrayList<>(atts.size());
            for (MessageAttachment a : atts) {
                MessageAttachmentDto ad = MessageAttachmentDto.from(a);
                ad.setDownloadUrl("/api/messages/attachment/" + a.getId() + "/download");
                attDtos.add(ad);
            }
            dto.setAttachments(attDtos);
        }

        // SSE 推送（事务提交后）：HR ↔ 候选人 双方都推送（发送方多 tab 同步、接收方新消息提示）
        Long peerId = Message.ROLE_HR.equals(role) ? conv.getCandidateId() : conv.getHrUserId();
        Map<String, Object> event = new HashMap<>();
        event.put("conversationId", conv.getId());
        event.put("message", dto);
        pushAfterCommit(peerId, "new_message", event);
        pushAfterCommit(senderId, "new_message", event);

        log.info("[MessageService] send conv={} from={} role={} msgId={}", conv.getId(), senderId, role, msg.getId());
        return dto;
    }

    @Override
    public List<MessageDto> enrichWithAttachments(List<MessageDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return dtos;
        List<Long> ids = new ArrayList<>(dtos.size());
        for (MessageDto d : dtos) ids.add(d.getId());
        List<MessageAttachment> atts = attachmentMapper.selectByMessageIds(ids);
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
        return dtos;
    }

    // ============ §4 撤回触发：系统消息 ============

    @Override
    // v0.7.4.3 修复：REQUIRES_NEW 独立事务——撤回流程 (§4) 中 sendSystemMessage 异常不应让外层
    // withdraw 事务被标记 rollback-only。REQUIRED 默认会污染外层事务，即使 try/catch 捕获了
    // 原异常，提交时仍会抛 UnexpectedRollbackException，导致 application 状态更新被一并回滚。
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long sendSystemMessage(Long hrUserId, Long candidateId, String content, Long jobId) {
        Conversation conv = conversationMapper.findByHrAndCandidate(hrUserId, candidateId);
        if (conv == null) {
            conv = new Conversation();
            conv.setHrUserId(hrUserId);
            conv.setCandidateId(candidateId);
            conversationMapper.insert(conv);
            log.info("[MessageService] 系统消息触发的会话创建 conv={} hr={} candidate={}", conv.getId(), hrUserId, candidateId);
        }

        Message msg = new Message();
        msg.setConversationId(conv.getId());
        msg.setSenderId(SYSTEM_SENDER_ID);
        msg.setSenderRole(Message.ROLE_SYSTEM);
        msg.setJobId(jobId);
        msg.setContent(content);
        msg.setReadFlag(0);
        messageMapper.insert(msg);

        Conversation update = new Conversation();
        update.setId(conv.getId());
        update.setLastMessageAt(LocalDateTime.now());
        conversationMapper.updateById(update);

        // 推送 HR（接收方）—— SYSTEM 不推送发送方（无发送方）
        MessageDto dto = MessageDto.from(msg);
        Map<String, Object> event = new HashMap<>();
        event.put("conversationId", conv.getId());
        event.put("message", dto);
        pushAfterCommit(hrUserId, "new_message", event);

        log.info("[MessageService] system msg conv={} jobId={} msgId={}", conv.getId(), jobId, msg.getId());
        return msg.getId();
    }

    // ============ 内部 ============

    private boolean isParticipant(Conversation conv, Long userId, String role) {
        if (Message.ROLE_HR.equals(role)) {
            return userId.equals(conv.getHrUserId());
        }
        if (Message.ROLE_CANDIDATE.equals(role)) {
            return userId.equals(conv.getCandidateId());
        }
        return false;
    }

    /**
     * 事务提交后再推送，避免接收方在推送时还查不到消息。
     * 无活跃事务时立即推送。
     */
    private void pushAfterCommit(Long userId, String eventName, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseManager.pushToUser(userId, eventName, payload);
                }
            });
        } else {
            sseManager.pushToUser(userId, eventName, payload);
        }
    }
}
