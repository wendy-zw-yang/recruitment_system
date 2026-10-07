package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.profile.CandidatePreferenceDto;
import com.example.recruitmentsystem.dto.profile.ChangePasswordRequest;
import com.example.recruitmentsystem.dto.profile.CompanySelfDto;
import com.example.recruitmentsystem.dto.profile.ProfileDto;
import com.example.recruitmentsystem.dto.profile.UpdateProfileRequest;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.DictCityMapper;
import com.example.recruitmentsystem.mapper.DictIndustryMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.profile.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProfileService 单元测试。
 *
 * <p>覆盖 Profile.vue 4 个保存按钮对应的后端方法：
 *  - 修改个人资料（昵称 / 手机）
 *  - 修改密码
 *  - 更新求职偏好（候选人）
 *  - 更新公司信息（HR）</p>
 */
@SpringBootTest
class ProfileServiceTest {

    @Autowired private ProfileService profileService;
    @Autowired private UserMapper userMapper;
    @Autowired private CompanyMapper companyMapper;
    @Autowired private DictIndustryMapper industryMapper;
    @Autowired private DictCityMapper cityMapper;

    private Long createUser(String email, String role, String password) {
        User u = new User();
        u.setEmail(email + "-" + System.nanoTime() + "@test.local");
        u.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
        u.setRoleCode(role);
        u.setStatus("ENABLED");
        u.setUsername("default");
        userMapper.insert(u);
        return u.getId();
    }

    private Long createCompany(Long hrUserId, String name) {
        Company c = new Company();
        c.setHrUserId(hrUserId);
        c.setName(name);
        c.setAuthStatus("PENDING");
        companyMapper.insert(c);
        return c.getId();
    }

    // ============ 个人资料 ============

    @Test
    @Transactional
    void getProfile_returnsUserFields() {
        Long userId = createUser("p1", "CANDIDATE", "Password123");
        ProfileDto dto = profileService.getProfile(userId);
        assertNotNull(dto);
        assertEquals(userId, dto.getId());
        assertEquals("CANDIDATE", dto.getRoleCode());
    }

    @Test
    @Transactional
    void updateProfile_changesUsernameAndPhone() {
        Long userId = createUser("p2", "CANDIDATE", "Password123");
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("新昵称");
        req.setPhone("13800138000");
        ProfileDto updated = profileService.updateProfile(userId, req);
        assertEquals("新昵称", updated.getUsername());
        assertEquals("13800138000", updated.getPhone());
        // 校验 DB 也已写入
        User u = userMapper.selectById(userId);
        assertEquals("新昵称", u.getUsername());
        assertEquals("13800138000", u.getPhone());
    }

