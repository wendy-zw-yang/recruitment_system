package com.example.recruitmentsystem.service.message;

import com.example.recruitmentsystem.dto.message.AttachmentUploadRef;
import org.springframework.web.multipart.MultipartFile;

/**
 * §5 消息附件 Service 接口。
 */
public interface AttachmentService {

    /**
     * 上传一个附件（仅落盘 + 解析 mime，不入库；由 sendMessage 一并绑定 messageId）。
     *
     * @param uploaderId 当前用户 ID（HR 或 CANDIDATE）
     * @param uploaderRole 当前用户角色
     * @param file 上传文件
     * @return 临时附件引用（含 filePath），供 sendMessage 携带
     */
    AttachmentUploadRef upload(Long uploaderId, String uploaderRole, MultipartFile file);

    /**
     * 加载附件字节 + 文件名 + MIME（给 Controller 包装 ResponseEntity）。
     *
     * <p>鉴权：仅消息会话参与者可下载。</p>
     *
     * @return {@code [bytes, fileName, mime]} 三元组
     */
    DownloadPayload download(Long requesterId, String requesterRole, Long attachmentId);

    /** 下载字节 + 元数据 */
    record DownloadPayload(byte[] bytes, String fileName, String mime, String relativePath) {}
}
