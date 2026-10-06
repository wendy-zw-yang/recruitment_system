package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.message.AttachmentUploadRef;
import com.example.recruitmentsystem.dto.message.MessageDto;
import com.example.recruitmentsystem.dto.message.SendMessageRequest;
import com.example.recruitmentsystem.entity.Conversation;
import com.example.recruitmentsystem.entity.Message;
import com.example.recruitmentsystem.entity.MessageAttachment;
import com.example.recruitmentsystem.mapper.ConversationMapper;
import com.example.recruitmentsystem.mapper.MessageAttachmentMapper;
import com.example.recruitmentsystem.mapper.MessageMapper;
import com.example.recruitmentsystem.service.message.SseEmitterManager;
import com.example.recruitmentsystem.service.message.impl.AttachmentServiceImpl;
import com.example.recruitmentsystem.service.message.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MessageService 单元测试。
 *
 * <p>覆盖：sendMessage（参与者校验 / 内容校验 / 推送）/ sendSystemMessage（撤回触发）。
 * 不测附件绑定（AttachmentServiceImpl.bindToMessage 直接 mock）。</p>
 */
class MessageServiceTest {

    private MessageMapper messageMapper;
    private MessageAttachmentMapper attachmentMapper;
    private ConversationMapper conversationMapper;
    private AttachmentServiceImpl attachmentServiceImpl;
    private SseEmitterManager sseManager;
    private MessageServiceImpl svc;

    @BeforeEach
    void setUp() {
        messageMapper = mock(MessageMapper.class);
        attachmentMapper = mock(MessageAttachmentMapper.class);
        conversationMapper = mock(ConversationMapper.class);
        attachmentServiceImpl = mock(AttachmentServiceImpl.class);
        sseManager = mock(SseEmitterManager.class);

        svc = new MessageServiceImpl(messageMapper, attachmentMapper, conversationMapper,
                attachmentServiceImpl, sseManager);

        // chatMapper.insert auto-increment id
        doAnswer(inv -> {
            Message m = inv.getArgument(0);
            if (m.getId() == null) m.setId(System.nanoTime());
            return 1;
        }).when(messageMapper).insert(any(Message.class));
    }

    @Test
    void sendMessage_blankContentAndNoAttachment_rejected() {
        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(10L);
        assertThrows(BusinessException.class,
                () -> svc.sendMessage(2L, "CANDIDATE", req));
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void sendMessage_nonParticipant_rejected() {
        Conversation c = new Conversation();
        c.setId(10L); c.setHrUserId(1L); c.setCandidateId(2L);
        when(conversationMapper.selectById(10L)).thenReturn(c);

        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(10L);
        req.setContent("hello");
        // requesterId=99 不是会话参与者
        assertThrows(BusinessException.class,
                () -> svc.sendMessage(99L, "HR", req));
    }

    @Test
    void sendMessage_hrSends_persistsAndPushesBothSides() {
        Conversation c = new Conversation();
        c.setId(10L); c.setHrUserId(1L); c.setCandidateId(2L);
        when(conversationMapper.selectById(10L)).thenReturn(c);
        when(attachmentMapper.selectByMessageIds(any())).thenReturn(Collections.emptyList());

        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(10L);
        req.setContent("请问何时方便面试？");
        req.setJobId(50L);

        MessageDto dto = svc.sendMessage(1L, "HR", req);

        assertNotNull(dto);
        assertEquals("HR", dto.getSenderRole());
        assertEquals("请问何时方便面试？", dto.getContent());
        // 验证 messageMapper.insert 调用一次
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper, times(1)).insert(captor.capture());
        assertEquals(Message.ROLE_HR, captor.getValue().getSenderRole());
        assertEquals(50L, captor.getValue().getJobId());
        // 验证会话 last_message_at 更新
        verify(conversationMapper, times(1)).updateById(any(Conversation.class));
        // 推送双方（HR + 候选人）
        verify(sseManager, times(1)).pushToUser(eq(1L), eq("new_message"), any());
        verify(sseManager, times(1)).pushToUser(eq(2L), eq("new_message"), any());
    }

