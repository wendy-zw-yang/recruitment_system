package com.example.recruitmentsystem.dto.message;

import com.example.recruitmentsystem.entity.MessageAttachment;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息附件 DTO。
 */
@Data
public class MessageAttachmentDto {

    private Long id;

    private Long messageId;

    private String fileName;

    /** 文件字节数 */
    private Long fileSize;

    /** MIME */
    private String mimeType;

    /** 下载 URL（前端可直接拼到 {@code <a href>}） */
    private String downloadUrl;

    private LocalDateTime createdAt;

    public static MessageAttachmentDto from(MessageAttachment a) {
        MessageAttachmentDto dto = new MessageAttachmentDto();
        dto.setId(a.getId());
        dto.setMessageId(a.getMessageId());
        dto.setFileName(a.getFileName());
        dto.setFileSize(a.getFileSize());
        dto.setMimeType(a.getMimeType());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
