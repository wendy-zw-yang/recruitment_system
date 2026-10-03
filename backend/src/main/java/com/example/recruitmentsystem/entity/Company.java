package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公司实体。HR 注册时同步创建（{@code auth_status = PENDING}），由管理员事后审核。
 *
 * <p>对应表：{@code company}（详见 {@code docs/DataBase/schema.sql}）。</p>
 */
@Data
@TableName("company")
public class Company {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** HR user.id（与 HR 1:1 关联） */
    private Long hrUserId;

    private String name;

    /** 行业（指向 {@code dict_industry.id}，可选） */
    private Long industryId;

    private String scale;

    private String description;

    /** PENDING / VERIFIED / REJECTED */
    private String authStatus;

    private String authNote;

    private LocalDateTime verifiedAt;

    /** 审核管理员 user.id */
    private Long verifiedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
