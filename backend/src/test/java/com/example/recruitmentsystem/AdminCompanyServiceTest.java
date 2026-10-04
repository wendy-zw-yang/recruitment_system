package com.example.recruitmentsystem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.admin.CompanyAuditItem;
import com.example.recruitmentsystem.dto.admin.CompanyRejectRequest;
import com.example.recruitmentsystem.entity.AuditLog;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.AuditLogMapper;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.AdminCompanyService;
import com.example.recruitmentsystem.service.AuditLogService;
import com.example.recruitmentsystem.service.impl.AdminCompanyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §9 UC-36 公司审核单元测试。
 */
@SpringBootTest
class AdminCompanyServiceTest {

    @Autowired private UserMapper userMapper;
    @Autowired private CompanyMapper companyMapper;
    @Autowired private AuditLogMapper auditLogMapper;
    @Autowired private AuditLogService auditLogService;

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

    private AdminCompanyService service() {
        return new AdminCompanyServiceImpl(companyMapper, userMapper, null, auditLogService);
    }

    private Long createHrWithCompany(String authStatus) {
        User hr = new User();
        hr.setEmail("hr-" + System.nanoTime() + "@test.local");
        hr.setPasswordHash("x");
        hr.setRoleCode("HR");
        hr.setStatus("ENABLED");
        hr.setUsername("hr");
        userMapper.insert(hr);

        Company c = new Company();
        c.setHrUserId(hr.getId());
        c.setName("测试公司-" + System.nanoTime());
        c.setScale("100-500");
        c.setDescription("desc");
        c.setAuthStatus(authStatus);
        companyMapper.insert(c);
        return c.getId();
    }

    // ============ list ============

    @Test
    @Transactional
    void listCompanies_defaultPending() {
        Long pendingId = createHrWithCompany("PENDING");
        Long verifiedId = createHrWithCompany("VERIFIED");

        IPage<CompanyAuditItem> page = service().listCompanies("PENDING", 1, 10);
        assertTrue(page.getRecords().stream().anyMatch(r -> r.getId().equals(pendingId)));
        assertTrue(page.getRecords().stream().noneMatch(r -> r.getId().equals(verifiedId)));
    }

    @Test
    @Transactional
    void listCompanies_allWhenAuthStatusBlank() {
        Long pendingId = createHrWithCompany("PENDING");
        Long verifiedId = createHrWithCompany("VERIFIED");

        IPage<CompanyAuditItem> page = service().listCompanies(null, 1, 50);
        assertTrue(page.getRecords().stream().anyMatch(r -> r.getId().equals(pendingId)));
        assertTrue(page.getRecords().stream().anyMatch(r -> r.getId().equals(verifiedId)));
    }

    // ============ verify ============

    @Test
    @Transactional
    void verify_changesStatusAndWritesAuditLog() {
        Long companyId = createHrWithCompany("PENDING");

        service().verify(adminId, companyId);

        Company after = companyMapper.selectById(companyId);
        assertEquals("VERIFIED", after.getAuthStatus());
        assertNotNull(after.getVerifiedAt());
        assertEquals(adminId, after.getVerifiedBy());

        assertNotNull(latestLog("COMPANY_VERIFY", companyId));
    }

    @Test
    @Transactional
    void verify_nonPendingState_throws() {
        Long companyId = createHrWithCompany("VERIFIED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().verify(adminId, companyId));
        assertTrue(ex.getMessage().contains("不处于待审核"));
    }

    // ============ reject ============

    @Test
    @Transactional
    void reject_changesStatusAndRequiresNote() {
        Long companyId = createHrWithCompany("PENDING");

        CompanyRejectRequest req = new CompanyRejectRequest();
        req.setNote("资质不全");

        service().reject(adminId, companyId, req);

        Company after = companyMapper.selectById(companyId);
        assertEquals("REJECTED", after.getAuthStatus());
        assertEquals("资质不全", after.getAuthNote());

        assertNotNull(latestLog("COMPANY_REJECT", companyId));
    }

    @Test
    @Transactional
    void reject_missingNote_throws() {
        Long companyId = createHrWithCompany("PENDING");

        CompanyRejectRequest req = new CompanyRejectRequest();
        // note is null

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().reject(adminId, companyId, req));
        assertTrue(ex.getMessage().contains("请填写驳回理由"));
    }

    @Test
    @Transactional
    void reject_disableHr_disablesHrAccount() {
        Long companyId = createHrWithCompany("PENDING");
        Company before = companyMapper.selectById(companyId);
        Long hrId = before.getHrUserId();

        CompanyRejectRequest req = new CompanyRejectRequest();
        req.setNote("虚假信息");
        req.setDisableHr(true);

        service().reject(adminId, companyId, req);

        // HR 状态变 DISABLED
        User hr = userMapper.selectById(hrId);
        assertEquals("DISABLED", hr.getStatus());

        // 写了 COMPANY_REJECT + USER_DISABLE 两条 audit_log
        assertNotNull(latestLog("COMPANY_REJECT", companyId));
        assertNotNull(latestLog("USER_DISABLE", hrId));
    }

    @Test
    @Transactional
    void reject_disableHrFalse_doesNotDisableHr() {
        Long companyId = createHrWithCompany("PENDING");
        Company before = companyMapper.selectById(companyId);
        Long hrId = before.getHrUserId();

        CompanyRejectRequest req = new CompanyRejectRequest();
        req.setNote("材料不清晰");
        req.setDisableHr(false);

        service().reject(adminId, companyId, req);

        User hr = userMapper.selectById(hrId);
        assertEquals("ENABLED", hr.getStatus());
    }

    // ============ 私有辅助 ============

    private AuditLog latestLog(String actionType, Long targetId) {
        return auditLogMapper.selectOne(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getActionType, actionType)
                .eq(AuditLog::getTargetId, targetId)
                .orderByDesc(AuditLog::getCreatedAt)
                .last("limit 1"));
    }
}