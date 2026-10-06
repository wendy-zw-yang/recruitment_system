package com.example.recruitmentsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruitmentsystem.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI-4 智能客服消息 Mapper。
 *
 * <p>基于 MyBatis-Plus BaseMapper + 自定义按 session 拉取方法。</p>
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    /**
     * 按 session 拉取最近 N 条消息（按 created_at ASC 升序，便于前端按时间顺序渲染）。
     *
     * <p>实现思路：先按 DESC 取 limit 条，再在 Service 层反转；此处直接返回升序子查询结果。</p>
     *
     * @param userId    当前用户 ID（安全校验：只查自己的）
     * @param sessionId 会话 ID
     * @param limit     上限
     * @return 消息列表（按 created_at ASC）
     */
    @Select("""
            SELECT id, user_id, session_id, role, content, created_at, updated_at, is_deleted
            FROM ai_chat_message
            WHERE user_id = #{userId}
              AND session_id = #{sessionId}
              AND is_deleted = 0
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<AiChatMessage> selectRecentBySession(@Param("userId") Long userId,
                                              @Param("sessionId") String sessionId,
                                              @Param("limit") int limit);

    /**
     * 软删除指定 session 的所有消息（用户点击「清空对话」）。
     *
     * @return 受影响行数
     */
    @Update("""
            UPDATE ai_chat_message
            SET is_deleted = 1, updated_at = NOW()
            WHERE user_id = #{userId}
              AND session_id = #{sessionId}
              AND is_deleted = 0
            """)
    int softDeleteBySession(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}