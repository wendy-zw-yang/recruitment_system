package com.example.recruitmentsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruitmentsystem.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话 Mapper。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 按 (hrUserId, candidateId) 查有效会话。
     *
     * <p>依赖表上唯一键 {@code uk_conversation_hr_candidate (hr_user_id, candidate_id, is_deleted)}，
     * 最多返回 1 行。</p>
     */
    @Select("""
            SELECT id, hr_user_id, candidate_id, last_message_at,
                   created_at, updated_at, is_deleted
            FROM conversation
            WHERE hr_user_id = #{hrUserId}
              AND candidate_id = #{candidateId}
              AND is_deleted = 0
            LIMIT 1
            """)
    Conversation findByHrAndCandidate(@Param("hrUserId") Long hrUserId,
                                      @Param("candidateId") Long candidateId);

    /**
     * 我的会话列表（当前用户参与的所有会话，按 last_message_at 倒序；NULL 排最后）。
     *
     * <p>调用方根据 role 决定查 hr_user_id 还是 candidate_id。</p>
     */
    @Select("""
            SELECT id, hr_user_id, candidate_id, last_message_at,
                   created_at, updated_at, is_deleted
            FROM conversation
            WHERE (hr_user_id = #{userId} OR candidate_id = #{userId})
              AND is_deleted = 0
            ORDER BY last_message_at IS NULL, last_message_at DESC, id DESC
            """)
    List<Conversation> listMine(@Param("userId") Long userId);
}
