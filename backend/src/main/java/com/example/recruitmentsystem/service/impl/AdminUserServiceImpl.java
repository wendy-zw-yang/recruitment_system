package com.example.recruitmentsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.admin.ChangeRoleRequest;
import com.example.recruitmentsystem.dto.admin.ResetPasswordResponse;
import com.example.recruitmentsystem.dto.admin.UserListItem;
import com.example.recruitmentsystem.dto.admin.UserListQuery;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.AdminUserService;
import com.example.recruitmentsystem.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminUserService 实现。详见 {@code docs/系统设计/详细设计/管理员.md §7.2.1 / §7.3.1}。
 *
 * <p>约束：</p>
 * <ul>
 *   <li>ADMIN 账号不可被 disable / resetPassword / changeRole</li>
 *   <li>禁用自己抛错（避免管理员误锁自己）</li>
 *   <li>所有写操作写 {@code audit_log}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    public IPage<UserListItem> listUsers(UserListQuery query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        if (query.getRole() != null && !query.getRole().isBlank()) {
            q.eq(User::getRoleCode, query.getRole().trim());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            q.eq(User::getStatus, query.getStatus().trim());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            q.like(User::getEmail, query.getKeyword().trim());
        }
        q.orderByDesc(User::getCreatedAt);

        IPage<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<UserListItem> records = page.getRecords().stream()
                .map(this::toItem)
                .collect(Collectors.toList());

        IPage<UserListItem> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional
    public void enable(Long adminId, Long userId) {
        User user = requireUser(userId);
        validateNotAdmin(user);
        if ("ENABLED".equals(user.getStatus())) {
            return; // 幂等
        }
        user.setStatus("ENABLED");
        userMapper.updateById(user);
        auditLogService.record(adminId, "USER_ENABLE", userId, "USER",
                "DISABLED", "ENABLED", null);
    }

    @Override
    @Transactional
    public void disable(Long adminId, Long userId) {
        if (userId.equals(adminId)) {
            throw new BusinessException(400, "不能禁用自己的账号");
        }
        User user = requireUser(userId);
        validateNotAdmin(user);
        if ("DISABLED".equals(user.getStatus())) {
            return; // 幂等
        }
        user.setStatus("DISABLED");
        userMapper.updateById(user);
        auditLogService.record(adminId, "USER_DISABLE", userId, "USER",
                "ENABLED", "DISABLED", null);
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetPassword(Long adminId, Long userId) {
        User user = requireUser(userId);
        validateNotAdmin(user);
        String tempPwd = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPwd));
        userMapper.updateById(user);
        auditLogService.record(adminId, "USER_RESET_PASSWORD", userId, "USER",
                null, null, "管理员重置密码（明文仅此一次返回）");
        log.info("[AdminUserService.resetPassword] adminId={} userId={} 临时密码已生成", adminId, userId);
        return ResetPasswordResponse.builder()
                .userId(userId)
                .tempPassword(tempPwd)
                .build();
    }

    @Override
    @Transactional
    public void changeRole(Long adminId, Long userId, ChangeRoleRequest request) {
        User user = requireUser(userId);
        validateNotAdmin(user);
        String newRole = request.getRoleCode().trim();
        if (user.getRoleCode().equals(newRole)) {
            return; // 幂等
        }
        String oldRole = user.getRoleCode();
        user.setRoleCode(newRole);
        userMapper.updateById(user);
        auditLogService.record(adminId, "USER_ROLE_CHANGE", userId, "USER",
                oldRole, newRole, null);
    }

    // ============ 私有辅助 ============

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "用户 id 不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private void validateNotAdmin(User user) {
        if (ADMIN_ROLE.equals(user.getRoleCode())) {
            throw new BusinessException(400, "管理员账号不可被禁用 / 重置密码 / 改角色");
        }
    }

    private UserListItem toItem(User u) {
        return UserListItem.builder()
                .id(u.getId())
                .email(u.getEmail())
                .username(u.getUsername())
                .roleCode(u.getRoleCode())
                .status(u.getStatus())
                .phone(u.getPhone())
                .createdAt(u.getCreatedAt())
                .build();
    }

    /** 生成 6 位临时密码（数字 + 大小写字母）。 */
    private String generateTempPassword() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}