package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * HR 内部备注（仅 HR 自己可见，不影响候选人侧）。
 *
 * <p>对应表 {@code application_note}（详见 {@code docs/DataBase/schema.sql}）。</p>
 */
@Data
@TableName("application_note")
public class ApplicationNote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private Long hrUserId;

    /** 备注内容 */
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
