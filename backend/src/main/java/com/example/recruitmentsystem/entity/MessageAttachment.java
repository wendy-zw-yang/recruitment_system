package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息附件。
 *
 * <p>对应表 {@code message_attachment}（详见 {@code docs/DataBase/schema.sql §17}）。</p>
 */
@Data
@TableName("message_attachment")
public class MessageAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private String fileName;

    /** 相对 {@code app.upload.dir} 的存储路径 */
    private String filePath;

    private Long fileSize;

    /** MIME 类型 */
    private String mimeType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
