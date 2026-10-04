package com.example.recruitmentsystem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.admin.ChangeRoleRequest;
import com.example.recruitmentsystem.dto.admin.ResetPasswordResponse;
import com.example.recruitmentsystem.dto.admin.UserListItem;
import com.example.recruitmentsystem.dto.admin.UserListQuery;
import com.example.recruitmentsystem.entity.AuditLog;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.AuditLogMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.AuditLogService;
import com.example.recruitmentsystem.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §9 UC-34 用户管理单元测试。
 */
@SpringBootTest
class AdminUserServiceTest {

    @Autowired private UserMapper userMapper;
    @Autowired private AuditLogMapper auditLogMapper;
    @Autowired private AuditLogService auditLogService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private Long adminId;

    @BeforeEach
    void setupAdmin() {
        User admin = new User();
        admin.setEmail("admin-" + System.nanoTime() + "@test.local");
        admin.setPasswordHash("x");
        admin.setRoleCode("ADMIN");
        admin.setStatus("ENABLED");
        admin.setUsername("admin");
        userMapper.insert(admin);
        adminId = admin.getId();
    }

    private AdminUserServiceImpl service() {
        return new AdminUserServiceImpl(userMapper, passwordEncoder, auditLogService);
    }

    private Long createUser(String role) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setRoleCode(role);
        u.setStatus("ENABLED");
        u.setUsername(role.toLowerCase());
        userMapper.insert(u);
        return u.getId();
    }

    // ======================== enable / disable ========================

    @Test
    @Transactional
    void disable_changesStatusAndWritesAuditLog() {
        Long uid = createUser("CANDIDATE");

        service().disable(adminId, uid);

        User after = userMapper.selectById(uid);
        assertEquals("DISABLED", after.getStatus());

        AuditLog log = latestLog("USER_DISABLE", uid);
        assertNotNull(log);
        assertEquals(adminId, log.getAdminId());
        assertEquals("USER", log.getTargetType());
    }

    @Test
    @Transactional
    void enable_restoresStatusAndWritesAuditLog() {
        Long uid = createUser("CANDIDATE");
        service().disable(adminId, uid);

        service().enable(adminId, uid);

        User after = userMapper.selectById(uid);
        assertEquals("ENABLED", after.getStatus());
        assertNotNull(latestLog("USER_ENABLE", uid));
    }

    @Test
    @Transactional
    void disable_selfAccount_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().disable(adminId, adminId));
        assertTrue(ex.getMessage().contains("不能禁用自己的账号"));
    }

    @Test
    @Transactional
    void disable_adminAccount_throws() {
        // adminId 是 ADMIN，再创建另一个 ADMIN 账号，让 adminId 来禁它
        Long anotherAdminId = createUser("ADMIN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().disable(adminId, anotherAdminId));
        assertTrue(ex.getMessage().contains("管理员账号不可被禁用"));
    }

    @Test
    @Transactional
    void enable_nonexistentUser_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().enable(adminId, 999999999L));
        assertTrue(ex.getMessage().contains("用户不存在"));
    }

    // ======================== resetPassword ========================

    @Test
    @Transactional
    void resetPassword_returnsTempPasswordAndWritesAuditLog() {
        Long uid = createUser("CANDIDATE");
        // 原始 hash 是 BCrypt("x")，但因 MyBatis 缓存，selectById 返回的是同一实体，
        // 不能用它做"前后对比"。改用"新密码能用 BCrypt 校验"作为不可逆修改的判断。

        ResetPasswordResponse resp = service().resetPassword(adminId, uid);

        assertNotNull(resp);
        assertEquals(uid, resp.getUserId());
        assertNotNull(resp.getTempPassword());
        assertEquals(6, resp.getTempPassword().length());

        User reloaded = userMapper.selectById(uid);
        // 新 hash 应该是 BCrypt(tempPassword) 的合法形式（$2a$ 开头 + 60 字符左右）
        assertTrue(reloaded.getPasswordHash().startsWith("$2a$"));
        // 新密码可登录（BCrypt 验证）
        assertTrue(passwordEncoder.matches(resp.getTempPassword(), reloaded.getPasswordHash()));
        // 旧密码 "x" 已失效
        assertTrue(!passwordEncoder.matches("x", reloaded.getPasswordHash()));

        assertNotNull(latestLog("USER_RESET_PASSWORD", uid));
    }

    @Test
    @Transactional
    void resetPassword_adminAccount_throws() {
        Long anotherAdminId = createUser("ADMIN");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().resetPassword(adminId, anotherAdminId));
        assertTrue(ex.getMessage().contains("管理员账号"));
    }

    // ======================== changeRole ========================

    @Test
    @Transactional
    void changeRole_candidateToHr_succeedsAndWritesAuditLog() {
        Long uid = createUser("CANDIDATE");
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRoleCode("HR");

        service().changeRole(adminId, uid, req);

        User after = userMapper.selectById(uid);
        assertEquals("HR", after.getRoleCode());

        AuditLog log = latestLog("USER_ROLE_CHANGE", uid);
        assertNotNull(log);
        // toJson 把字符串序列化为 "CANDIDATE"（含引号），断言包含即可
        assertTrue(log.getBeforeValue() != null && log.getBeforeValue().contains("CANDIDATE"));
        assertTrue(log.getAfterValue() != null && log.getAfterValue().contains("HR"));
    }

    @Test
    @Transactional
    void changeRole_adminAccount_throws() {
        Long anotherAdminId = createUser("ADMIN");
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRoleCode("CANDIDATE");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().changeRole(adminId, anotherAdminId, req));
        assertTrue(ex.getMessage().contains("管理员账号"));
    }

    // ======================== listUsers ========================

    @Test
    @Transactional
    void listUsers_filtersByRoleAndStatus() {
        Long cId = createUser("CANDIDATE");
        Long hId = createUser("HR");
        // disable 一个 candidate
        User admin = userMapper.selectById(cId);
        admin.setStatus("DISABLED");
        userMapper.updateById(admin);

        UserListQuery q = new UserListQuery();
        q.setRole("CANDIDATE");
        IPage<UserListItem> page = service().listUsers(q);
        // 至少包含 disabled 的那个
        assertTrue(page.getRecords().stream().anyMatch(r -> r.getId().equals(cId)));
        // 不应包含 HR
        assertTrue(page.getRecords().stream().noneMatch(r -> r.getId().equals(hId)));
    }

    @Test
    @Transactional
    void listUsers_paginates() {
        for (int i = 0; i < 5; i++) createUser("CANDIDATE");

        UserListQuery q = new UserListQuery();
        q.setPageNum(1);
        q.setPageSize(3);
        IPage<UserListItem> page = service().listUsers(q);
        assertEquals(3, page.getRecords().size());
        assertTrue(page.getTotal() >= 5);
    }

    @Test
    @Transactional
    void listUsers_keywordMatchesEmail() {
        String marker = "marker-" + System.nanoTime();
        User u = new User();
        u.setEmail(marker + "@test.local");
        u.setPasswordHash("x");
        u.setRoleCode("CANDIDATE");
        u.setStatus("ENABLED");
        u.setUsername("u");
        userMapper.insert(u);

        UserListQuery q = new UserListQuery();
        q.setKeyword(marker);
        IPage<UserListItem> page = service().listUsers(q);
        assertTrue(page.getRecords().stream().anyMatch(r -> r.getEmail().startsWith(marker)));
    }

    // ======================== 辅助 ========================

    private AuditLog latestLog(String actionType, Long targetId) {
        return auditLogMapper.selectOne(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getActionType, actionType)
                .eq(AuditLog::getTargetId, targetId)
                .orderByDesc(AuditLog::getCreatedAt)
                .last("limit 1"));
    }
}