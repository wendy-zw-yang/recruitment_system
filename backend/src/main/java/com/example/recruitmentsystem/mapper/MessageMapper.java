package com.example.recruitmentsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruitmentsystem.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 消息 Mapper。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 按会话分页拉取消息（按 created_at DESC 倒序，offset/limit）。
     *
     * <p>返回按时间倒序；调用方如需正序展示请自行 reverse。</p>
     */
    @Select("""
            SELECT id, conversation_id, sender_id, sender_role, job_id, content,
                   read_flag, created_at, updated_at, is_deleted
            FROM message
            WHERE conversation_id = #{conversationId}
              AND is_deleted = 0
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Message> listByConversationDesc(@Param("conversationId") Long conversationId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /**
     * 拉取会话最近 N 条（按 created_at ASC 升序）。
     *
     * <p>用于"进入会话页"时快速加载最新消息（无需分页）。</p>
     */
    @Select("""
            SELECT id, conversation_id, sender_id, sender_role, job_id, content,
                   read_flag, created_at, updated_at, is_deleted
            FROM message
            WHERE conversation_id = #{conversationId}
              AND is_deleted = 0
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<Message> selectRecentByConversation(@Param("conversationId") Long conversationId,
                                             @Param("limit") int limit);

    /**
     * 标记会话中对方发来的消息为已读（read_flag=1）。
     *
     * <p>注意：{@code senderRole} 是"对方"角色；本方法把 {@code !=senderRole} 的全部置 1。</p>
     */
    @Update("""
            UPDATE message
            SET read_flag = 1, updated_at = NOW()
            WHERE conversation_id = #{conversationId}
              AND sender_role <> #{senderRole}
              AND read_flag = 0
              AND is_deleted = 0
            """)
    int markPeerMessagesRead(@Param("conversationId") Long conversationId,
                             @Param("senderRole") String senderRole);

    /**
     * 会话未读数（对方发来的 read_flag=0）。
     */
    @Select("""
            SELECT COUNT(*)
            FROM message
            WHERE conversation_id = #{conversationId}
              AND sender_role <> #{myRole}
              AND read_flag = 0
              AND is_deleted = 0
            """)
    int countUnread(@Param("conversationId") Long conversationId,
                    @Param("myRole") String myRole);

    /**
     * 当前用户所有会话的未读消息总数（用于 HR 首页 stats）。
     */
    @Select("""
            SELECT COUNT(*)
            FROM message m
            JOIN conversation c ON c.id = m.conversation_id AND c.is_deleted = 0
            WHERE m.is_deleted = 0
              AND m.read_flag = 0
              AND m.sender_role <> #{myRole}
              AND ((#{myRole} = 'HR' AND c.hr_user_id = #{userId})
                OR (#{myRole} = 'CANDIDATE' AND c.candidate_id = #{userId}))
            """)
    int countMyUnread(@Param("userId") Long userId,
                      @Param("myRole") String myRole);
}
