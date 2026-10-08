package com.example.recruitmentsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.job.JobCreateRequest;
import com.example.recruitmentsystem.dto.job.JobDto;
import com.example.recruitmentsystem.dto.job.JobUpdateRequest;
import com.example.recruitmentsystem.dto.resume.ResumeDto;
import com.example.recruitmentsystem.entity.CandidateProfile;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.DictCity;
import com.example.recruitmentsystem.entity.DictIndustry;
import com.example.recruitmentsystem.entity.FavoriteJob;
import com.example.recruitmentsystem.entity.Job;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.llm.service.LlmJdService;
import com.example.recruitmentsystem.llm.service.RecommendService;
import com.example.recruitmentsystem.mapper.CandidateProfileMapper;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.DictCityMapper;
import com.example.recruitmentsystem.mapper.DictIndustryMapper;
import com.example.recruitmentsystem.mapper.FavoriteJobMapper;
import com.example.recruitmentsystem.mapper.JobMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.JobService;
import com.example.recruitmentsystem.service.ResumeService;
import com.example.recruitmentsystem.service.message.SseEmitterManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JobService 实现。详见 {@code docs/系统设计/详细设计/职位与公司.md §3.3}。
 *
 * <p>v0.4 状态机（status）：</p>
 * <ul>
 *   <li>DRAFT → ONLINE（无审核）</li>
 *   <li>ONLINE → OFFLINE</li>
 *   <li>DRAFT / OFFLINE → DELETED（软删）</li>
 * </ul>
 *
 * <p>HR 端 3 个 Tab 互斥（严格按 status 字段切分）：</p>
 * <ul>
 *   <li>草稿：status=DRAFT</li>
 *   <li>招聘中：status=ONLINE</li>
 *   <li>已下架：status=OFFLINE</li>
 * </ul>
 *
 * <p>audit_status 字段保留（DB 默认 'NONE'），不再被读 / 写操作使用（v0.4 取消审核流）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobMapper jobMapper;
    private final FavoriteJobMapper favoriteJobMapper;
    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final DictIndustryMapper industryMapper;
    private final DictCityMapper cityMapper;
    private final CandidateProfileMapper candidateProfileMapper;
    private final LlmJdService llmJdService;
    // v0.7.3：AI 智能匹配度（异步 LLM 重排 + 评分）
    private final RecommendService recommendService;
    private final ResumeService resumeService;
    private final SseEmitterManager sseManager;
    private final ObjectMapper objectMapper;

    // v0.7.3：候选人推荐分数缓存（userId → jobId → score，0-100）
    // 失效时机：用户改简历 / 改偏好时清空（recommendPendingFlag 驱动）
    // v0.7.4.1：visibility 从 private 提到 public，与 recommendPendingFlag 对齐，便于同包测试清理
    public static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Integer>> recommendScoresCache
            = new ConcurrentHashMap<>();

    // v0.7.3：标记用户更新了偏好 / 简历，下次查询时需重新跑 AI
    public static final ConcurrentHashMap<Long, Boolean> recommendPendingFlag = new ConcurrentHashMap<>();

    /**
     * v0.7.3：触发 AI 重排的阈值（候选人首页常见 pageSize=10，超过该值不触发避免 LLM 成本失控）。
     */
    private static final int AI_RERANK_MAX_JOBS = 10;

    // ============ HR 端 ============

    @Override
    @Transactional
    public JobDto createJob(Long hrUserId, JobCreateRequest request) {
        validateHr(hrUserId);
        validateSalary(request.getSalaryMin(), request.getSalaryMax());
        validateDicts(request.getIndustryId(), request.getCityId());
        String province = validateAndResolveProvince(request.getCityId(), request.getProvince());

        Long companyId = ensureCompanyForHr(hrUserId);

        Job job = new Job();
        job.setHrUserId(hrUserId);
        job.setCompanyId(companyId);
        applyCreateFields(job, request);
        job.setProvince(province);
        job.setStatus("DRAFT");
        job.setAuditStatus("NONE");
        jobMapper.insert(job);
        return enrich(job);
    }

    @Override
    @Transactional
    public JobDto updateJob(Long hrUserId, Long jobId, JobUpdateRequest request) {
        Job job = requireOwned(hrUserId, jobId);
        if ("ONLINE".equals(job.getStatus())) {
            throw new BusinessException(400, "已上线职位不可编辑，请先下架");
        }
        if ("DELETED".equals(job.getStatus())) {
            throw new BusinessException(400, "已删除职位不可编辑");
        }
        validateSalary(request.getSalaryMin(), request.getSalaryMax());
        validateDicts(request.getIndustryId(), request.getCityId());
        String province = validateAndResolveProvince(request.getCityId(), request.getProvince());

        job.setTitle(request.getTitle());
        job.setIndustryId(request.getIndustryId());
        job.setCityId(request.getCityId());
        job.setProvince(province);
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setKeywords(normalizeKeywords(request.getKeywords()));
        jobMapper.updateById(job);
        return enrich(job);
    }

    @Override
    @Transactional
    public JobDto publishJob(Long hrUserId, Long jobId) {
        Job job = requireOwned(hrUserId, jobId);
        if (!"DRAFT".equals(job.getStatus()) && !"OFFLINE".equals(job.getStatus())) {
            throw new BusinessException(400, "当前状态不可上线");
        }
        job.setStatus("ONLINE");
        job.setPublishedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        return enrich(job);
    }

    @Override
    @Transactional
    public JobDto offlineJob(Long hrUserId, Long jobId) {
        Job job = requireOwned(hrUserId, jobId);
        if (!"ONLINE".equals(job.getStatus())) {
            throw new BusinessException(400, "当前状态不可下架");
        }
        job.setStatus("OFFLINE");
        jobMapper.updateById(job);
        return enrich(job);
    }

    @Override
    @Transactional
    public void deleteJob(Long hrUserId, Long jobId) {
        Job job = requireOwned(hrUserId, jobId);
        if ("ONLINE".equals(job.getStatus())) {
            throw new BusinessException(400, "在线职位不可删除，请先下架");
        }
        // 用 @TableLogic 软删：BaseMapper.deleteById 会把 is_deleted 置 1
        jobMapper.deleteById(jobId);
    }

    @Override
    public IPage<JobDto> listMineByTab(Long hrUserId, String tab, int pageNum, int pageSize) {
        validateHr(hrUserId);
        Page<Job> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Job> q = new LambdaQueryWrapper<>();
        q.eq(Job::getHrUserId, hrUserId);
        if ("DRAFT".equals(tab)) {
            q.eq(Job::getStatus, "DRAFT");
        } else if ("ONLINE".equals(tab)) {
            q.eq(Job::getStatus, "ONLINE");
        } else if ("OFFLINE".equals(tab)) {
            q.eq(Job::getStatus, "OFFLINE");
        } else {
            throw new BusinessException(400, "未知 tab: " + tab);
        }
        q.orderByDesc(Job::getUpdatedAt);
        IPage<Job> raw = jobMapper.selectPage(page, q);
        return raw.convert(this::enrich);
    }

    // ============ 候选人端 ============

    @Override
    public IPage<JobDto> listForCandidate(String keyword, Long industryId, Long cityId, String province,
                                          String cityName, Boolean favoritedOnly, Long candidateId,
                                          String sort, int pageNum, int pageSize) {
        Page<Job> page = new Page<>(pageNum, pageSize);
        IPage<Job> raw = jobMapper.selectPageForCandidate(
                page, keyword, industryId, cityId, province, cityName, favoritedOnly, candidateId, sort);
        IPage<JobDto> dtos = raw.convert(this::enrich);
        // 候选人访问时，批量填充 favorited 字段（避免 toggleFavorite 后刷新丢失状态）
        if (candidateId != null && dtos.getRecords() != null && !dtos.getRecords().isEmpty()) {
            List<Long> jobIds = dtos.getRecords().stream().map(JobDto::getId).collect(Collectors.toList());
            List<Long> favIds = jobMapper.selectFavoriteJobIds(candidateId, jobIds);
            java.util.Set<Long> favSet = new java.util.HashSet<>(favIds);
            for (JobDto dto : dtos.getRecords()) {
                dto.setFavorited(favSet.contains(dto.getId()));
            }
        }
        return dtos;
    }

    @Override
    public JobDto getDetail(Long requesterId, String requesterRole, Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "职位不存在");
        }
        JobDto dto = enrich(job);
        if ("CANDIDATE".equals(requesterRole) && requesterId != null) {
            dto.setFavorited(isFavorited(requesterId, jobId));
        }
        return dto;
    }

    @Override
    @Transactional
    public boolean toggleFavorite(Long candidateId, Long jobId) {
        validateCandidate(candidateId);
        if (jobMapper.selectById(jobId) == null) {
            throw new BusinessException(404, "职位不存在");
        }
        LambdaQueryWrapper<FavoriteJob> q = new LambdaQueryWrapper<>();
        q.eq(FavoriteJob::getCandidateId, candidateId)
                .eq(FavoriteJob::getJobId, jobId);
        FavoriteJob existing = favoriteJobMapper.selectOne(q);
        if (existing != null) {
            favoriteJobMapper.deleteById(existing.getId());
            return false;
        }
        FavoriteJob fav = new FavoriteJob();
        fav.setCandidateId(candidateId);
        fav.setJobId(jobId);
        favoriteJobMapper.insert(fav);
        return true;
    }

    @Override
    public boolean isFavorited(Long candidateId, Long jobId) {
        if (candidateId == null || jobId == null) return false;
        LambdaQueryWrapper<FavoriteJob> q = new LambdaQueryWrapper<>();
        q.eq(FavoriteJob::getCandidateId, candidateId)
                .eq(FavoriteJob::getJobId, jobId);
        return favoriteJobMapper.selectCount(q) > 0;
    }

    @Override
    public IPage<JobDto> recommendOnResume(Long candidateId, int pageNum, int pageSize) {
        // 1. 读取候选人偏好（candidate_profile）
        Long industryId = null;
        String province = null;
        Long cityId = null;
        List<String> tokens = new ArrayList<>();
        if (candidateId != null) {
            CandidateProfile pref = candidateProfileMapper.selectOne(
                    new LambdaQueryWrapper<CandidateProfile>()
                            .eq(CandidateProfile::getUserId, candidateId));
            if (pref != null) {
                industryId = pref.getExpectedIndustryId();
                province = pref.getExpectedProvince();
                cityId = pref.getExpectedCityId();
                if (pref.getExpectedPosition() != null && !pref.getExpectedPosition().isBlank()) {
                    String raw = pref.getExpectedPosition().trim();
                    for (String tk : raw.split("\\s+")) {
                        if (!tk.isEmpty()) tokens.add(tk);
                    }
                    // v0.7.3.1：连续无空白中文输入（如"系统架构师"）额外拆 2-char 滑动串，
                    // 解决"系统架构师"匹配不上"系统设计架构师"（中间隔着"设计"）的问题。
                    // SQL 任一命中即入选，所以"系统"命中就够了。
                    for (String part : raw.split("\\s+")) {
                        if (part.length() >= 4) {
                            for (int i = 0; i <= part.length() - 2; i++) {
                                String chunk = part.substring(i, i + 2);
                                if (!tokens.contains(chunk)) tokens.add(chunk);
                            }
                        }
                    }
                }
            }
        }
        boolean hasAnyPref = industryId != null
                || (province != null && !province.isBlank())
                || cityId != null
                || !tokens.isEmpty();

        Page<Job> page = new Page<>(pageNum, pageSize);

        if (!hasAnyPref) {
            // 偏好全空 → 退化为最新发布（与 /jobs 接口首页行为一致）
            List<Job> raw = jobMapper.selectPageForRecommendation(
                    null, null, null, null, 0, pageSize);
            return wrapAsPage(raw, pageNum, pageSize, candidateId);
        }

        // 2. SQL 宽筛：行业 / 城市 / 省份 / 关键词 任一命中 → 拉取较多候选
        int broadLimit = Math.max(pageSize * 5, 50); // 拉 5 倍候选集供打分
        List<Job> candidates = jobMapper.selectPageForRecommendation(
                industryId, cityId, province, tokens.isEmpty() ? null : tokens,
                0, broadLimit);

        // 3. Java 内存打分（4 维：行业 + 城市 + 省份 + 关键词）
        // score 越高越相关；同分按发布时间倒序
        // 关键：lambda 不能捕获非 effectively-final 变量；将偏好绑成局部常量
        final Long fIndustryId = industryId;
        final Long fCityId = cityId;
        final String fProvince = province;
        final List<String> fTokens = tokens;
        List<Job> scored = candidates.stream()
                .map(j -> new ScoredJob(j, scoreJob(j, fIndustryId, fCityId, fProvince, fTokens)))
                .sorted(Comparator
                        .comparingInt(ScoredJob::getScore).reversed()
                        .thenComparing(ScoredJob::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ScoredJob::getJob)
                .collect(Collectors.toList());

        // 4. 内存分页
        int total = scored.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Job> pageRecords = scored.subList(from, to);

        // v0.7.3：触发 AI 重排（仅：有候选人 + 简历 + 满足触发条件 + 候选数 ≤ AI_RERANK_MAX_JOBS）
        tryTriggerAiPolish(candidateId, scored, industryId, cityId, province, tokens);

        return wrapAsPage(pageRecords, pageNum, pageSize, candidateId, (long) total);
    }

    /** 把内存 List 包装成 IPage&lt;JobDto&gt;（按推荐位或无偏好共用） */
    private IPage<JobDto> wrapAsPage(List<Job> jobs, int pageNum, int pageSize, Long candidateId) {
        return wrapAsPage(jobs, pageNum, pageSize, candidateId, (long) jobs.size());
    }

    private IPage<JobDto> wrapAsPage(List<Job> jobs, int pageNum, int pageSize, Long candidateId, Long total) {
        Page<Job> memPage = new Page<>(pageNum, pageSize, total);
        memPage.setRecords(jobs);
        IPage<JobDto> dtos = memPage.convert(this::enrich);
        fillFavorited(dtos, candidateId);
        fillAiScore(dtos, candidateId);
        // v0.7.4.1：决策 #2 落地——AI 重排完成后按 aiScore DESC 重排（高分优先），
        // 无 aiScore 的卡片排到末尾。仅当 cache 有此 userId 数据时生效（说明 AI 已跑过）。
        reorderByAiScore(dtos, candidateId);
        return dtos;
    }

    /**
     * v0.7.4.1：决策 #2 落地——按 aiScore DESC 重排（高分优先）。
     * <ul>
     *   <li>仅当 {@code recommendScoresCache[userId]} 非空时生效（AI 已跑过）</li>
     *   <li>aiScore 为 null 的卡片排到末尾（保持原顺序靠后）</li>
     *   <li>同分按发布时间倒序（nullsLast）</li>
     * </ul>
     */
    private void reorderByAiScore(IPage<JobDto> dtos, Long candidateId) {
        if (candidateId == null) return;
        ConcurrentHashMap<Long, Integer> userCache = recommendScoresCache.get(candidateId);
        if (userCache == null || userCache.isEmpty()) return;
        List<JobDto> records = dtos.getRecords();
        if (records == null || records.size() <= 1) return;
        records.sort((a, b) -> {
            Integer sa = a.getAiScore();
            Integer sb = b.getAiScore();
            // 两者都 null → 保持原序
            if (sa == null && sb == null) return 0;
            // null 排最后
            if (sa == null) return 1;
            if (sb == null) return -1;
            // DESC：高分在前
            int cmp = Integer.compare(sb, sa);
            if (cmp != 0) return cmp;
            // 同分按发布时间倒序（nullsLast）
            if (a.getPublishedAt() == null && b.getPublishedAt() == null) return 0;
            if (a.getPublishedAt() == null) return 1;
            if (b.getPublishedAt() == null) return -1;
            return b.getPublishedAt().compareTo(a.getPublishedAt());
        });
    }

    // ============ v0.7.3 AI 重排 + 分数推送 ============

    /**
     * 判断是否触发 AI 重排，满足条件则起 @Async 异步打分。
     *
     * <p>触发条件（任一为真即触发）：</p>
     * <ul>
     *   <li>{@code recommendPendingFlag[userId] == true}（用户改简历 / 改偏好触发）</li>
     *   <li>首次访问 + 有简历 + 缓存为空（避免老用户重复触发）</li>
     * </ul>
     *
     * <p>不触发条件：</p>
     * <ul>
     *   <li>用户未登录（candidateId == null）</li>
     *   <li>用户无 ACTIVE 简历（返回 null）</li>
     *   <li>候选集为空或超过 {@link #AI_RERANK_MAX_JOBS}</li>
     * </ul>
     */
    private void tryTriggerAiPolish(Long candidateId,
                                   List<Job> candidates,
                                   Long industryId,
                                   Long cityId,
                                   String province,
                                   List<String> tokens) {
        if (candidateId == null || candidates == null || candidates.isEmpty()) return;
        if (candidates.size() > AI_RERANK_MAX_JOBS) return;

        boolean pending = Boolean.TRUE.equals(recommendPendingFlag.get(candidateId));
        boolean firstVisitNoCache = !recommendScoresCache.containsKey(candidateId);
        if (!pending && !firstVisitNoCache) return;

        // 读 ACTIVE 简历（无则 abort）
        ResumeDto resume = null;
        try {
            resume = resumeService.getCurrentActive(candidateId);
        } catch (Exception e) {
            log.warn("[JobServiceImpl] 读简历失败 candidateId={}: {}", candidateId, e.getMessage());
        }
        if (resume == null || Boolean.TRUE.equals(resume.getArchived())) {
            return;
        }

        // 清 flag（仅 firstVisit 不清；下次按缓存命中走）
        if (pending) {
            recommendPendingFlag.put(candidateId, false);
        }

        // 切片：宽筛前 pageSize 条（防止 LLM 处理太多）
        int limit = Math.min(candidates.size(), AI_RERANK_MAX_JOBS);
        List<Job> top = new ArrayList<>(candidates.subList(0, limit));

        // 转换 Job → Markdown 列表
        String jobsContent = formatJobsForPrompt(top);
        String resumeJson;
        try {
            resumeJson = objectMapper.writeValueAsString(resume);
        } catch (JsonProcessingException e) {
            log.warn("[JobServiceImpl] 简历序列化失败 candidateId={}", candidateId);
            return;
        }

        // 偏好字段（按行业 id 取名）
        String industryName = null;
        if (industryId != null) {
            DictIndustry ind = industryMapper.selectById(industryId);
            if (ind != null) industryName = ind.getName();
        }
        String cityName = null;
        if (cityId != null) {
            DictCity city = cityMapper.selectById(cityId);
            if (city != null) cityName = city.getName();
        }
        String positionText = tokens.isEmpty() ? null : String.join(" ", tokens);

        recommendAsyncPolish(candidateId, top, resumeJson,
                positionText, industryName, province, cityName);
    }

    /**
     * 把职位列表格式化成 Markdown 行喂给 LLM（jobId / title / cityName / keywords）。
     */
    private String formatJobsForPrompt(List<Job> jobs) {
        StringBuilder sb = new StringBuilder();
        for (Job j : jobs) {
            String cityName = "（未知城市）";
            if (j.getCityId() != null) {
                DictCity c = cityMapper.selectById(j.getCityId());
                if (c != null) cityName = c.getName();
            }
            String title = j.getTitle() == null ? "" : j.getTitle();
            String keywords = j.getKeywords() == null ? "" : j.getKeywords();
            sb.append("- jobId=").append(j.getId())
                    .append(", title=").append(title)
                    .append(", city=").append(cityName)
                    .append(", keywords=").append(keywords)
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * v0.7.3：异步调 LLM 给推荐职位打分，写缓存 + SSE 推送给前端。
     *
     * <p>调用方 {@link #tryTriggerAiPolish} 已确保有 ACTIVE 简历 + 候选数 ≤ 10。</p>
     */
    @Async("taskExecutor")
    public void recommendAsyncPolish(Long candidateId,
                                    List<Job> jobs,
                                    String resumeJson,
                                    String positionText,
                                    String industryName,
                                    String province,
                                    String cityName) {
        if (candidateId == null || jobs == null || jobs.isEmpty()) return;
        String jobsContent = formatJobsForPrompt(jobs);
        Map<Long, Integer> scores = recommendService.rank(
                String.valueOf(candidateId),
                resumeJson, positionText, industryName, province, cityName,
                jobsContent);
        if (scores == null || scores.isEmpty()) {
            log.info("[JobServiceImpl] AI 重排未返回分数 candidateId={}", candidateId);
            return;
        }

        // 写缓存：覆盖 userId 整条（避免新旧分数混淆）
        ConcurrentHashMap<Long, Integer> userCache = new ConcurrentHashMap<>(scores);
        recommendScoresCache.put(candidateId, userCache);

        // SSE 推送（异步；前端 EventSource 收到 recommendation_ready 后重新拉推荐即可拿到新分数）
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("scores", scores);
            payload.put("jobsCount", jobs.size());
            sseManager.pushToUser(candidateId, "recommendation_ready", payload);
            log.info("[JobServiceImpl] AI 重排完成 candidateId={} scored={}", candidateId, scores.size());
        } catch (Exception e) {
            log.warn("[JobServiceImpl] SSE 推送失败 candidateId={}: {}", candidateId, e.getMessage());
        }
    }

    /**
     * v0.7.3：外部（ProfileServiceImpl）写入待重排 flag。
     * 用户更新简历 / 偏好时调用，下次 recommendOnResume 触发 AI。
     */
    public static void markRecommendPending(Long userId) {
        if (userId != null) recommendPendingFlag.put(userId, true);
    }

    /**
     * 职位评分（4 维度位图求和）：
     * <ul>
     *   <li>bit 0（+8）：行业命中</li>
     *   <li>bit 1（+4）：城市精确命中</li>
     *   <li>bit 2（+2）：省份命中（仅在未命中城市时计分）</li>
     *   <li>bit 3（+1）：关键词命中（任一 token 在 title/requirements/keywords 命中）</li>
     * </ul>
     * 总分越高越相关；同分按发布时间倒序（由调用方排）。
     */
    private int scoreJob(Job j, Long industryId, Long cityId, String province, List<String> tokens) {
        int score = 0;
        boolean cityMatched = false;
        if (industryId != null && industryId.equals(j.getIndustryId())) {
            score += 8;
        }
        if (cityId != null && cityId.equals(j.getCityId())) {
            score += 4;
            cityMatched = true;
        }
        if (!cityMatched && province != null && !province.isBlank()
                && province.equals(j.getProvince())) {
            score += 2;
        }
        if (!tokens.isEmpty()) {
            String title = j.getTitle() == null ? "" : j.getTitle();
            String req = j.getRequirements() == null ? "" : j.getRequirements();
            String kw = j.getKeywords() == null ? "" : j.getKeywords();
            for (String tk : tokens) {
                if (title.contains(tk) || req.contains(tk) || kw.contains(tk)) {
                    score += 1;
                    break;
                }
            }
        }
        return score;
    }

    /** 评分包装类（Java 内存排序用） */
    private static class ScoredJob {
        private final Job job;
        private final int score;

        ScoredJob(Job job, int score) {
            this.job = job;
            this.score = score;
        }

        Job getJob() { return job; }
        int getScore() { return score; }
        LocalDateTime getPublishedAt() { return job.getPublishedAt(); }
    }

    /**
     * 填充 favorited 字段（参考 listForCandidate 实现，避免 N+1）。
     */
    private void fillFavorited(IPage<JobDto> dtos, Long candidateId) {
        if (candidateId == null || dtos.getRecords() == null || dtos.getRecords().isEmpty()) return;
        List<Long> jobIds = dtos.getRecords().stream().map(JobDto::getId).collect(Collectors.toList());
        List<Long> favIds = jobMapper.selectFavoriteJobIds(candidateId, jobIds);
        java.util.Set<Long> favSet = new java.util.HashSet<>(favIds);
        for (JobDto dto : dtos.getRecords()) {
            dto.setFavorited(favSet.contains(dto.getId()));
        }
    }

    /**
     * v0.7.3：从 {@link #recommendScoresCache} 注入 AI 智能匹配度。无缓存则 aiScore 保持 null。
     */
    private void fillAiScore(IPage<JobDto> dtos, Long candidateId) {
        if (candidateId == null || dtos.getRecords() == null || dtos.getRecords().isEmpty()) return;
        ConcurrentHashMap<Long, Integer> userCache = recommendScoresCache.get(candidateId);
        if (userCache == null || userCache.isEmpty()) return;
        for (JobDto dto : dtos.getRecords()) {
            Integer score = userCache.get(dto.getId());
            if (score != null) dto.setAiScore(score);
        }
    }

    @Override
    public LlmJdService.PolishedJd polishJd(Long hrUserId,
                                              String title,
                                              String industryName,
                                              String cityName,
                                              String salaryRange,
                                              String description,
                                              String requirements,
                                              String keywords) {
        validateHr(hrUserId);
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "请填写职位标题");
        }
        return llmJdService.polish(title, industryName, cityName, salaryRange,
                description, requirements, keywords, hrUserId);
    }

    // ============ 私有方法 ============

    private void applyCreateFields(Job job, JobCreateRequest request) {
        job.setTitle(request.getTitle());
        job.setIndustryId(request.getIndustryId());
        job.setCityId(request.getCityId());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setKeywords(normalizeKeywords(request.getKeywords()));
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

    private void validateCandidate(Long candidateId) {
        User u = userMapper.selectById(candidateId);
        if (u == null || !"CANDIDATE".equals(u.getRoleCode())) {
            throw new BusinessException(403, "仅候选人可操作");
        }
    }

    private void validateSalary(Integer min, Integer max) {
        if (min != null && max != null && min > max) {
            throw new BusinessException(400, "薪资下限不能大于上限");
        }
    }

    private void validateDicts(Long industryId, Long cityId) {
        if (industryId != null && industryMapper.selectById(industryId) == null) {
            throw new BusinessException(400, "行业无效");
        }
        if (cityId != null && cityMapper.selectById(cityId) == null) {
            throw new BusinessException(400, "城市无效");
        }
    }

    /** 关键词规范化：去空白、限制最多 10 个、逗号分隔；空字符串返回 null（不入库） */
    private String normalizeKeywords(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split("[,，;；\\s]+");
        java.util.List<String> kept = new java.util.ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) kept.add(t);
        }
        if (kept.isEmpty()) return null;
        if (kept.size() > 10) kept = kept.subList(0, 10);
        return String.join(",", kept);
    }

    /**
     * 校验 cityId 与 province 一致性，返回城市所属省份（用于冗余写入 job.province）。
     *
     * <ul>
     *   <li>cityId 为 null → 不校验，province 留空</li>
     *   <li>cityId 非 null 且 province 为空 → 取城市自身 province</li>
     *   <li>两者都填 → 必须匹配</li>
     * </ul>
     */
    private String validateAndResolveProvince(Long cityId, String province) {
        if (cityId == null) {
            return province == null ? "" : province.trim();
        }
        DictCity city = cityMapper.selectById(cityId);
        if (city == null) {
            throw new BusinessException(400, "城市无效");
        }
        String cityProvince = city.getProvince() == null ? "" : city.getProvince().trim();
        if (province == null || province.isBlank()) {
            return cityProvince;
        }
        if (!cityProvince.equals(province.trim())) {
            throw new BusinessException(400, "省份与城市不匹配");
        }
        return cityProvince;
    }

    private Job requireOwned(Long hrUserId, Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "职位不存在");
        }
        if (!job.getHrUserId().equals(hrUserId)) {
            throw new BusinessException(403, "无权操作他人职位");
        }
        return job;
    }

    /**
     * 若 HR 尚未有 Company 则自动创建一个 PENDING 状态的占位。
     * 真实 HR 注册流程已建 Company（见 AuthServiceImpl），此方法仅防御性兜底。
     */
    private Long ensureCompanyForHr(Long hrUserId) {
        LambdaQueryWrapper<Company> q = new LambdaQueryWrapper<>();
        q.eq(Company::getHrUserId, hrUserId);
        Company existing = companyMapper.selectOne(q);
        if (existing != null) {
            return existing.getId();
        }
        Company c = new Company();
        c.setHrUserId(hrUserId);
        c.setName("我的公司");
        c.setAuthStatus("PENDING");
        companyMapper.insert(c);
        return c.getId();
    }

    private JobDto enrich(Job job) {
        JobDto dto = new JobDto();
        dto.setId(job.getId());
        dto.setHrUserId(job.getHrUserId());
        dto.setTitle(job.getTitle());
        dto.setIndustryId(job.getIndustryId());
        dto.setCityId(job.getCityId());
        dto.setProvince(job.getProvince());
        dto.setSalaryMin(job.getSalaryMin());
        dto.setSalaryMax(job.getSalaryMax());
        dto.setDescription(job.getDescription());
        dto.setRequirements(job.getRequirements());
        dto.setKeywords(job.getKeywords());
        dto.setStatus(job.getStatus());
        dto.setAuditStatus(job.getAuditStatus());
        dto.setAuditNote(job.getAuditNote());
        dto.setPublishedAt(job.getPublishedAt());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUpdatedAt(job.getUpdatedAt());

        if (job.getIndustryId() != null) {
            DictIndustry ind = industryMapper.selectById(job.getIndustryId());
            if (ind != null) dto.setIndustryName(ind.getName());
        }
        if (job.getCityId() != null) {
            DictCity city = cityMapper.selectById(job.getCityId());
            if (city != null) {
                dto.setCityName(city.getName());
                if (dto.getProvince() == null || dto.getProvince().isBlank()) {
                    dto.setProvince(city.getProvince());
                }
            }
        }
        if (job.getCompanyId() != null) {
            Company company = companyMapper.selectById(job.getCompanyId());
            if (company != null) dto.setCompanyName(company.getName());
        }
        return dto;
    }

    // ============ 内部使用：HR listAll（不暴露接口） ============

    /**
     * Admin / 其他场景使用：分页查询所有未软删职位。
     * 当前未在 controller 层暴露接口（审计流已取消，预留给将来需要时使用）。
     */
    public List<JobDto> listAllJobs(int limit) {
        return jobMapper.selectList(new LambdaQueryWrapper<Job>()
                .orderByDesc(Job::getUpdatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 1000))))
                .stream()
                .map(this::enrich)
                .collect(Collectors.toList());
    }
}