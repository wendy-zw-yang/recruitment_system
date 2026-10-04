package com.example.recruitmentsystem.dto.admin;

import lombok.Data;

/**
 * 管理员用户管理列表查询。详见 {@code docs/系统设计/详细设计/管理员.md §7.2.1}。
 *
 * <p>role / status 均可空（空表示不过滤）。</p>
 */
@Data
public class UserListQuery {

    /** 角色筛选：CANDIDATE / HR / ADMIN */
    private String role;

    /** 状态筛选：ENABLED / DISABLED */
    private String status;

    /** 邮箱模糊匹配 */
    private String keyword;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}