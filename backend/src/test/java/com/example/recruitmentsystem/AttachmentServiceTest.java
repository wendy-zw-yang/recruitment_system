package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.message.AttachmentUploadRef;
import com.example.recruitmentsystem.entity.Conversation;
import com.example.recruitmentsystem.entity.Message;
import com.example.recruitmentsystem.entity.MessageAttachment;
import com.example.recruitmentsystem.mapper.ConversationMapper;
import com.example.recruitmentsystem.mapper.MessageAttachmentMapper;
import com.example.recruitmentsystem.mapper.MessageMapper;
import com.example.recruitmentsystem.service.FileStorageService;
import com.example.recruitmentsystem.service.message.AttachmentService;
import com.example.recruitmentsystem.service.message.impl.AttachmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttachmentService 单元测试。
 *
 * <p>覆盖：upload（角色校验 / 返回 UploadRef）/ download（参与者鉴权 / 404 / 403）/ bindToMessage（直接调用 mapper）。</p>
 */
class AttachmentServiceTest {

    private MessageAttachmentMapper attachmentMapper;
    private MessageMapper messageMapper;
    private ConversationMapper conversationMapper;
    private FileStorageService fileStorageService;
    private AttachmentServiceImpl svc;

    @BeforeEach
    void setUp() {
        attachmentMapper = mock(MessageAttachmentMapper.class);
        messageMapper = mock(MessageMapper.class);
        conversationMapper = mock(ConversationMapper.class);
        fileStorageService = mock(FileStorageService.class);
        svc = new AttachmentServiceImpl(attachmentMapper, messageMapper, conversationMapper, fileStorageService);
    }

    @Test
    void upload_hr_savesAndReturnsRef() {
        when(fileStorageService.save(any(), eq("message"))).thenReturn("message/2026-10/abc.pdf");
        when(fileStorageService.resolveMime("message/2026-10/abc.pdf")).thenReturn("application/pdf");

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "hello".getBytes());
        AttachmentUploadRef ref = svc.upload(1L, "HR", file);

        assertEquals("resume.pdf", ref.getFileName());
        assertEquals("message/2026-10/abc.pdf", ref.getFilePath());
        assertEquals(5L, ref.getFileSize());
        assertEquals("application/pdf", ref.getMimeType());
        // 不写 DB（两阶段策略）
        verify(attachmentMapper, never()).insert(any(MessageAttachment.class));
    }

    @Test
    void upload_admin_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "x".getBytes());
        assertThrows(BusinessException.class,
                () -> svc.upload(99L, "ADMIN", file));
    }

    @Test
    void bindToMessage_insertsAttachmentRows() {
        AttachmentUploadRef r1 = new AttachmentUploadRef("a.txt", "message/x/a.txt", 10L, "text/plain");
        AttachmentUploadRef r2 = new AttachmentUploadRef("b.txt", "message/x/b.txt", 20L, "text/plain");
        svc.bindToMessage(100L, java.util.List.of(r1, r2));

        verify(attachmentMapper, times(2)).insert(any(MessageAttachment.class));
    }

    @Test
    void download_nonParticipant_throws403() {
        MessageAttachment a = new MessageAttachment();
        a.setId(1L); a.setMessageId(100L); a.setFileName("x.pdf");
        a.setFilePath("message/x/x.pdf"); a.setFileSize(10L); a.setMimeType("application/pdf");
        when(attachmentMapper.selectById(1L)).thenReturn(a);

        Message msg = new Message();
        msg.setId(100L); msg.setConversationId(10L);
        when(messageMapper.selectById(100L)).thenReturn(msg);

        Conversation c = new Conversation();
        c.setId(10L); c.setHrUserId(1L); c.setCandidateId(2L);
        when(conversationMapper.selectById(10L)).thenReturn(c);

        // requesterId=99 不是会话参与者
        assertThrows(BusinessException.class,
                () -> svc.download(99L, "HR", 1L));
    }

    @Test
    void download_participant_returnsBytes() {
        MessageAttachment a = new MessageAttachment();
        a.setId(1L); a.setMessageId(100L); a.setFileName("x.pdf");
        a.setFilePath("message/x/x.pdf"); a.setFileSize(10L); a.setMimeType("application/pdf");
        a.setCreatedAt(LocalDateTime.now());
        when(attachmentMapper.selectById(1L)).thenReturn(a);

        Message msg = new Message();
        msg.setId(100L); msg.setConversationId(10L);
        when(messageMapper.selectById(100L)).thenReturn(msg);

        Conversation c = new Conversation();
        c.setId(10L); c.setHrUserId(1L); c.setCandidateId(2L);
        when(conversationMapper.selectById(10L)).thenReturn(c);
        when(fileStorageService.load("message/x/x.pdf")).thenReturn("hello".getBytes());

        AttachmentService.DownloadPayload p = svc.download(1L, "HR", 1L);

        assertEquals("hello", new String(p.bytes()));
        assertEquals("x.pdf", p.fileName());
        assertEquals("application/pdf", p.mime());
    }

    @Test
    void download_attachmentNotFound_throws404() {
        when(attachmentMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> svc.download(1L, "HR", 99L));
    }
}
