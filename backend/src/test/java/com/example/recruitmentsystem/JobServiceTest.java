package com.example.recruitmentsystem;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.job.JobCreateRequest;
import com.example.recruitmentsystem.dto.job.JobDto;
import com.example.recruitmentsystem.dto.job.JobUpdateRequest;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.Job;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.DictCityMapper;
import com.example.recruitmentsystem.mapper.DictIndustryMapper;
import com.example.recruitmentsystem.mapper.FavoriteJobMapper;
import com.example.recruitmentsystem.mapper.JobMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.AuditLogService;
import com.example.recruitmentsystem.service.impl.JobServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JobService 单元测试。覆盖：状态机 / 所有权校验 / 收藏 toggle / salary 校验 / dict 校验。
 */
@SpringBootTest
class JobServiceTest {

    @Autowired private JobMapper jobMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private CompanyMapper companyMapper;
    @Autowired private DictIndustryMapper industryMapper;
    @Autowired private DictCityMapper cityMapper;
    @Autowired private FavoriteJobMapper favoriteJobMapper;
    @Autowired private AuditLogService auditLogService;

    private JobServiceImpl jobService() {
        return new JobServiceImpl(jobMapper, favoriteJobMapper, userMapper, companyMapper,
                industryMapper, cityMapper,
                org.mockito.Mockito.mock(com.example.recruitmentsystem.llm.service.LlmJdService.class),
                auditLogService);
    }

