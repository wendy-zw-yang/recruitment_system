package com.example.recruitmentsystem.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传后的临时附件引用（不写 DB，由 {@link com.example.recruitmentsystem.dto.message.SendMessageRequest}
 * 携带，发消息时一并入库 + 绑定 messageId）。
 *
 * <p>为何不直接写库：{@code message_attachment.message_id} 有 FK → message.id，
 * 孤儿 attachment 无法存在。所以采取"先落盘、再随消息入库"两阶段策略。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentUploadRef {

    /** 原始文件名 */
    private String fileName;

    /** 相对 {@code app.upload.dir} 的路径 */
    private String filePath;

    /** 文件字节数 */
    private Long fileSize;

    /** MIME 类型 */
    private String mimeType;
}
