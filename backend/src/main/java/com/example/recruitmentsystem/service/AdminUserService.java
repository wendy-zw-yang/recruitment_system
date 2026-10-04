package com.example.recruitmentsystem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.dto.admin.ChangeRoleRequest;
import com.example.recruitmentsystem.dto.admin.ResetPasswordResponse;
import com.example.recruitmentsystem.dto.admin.UserListItem;
import com.example.recruitmentsystem.dto.admin.UserListQuery;

/**
 * 管理员用户管理服务。覆盖 UC-34 + 部分 UC-36（公司审核驳回时禁用 HR 账号）。
 *
 * <p>所有写操作通过 {@link AuditLogService} 记录。</p>
 */
public interface AdminUserService {

    /** 分页列出用户（角色 + 状态 + 邮箱模糊）。 */
    IPage<UserListItem> listUsers(UserListQuery query);

    /** 启用账号。 */
    void enable(Long adminId, Long userId);

    /** 禁用账号。 */
    void disable(Long adminId, Long userId);

    /** 重置密码，返回临时明文密码（仅此一次）。 */
    ResetPasswordResponse resetPassword(Long adminId, Long userId);

    /** 角色切换：仅 CANDIDATE ↔ HR，ADMIN 不可改。 */
    void changeRole(Long adminId, Long userId, ChangeRoleRequest request);
}