    @Test
    @Transactional
    void updateProfile_userNotExists_throws404() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("x");
        BusinessException e = assertThrows(BusinessException.class,
                () -> profileService.updateProfile(99999999L, req));
        assertEquals(404, e.getCode());
    }

    // ============ 修改密码 ============

    @Test
    @Transactional
    void changePassword_correctOld_succeeds() {
        Long userId = createUser("p3", "CANDIDATE", "OldPassword1");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("OldPassword1");
        req.setNewPassword("NewPassword2");
        profileService.changePassword(userId, req);

        // 校验 DB 中 hash 已变化
        User u = userMapper.selectById(userId);
        assertNotEquals(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("OldPassword1"),
                u.getPasswordHash());
        // 新密码可登录验证（用 BCrypt.matches）
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("NewPassword2", u.getPasswordHash()));
    }

    @Test
    @Transactional
    void changePassword_wrongOld_throws400() {
        Long userId = createUser("p4", "CANDIDATE", "OldPassword1");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("WrongOld");
        req.setNewPassword("NewPassword2");
        BusinessException e = assertThrows(BusinessException.class,
                () -> profileService.changePassword(userId, req));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("当前密码"));
    }

    @Test
    @Transactional
    void changePassword_sameAsOld_throws400() {
        Long userId = createUser("p5", "CANDIDATE", "SamePassword1");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("SamePassword1");
        req.setNewPassword("SamePassword1");
        BusinessException e = assertThrows(BusinessException.class,
                () -> profileService.changePassword(userId, req));
        assertEquals(400, e.getCode());
    }

    // ============ 候选人求职偏好 ============

    @Test
    @Transactional
    void preference_createWhenMissing_persists() {
        Long candidateId = createUser("p6", "CANDIDATE", "Password123");
        CandidatePreferenceDto dto = new CandidatePreferenceDto();
        dto.setExpectedPosition("Java 工程师");
        CandidatePreferenceDto result = profileService.updatePreference(candidateId, dto);
        assertEquals("Java 工程师", result.getExpectedPosition());
        // 再读一遍确认持久化
        assertEquals("Java 工程师", profileService.getPreference(candidateId).getExpectedPosition());
    }

    @Test
    @Transactional
    void preference_updateExisting_overwrites() {
        Long candidateId = createUser("p7", "CANDIDATE", "Password123");
        CandidatePreferenceDto first = new CandidatePreferenceDto();
        first.setExpectedPosition("Java");
        profileService.updatePreference(candidateId, first);

        CandidatePreferenceDto second = new CandidatePreferenceDto();
        second.setExpectedPosition("高级 Java 工程师");
        profileService.updatePreference(candidateId, second);

        assertEquals("高级 Java 工程师", profileService.getPreference(candidateId).getExpectedPosition());
    }

    // ============ v0.7.3.2 回归：清空偏好必须真正清空 DB ============
    //
    // Bug 现象：候选人保存偏好后改回顶部 navbar 清空（el-select 触发 null），
    // 后端因 if (req.getXxx != null) 守卫跳过写入，DB 仍存旧值；
    // 下次首页 SQL 推荐仍按旧偏好筛选 + 偏好设置页又自动填上旧值。
    //
    // 修复：去掉 null 守卫，null/blank 视为清空。

    @Test
    @Transactional
    void preference_clearAllFieldsWithNull_shouldPersistAsNull() {
        Long candidateId = createUser("p8-clear", "CANDIDATE", "Password123");
        // 取 seed 字典中真实存在的 id（避免 FK 约束失败）
        Long realIndustryId = industryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.recruitmentsystem.entity.DictIndustry>()
                        .orderByAsc(com.example.recruitmentsystem.entity.DictIndustry::getSortOrder))
                .get(0).getId();
        Long realCityId = cityMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.recruitmentsystem.entity.DictCity>()
                        .orderByAsc(com.example.recruitmentsystem.entity.DictCity::getSortOrder))
                .get(0).getId();

        // 1. 先保存完整偏好（4 字段全填）
        CandidatePreferenceDto saved = new CandidatePreferenceDto();
        saved.setExpectedPosition("Java 工程师");
        saved.setExpectedIndustryId(realIndustryId);
        saved.setExpectedProvince("浙江");
        saved.setExpectedCityId(realCityId);
        profileService.updatePreference(candidateId, saved);
        assertEquals("Java 工程师", profileService.getPreference(candidateId).getExpectedPosition());

        // 2. 清空（前端 el-select clearable 触发 null）
        CandidatePreferenceDto cleared = new CandidatePreferenceDto();
        // 4 字段全部 null / blank
        profileService.updatePreference(candidateId, cleared);

        // 3. 重新读取必须为空
        CandidatePreferenceDto read = profileService.getPreference(candidateId);
        assertTrue(read.getExpectedPosition() == null || read.getExpectedPosition().isEmpty(),
                "期望职位应被清空，但 DB 仍存 " + read.getExpectedPosition());
        // v0.7.3.2 关键：id 字段必须被清空（之前 bug 是 id 字段保留旧值）
        assertEquals(null, read.getExpectedIndustryId(), "行业 id 未清空（v0.7.3.2 bug）");
        assertEquals(null, read.getExpectedProvince(), "省份未清空");
        assertEquals(null, read.getExpectedCityId(), "城市 id 未清空（v0.7.3.2 bug）");
    }

    @Test
    @Transactional
    void preference_clearTextFieldWithBlankString_shouldPersistAsNull() {
        Long candidateId = createUser("p9-blank", "CANDIDATE", "Password123");
        CandidatePreferenceDto saved = new CandidatePreferenceDto();
        saved.setExpectedPosition("Java 工程师");
        profileService.updatePreference(candidateId, saved);

        // 发送空白字符串
        CandidatePreferenceDto cleared = new CandidatePreferenceDto();
        cleared.setExpectedPosition("   ");  // 全空白
        cleared.setExpectedIndustryId(null);
        cleared.setExpectedProvince("");
        cleared.setExpectedCityId(null);
        profileService.updatePreference(candidateId, cleared);

        CandidatePreferenceDto read = profileService.getPreference(candidateId);
        assertTrue(read.getExpectedPosition() == null || read.getExpectedPosition().isEmpty(),
                "空白字符串应被识别为清空");
        assertEquals(null, read.getExpectedProvince());
    }

    @Test
    @Transactional
    void preference_partialUpdate_overwritesOnlyGivenFields_clearsNull() {
        Long candidateId = createUser("p10-partial", "CANDIDATE", "Password123");
        Long realIndustryId = industryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.recruitmentsystem.entity.DictIndustry>()
                        .orderByAsc(com.example.recruitmentsystem.entity.DictIndustry::getSortOrder))
                .get(0).getId();
        Long realCityId = cityMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.recruitmentsystem.entity.DictCity>()
                        .orderByAsc(com.example.recruitmentsystem.entity.DictCity::getSortOrder))
                .get(0).getId();

        // 先存行业 + 城市
        CandidatePreferenceDto first = new CandidatePreferenceDto();
        first.setExpectedPosition("Java");
        first.setExpectedIndustryId(realIndustryId);
        first.setExpectedProvince("浙江");
        first.setExpectedCityId(realCityId);
        profileService.updatePreference(candidateId, first);

        // 用户修改期望职位为 "高级 Java"，其它不动（前端 payload 不携带 id 字段时序列化仍会带 null）
        CandidatePreferenceDto second = new CandidatePreferenceDto();
        second.setExpectedPosition("高级 Java");
        // industryId/province/cityId 不设 → null → 应清空（v0.7.3.2 行为）
        profileService.updatePreference(candidateId, second);

        CandidatePreferenceDto read = profileService.getPreference(candidateId);
        assertEquals("高级 Java", read.getExpectedPosition());
        // v0.7.3.2：partial update 行为是"以请求为最终状态"——未传字段视为清空
        assertEquals(null, read.getExpectedIndustryId(), "partial update 必须清空未传 id 字段（v0.7.3.2 当前行为）");
    }

    // ============ HR 公司自管理 ============

    @Test
    @Transactional
    void company_getOrCreate_returnsExisting() {
        Long hrId = createUser("p8", "HR", "Password123");
        createCompany(hrId, "初始公司名");
        CompanySelfDto dto = profileService.getMyCompany(hrId);
        assertEquals("初始公司名", dto.getName());
    }

    @Test
    @Transactional
    void company_getMissing_returnsEmptyWithPendingStatus() {
        Long hrId = createUser("p9", "HR", "Password123");
        CompanySelfDto dto = profileService.getMyCompany(hrId);
        assertEquals("PENDING", dto.getAuthStatus());
        assertEquals(null, dto.getName());  // 未设置
    }

    @Test
    @Transactional
    void company_update_modifiesNameScaleDescription() {
        Long hrId = createUser("p10", "HR", "Password123");
        createCompany(hrId, "旧公司");

        CompanySelfDto req = new CompanySelfDto();
        req.setName("新公司名");
        req.setScale("100-500 人");
        req.setDescription("专注互联网产品");

        CompanySelfDto updated = profileService.updateMyCompany(hrId, req);
        assertEquals("新公司名", updated.getName());
        assertEquals("100-500 人", updated.getScale());
        assertEquals("专注互联网产品", updated.getDescription());
    }

    @Test
    @Transactional
    void company_update_doesNotChangeAuthStatus() {
        Long hrId = createUser("p11", "HR", "Password123");
        createCompany(hrId, "公司");

        CompanySelfDto req = new CompanySelfDto();
        req.setName("x");
        req.setAuthStatus("VERIFIED");  // 试图绕过
        profileService.updateMyCompany(hrId, req);

        // authStatus 仍为 PENDING（HR 不能改）
        CompanySelfDto after = profileService.getMyCompany(hrId);
        assertEquals("PENDING", after.getAuthStatus());
    }
}
