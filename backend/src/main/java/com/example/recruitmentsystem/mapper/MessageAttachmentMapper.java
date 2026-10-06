package com.example.recruitmentsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruitmentsystem.entity.MessageAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 消息附件 Mapper。
 */
@Mapper
public interface MessageAttachmentMapper extends BaseMapper<MessageAttachment> {

    /**
     * 按 messageId 查附件列表。
     */
    @Select("""
            SELECT id, message_id, file_name, file_path, file_size, mime_type,
                   created_at, updated_at, is_deleted
            FROM message_attachment
            WHERE message_id = #{messageId}
              AND is_deleted = 0
            """)
    List<MessageAttachment> selectByMessageId(@Param("messageId") Long messageId);

    /**
     * 按 messageId 列表批量查附件（用于消息流渲染）。
     */
    @Select("""
            <script>
            SELECT id, message_id, file_name, file_path, file_size, mime_type,
                   created_at, updated_at, is_deleted
            FROM message_attachment
            WHERE is_deleted = 0
              AND message_id IN
              <foreach collection="messageIds" item="mid" open="(" close=")" separator=",">
                  #{mid}
              </foreach>
            </script>
            """)
    List<MessageAttachment> selectByMessageIds(@Param("messageIds") List<Long> messageIds);
}
