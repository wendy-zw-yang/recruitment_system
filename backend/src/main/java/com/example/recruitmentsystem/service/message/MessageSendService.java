package com.example.recruitmentsystem.service.message;

import com.example.recruitmentsystem.dto.message.MessageDto;
import com.example.recruitmentsystem.dto.message.SendMessageRequest;

import java.util.List;

/**
 * §5 消息中心 - 收发消息 Service 接口。
 */
public interface MessageSendService {

    /**
     * 发送一条消息（用户主动发的 CANDIDATE/HR 消息）。
     *
     * @return 写入的消息 DTO（已含 ID、附件列表）
     */
    MessageDto sendMessage(Long senderId, String senderRole, SendMessageRequest request);

    /**
     * 批量加载消息的附件（一次 SQL 查全部）。
     */
    List<MessageDto> enrichWithAttachments(List<MessageDto> dtos);
}
