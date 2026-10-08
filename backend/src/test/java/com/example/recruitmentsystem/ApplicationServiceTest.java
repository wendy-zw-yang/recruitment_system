package com.example.recruitmentsystem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.application.AdvanceStatusRequest;
import com.example.recruitmentsystem.dto.application.AdvanceStatusResponse;
import com.example.recruitmentsystem.dto.application.ApplicationDetailDto;
import com.example.recruitmentsystem.dto.application.ApplicationDto;
import com.example.recruitmentsystem.dto.application.ApplicationNoteDto;
import com.example.recruitmentsystem.dto.application.ApplyRequest;
import com.example.recruitmentsystem.dto.application.ApplyResponse;
import com.example.recruitmentsystem.dto.application.HrApplicationListQuery;
import com.example.recruitmentsystem.dto.application.WithdrawResponse;
import com.example.recruitmentsystem.dto.resume.ResumeDto;
import com.example.recruitmentsystem.entity.Application;
import com.example.recruitmentsystem.entity.ApplicationNote;
import com.example.recruitmentsystem.entity.ApplicationStatusHistory;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.Job;
import com.example.recruitmentsystem.entity.Resume;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.ApplicationMapper;
import com.example.recruitmentsystem.mapper.ApplicationNoteMapper;
import com.example.recruitmentsystem.mapper.ApplicationStatusHistoryMapper;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.JobMapper;
import com.example.recruitmentsystem.mapper.ResumeAttachmentMapper;
import com.example.recruitmentsystem.mapper.ResumeMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.ResumeService;
import com.example.recruitmentsystem.service.impl.ApplicationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.test.annotation.Commit;  // not used; @Transactional alone is enough
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ApplicationService 单元测试。
 *
 * <p>覆盖：投递（正常 / 无简历 / 职位不可投 / 重复）/ 撤回（正常 / 终态 / 非本人）/
 * 状态推进（合法 / 非法 / HR 越权）/ 备注 CRUD / HR 列表过滤 / AI-2 失败降级。</p>
 */
@SpringBootTest
class ApplicationServiceTest {

    @Autowired private ApplicationMapper applicationMapper;
    @Autowired private ApplicationStatusHistoryMapper historyMapper;
    @Autowired private ApplicationNoteMapper noteMapper;
    @Autowired private JobMapper jobMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private CompanyMapper companyMapper;
    @Autowired private ResumeMapper resumeMapper;
    @Autowired private ResumeAttachmentMapper resumeAttachmentMapper;

    private ApplicationServiceImpl newService(ResumeService mockResumeService,
                                              com.example.recruitmentsystem.llm.service.LlmScoreService mockLlm) {
        return new ApplicationServiceImpl(
                applicationMapper, historyMapper, noteMapper,
                jobMapper, userMapper, companyMapper,
                resumeMapper, resumeAttachmentMapper,
                mockResumeService, mockLlm,
                Mockito.mock(com.example.recruitmentsystem.service.message.MessageService.class),
                new ObjectMapper());
    }

    private ResumeService mockResumeServiceWithActive(Long candidateId, Long resumeId) {
        ResumeService mock = Mockito.mock(ResumeService.class);
        // 任何 candidateId 都返回 ACTIVE 简历（测试中所有候选人都是合法的）
        ResumeDto dto = new ResumeDto();
        dto.setId(resumeId);
        dto.setCandidateId(candidateId);
        dto.setBasicName("测试候选人");
        when(mock.getCurrentActive(any(Long.class))).thenReturn(dto);
        return mock;
    }

    private ResumeService mockResumeServiceEmpty(Long candidateId) {
        ResumeService mock = Mockito.mock(ResumeService.class);
        when(mock.getCurrentActive(candidateId)).thenReturn(null);
        return mock;
    }

