package com.example.recruitmentsystem.controller.message;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.LoginRequired;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.message.MessageDto;
import com.example.recruitmentsystem.dto.message.MessageListQuery;
import com.example.recruitmentsystem.dto.message.MessageListResponse;
import com.example.recruitmentsystem.dto.message.SendMessageRequest;
import com.example.recruitmentsystem.service.message.ConversationService;
import com.example.recruitmentsystem.service.message.MessageSendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * §5 消息中心 - 消息收发 Controller。
 */
@LoginRequired
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageSendService messageSendService;
    private final ConversationService conversationService;

    /** 发消息（CANDIDATE / HR）。SYSTEM 角色走 {@code sendSystemMessage}（Service 内部） */
    @PostMapping
    public Result<MessageDto> send(@Valid @RequestBody SendMessageRequest request) {
        return Result.success(messageSendService.sendMessage(
                CurrentUserContext.getUserId(), CurrentUserContext.getRole(), request));
    }

    /** 按会话分页拉消息（按 created_at DESC） */
    @PostMapping("/conversation/{id}/list")
    public Result<MessageListResponse> listByConversation(@PathVariable Long id,
                                                          @Valid @RequestBody MessageListQuery query) {
        return Result.success(conversationService.listMessages(
                CurrentUserContext.getUserId(), CurrentUserContext.getRole(), id,
                query.getPageNum(), query.getPageSize()));
    }
}
