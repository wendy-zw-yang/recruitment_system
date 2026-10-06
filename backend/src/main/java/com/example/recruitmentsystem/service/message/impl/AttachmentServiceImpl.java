package com.example.recruitmentsystem.service.message.impl;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 消息附件上传 / 下载。
 *
 * <p>两阶段策略：</p>
 * <ul>
 *   <li>上传阶段：仅落盘（{@link FileStorageService} 负责 mime 白名单 + 大小校验），
 *       不写 DB，返回 {@link AttachmentUploadRef} 给前端</li>
 *   <li>绑定阶段：前端把 {@code UploadRef} 通过 {@code sendMessage} 接口提交，
 *       本服务在 {@code MessageServiceImpl.sendMessage} 内部一并写入 message_attachment 表（FK messageId 已存在）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    /** 文件存储子目录（FileStorageService 内置 mime 白名单 pdf/docx/doc/png/jpg/jpeg，20MB 上限） */
    private static final String FILE_TYPE = "message";

    private final MessageAttachmentMapper attachmentMapper;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final FileStorageService fileStorageService;

    @Override
    public AttachmentUploadRef upload(Long uploaderId, String uploaderRole, MultipartFile file) {
        if (!"HR".equals(uploaderRole) && !"CANDIDATE".equals(uploaderRole)) {
            throw new BusinessException(403, "ADMIN 不能上传消息附件");
        }
        String relative = fileStorageService.save(file, FILE_TYPE);
        String mime = fileStorageService.resolveMime(relative);
        log.info("[AttachmentService] upload userId={} file={} ({} bytes)", uploaderId, relative, file.getSize());
        return new AttachmentUploadRef(file.getOriginalFilename(), relative, file.getSize(), mime);
    }

    @Override
    public DownloadPayload download(Long requesterId, String requesterRole, Long attachmentId) {
        MessageAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BusinessException(404, "附件不存在");
        }
        Message msg = messageMapper.selectById(a.getMessageId());
        if (msg == null) {
            throw new BusinessException(404, "附件关联消息不存在");
        }
        Conversation conv = conversationMapper.selectById(msg.getConversationId());
        if (conv == null || !isParticipant(conv, requesterId, requesterRole)) {
            throw new BusinessException(403, "无权限下载该附件");
        }
        byte[] bytes = fileStorageService.load(a.getFilePath());
        return new DownloadPayload(bytes, a.getFileName(), a.getMimeType(), a.getFilePath());
    }

    /**
     * 把 {@link AttachmentUploadRef} 列表入库（service 层复用，public 让 MessageServiceImpl 调用）。
     */
    public void bindToMessage(Long messageId, List<AttachmentUploadRef> refs) {
        if (refs == null || refs.isEmpty()) return;
        for (AttachmentUploadRef r : refs) {
            MessageAttachment a = new MessageAttachment();
            a.setMessageId(messageId);
            a.setFileName(r.getFileName());
            a.setFilePath(r.getFilePath());
            a.setFileSize(r.getFileSize());
            a.setMimeType(r.getMimeType());
            attachmentMapper.insert(a);
        }
    }

    private boolean isParticipant(Conversation conv, Long userId, String role) {
        if ("HR".equals(role)) return userId.equals(conv.getHrUserId());
        if ("CANDIDATE".equals(role)) return userId.equals(conv.getCandidateId());
        return false;
    }
}