    private com.example.recruitmentsystem.llm.service.LlmScoreService mockLlmOk() {
        com.example.recruitmentsystem.llm.service.LlmScoreService mock =
                Mockito.mock(com.example.recruitmentsystem.llm.service.LlmScoreService.class);
        when(mock.scoreSync(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new com.example.recruitmentsystem.llm.service.LlmScoreService.ScoreResult(75, "匹配度较好"));
        return mock;
    }

    private com.example.recruitmentsystem.llm.service.LlmScoreService mockLlmThrow() {
        com.example.recruitmentsystem.llm.service.LlmScoreService mock =
                Mockito.mock(com.example.recruitmentsystem.llm.service.LlmScoreService.class);
        when(mock.scoreSync(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException(500, "AI mock fail"));
        return mock;
    }

    private Long createUser(String label, String role) {
        User u = new User();
        u.setEmail(label + "-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setRoleCode(role);
        u.setStatus("ENABLED");
        u.setUsername(label);
        userMapper.insert(u);
        return u.getId();
    }

    private Long createHrWithCompany(String label) {
        Long uid = createUser(label, "HR");
        Company c = new Company();
        c.setHrUserId(uid);
        c.setName(label + " Inc.");
        c.setAuthStatus("PENDING");
        companyMapper.insert(c);
        return uid;
    }

    private Long createCandidate(String label) {
        return createUser(label, "CANDIDATE");
    }

    private Long createActiveResume(Long candidateId) {
        Resume r = new Resume();
        r.setCandidateId(candidateId);
        r.setBasicName("测试");
        r.setBasicEmail("test@test.local");
        r.setBasicPhone("13800000000");
        r.setSkills("Java,Spring Boot");
        r.setSelfIntro("3 年后端");
        r.setArchived(false);
        resumeMapper.insert(r);
        return r.getId();
    }

    private Job createOnlineJob(Long hrUserId) {
        Job j = new Job();
        j.setHrUserId(hrUserId);
        Company c = companyMapper.selectOne(new LambdaQueryWrapper<Company>().eq(Company::getHrUserId, hrUserId));
        j.setCompanyId(c == null ? null : c.getId());
        j.setTitle("Java 工程师");
        j.setDescription("后端开发");
        j.setRequirements("3 年 Java");
        j.setStatus("ONLINE");
        j.setAuditStatus("NONE");
        j.setPublishedAt(LocalDateTime.now());
        jobMapper.insert(j);
        return j;
    }

    // ============ apply ============

    @Test
    @Transactional
    void apply_normal_succeeds() {
        Long candidateId = createCandidate("c1");
        Long hrUserId = createHrWithCompany("h1");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);
        ApplicationServiceImpl svc = newService(rs, mockLlmOk());

        ApplyRequest req = new ApplyRequest();
        req.setJobId(job.getId());
        ApplyResponse resp = svc.apply(candidateId, req);

        assertNotNull(resp.getApplicationId());
        assertEquals(ApplicationServiceImpl.STATUS_PENDING_REVIEW, resp.getStatus());
        // v0.5：候选人侧 ApplyResponse 不返回 aiScore（异步评分中，候选人不可见）
        assertNull(resp.getAiScore());
        // 初始状态历史
        long histCount = historyMapper.selectCount(new LambdaQueryWrapper<ApplicationStatusHistory>()
                .eq(ApplicationStatusHistory::getApplicationId, resp.getApplicationId()));
        assertEquals(1, histCount);
    }

    @Test
    @Transactional
    void apply_noActiveResume_throws() {
        Long candidateId = createCandidate("c2");
        Long hrUserId = createHrWithCompany("h2");
        Job job = createOnlineJob(hrUserId);

        ApplicationServiceImpl svc = newService(mockResumeServiceEmpty(candidateId), mockLlmOk());

        ApplyRequest req = new ApplyRequest();
        req.setJobId(job.getId());

        BusinessException e = assertThrows(BusinessException.class, () -> svc.apply(candidateId, req));
        assertTrue(e.getMessage().contains("简历"));
    }

    @Test
    @Transactional
    void apply_jobNotOnline_throws() {
        Long candidateId = createCandidate("c3");
        Long hrUserId = createHrWithCompany("h3");
        Long resumeId = createActiveResume(candidateId);

        Job job = createOnlineJob(hrUserId);
        job.setStatus("OFFLINE");
        jobMapper.updateById(job);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);
        ApplicationServiceImpl svc = newService(rs, mockLlmOk());

        ApplyRequest req = new ApplyRequest();
        req.setJobId(job.getId());

        BusinessException e = assertThrows(BusinessException.class, () -> svc.apply(candidateId, req));
        assertTrue(e.getMessage().contains("不可投递"));
    }

    @Test
    @Transactional
    void apply_duplicate_throws() {
        Long candidateId = createCandidate("c4");
        Long hrUserId = createHrWithCompany("h4");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);
        ApplicationServiceImpl svc = newService(rs, mockLlmOk());

        ApplyRequest req = new ApplyRequest();
        req.setJobId(job.getId());
        svc.apply(candidateId, req);  // 第一次成功

        BusinessException e = assertThrows(BusinessException.class, () -> svc.apply(candidateId, req));
        assertTrue(e.getMessage().contains("已投递"));
    }