    private Long createHr(String label) {
        User u = new User();
        u.setEmail("hr-" + label + "-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setRoleCode("HR");
        u.setStatus("ENABLED");
        u.setUsername(label);
        userMapper.insert(u);

        Company c = new Company();
        c.setHrUserId(u.getId());
        c.setName(label + " Inc.");
        c.setAuthStatus("PENDING");
        companyMapper.insert(c);
        return u.getId();
    }

    private Long createCandidate(String label) {
        User u = new User();
        u.setEmail("c-" + label + "-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setRoleCode("CANDIDATE");
        u.setStatus("ENABLED");
        u.setUsername(label);
        userMapper.insert(u);
        return u.getId();
    }

    private JobCreateRequest sampleCreate() {
        JobCreateRequest req = new JobCreateRequest();
        req.setTitle("Java 工程师");
        req.setSalaryMin(20);
        req.setSalaryMax(40);
        req.setDescription("负责后端开发");
        req.setRequirements("3 年以上 Java 经验");
        return req;
    }

    @Test
    @Transactional
    void createJob_draftPendingAudit() {
        Long hrId = createHr("hr1");
        JobDto dto = jobService().createJob(hrId, sampleCreate());

        assertNotNull(dto.getId());
        assertEquals("DRAFT", dto.getStatus());
        assertEquals("PENDING", dto.getAuditStatus());
        assertEquals("Java 工程师", dto.getTitle());
    }

    @Test
    @Transactional
    void publishJob_requiresApprovedAudit() {
        Long hrId = createHr("hr2");
        JobDto created = jobService().createJob(hrId, sampleCreate());

        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().publishJob(hrId, created.getId()));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void publishJob_succeedsAfterAuditApprove() {
        User admin = new User();
        admin.setEmail("admin-" + System.nanoTime() + "@test.local");
        admin.setPasswordHash("x");
        admin.setRoleCode("ADMIN");
        admin.setStatus("ENABLED");
        admin.setUsername("admin");
        userMapper.insert(admin);

        Long hrId = createHr("hr3");
        JobDto created = jobService().createJob(hrId, sampleCreate());

        com.example.recruitmentsystem.dto.job.JobAuditRequest req =
                new com.example.recruitmentsystem.dto.job.JobAuditRequest();
        req.setApprove(true);
        jobService().auditJob(admin.getId(), created.getId(), req);

        JobDto published = jobService().publishJob(hrId, created.getId());
        assertEquals("ONLINE", published.getStatus());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    @Transactional
    void offlineJob_requiresOnline() {
        Long hrId = createHr("hr4");
        JobDto created = jobService().createJob(hrId, sampleCreate());

        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().offlineJob(hrId, created.getId()));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void deleteJob_blocksOnline() {
        Long adminId = 1L;
        User admin = new User();
        admin.setEmail("admin-" + System.nanoTime() + "@test.local");
        admin.setPasswordHash("x");
        admin.setRoleCode("ADMIN");
        admin.setStatus("ENABLED");
        admin.setUsername("admin");
        userMapper.insert(admin);

        Long hrId = createHr("hr5");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        com.example.recruitmentsystem.dto.job.JobAuditRequest req =
                new com.example.recruitmentsystem.dto.job.JobAuditRequest();
        req.setApprove(true);
        jobService().auditJob(admin.getId(), created.getId(), req);
        jobService().publishJob(hrId, created.getId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().deleteJob(hrId, created.getId()));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void updateJob_resetsAuditToPending() {
        Long adminId = 1L;
        User admin = new User();
        admin.setEmail("admin-" + System.nanoTime() + "@test.local");
        admin.setPasswordHash("x");
        admin.setRoleCode("ADMIN");
        admin.setStatus("ENABLED");
        admin.setUsername("admin");
        userMapper.insert(admin);

        Long hrId = createHr("hr6");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        com.example.recruitmentsystem.dto.job.JobAuditRequest req =
                new com.example.recruitmentsystem.dto.job.JobAuditRequest();
        req.setApprove(true);
        jobService().auditJob(admin.getId(), created.getId(), req);

        JobUpdateRequest update = new JobUpdateRequest();
        update.setTitle("资深 Java");
        update.setSalaryMin(30);
        update.setSalaryMax(50);
        update.setDescription("架构设计");
        update.setRequirements("5 年以上");

        JobDto updated = jobService().updateJob(hrId, created.getId(), update);
        assertEquals("资深 Java", updated.getTitle());
        assertEquals("PENDING", updated.getAuditStatus());
    }

    @Test
    @Transactional
    void salaryValidation_minGreaterThanMax() {
        Long hrId = createHr("hr7");
        JobCreateRequest req = sampleCreate();
        req.setSalaryMin(50);
        req.setSalaryMax(20);
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().createJob(hrId, req));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void toggleFavorite_addAndRemove() {
        Long hrId = createHr("hr8");
        Long candidateId = createCandidate("c1");
        JobDto created = jobService().createJob(hrId, sampleCreate());

        boolean first = jobService().toggleFavorite(candidateId, created.getId());
        assertTrue(first);
        assertTrue(jobService().isFavorited(candidateId, created.getId()));

        boolean second = jobService().toggleFavorite(candidateId, created.getId());
        assertFalse(second);
        assertFalse(jobService().isFavorited(candidateId, created.getId()));
    }

    @Test
    @Transactional
    void toggleFavorite_rejectsNonCandidate() {
        Long hrId = createHr("hr9");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().toggleFavorite(hrId, created.getId()));
        assertEquals(403, e.getCode());
    }

    @Test
    @Transactional
    void createJob_rejectsNonHr() {
        Long candidateId = createCandidate("c2");
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().createJob(candidateId, sampleCreate()));
        assertEquals(403, e.getCode());
    }

    @Test
    @Transactional
    void auditJob_rejectsMissingReasonOnReject() {
        User admin = new User();
        admin.setEmail("admin-" + System.nanoTime() + "@test.local");
        admin.setPasswordHash("x");
        admin.setRoleCode("ADMIN");
        admin.setStatus("ENABLED");
        admin.setUsername("admin");
        userMapper.insert(admin);

        Long hrId = createHr("hr10");
        JobDto created = jobService().createJob(hrId, sampleCreate());

        com.example.recruitmentsystem.dto.job.JobAuditRequest req =
                new com.example.recruitmentsystem.dto.job.JobAuditRequest();
        req.setApprove(false);
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().auditJob(admin.getId(), created.getId(), req));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void createJob_withMismatchedProvinceCity_throws() {
        Long hrId = createHr("hr11");
        com.example.recruitmentsystem.entity.DictCity city = new com.example.recruitmentsystem.entity.DictCity();
        city.setName("测试市-" + System.nanoTime());
        city.setProvince("广东");
        city.setSortOrder(1);
        cityMapper.insert(city);

        JobCreateRequest req = sampleCreate();
        req.setCityId(city.getId());
        req.setProvince("江苏");
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().createJob(hrId, req));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void createJob_withMatchingProvince_succeeds() {
        Long hrId = createHr("hr12");
        com.example.recruitmentsystem.entity.DictCity city = new com.example.recruitmentsystem.entity.DictCity();
        city.setName("匹配市-" + System.nanoTime());
        city.setProvince("浙江");
        city.setSortOrder(1);
        cityMapper.insert(city);

        JobCreateRequest req = sampleCreate();
        req.setCityId(city.getId());
        req.setProvince("浙江");
        JobDto dto = jobService().createJob(hrId, req);
        assertEquals("浙江", dto.getProvince());
        assertEquals(city.getId(), dto.getCityId());
    }
}
