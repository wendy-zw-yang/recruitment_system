package com.example.recruitmentsystem.controller.message;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.LoginRequired;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.message.ConversationDetailResponse;
import com.example.recruitmentsystem.dto.message.ConversationDto;
import com.example.recruitmentsystem.dto.message.CreateConversationRequest;
import com.example.recruitmentsystem.dto.message.UnreadStatsResponse;
import com.example.recruitmentsystem.service.message.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * §5 消息中心 - 会话 Controller（覆盖 UC-16 / UC-17 / UC-31 / UC-32）。
 *
 * <p>API 路径与设计文档一致：{@code /api/conversations/*}。</p>
 */
@LoginRequired
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 创建或获取会话（按当前用户角色 + 对方 ID 决定二元组方向）。
     */
    @PostMapping
    public Result<ConversationDto> createOrGet(@Valid @RequestBody CreateConversationRequest request) {
        return Result.success(conversationService.findOrCreate(
                CurrentUserContext.getUserId(), CurrentUserContext.getRole(), request.getPeerId()));
    }

    /**
     * 我的会话列表（按 last_message_at DESC）。
     */
    @GetMapping("/mine")
    public Result<List<ConversationDto>> listMine() {
        return Result.success(conversationService.listMine(
                CurrentUserContext.getUserId(), CurrentUserContext.getRole()));
    }

    /**
     * 进入会话详情：返回会话 + 消息流 + 自动已读。
     */
    @GetMapping("/{id}")
    public Result<ConversationDetailResponse> open(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "50") int recentLimit) {
        return Result.success(conversationService.open(
                CurrentUserContext.getUserId(), CurrentUserContext.getRole(), id, recentLimit));
    }

    /**
     * 未读消息总数（首页 stats 用）。
     */
    @GetMapping("/unread-stats")
    public Result<UnreadStatsResponse> unreadStats() {
        return Result.success(conversationService.unreadStats(
                CurrentUserContext.getUserId(), CurrentUserContext.getRole()));
    }
}