    @Test
    @Transactional
    void apply_aiFailure_keepsScoreNull() throws InterruptedException {
        Long candidateId = createCandidate("c5");
        Long hrUserId = createHrWithCompany("h5");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);
        ApplicationServiceImpl svc = newService(rs, mockLlmThrow());

        ApplyRequest req = new ApplyRequest();
        req.setJobId(job.getId());
        ApplyResponse resp = svc.apply(candidateId, req);

        // 等待异步触发（虽然会失败，但同步部分已写入）
        Thread.sleep(200);

        // ai_score 应保持 NULL
        Application app = applicationMapper.selectById(resp.getApplicationId());
        assertNull(app.getAiScore());
    }

    /**
     * v0.5 安全合规回归：候选人侧 DTO 不应填充 aiScore / aiReason。
     * 验证 listMine（候选人列表）和 getDetail（候选人详情）均不暴露 AI 评分。
     * 前提：异步 AI 已成功写入分数（用 mockLlmOk() 同步触发）。
     */
    @Test
    @Transactional
    void listMine_candidateCannotSeeAiScore() throws InterruptedException {
        Long candidateId = createCandidate("c-vis");
        Long hrUserId = createHrWithCompany("h-vis");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);
        ApplicationServiceImpl svc = newService(rs, mockLlmOk());

        ApplyRequest req = new ApplyRequest();
        req.setJobId(job.getId());
        ApplyResponse resp = svc.apply(candidateId, req);

        // 等异步 AI 完成（mock 直接同步写库）
        Thread.sleep(300);
        Application app = applicationMapper.selectById(resp.getApplicationId());
        assertNotNull(app.getAiScore(), "AI 已成功写入 DB（HR 应可见）");
        assertTrue(app.getAiScore() >= 0);

        // 候选人列表查询：aiScore / aiReason 必须为 null
        IPage<ApplicationDto> minePage = svc.listMine(candidateId, 1, 10);
        ApplicationDto mineDto = minePage.getRecords().stream()
                .filter(a -> a.getId().equals(resp.getApplicationId())).findFirst().orElse(null);
        assertNotNull(mineDto);
        assertNull(mineDto.getAiScore(), "候选人列表不应暴露 aiScore");
        assertNull(mineDto.getAiReason(), "候选人列表不应暴露 aiReason");

        // 候选人详情查询（以候选人身份）：aiScore / aiReason 必须为 null
        ApplicationDetailDto myDetail = svc.getDetail(candidateId, "CANDIDATE", resp.getApplicationId());
        assertNull(myDetail.getAiScore(), "候选人详情不应暴露 aiScore");
        assertNull(myDetail.getAiReason(), "候选人详情不应暴露 aiReason");
    }

    /**
     * v0.5 安全合规回归：HR 端 DTO 应正常显示 aiScore / aiReason。
     */
    @Test
    @Transactional
    void hrDetail_canSeeAiScore() throws InterruptedException {
        Long candidateId = createCandidate("c-hrs");
        Long hrUserId = createHrWithCompany("h-hrs");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);
        ApplicationServiceImpl svc = newService(rs, mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        Thread.sleep(300);
        // HR 端列表 + 详情：aiScore 应可见
        HrApplicationListQuery q = new HrApplicationListQuery();
        q.setJobId(job.getId());
        IPage<ApplicationDto> hrList = svc.listForHr(hrUserId, q);
        ApplicationDto hrItem = hrList.getRecords().stream()
                .filter(a -> a.getId().equals(resp.getApplicationId())).findFirst().orElse(null);
        assertNotNull(hrItem);
        assertNotNull(hrItem.getAiScore(), "HR 列表应可见 aiScore");
        assertNotNull(hrItem.getAiReason(), "HR 列表应可见 aiReason");

        ApplicationDetailDto hrDetail = svc.getDetail(hrUserId, "HR", resp.getApplicationId());
        assertNotNull(hrDetail.getAiScore(), "HR 详情应可见 aiScore");
        assertNotNull(hrDetail.getAiReason(), "HR 详情应可见 aiReason");
    }

    /**
     * v0.7.4.5 回归：候选人「我的投递」应显示 VIEWED_BY_HR 状态的投递。
     *
     * <p>历史 bug：listMine 用 {@code .ne(VIEWED_BY_HR)} 隐藏 VIEWED_BY_HR 状态。
     * HR 把状态从 PENDING_REVIEW 推进到 VIEWED_BY_HR 后，候选人侧列表突然少一条；
     * 候选人无法再次投递同一职位（防重逻辑）→ 永远看不到该投递。</p>
     *
     * <p>本测试：候选人投递 → HR 推进到 VIEWED_BY_HR → listMine 仍应包含此 application，
     * 且 status = VIEWED_BY_HR。</p>
     */
    @Test
    @Transactional
    void listMine_includesViewedByHr_status() {
        Long candidateId = createCandidate("c-viewed");
        Long hrUserId = createHrWithCompany("h-viewed");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ApplicationServiceImpl svc = newService(
                mockResumeServiceWithActive(candidateId, resumeId), mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        // HR 推进到 VIEWED_BY_HR
        AdvanceStatusResponse r = svc.pushStatus(hrUserId, resp.getApplicationId(),
                ApplicationServiceImpl.STATUS_VIEWED_BY_HR, null);
        assertEquals(ApplicationServiceImpl.STATUS_VIEWED_BY_HR, r.getToStatus());

        // 候选人 listMine 应**仍**包含此 application，状态为 VIEWED_BY_HR
        IPage<ApplicationDto> minePage = svc.listMine(candidateId, 1, 10);
        ApplicationDto mineDto = minePage.getRecords().stream()
                .filter(a -> a.getId().equals(resp.getApplicationId())).findFirst().orElse(null);
        assertNotNull(mineDto, "VIEWED_BY_HR 状态的投递必须出现在「我的投递」中");
        assertEquals(ApplicationServiceImpl.STATUS_VIEWED_BY_HR, mineDto.getStatus(),
                "状态字段必须是 VIEWED_BY_HR（而非被过滤）");
    }

    // ============ withdraw ============

    @Test
    @Transactional
    void withdraw_normal_succeeds() {
        Long candidateId = createCandidate("c6");
        Long hrUserId = createHrWithCompany("h6");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);
        ApplicationServiceImpl svc = newService(rs, mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        WithdrawResponse wr = svc.withdraw(candidateId, resp.getApplicationId());
        assertEquals(ApplicationServiceImpl.STATUS_WITHDRAWN, wr.getStatus());

        Application app = applicationMapper.selectById(resp.getApplicationId());
        assertEquals(ApplicationServiceImpl.STATUS_WITHDRAWN, app.getStatus());
    }

    @Test
    @Transactional
    void withdraw_terminalState_throws() {
        Long candidateId = createCandidate("c7");
        Long hrUserId = createHrWithCompany("h7");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);
        ApplicationServiceImpl svc = newService(rs, mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        // 直接把 status 改成 HIRED（终态），模拟已录用场景
        Application app = applicationMapper.selectById(resp.getApplicationId());
        app.setStatus(ApplicationServiceImpl.STATUS_HIRED);
        applicationMapper.updateById(app);

        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.withdraw(candidateId, resp.getApplicationId()));
        assertTrue(e.getMessage().contains("终止"));
    }

    @Test
    @Transactional
    void withdraw_notOwner_throws() {
        Long candidateA = createCandidate("c8a");
        Long candidateB = createCandidate("c8b");
        Long hrUserId = createHrWithCompany("h8");
        Job job = createOnlineJob(hrUserId);
        Long resumeIdA = createActiveResume(candidateA);

        ResumeService rs = mockResumeServiceWithActive(candidateA, resumeIdA);
        ApplicationServiceImpl svc = newService(rs, mockLlmOk());
        ApplyResponse resp = svc.apply(candidateA, new ApplyRequest() {{ setJobId(job.getId()); }});

        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.withdraw(candidateB, resp.getApplicationId()));
        assertTrue(e.getMessage().contains("无权"));
    }

    // ============ pushStatus ============

    @Test
    @Transactional
    void pushStatus_legalTransition_succeeds() {
        Long candidateId = createCandidate("c9");
        Long hrUserId = createHrWithCompany("h9");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ApplicationServiceImpl svc = newService(mockResumeServiceWithActive(candidateId, resumeId), mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        AdvanceStatusResponse r = svc.pushStatus(hrUserId, resp.getApplicationId(),
                ApplicationServiceImpl.STATUS_VIEWED_BY_HR, null);
        assertEquals(ApplicationServiceImpl.STATUS_VIEWED_BY_HR, r.getToStatus());

        Application app = applicationMapper.selectById(resp.getApplicationId());
        assertEquals(ApplicationServiceImpl.STATUS_VIEWED_BY_HR, app.getStatus());
    }

    @Test
    @Transactional
    void pushStatus_illegalTransition_throws() {
        Long candidateId = createCandidate("c10");
        Long hrUserId = createHrWithCompany("h10");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ApplicationServiceImpl svc = newService(mockResumeServiceWithActive(candidateId, resumeId), mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        // PENDING_REVIEW 不能直接跳到 INTERVIEWING
        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.pushStatus(hrUserId, resp.getApplicationId(),
                        ApplicationServiceImpl.STATUS_INTERVIEWING, null));
        assertTrue(e.getMessage().contains("非法"));
    }

    @Test
    @Transactional
    void pushStatus_otherHrJob_throws() {
        Long candidateId = createCandidate("c11");
        Long hrA = createHrWithCompany("h11a");
        Long hrB = createHrWithCompany("h11b");
        Job job = createOnlineJob(hrA);
        Long resumeId = createActiveResume(candidateId);

        ApplicationServiceImpl svc = newService(mockResumeServiceWithActive(candidateId, resumeId), mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        // hrB 试图推进 hrA 的投递
        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.pushStatus(hrB, resp.getApplicationId(),
                        ApplicationServiceImpl.STATUS_VIEWED_BY_HR, null));
        assertTrue(e.getMessage().contains("无权"));
    }

    // ============ Notes ============

    @Test
    @Transactional
    void noteCrud_byOwnerHr_succeeds() {
        Long candidateId = createCandidate("c12");
        Long hrUserId = createHrWithCompany("h12");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ApplicationServiceImpl svc = newService(mockResumeServiceWithActive(candidateId, resumeId), mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        ApplicationNoteDto created = svc.addNote(hrUserId, resp.getApplicationId(), "初次面试通过");
        assertNotNull(created.getId());

        List<ApplicationNoteDto> notes = svc.listNotes(hrUserId, resp.getApplicationId());
        assertEquals(1, notes.size());

        ApplicationNoteDto updated = svc.updateNote(hrUserId, resp.getApplicationId(), created.getId(), "已发 offer");
        assertEquals("已发 offer", updated.getContent());

        svc.deleteNote(hrUserId, resp.getApplicationId(), created.getId());
        List<ApplicationNoteDto> afterDelete = svc.listNotes(hrUserId, resp.getApplicationId());
        assertEquals(0, afterDelete.size());
    }

    @Test
    @Transactional
    void noteCrud_otherHrJob_throws() {
        Long candidateId = createCandidate("c13");
        Long hrA = createHrWithCompany("h13a");
        Long hrB = createHrWithCompany("h13b");
        Job job = createOnlineJob(hrA);
        Long resumeId = createActiveResume(candidateId);

        ApplicationServiceImpl svc = newService(mockResumeServiceWithActive(candidateId, resumeId), mockLlmOk());
        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        BusinessException e = assertThrows(BusinessException.class,
                () -> svc.addNote(hrB, resp.getApplicationId(), "x"));
        assertTrue(e.getMessage().contains("无权"));
    }

    // ============ listForHr ============

    @Test
    @Transactional
    void listForHr_jobIdNull_returnsAcrossAllHrJobs() {
        // v0.5：HR 首页跨职位聚合 —— 不传 jobId 时返回该 HR 全部职位下的投递
        Long candidate1 = createCandidate("c15a");
        Long candidate2 = createCandidate("c15b");
        Long hrUserId = createHrWithCompany("h15");

        // 用 createOnlineJob 助手（直接创建 + 上线 + DRAFT 也可，但这里要两个职位）
        Long resumeId1 = createActiveResume(candidate1);
        Long resumeId2 = createActiveResume(candidate2);
        Job jobA = createOnlineJob(hrUserId);
        Job jobB = createOnlineJob(hrUserId);

        // candidate1 投 JobA，candidate2 投 JobB
        ApplicationServiceImpl svc = newService(
                mockResumeServiceWithActive(candidate1, resumeId1), mockLlmOk());
        svc.apply(candidate1, new ApplyRequest() {{ setJobId(jobA.getId()); }});

        ApplicationServiceImpl svc2 = newService(
                mockResumeServiceWithActive(candidate2, resumeId2), mockLlmOk());
        svc2.apply(candidate2, new ApplyRequest() {{ setJobId(jobB.getId()); }});

        // 不传 jobId → 应返回该 HR 全部职位下的 2 条
        HrApplicationListQuery q = new HrApplicationListQuery();
        IPage<ApplicationDto> all = svc.listForHr(hrUserId, q);
        assertEquals(2, all.getRecords().size());
        assertTrue(all.getRecords().stream().anyMatch(a -> a.getJobId().equals(jobA.getId())));
        assertTrue(all.getRecords().stream().anyMatch(a -> a.getJobId().equals(jobB.getId())));
    }

    @Test
    @Transactional
    void listForHr_excludesWithdrawnByDefault() {
        Long candidate1 = createCandidate("c14a");
        Long candidate2 = createCandidate("c14b");
        Long hrUserId = createHrWithCompany("h14");
        Job job = createOnlineJob(hrUserId);
        Long resumeId1 = createActiveResume(candidate1);
        Long resumeId2 = createActiveResume(candidate2);

        ApplicationServiceImpl svc = newService(
                mockResumeServiceWithActive(candidate1, resumeId1), mockLlmOk());

        ApplyResponse a1 = svc.apply(candidate1, new ApplyRequest() {{ setJobId(job.getId()); }});
        ApplyResponse a2 = svc.apply(candidate2, new ApplyRequest() {{ setJobId(job.getId()); }});

        // candidate2 撤回
        svc.withdraw(candidate2, a2.getApplicationId());

        HrApplicationListQuery q = new HrApplicationListQuery();
        q.setJobId(job.getId());
        IPage<ApplicationDto> page = svc.listForHr(hrUserId, q);
        // 默认排除 WITHDRAWN
        assertEquals(1, page.getRecords().size());
        assertFalse(page.getRecords().get(0).getWithdrawn());

        // 显式查询 WITHDRAWN
        q.setStatus("WITHDRAWN");
        IPage<ApplicationDto> withdrawnPage = svc.listForHr(hrUserId, q);
        assertEquals(1, withdrawnPage.getRecords().size());
        assertTrue(withdrawnPage.getRecords().get(0).getWithdrawn());
    }

    /**
     * v0.7.4.3 回归：撤回流程的 sendSystemMessage 异常**不应**让外层 withdraw 事务回滚。
     *
     * <p>历史 bug：{@code sendSystemMessage} 是 {@code @Transactional}（REQUIRED 默认传播），
     * 加入外层 {@code withdraw} 事务；任一异常会让整个外层事务被标记 rollback-only，
     * 即使 try/catch 捕获了原异常，{@code withdraw} 提交时仍会抛 UnexpectedRollbackException，
     * 导致 application 状态更新被一并回滚，用户看到"系统异常，请稍后重试"。</p>
     *
     * <p>本测试用 mock MessageService 抛运行时异常模拟 sendSystemMessage 失败；
     * 验证：withdraw 正常返回 + application.status 实际变为 WITHDRAWN（事务已提交）。</p>
     */
    @Test
    @Transactional
    void withdraw_messageServiceThrows_stillCommitsStatusUpdate() {
        Long candidateId = createCandidate("c-wdraw-msgerr");
        Long hrUserId = createHrWithCompany("h-wdraw-msgerr");
        Job job = createOnlineJob(hrUserId);
        Long resumeId = createActiveResume(candidateId);

        ResumeService rs = mockResumeServiceWithActive(candidateId, resumeId);

        // 构造一个抛 RuntimeException 的 mock MessageService（模拟 sendSystemMessage 失败）
        com.example.recruitmentsystem.service.message.MessageService throwingMsgService =
                Mockito.mock(com.example.recruitmentsystem.service.message.MessageService.class);
        Mockito.when(throwingMsgService.sendSystemMessage(
                        Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyLong()))
                .thenThrow(new RuntimeException("simulated system message failure"));

        ApplicationServiceImpl svc = new ApplicationServiceImpl(
                applicationMapper, historyMapper, noteMapper,
                jobMapper, userMapper, companyMapper,
                resumeMapper, resumeAttachmentMapper,
                rs, mockLlmOk(),
                throwingMsgService,    // 抛异常的 mock
                new ObjectMapper());

        ApplyResponse resp = svc.apply(candidateId, new ApplyRequest() {{ setJobId(job.getId()); }});

        // 关键断言：withdraw 不应抛 UnexpectedRollbackException；status 应被提交为 WITHDRAWN
        WithdrawResponse wr = svc.withdraw(candidateId, resp.getApplicationId());
        assertEquals(ApplicationServiceImpl.STATUS_WITHDRAWN, wr.getStatus());

        // 直接查 DB 确认：状态确实更新为 WITHDRAWN（外层事务已 commit）
        Application app = applicationMapper.selectById(resp.getApplicationId());
        assertEquals(ApplicationServiceImpl.STATUS_WITHDRAWN, app.getStatus(),
                "withdraw 主流程必须在 system message 失败时仍能提交状态更新");

        // 状态历史也被写入
        java.util.List<ApplicationStatusHistory> histories = historyMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApplicationStatusHistory>()
                        .eq(ApplicationStatusHistory::getApplicationId, resp.getApplicationId()));
        assertTrue(histories.stream().anyMatch(h ->
                        ApplicationServiceImpl.STATUS_WITHDRAWN.equals(h.getToStatus())),
                "状态历史应包含 WITHDRAWN 记录");
    }
}
