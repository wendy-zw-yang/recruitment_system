package com.example.recruitmentsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.application.AdvanceStatusResponse;
import com.example.recruitmentsystem.dto.application.ApplicationDetailDto;
import com.example.recruitmentsystem.dto.application.ApplicationDto;
import com.example.recruitmentsystem.dto.application.ApplicationNoteDto;
import com.example.recruitmentsystem.dto.application.ApplyRequest;
import com.example.recruitmentsystem.dto.application.ApplyResponse;
import com.example.recruitmentsystem.dto.application.HrApplicationListQuery;
import com.example.recruitmentsystem.dto.application.ResumeSnapshotDto;
import com.example.recruitmentsystem.dto.application.WithdrawResponse;
import com.example.recruitmentsystem.dto.resume.ResumeDto;
import com.example.recruitmentsystem.entity.Application;
import com.example.recruitmentsystem.entity.ApplicationNote;
import com.example.recruitmentsystem.entity.ApplicationStatusHistory;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.Job;
import com.example.recruitmentsystem.entity.Resume;
import com.example.recruitmentsystem.entity.ResumeAttachment;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.llm.service.LlmScoreService;
import com.example.recruitmentsystem.mapper.ApplicationMapper;
import com.example.recruitmentsystem.mapper.ApplicationNoteMapper;
import com.example.recruitmentsystem.mapper.ApplicationStatusHistoryMapper;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.JobMapper;
import com.example.recruitmentsystem.mapper.ResumeAttachmentMapper;
import com.example.recruitmentsystem.mapper.ResumeMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.ApplicationService;
import com.example.recruitmentsystem.service.ResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ApplicationService 实现。详见 {@code docs/系统设计/详细设计/投递.md §4}。
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>8 状态机（PENDING_REVIEW / VIEWED_BY_HR / RESUME_PASSED / INTERVIEWING / OFFERED / HIRED / REJECTED / WITHDRAWN）</li>
 *   <li>投递校验：候选人 ACTIVE 简历存在 + 职位 status=ONLINE + 未重复投递（v0.3 取消 audit_status 校验）</li>
 *   <li>简历快照：{@code resume_snapshot_id} 存 ACTIVE 简历 id（FK），候选人后续编辑 / 归档不影响</li>
 *   <li>AI-2 异步：{@code @Async("taskExecutor")} 调用 {@link LlmScoreService#score}，完成回调 {@link #onAiScoreCompleted}</li>
 *   <li>撤回：仅候选人本人 + 非终态；触发后写状态历史（不发站内信，待 §5）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationMapper applicationMapper;
    private final ApplicationStatusHistoryMapper historyMapper;
    private final ApplicationNoteMapper noteMapper;
    private final JobMapper jobMapper;
    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeAttachmentMapper resumeAttachmentMapper;
    private final ResumeService resumeService;
    private final LlmScoreService llmScoreService;
    private final ObjectMapper objectMapper;

    // ============ 状态机常量 ============

    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_VIEWED_BY_HR   = "VIEWED_BY_HR";
    public static final String STATUS_RESUME_PASSED  = "RESUME_PASSED";
    public static final String STATUS_INTERVIEWING   = "INTERVIEWING";
    public static final String STATUS_OFFERED        = "OFFERED";
    public static final String STATUS_HIRED          = "HIRED";
    public static final String STATUS_REJECTED       = "REJECTED";
    public static final String STATUS_WITHDRAWN      = "WITHDRAWN";

    /** 终态 */
    public static final List<String> TERMINAL_STATUSES = List.of(
            STATUS_HIRED, STATUS_REJECTED, STATUS_WITHDRAWN);

    /** 合法转换图（from → to 列表） */
    private static final Map<String, List<String>> VALID_TRANSITIONS;

    static {
        Map<String, List<String>> m = new HashMap<>();
        m.put(STATUS_PENDING_REVIEW, List.of(STATUS_VIEWED_BY_HR, STATUS_REJECTED, STATUS_WITHDRAWN));
        m.put(STATUS_VIEWED_BY_HR,   List.of(STATUS_RESUME_PASSED, STATUS_REJECTED, STATUS_WITHDRAWN));
        m.put(STATUS_RESUME_PASSED,  List.of(STATUS_INTERVIEWING, STATUS_REJECTED, STATUS_WITHDRAWN));
        m.put(STATUS_INTERVIEWING,   List.of(STATUS_OFFERED, STATUS_REJECTED, STATUS_WITHDRAWN));
        m.put(STATUS_OFFERED,        List.of(STATUS_HIRED, STATUS_REJECTED, STATUS_WITHDRAWN));
        m.put(STATUS_HIRED,          Collections.emptyList());
        m.put(STATUS_REJECTED,       Collections.emptyList());
        m.put(STATUS_WITHDRAWN,      Collections.emptyList());
        VALID_TRANSITIONS = Collections.unmodifiableMap(m);
    }

    // ============ 候选人端 ============

    @Override
    @Transactional
    public ApplyResponse apply(Long candidateId, ApplyRequest request) {
        validateCandidate(candidateId);

        Job job = jobMapper.selectById(request.getJobId());
        if (job == null) {
            throw new BusinessException(404, "职位不存在");
        }
        if (!"ONLINE".equals(job.getStatus())) {
            throw new BusinessException(400, "该职位当前不可投递");
        }

        // 取候选人 ACTIVE 简历（快照引用）
        ResumeDto activeResume = resumeService.getCurrentActive(candidateId);
        if (activeResume == null) {
            throw new BusinessException(400, "请先上传简历");
        }

        // 重复投递校验（同候选人同职位仅 1 条非 WITHDRAWN）
        long existing = countActiveApplication(candidateId, job.getId());
        if (existing > 0) {
            throw new BusinessException(400, "已投递过该职位");
        }

        Application app = new Application();
        app.setCandidateId(candidateId);
        app.setJobId(job.getId());
        app.setResumeSnapshotId(activeResume.getId());
        app.setStatus(STATUS_PENDING_REVIEW);
        app.setAiScore(null);
        app.setAiReason(null);
        app.setAppliedAt(LocalDateTime.now());
        applicationMapper.insert(app);

        // 写初始状态历史
        recordHistory(app.getId(), null, STATUS_PENDING_REVIEW, candidateId, "候选人初次投递");

        // 异步触发 AI-2 评分（不阻塞 HTTP 返回）
        triggerAiScoreAsync(app.getId(), candidateId, job, activeResume);

        ApplyResponse resp = new ApplyResponse();
        resp.setApplicationId(app.getId());
        resp.setStatus(app.getStatus());
        // v0.5：候选人侧不返回 aiScore —— 投递瞬间 aiScore 必为 NULL（异步评分中），
        // 返回会让前端误以为"评分中状态可查询"。直接不返回即可。
        resp.setAppliedAt(app.getAppliedAt());
        return resp;
    }

    @Override
    public IPage<ApplicationDto> listMine(Long candidateId, int pageNum, int pageSize) {
        validateCandidate(candidateId);
        Page<Application> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Application> q = new LambdaQueryWrapper<>();
        q.eq(Application::getCandidateId, candidateId)
                .ne(Application::getStatus, STATUS_VIEWED_BY_HR)  // 候选人侧隐藏 VIEWED_BY_HR
                .orderByDesc(Application::getAppliedAt);
        IPage<Application> raw = applicationMapper.selectPage(page, q);
        return raw.convert(this::toDtoForCandidate);
    }

    @Override
    public ApplicationDetailDto getDetail(Long requesterId, String requesterRole, Long applicationId) {
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "投递不存在");
        }
        // 鉴权：候选人本人 / 投递对应职位的 HR / 管理员
        boolean isOwnerCandidate = "CANDIDATE".equals(requesterRole) && app.getCandidateId().equals(requesterId);
        boolean isJobHr = "HR".equals(requesterRole) && isJobOwnedByHr(app.getJobId(), requesterId);
        boolean isAdmin = "ADMIN".equals(requesterRole);
        if (!isOwnerCandidate && !isJobHr && !isAdmin) {
            throw new BusinessException(403, "无权查看此投递");
        }
        // AI 评分仅 HR 端可见（候选人看不到；admin 也不可见——仅 HR 自己可见）
        // 候选人敏感信息（邮箱/手机）保留给 HR + admin 用于排障
        return enrichDetail(app, isJobHr || isAdmin, isJobHr);
    }

    @Override
    @Transactional
    public WithdrawResponse withdraw(Long candidateId, Long applicationId) {
        validateCandidate(candidateId);
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "投递不存在");
        }
        if (!app.getCandidateId().equals(candidateId)) {
            throw new BusinessException(403, "无权撤回他人投递");
        }
        if (TERMINAL_STATUSES.contains(app.getStatus())) {
            throw new BusinessException(400, "已终止的投递无法撤回");
        }
        String from = app.getStatus();
        app.setStatus(STATUS_WITHDRAWN);
        app.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(app);
        recordHistory(app.getId(), from, STATUS_WITHDRAWN, candidateId, "候选人主动撤回");
        log.info("[ApplicationService] 候选人 {} 撤回投递 {}", candidateId, applicationId);

        WithdrawResponse resp = new WithdrawResponse();
        resp.setApplicationId(app.getId());
        resp.setStatus(app.getStatus());
        resp.setUpdatedAt(app.getUpdatedAt());
        return resp;
    }

    // ============ HR 端 ============

    @Override
    public IPage<ApplicationDto> listForHr(Long hrUserId, HrApplicationListQuery query) {
        validateHr(hrUserId);

        Page<Application> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Application> q = new LambdaQueryWrapper<>();

        if (query.getJobId() == null) {
            // v0.5：HR 首页跨职位聚合 —— 查询该 HR 全部职位下的投递
            List<Long> hrJobIds = jobMapper.selectList(new LambdaQueryWrapper<Job>()
                            .eq(Job::getHrUserId, hrUserId)
                            .eq(Job::getIsDeleted, 0))
                    .stream().map(Job::getId).collect(Collectors.toList());
            if (hrJobIds.isEmpty()) {
                Page<Application> empty = new Page<>(query.getPageNum(), query.getPageSize());
                empty.setRecords(Collections.emptyList());
                return empty.convert(this::toDtoForHr);
            }
            q.in(Application::getJobId, hrJobIds);
        } else {
            // 校验职位归属
            Job job = jobMapper.selectById(query.getJobId());
            if (job == null) {
                throw new BusinessException(404, "职位不存在");
            }
            if (!job.getHrUserId().equals(hrUserId)) {
                throw new BusinessException(403, "无权查看此职位的投递");
            }
            q.eq(Application::getJobId, query.getJobId());
        }

        // 状态过滤：默认排除 WITHDRAWN
        if ("WITHDRAWN".equalsIgnoreCase(query.getStatus())) {
            q.eq(Application::getStatus, STATUS_WITHDRAWN);
        } else if (query.getStatus() != null && !query.getStatus().isBlank()
                && !"ACTIVE".equalsIgnoreCase(query.getStatus())) {
            q.eq(Application::getStatus, query.getStatus().trim());
        } else {
            q.ne(Application::getStatus, STATUS_WITHDRAWN);
        }

        // 关键词（候选人姓名 / 邮箱）
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            List<Long> candidateIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getUsername, query.getKeyword().trim())
                            .or().like(User::getEmail, query.getKeyword().trim()))
                    .stream().map(User::getId).collect(Collectors.toList());
            if (candidateIds.isEmpty()) {
                // 没匹配到候选人，直接返回空页
                Page<Application> empty = new Page<>(query.getPageNum(), query.getPageSize());
                empty.setRecords(Collections.emptyList());
                return empty.convert(this::toDtoForHr);
            }
            q.in(Application::getCandidateId, candidateIds);
        }

        // 排序：默认按 AI 评分降序；applied_desc 按投递时间
        if ("applied_desc".equalsIgnoreCase(query.getSort())) {
            q.orderByDesc(Application::getAppliedAt);
        } else {
            // score 降序：NULL 排最后（isNull ASC 等价于 NULLS LAST in MyBatis-Plus）
            q.orderByDesc(Application::getAiScore)
                    .orderByDesc(Application::getAppliedAt);
        }

        IPage<Application> raw = applicationMapper.selectPage(page, q);
        return raw.convert(this::toDtoForHr);
    }

    @Override
    @Transactional
    public AdvanceStatusResponse pushStatus(Long hrUserId, Long applicationId, String toStatus, String note) {
        validateHr(hrUserId);
        if (toStatus == null || toStatus.isBlank()) {
            throw new BusinessException(400, "请选择目标状态");
        }
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "投递不存在");
        }
        if (!isJobOwnedByHr(app.getJobId(), hrUserId)) {
            throw new BusinessException(403, "无权操作此投递");
        }
        String from = app.getStatus();
        if (!isValidTransition(from, toStatus)) {
            throw new BusinessException(400, "非法的状态转换：" + from + " → " + toStatus);
        }
        app.setStatus(toStatus);
        app.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(app);
        recordHistory(app.getId(), from, toStatus, hrUserId, note);

        AdvanceStatusResponse resp = new AdvanceStatusResponse();
        resp.setApplicationId(app.getId());
        resp.setFromStatus(from);
        resp.setToStatus(toStatus);
        resp.setUpdatedAt(app.getUpdatedAt());
        return resp;
    }

    @Override
    public List<ApplicationNoteDto> listNotes(Long hrUserId, Long applicationId) {
        validateNoteAccess(hrUserId, applicationId);
        LambdaQueryWrapper<ApplicationNote> q = new LambdaQueryWrapper<>();
        q.eq(ApplicationNote::getApplicationId, applicationId)
                .orderByDesc(ApplicationNote::getCreatedAt);
        List<ApplicationNote> notes = noteMapper.selectList(q);
        return notes.stream().map(this::toNoteDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ApplicationNoteDto addNote(Long hrUserId, Long applicationId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "备注内容不能为空");
        }
        validateNoteAccess(hrUserId, applicationId);
        ApplicationNote n = new ApplicationNote();
        n.setApplicationId(applicationId);
        n.setHrUserId(hrUserId);
        n.setContent(content);
        noteMapper.insert(n);
        return toNoteDto(n);
    }

    @Override
    @Transactional
    public ApplicationNoteDto updateNote(Long hrUserId, Long applicationId, Long noteId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "备注内容不能为空");
        }
        validateNoteAccess(hrUserId, applicationId);
        ApplicationNote n = noteMapper.selectById(noteId);
        if (n == null || !n.getApplicationId().equals(applicationId)) {
            throw new BusinessException(404, "备注不存在");
        }
        if (!n.getHrUserId().equals(hrUserId)) {
            throw new BusinessException(403, "仅可编辑本人备注");
        }
        n.setContent(content);
        n.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(n);
        return toNoteDto(n);
    }

    @Override
    @Transactional
    public void deleteNote(Long hrUserId, Long applicationId, Long noteId) {
        validateNoteAccess(hrUserId, applicationId);
        ApplicationNote n = noteMapper.selectById(noteId);
        if (n == null || !n.getApplicationId().equals(applicationId)) {
            throw new BusinessException(404, "备注不存在");
        }
        if (!n.getHrUserId().equals(hrUserId)) {
            throw new BusinessException(403, "仅可删除本人备注");
        }
        noteMapper.deleteById(noteId);
    }

    @Override
    public ResumeSnapshotDto getResumeSnapshot(Long hrUserId, Long applicationId) {
        validateHr(hrUserId);
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "投递不存在");
        }
        if (!isJobOwnedByHr(app.getJobId(), hrUserId)) {
            throw new BusinessException(403, "无权查看此投递的简历");
        }
        Resume r = resumeMapper.selectById(app.getResumeSnapshotId());
        if (r == null) {
            throw new BusinessException(404, "简历快照已不存在（候选人可能已删除）");
        }
        ResumeSnapshotDto dto = new ResumeSnapshotDto();
        dto.setResumeId(r.getId());
        dto.setSnapshotAt(app.getAppliedAt());
        dto.setFullName(r.getBasicName());
        dto.setEmail(r.getBasicEmail());
        dto.setPhone(r.getBasicPhone());
        dto.setSelfIntro(r.getSelfIntro());
        // 组装结构化 JSON：education/work/projects/skills 数组 + 基础字段
        dto.setStructuredJson(buildResumeJson(r));

        // 附件列表
        LambdaQueryWrapper<ResumeAttachment> aq = new LambdaQueryWrapper<>();
        aq.eq(ResumeAttachment::getResumeId, r.getId());
        List<ResumeAttachment> atts = resumeAttachmentMapper.selectList(aq);
        if (!atts.isEmpty()) {
            List<ResumeSnapshotDto.AttachmentItem> items = new ArrayList<>();
            for (ResumeAttachment att : atts) {
                ResumeSnapshotDto.AttachmentItem item = new ResumeSnapshotDto.AttachmentItem();
                item.setId(att.getId());
                item.setFileName(att.getFileName());
                // 简历附件下载用 ResumeService.loadAttachment 鉴权；此处给相对路径
                item.setUrl("/api/resumes/attachments/" + att.getId());
                item.setSizeBytes(att.getFileSize());
                items.add(item);
            }
            dto.setAttachments(items);
        }
        return dto;
    }

    /**
     * 把 Resume 实体组装成结构化 JSON 字符串（用于 AI-2 评分 / HR 简历快照）。
     * 注意：{@code education/work/projects} 字段本身就是 JSON 字符串，原样嵌入。
     */
    private String buildResumeJson(Resume r) {
        try {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("basicName", r.getBasicName());
            map.put("basicEmail", r.getBasicEmail());
            map.put("basicPhone", r.getBasicPhone());
            map.put("selfIntro", r.getSelfIntro());
            map.put("education", parseJsonArrayOrEmpty(r.getEducation()));
            map.put("work", parseJsonArrayOrEmpty(r.getWork()));
            map.put("projects", parseJsonArrayOrEmpty(r.getProjects()));
            map.put("skills", r.getSkills() == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(r.getSkills().split("[,，;；\\s]+")));
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[ApplicationService] 简历 JSON 组装失败 id={}", r.getId(), e);
            return "{}";
        }
    }

    private List<Object> parseJsonArrayOrEmpty(String json) {
        if (json == null || json.isBlank()) return java.util.Collections.emptyList();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {});
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    // ============ AI-2 异步回调 ============

    @Override
    @Transactional
    public void onAiScoreCompleted(Long applicationId, int score, String reason) {
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            log.warn("[ApplicationService] AI-2 回调时投递 {} 已不存在", applicationId);
            return;
        }
        app.setAiScore(score);
        app.setAiReason(reason);
        app.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(app);
        log.info("[ApplicationService] AI-2 评分完成 applicationId={} score={}", applicationId, score);
    }

    // ============ 私有方法 ============

    /**
     * 异步触发 AI-2 评分。失败时仅日志，不抛错给候选人（候选人已经拿到 applicationId）。
     */
    @Async("taskExecutor")
    public void triggerAiScoreAsync(Long applicationId, Long candidateId, Job job, ResumeDto resumeSnapshot) {
        try {
            // 构造简历 JSON：把 ResumeDto 转成结构化字符串喂给 LLM
            String resumeJson = objectMapper.writeValueAsString(resumeSnapshot);
            LlmScoreService.ScoreResult result = llmScoreService.scoreSync(
                    resumeJson,
                    job.getTitle(),
                    job.getDescription(),
                    job.getRequirements(),
                    candidateId);
            onAiScoreCompleted(applicationId, result.score, result.reason);
        } catch (BusinessException be) {
            log.warn("[ApplicationService] AI-2 评分失败 applicationId={} code={} msg={}",
                    applicationId, be.getCode(), be.getMessage());
            // ai_score 保持 NULL，HR 端展示「待评分」
        } catch (Exception ex) {
            log.error("[ApplicationService] AI-2 评分异常 applicationId={}", applicationId, ex);
            // 同上，ai_score NULL
        }
    }

    private boolean isValidTransition(String from, String to) {
        List<String> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    private long countActiveApplication(Long candidateId, Long jobId) {
        return applicationMapper.selectCount(new LambdaQueryWrapper<Application>()
                .eq(Application::getCandidateId, candidateId)
                .eq(Application::getJobId, jobId)
                .ne(Application::getStatus, STATUS_WITHDRAWN));
    }

    private boolean isJobOwnedByHr(Long jobId, Long hrUserId) {
        Job job = jobMapper.selectById(jobId);
        return job != null && job.getHrUserId().equals(hrUserId);
    }

    private void recordHistory(Long applicationId, String from, String to, Long changedBy, String note) {
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplicationId(applicationId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(changedBy);
        h.setNote(note);
        historyMapper.insert(h);
    }

    private void validateCandidate(Long candidateId) {
        User u = userMapper.selectById(candidateId);
        if (u == null || !"CANDIDATE".equals(u.getRoleCode())) {
            throw new BusinessException(403, "仅候选人可操作");
        }
        if (!"ENABLED".equals(u.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
    }

    private void validateHr(Long hrUserId) {
        User u = userMapper.selectById(hrUserId);
        if (u == null || !"HR".equals(u.getRoleCode())) {
            throw new BusinessException(403, "仅 HR 可操作");
        }
        if (!"ENABLED".equals(u.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
    }

    private void validateNoteAccess(Long hrUserId, Long applicationId) {
        validateHr(hrUserId);
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "投递不存在");
        }
        if (!isJobOwnedByHr(app.getJobId(), hrUserId)) {
            throw new BusinessException(403, "无权操作此投递");
        }
    }

    // ============ DTO 转换 ============

    /**
     * 候选人侧列表 DTO 转换。
     *
     * <p>v0.5 安全合规：<b>不填充 aiScore / aiReason</b>。AI-2 评分仅 HR 可见，
     * 候选人投递后无需知道"被评分"或"匹配度"这件事。具体规则见
     * {@code docs/系统设计/详细设计/投递.md §4.3.6} 与
     * {@code docs/系统设计/详细设计/AI集成.md §6.4.3}。</p>
     */
    private ApplicationDto toDtoForCandidate(Application app) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(app.getId());
        dto.setCandidateId(app.getCandidateId());
        dto.setJobId(app.getJobId());
        dto.setStatus(app.getStatus());
        // 不调用 setAiScore / setAiReason —— 候选人侧永远为 null
        dto.setAppliedAt(app.getAppliedAt());
        dto.setUpdatedAt(app.getUpdatedAt());
        dto.setWithdrawn(STATUS_WITHDRAWN.equals(app.getStatus()));
        dto.setWithdrawable(!TERMINAL_STATUSES.contains(app.getStatus()));
        enrichJobAndCandidate(app, dto);
        return dto;
    }

    /**
     * HR 侧列表 DTO 转换：在候选人侧基础上增加 aiScore / aiReason。
     *
     * <p>仅本方法（与 {@link #enrichDetail}）会填充 AI 评分字段。</p>
     */
    private ApplicationDto toDtoForHr(Application app) {
        ApplicationDto dto = toDtoForCandidate(app);
        // HR 端才可见 AI 评分
        dto.setAiScore(app.getAiScore());
        dto.setAiReason(app.getAiReason());
        // HR 端显示候选人姓名
        User candidate = userMapper.selectById(app.getCandidateId());
        if (candidate != null) {
            dto.setCandidateName(candidate.getUsername() != null ? candidate.getUsername() : candidate.getEmail());
        }
        return dto;
    }

    private void enrichJobAndCandidate(Application app, ApplicationDto dto) {
        Job job = jobMapper.selectById(app.getJobId());
        if (job != null) {
            dto.setJobTitle(job.getTitle());
            if (job.getCompanyId() != null) {
                Company company = companyMapper.selectById(job.getCompanyId());
                if (company != null) dto.setCompanyName(company.getName());
            }
        }
        if (dto.getCandidateName() == null) {
            User candidate = userMapper.selectById(app.getCandidateId());
            if (candidate != null) {
                dto.setCandidateName(candidate.getUsername() != null ? candidate.getUsername() : candidate.getEmail());
            }
        }
    }

    /**
     * 投递详情 DTO 转换。
     *
     * @param app                                  投递实体
     * @param includeSensitiveCandidateFields  true = 显示候选人邮箱/手机（HR / admin 可看；候选人自己也可看自己）
     * @param showAiScore                          true = 显示 aiScore / aiReason（仅 HR 端；候选人/admin 都不可见）
     */
    private ApplicationDetailDto enrichDetail(Application app,
                                              boolean includeSensitiveCandidateFields,
                                              boolean showAiScore) {
        ApplicationDetailDto dto = new ApplicationDetailDto();
        dto.setId(app.getId());
        dto.setJobId(app.getJobId());
        dto.setResumeSnapshotId(app.getResumeSnapshotId());
        dto.setStatus(app.getStatus());
        // v0.5：AI 评分字段按角色裁剪，仅 HR 端可见
        if (showAiScore) {
            dto.setAiScore(app.getAiScore());
            dto.setAiReason(app.getAiReason());
        }
        dto.setAppliedAt(app.getAppliedAt());
        dto.setUpdatedAt(app.getUpdatedAt());
        dto.setWithdrawn(STATUS_WITHDRAWN.equals(app.getStatus()));
        dto.setWithdrawable(!TERMINAL_STATUSES.contains(app.getStatus())
                && includeSensitiveCandidateFields == false);

        // 候选人
        User candidate = userMapper.selectById(app.getCandidateId());
        if (candidate != null) {
            dto.setCandidateId(candidate.getId());
            dto.setCandidateName(candidate.getUsername() != null ? candidate.getUsername() : candidate.getEmail());
            if (includeSensitiveCandidateFields) {
                dto.setCandidateEmail(candidate.getEmail());
                dto.setCandidatePhone(candidate.getPhone());
            }
        }

        // 职位
        Job job = jobMapper.selectById(app.getJobId());
        if (job != null) {
            dto.setJobTitle(job.getTitle());
            dto.setJobDescription(job.getDescription());
            dto.setJobRequirements(job.getRequirements());
            if (job.getCompanyId() != null) {
                Company company = companyMapper.selectById(job.getCompanyId());
                if (company != null) dto.setCompanyName(company.getName());
            }
        }

        // 简历快照
        Resume r = resumeMapper.selectById(app.getResumeSnapshotId());
        if (r != null) {
            dto.setResumeSnapshotName(r.getBasicName());
            dto.setResumeSnapshotEmail(r.getBasicEmail());
            dto.setResumeSnapshotPhone(r.getBasicPhone());
            dto.setResumeSnapshotJson(buildResumeJson(r));
        }

        // 状态历史
        LambdaQueryWrapper<ApplicationStatusHistory> hq = new LambdaQueryWrapper<>();
        hq.eq(ApplicationStatusHistory::getApplicationId, app.getId())
                .orderByAsc(ApplicationStatusHistory::getCreatedAt);
        List<ApplicationStatusHistory> history = historyMapper.selectList(hq);
        List<ApplicationDetailDto.HistoryItem> hItems = new ArrayList<>();
        for (ApplicationStatusHistory h : history) {
            ApplicationDetailDto.HistoryItem item = new ApplicationDetailDto.HistoryItem();
            item.setFromStatus(h.getFromStatus());
            item.setToStatus(h.getToStatus());
            item.setChangedBy(h.getChangedBy());
            User changer = userMapper.selectById(h.getChangedBy());
            if (changer != null) {
                item.setChangedByName(changer.getUsername() != null ? changer.getUsername() : changer.getEmail());
            }
            item.setNote(h.getNote());
            item.setChangedAt(h.getCreatedAt());
            hItems.add(item);
        }
        dto.setHistory(hItems);

        // 备注（HR 端可见，候选人侧不返）
        if (includeSensitiveCandidateFields) {
            LambdaQueryWrapper<ApplicationNote> nq = new LambdaQueryWrapper<>();
            nq.eq(ApplicationNote::getApplicationId, app.getId())
                    .orderByDesc(ApplicationNote::getCreatedAt);
            List<ApplicationNote> notes = noteMapper.selectList(nq);
            List<ApplicationDetailDto.NoteItem> nItems = new ArrayList<>();
            for (ApplicationNote n : notes) {
                ApplicationDetailDto.NoteItem item = new ApplicationDetailDto.NoteItem();
                item.setId(n.getId());
                item.setHrUserId(n.getHrUserId());
                User hr = userMapper.selectById(n.getHrUserId());
                if (hr != null) {
                    item.setHrUserName(hr.getUsername() != null ? hr.getUsername() : hr.getEmail());
                }
                item.setContent(n.getContent());
                item.setCreatedAt(n.getCreatedAt());
                item.setUpdatedAt(n.getUpdatedAt());
                nItems.add(item);
            }
            dto.setNotes(nItems);
        }

        return dto;
    }

    private ApplicationNoteDto toNoteDto(ApplicationNote n) {
        ApplicationNoteDto dto = new ApplicationNoteDto();
        dto.setId(n.getId());
        dto.setApplicationId(n.getApplicationId());
        dto.setHrUserId(n.getHrUserId());
        User hr = userMapper.selectById(n.getHrUserId());
        if (hr != null) {
            dto.setHrUserName(hr.getUsername() != null ? hr.getUsername() : hr.getEmail());
        }
        dto.setContent(n.getContent());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setUpdatedAt(n.getUpdatedAt());
        return dto;
    }
}