    @Test
    void sendMessage_withAttachment_bindsToMessage() {
        Conversation c = new Conversation();
        c.setId(10L); c.setHrUserId(1L); c.setCandidateId(2L);
        when(conversationMapper.selectById(10L)).thenReturn(c);

        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(10L);
        req.setContent("附简历");
        req.setAttachments(List.of(new AttachmentUploadRef("cv.pdf", "resume/2026-10/abc.pdf", 1024L, "application/pdf")));

        MessageAttachment att = new MessageAttachment();
        att.setId(200L); att.setMessageId(999L); att.setFileName("cv.pdf");
        att.setFilePath("resume/2026-10/abc.pdf"); att.setFileSize(1024L); att.setMimeType("application/pdf");
        when(attachmentMapper.selectByMessageIds(any())).thenReturn(List.of(att));

        MessageDto dto = svc.sendMessage(1L, "HR", req);

        assertEquals(1, dto.getAttachments().size());
        assertEquals("cv.pdf", dto.getAttachments().get(0).getFileName());
        assertTrue(dto.getAttachments().get(0).getDownloadUrl().contains("/api/messages/attachment/"));
        // bindToMessage 被调用
        verify(attachmentServiceImpl, times(1)).bindToMessage(anyLong(), anyList());
    }

    @Test
    void sendSystemMessage_noExistingConversation_createsAndSends() {
        when(conversationMapper.findByHrAndCandidate(1L, 2L)).thenReturn(null);
        doAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(88L);
            return 1;
        }).when(conversationMapper).insert(any(Conversation.class));

        Long msgId = svc.sendSystemMessage(1L, 2L, "候选人 C-2 已撤回对职位「后端工程师」的投递", 50L);

        assertNotNull(msgId);
        // SYSTEM 消息被插入
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper, times(1)).insert(msgCaptor.capture());
        Message sys = msgCaptor.getValue();
        assertEquals(Message.ROLE_SYSTEM, sys.getSenderRole());
        assertEquals(0L, sys.getSenderId());
        assertEquals(50L, sys.getJobId());
        // 会话被创建
        verify(conversationMapper, times(1)).insert(any(Conversation.class));
        // SSE 推 HR
        verify(sseManager, times(1)).pushToUser(eq(1L), eq("new_message"), any());
        // 不推送候选人（SYSTEM 无发送方）
        verify(sseManager, never()).pushToUser(eq(2L), anyString(), any());
    }

    @Test
    void sendSystemMessage_existingConversation_reusesAndSends() {
        Conversation existed = new Conversation();
        existed.setId(50L); existed.setHrUserId(1L); existed.setCandidateId(2L);
        when(conversationMapper.findByHrAndCandidate(1L, 2L)).thenReturn(existed);

        svc.sendSystemMessage(1L, 2L, "候选人 C-2 已撤回对职位 X 的投递", 50L);

        // 复用现有会话，不创建新会话
        verify(conversationMapper, never()).insert(any(Conversation.class));
        // 仍写 SYSTEM 消息
        verify(messageMapper, times(1)).insert(any(Message.class));
        verify(sseManager, times(1)).pushToUser(eq(1L), eq("new_message"), any());
    }

    @Test
    void enrichWithAttachments_emptyInput_returnsEmpty() {
        assertEquals(Collections.emptyList(), svc.enrichWithAttachments(Collections.emptyList()));
    }

    @Test
    void enrichWithAttachments_groupsByMessageId() {
        MessageDto d1 = new MessageDto();
        d1.setId(100L);
        MessageDto d2 = new MessageDto();
        d2.setId(200L);

        MessageAttachment a1 = new MessageAttachment();
        a1.setId(1L); a1.setMessageId(100L); a1.setFileName("a.txt");
        a1.setFilePath("message/x/a.txt"); a1.setFileSize(10L); a1.setMimeType("text/plain");
        MessageAttachment a2 = new MessageAttachment();
        a2.setId(2L); a2.setMessageId(200L); a2.setFileName("b.txt");
        a2.setFilePath("message/x/b.txt"); a2.setFileSize(20L); a2.setMimeType("text/plain");
        when(attachmentMapper.selectByMessageIds(any())).thenReturn(List.of(a1, a2));

        List<MessageDto> result = svc.enrichWithAttachments(new java.util.ArrayList<>(List.of(d1, d2)));

        assertEquals(1, result.get(0).getAttachments().size());
        assertEquals("a.txt", result.get(0).getAttachments().get(0).getFileName());
        assertEquals(1, result.get(1).getAttachments().size());
        assertEquals("b.txt", result.get(1).getAttachments().get(0).getFileName());
    }
}
