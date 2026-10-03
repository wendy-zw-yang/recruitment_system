package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（求职者 / HR / 管理员）。
 *
 * <p>对应表：{@code users}（详见 {@code docs/DataBase/schema.sql}）。</p>
 *
 * <p>软删由 {@code @TableLogic} + {@code is_deleted} 字段自动处理（详见 {@code docs/技术约束.md §5}）。</p>
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录邮箱（唯一） */
    private String email;

    /** BCrypt 哈希后的密码 */
    private String passwordHash;

    /** 角色：CANDIDATE / HR / ADMIN */
    private String roleCode;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 昵称 / 显示名（可选，存 user 表用于资料展示） */
    private String username;

    /** 手机号 */
    private String phone;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
