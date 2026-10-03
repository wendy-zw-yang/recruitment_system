package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历附件元数据。一份简历可关联多个附件（多版本追溯）。
 *
 * <p>对应表：{@code resume_attachment}。</p>
 */
@Data
@TableName("resume_attachment")
public class ResumeAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private String fileName;

    /** 相对 {@code UPLOAD_DIR} 的路径 */
    private String filePath;

    private Long fileSize;

    private String mimeType;

    private LocalDateTime uploadedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
