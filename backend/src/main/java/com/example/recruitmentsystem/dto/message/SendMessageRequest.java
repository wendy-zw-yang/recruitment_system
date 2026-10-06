package com.example.recruitmentsystem.dto.message;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发送消息请求。
 *
 * <p>{@code content} 与 {@code attachments} 至少有一项非空（Service 校验）。</p>
 *
 * <p>{@code attachments} 中的每一项是上传接口返回的临时引用（包含 filePath），
 * 发送时一并入库 + 绑定 messageId。</p>
 */
@Data
public class SendMessageRequest {

    @NotNull(message = "会话 ID 不能为空")
    private Long conversationId;

    @Size(max = 2000, message = "消息内容不能超过 2000 字符")
    private String content;

    /** 可选：发送的消息关联职位（用于会话内显示"关于 职位X"） */
    private Long jobId;

    /** 可选：上传后获得的附件引用（顺序无关） */
    private List<AttachmentUploadRef> attachments;
}
