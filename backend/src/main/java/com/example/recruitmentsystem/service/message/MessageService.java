package com.example.recruitmentsystem.service.message;

/**
 * §5 消息中心 Service 接口。
 */
public interface MessageService {

    /**
     * §4 投递撤回触发：给 HR 发一条系统通知消息。
     *
     * <p>由 {@link com.example.recruitmentsystem.service.impl.ApplicationServiceImpl#withdraw}
     * 调用；本方法内部 findOrCreate 会话 + 写 SYSTEM 消息 + 更新 last_message_at + SSE 推送 HR。
     * 与 {@link #sendMessage} 不同点：senderId=0、senderRole=SYSTEM、pushToUser 用 hrUserId。</p>
     *
     * @param hrUserId    HR 用户 ID
     * @param candidateId 候选人用户 ID
     * @param content     消息内容（已渲染好的人话文本，如 "候选人 X 已撤回对职位 Y 的投递"）
     * @param jobId       关联职位 ID（可空，但通常非空）
     * @return 写入的消息 ID
     */
    Long sendSystemMessage(Long hrUserId, Long candidateId, String content, Long jobId);
}
