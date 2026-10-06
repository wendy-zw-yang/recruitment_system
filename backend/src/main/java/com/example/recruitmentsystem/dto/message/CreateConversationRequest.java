package com.example.recruitmentsystem.dto.message;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建 / 获取会话请求。{@code peerId} 为对方用户 ID（HR 视角传 candidateId；候选人视角传 hrUserId）。
 *
 * <p>后端按当前用户角色自动决定 (hr, candidate) 二元组方向。</p>
 */
@Data
public class CreateConversationRequest {

    @NotNull(message = "对方用户 ID 不能为空")
    private Long peerId;
}
