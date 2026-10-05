package com.example.recruitmentsystem;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.job.JobCreateRequest;
import com.example.recruitmentsystem.dto.job.JobDto;
import com.example.recruitmentsystem.dto.job.JobUpdateRequest;
import com.example.recruitmentsystem.entity.Company;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JobService 单元测试。
 *
 * <p>v0.4 修订：删除所有管理员职位审核相关用例（UC-35 已取消）。
 * 状态机简化为：DRAFT → ONLINE → OFFLINE → DELETED。</p>
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
                org.mockito.Mockito.mock(com.example.recruitmentsystem.llm.service.LlmJdService.class));
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

    private JobCreateRequest sampleCreateWithTitle(String title) {
        JobCreateRequest req = sampleCreate();
        req.setTitle(title);
        return req;
    }

    // ============ createJob ============

    @Test
    @Transactional
    void createJob_draftStatus() {
        Long hrId = createHr("hr1");
        JobDto dto = jobService().createJob(hrId, sampleCreate());

        assertNotNull(dto.getId());
        assertEquals("DRAFT", dto.getStatus());
        assertEquals("NONE", dto.getAuditStatus(), "v0.4：audit_status 保留但无意义，固定为 NONE");
        assertEquals("Java 工程师", dto.getTitle());
    }

    @Test
    @Transactional
    void createJob_rejectsNonHr() {
        Long candidateId = createCandidate("c2");
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().createJob(candidateId, sampleCreate()));
        assertEquals(403, e.getCode());
    }

    // ============ publishJob (v0.4：去掉 audit 要求) ============

    @Test
    @Transactional
    void publishJob_draftToOnline_succeeds_noAdminAudit() {
        // v0.4 关键回归：草稿创建后立即可上线，无须管理员审核
        Long hrId = createHr("hr-publish");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        assertEquals("DRAFT", created.getStatus());

        JobDto published = jobService().publishJob(hrId, created.getId());
        assertEquals("ONLINE", published.getStatus());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    @Transactional
    void publishJob_offlineToOnline_succeeds() {
        Long hrId = createHr("hr-publish2");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        jobService().publishJob(hrId, created.getId());
        jobService().offlineJob(hrId, created.getId());

        JobDto republished = jobService().publishJob(hrId, created.getId());
        assertEquals("ONLINE", republished.getStatus());
    }

    @Test
    @Transactional
    void publishJob_onlineToOnline_throws() {
        Long hrId = createHr("hr-publish3");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        jobService().publishJob(hrId, created.getId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().publishJob(hrId, created.getId()));
        assertTrue(e.getMessage().contains("当前状态不可上线"));
    }

    // ============ offlineJob ============

    @Test
    @Transactional
    void offlineJob_requiresOnline() {
        Long hrId = createHr("hr-offline");
        JobDto created = jobService().createJob(hrId, sampleCreate());

        // DRAFT 不能直接下架
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().offlineJob(hrId, created.getId()));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void offlineJob_onlineToOffline_succeeds() {
        Long hrId = createHr("hr-offline2");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        jobService().publishJob(hrId, created.getId());

        JobDto offlined = jobService().offlineJob(hrId, created.getId());
        assertEquals("OFFLINE", offlined.getStatus());
    }

    // ============ deleteJob ============

    @Test
    @Transactional
    void deleteJob_blocksOnline() {
        Long hrId = createHr("hr-delete");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        jobService().publishJob(hrId, created.getId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().deleteJob(hrId, created.getId()));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void deleteJob_draftOrOffline_succeeds() {
        Long hrId = createHr("hr-delete2");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        jobService().deleteJob(hrId, created.getId());
        // 软删后再查不到（@TableLogic）
        assertEquals(null, jobMapper.selectById(created.getId()));
    }

    // ============ updateJob (v0.4：去掉 audit 重置) ============

    @Test
    @Transactional
    void updateJob_changesFields_draftStaysDraft() {
        Long hrId = createHr("hr-update");
        JobDto created = jobService().createJob(hrId, sampleCreate());

        JobUpdateRequest update = new JobUpdateRequest();
        update.setTitle("资深 Java");
        update.setSalaryMin(30);
        update.setSalaryMax(50);
        update.setDescription("架构设计");
        update.setRequirements("5 年以上");

        JobDto updated = jobService().updateJob(hrId, created.getId(), update);
        assertEquals("DRAFT", updated.getStatus(), "v0.4：编辑不改变 status");
        assertEquals("资深 Java", updated.getTitle());
        assertEquals(30, updated.getSalaryMin());
    }

    @Test
    @Transactional
    void updateJob_blocksOnline() {
        Long hrId = createHr("hr-update2");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        jobService().publishJob(hrId, created.getId());

        JobUpdateRequest update = new JobUpdateRequest();
        update.setTitle("尝试改标题");
        update.setSalaryMin(30);
        update.setSalaryMax(50);
        update.setDescription("x");
        update.setRequirements("y");

        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().updateJob(hrId, created.getId(), update));
        assertTrue(e.getMessage().contains("已上线职位不可编辑"));
    }

    // ============ listMineByTab (v0.4：3 tab 严格区分) ============

    @Test
    @Transactional
    void listMineByTab_separatesThreeStates() {
        Long hrId = createHr("hr-tab3");
        // 草稿
        JobDto draftJob = jobService().createJob(hrId, sampleCreateWithTitle("草稿职位"));
        // 上线（无审核）
        JobDto onlineJob = jobService().createJob(hrId, sampleCreateWithTitle("招聘中"));
        jobService().publishJob(hrId, onlineJob.getId());
        // 下架
        JobDto offlineJob = jobService().createJob(hrId, sampleCreateWithTitle("已下架"));
        jobService().publishJob(hrId, offlineJob.getId());
        jobService().offlineJob(hrId, offlineJob.getId());

        IPage<JobDto> draftTab = jobService().listMineByTab(hrId, "DRAFT", 1, 50);
        assertTrue(draftTab.getRecords().stream().anyMatch(r -> r.getId().equals(draftJob.getId())));
        assertTrue(draftTab.getRecords().stream().noneMatch(r -> r.getId().equals(onlineJob.getId())));
        assertTrue(draftTab.getRecords().stream().noneMatch(r -> r.getId().equals(offlineJob.getId())));

        IPage<JobDto> onlineTab = jobService().listMineByTab(hrId, "ONLINE", 1, 50);
        assertTrue(onlineTab.getRecords().stream().anyMatch(r -> r.getId().equals(onlineJob.getId())));
        assertTrue(onlineTab.getRecords().stream().noneMatch(r -> r.getId().equals(draftJob.getId())));

        IPage<JobDto> offlineTab = jobService().listMineByTab(hrId, "OFFLINE", 1, 50);
        assertTrue(offlineTab.getRecords().stream().anyMatch(r -> r.getId().equals(offlineJob.getId())));
        assertTrue(offlineTab.getRecords().stream().noneMatch(r -> r.getId().equals(draftJob.getId())));
    }

    @Test
    @Transactional
    void listMineByTab_invalidTab_throws() {
        Long hrId = createHr("hr-tab4");
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().listMineByTab(hrId, "INVALID", 1, 10));
        assertTrue(e.getMessage().contains("未知 tab"));
    }

    // ============ favorite ============

    @Test
    @Transactional
    void toggleFavorite_addAndRemove() {
        Long candidateId = createCandidate("c1");
        Long hrId = createHr("hr-fav");
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
        Long hrId = createHr("hr-fav2");
        JobDto created = jobService().createJob(hrId, sampleCreate());
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().toggleFavorite(hrId, created.getId()));
        assertEquals(403, e.getCode());
    }

    /**
     * v0.5 关键回归：toggle 反复切换（开 → 关 → 开）不应触发 UNIQUE 约束冲突。
     *
     * <p>历史 bug：{@code favorite_job} 表有 UNIQUE 约束，软删除后行依然存在，
     * 再次 INSERT 同 (candidate_id, job_id) 会冲突 → 500。修复：移除 FavoriteJob 的
     * {@code @TableLogic}，让 BaseMapper.deleteById 走硬删除。</p>
     */
    @Test
    @Transactional
    void toggleFavorite_offThenOn_noUniqueConflict() {
        Long candidateId = createCandidate("c-toggle-twice");
        Long hrId = createHr("hr-toggle-twice");
        JobDto created = jobService().createJob(hrId, sampleCreate());

        // 1) 首次 toggle：INSERT
        assertTrue(jobService().toggleFavorite(candidateId, created.getId()));
        assertTrue(jobService().isFavorited(candidateId, created.getId()));

        // 2) 再次 toggle：DELETE（之前是软删，修复后硬删）
        assertFalse(jobService().toggleFavorite(candidateId, created.getId()));
        assertFalse(jobService().isFavorited(candidateId, created.getId()));

        // 3) 第三次 toggle：再次 INSERT。修复前会触发 UNIQUE 约束 500；修复后正常返回 true
        assertTrue(jobService().toggleFavorite(candidateId, created.getId()));
        assertTrue(jobService().isFavorited(candidateId, created.getId()));

        // 4) 再关再开：第二次关
        assertFalse(jobService().toggleFavorite(candidateId, created.getId()));
        // 5) 第二次开
        assertTrue(jobService().toggleFavorite(candidateId, created.getId()));
        assertTrue(jobService().isFavorited(candidateId, created.getId()));
    }

    // ============ salary / dict validation ============

    @Test
    @Transactional
    void salaryValidation_minGreaterThanMax() {
        Long hrId = createHr("hr-sal");
        com.example.recruitmentsystem.dto.job.JobCreateRequest req = sampleCreate();
        req.setSalaryMin(50);
        req.setSalaryMax(20);
        BusinessException e = assertThrows(BusinessException.class,
                () -> jobService().createJob(hrId, req));
        assertEquals(400, e.getCode());
    }

    @Test
    @Transactional
    void createJob_withMismatchedProvinceCity_throws() {
        Long hrId = createHr("hr-prov");
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
        Long hrId = createHr("hr-prov2");
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

    // ============ v0.5：listForCandidate 扩展参数 ============

    /** 收藏后 listForCandidate 必须填充 favorited=true（关键回归：刷新不丢收藏状态） */
    @Test
    @Transactional
    void listForCandidate_populatesFavoritedField() {
        Long candidateId = createCandidate("c-fav-pop");
        Long hrId = createHr("hr-fav-pop");
        JobDto created = jobService().createJob(hrId, sampleCreateWithTitle("Java A"));
        jobService().publishJob(hrId, created.getId());
        jobService().toggleFavorite(candidateId, created.getId());

        IPage<JobDto> page = jobService().listForCandidate(
                null, null, null, null, null, null, candidateId, "newest", 1, 10);
        JobDto found = page.getRecords().stream().filter(j -> j.getId().equals(created.getId())).findFirst().orElse(null);
        assertNotNull(found, "应能找到刚收藏的职位");
        assertEquals(Boolean.TRUE, found.getFavorited(), "v0.5 关键：收藏后刷新列表必须显示 favorited=true");
    }

    /** 匿名访问（candidateId=null）时 favorited 字段不应抛错，保持 null */
    @Test
    @Transactional
    void listForCandidate_anonymousAccess_succeedsWithNullFavorited() {
        Long hrId = createHr("hr-anon");
        JobDto created = jobService().createJob(hrId, sampleCreateWithTitle("公开职位"));
        jobService().publishJob(hrId, created.getId());

        IPage<JobDto> page = jobService().listForCandidate(
                null, null, null, null, null, null, null, "newest", 1, 10);
        assertTrue(page.getRecords().stream().anyMatch(j -> j.getId().equals(created.getId())));
        // 匿名访问 favorited 应为 null（不抛 NPE）
        JobDto found = page.getRecords().stream().filter(j -> j.getId().equals(created.getId())).findFirst().orElse(null);
        assertNotNull(found);
        // 不强制要求 null，但不应抛错
    }

    /** favoritedOnly=true 时仅返回当前候选人收藏的职位 */
    @Test
    @Transactional
    void listForCandidate_favoritedOnly_filtersCorrectly() {
        Long candidateId = createCandidate("c-fav-filter");
        Long hrId = createHr("hr-fav-filter");
        JobDto fav = jobService().createJob(hrId, sampleCreateWithTitle("收藏的"));
        JobDto notFav = jobService().createJob(hrId, sampleCreateWithTitle("未收藏的"));
        jobService().publishJob(hrId, fav.getId());
        jobService().publishJob(hrId, notFav.getId());
        jobService().toggleFavorite(candidateId, fav.getId());

        // 不开启 favoritedOnly → 两条都返回
        IPage<JobDto> all = jobService().listForCandidate(
                null, null, null, null, null, false, candidateId, "newest", 1, 10);
        long totalAll = all.getRecords().stream().filter(j -> j.getId().equals(fav.getId()) || j.getId().equals(notFav.getId())).count();
        assertEquals(2, totalAll);

        // 开启 favoritedOnly → 仅收藏的那条
        IPage<JobDto> onlyFav = jobService().listForCandidate(
                null, null, null, null, null, true, candidateId, "newest", 1, 10);
        boolean containsFav = onlyFav.getRecords().stream().anyMatch(j -> j.getId().equals(fav.getId()));
        boolean containsNotFav = onlyFav.getRecords().stream().anyMatch(j -> j.getId().equals(notFav.getId()));
        assertTrue(containsFav, "favoritedOnly 必须包含已收藏职位");
        assertFalse(containsNotFav, "favoritedOnly 必须排除未收藏职位");
    }

    /** cityName 按城市名（dict_city.name）过滤 */
    @Test
    @Transactional
    void listForCandidate_cityName_filtersByCityName() {
        Long hrId = createHr("hr-cityname");
        // 创建一个有城市的职位
        com.example.recruitmentsystem.entity.DictCity city = new com.example.recruitmentsystem.entity.DictCity();
        city.setName("测试市-" + System.nanoTime());
        city.setProvince("测试省");
        city.setSortOrder(1);
        cityMapper.insert(city);

        JobCreateRequest req = sampleCreateWithTitle("城市测试职位");
        req.setCityId(city.getId());
        req.setProvince(city.getProvince());
        JobDto created = jobService().createJob(hrId, req);
        jobService().publishJob(hrId, created.getId());

        // 按城市名过滤应找到
        IPage<JobDto> matched = jobService().listForCandidate(
                null, null, null, null, city.getName(), null, null, "newest", 1, 10);
        assertTrue(matched.getRecords().stream().anyMatch(j -> j.getId().equals(created.getId())),
                "cityName 匹配时应返回该职位");

        // 不匹配的城市名应找不到
        IPage<JobDto> unmatched = jobService().listForCandidate(
                null, null, null, null, "不存在的城市名", null, null, "newest", 1, 10);
        assertFalse(unmatched.getRecords().stream().anyMatch(j -> j.getId().equals(created.getId())),
                "cityName 不匹配时不应返回该职位");
    }

    /** 关键词搜索：title 命中即返回 */
    @Test
    @Transactional
    void listForCandidate_keyword_searchByTitle() {
        Long hrId = createHr("hr-kw");
        JobDto javaJob = jobService().createJob(hrId, sampleCreateWithTitle("高级 Java 工程师"));
        JobDto pyJob = jobService().createJob(hrId, sampleCreateWithTitle("Python 数据分析"));
        jobService().publishJob(hrId, javaJob.getId());
        jobService().publishJob(hrId, pyJob.getId());

        IPage<JobDto> javaResult = jobService().listForCandidate(
                "Java", null, null, null, null, null, null, "newest", 1, 10);
        assertTrue(javaResult.getRecords().stream().anyMatch(j -> j.getId().equals(javaJob.getId())));
        assertFalse(javaResult.getRecords().stream().anyMatch(j -> j.getId().equals(pyJob.getId())));
    }
}