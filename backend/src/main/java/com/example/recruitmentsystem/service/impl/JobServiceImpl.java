package com.example.recruitmentsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.job.JobAuditRequest;
import com.example.recruitmentsystem.dto.job.JobCreateRequest;
import com.example.recruitmentsystem.dto.job.JobDto;
import com.example.recruitmentsystem.dto.job.JobUpdateRequest;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.DictCity;
import com.example.recruitmentsystem.entity.DictIndustry;
import com.example.recruitmentsystem.entity.FavoriteJob;
import com.example.recruitmentsystem.entity.Job;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.llm.service.LlmJdService;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.DictCityMapper;
import com.example.recruitmentsystem.mapper.DictIndustryMapper;
import com.example.recruitmentsystem.mapper.FavoriteJobMapper;
import com.example.recruitmentsystem.mapper.JobMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.AuditLogService;
import com.example.recruitmentsystem.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JobService 实现。详见 {@code docs/系统设计/详细设计/职位与公司.md §3.3}。
 *
 * <p>状态机（status）：</p>
 * <ul>
 *   <li>DRAFT → ONLINE（需 audit_status=APPROVED）</li>
 *   <li>ONLINE → OFFLINE</li>
 *   <li>DRAFT / OFFLINE → DELETED（软删）</li>
 * </ul>
 *
 * <p>审核状态（audit_status）：PENDING → APPROVED / REJECTED（仅 Admin 可改）</p>
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
    private final LlmJdService llmJdService;
    private final AuditLogService auditLogService;

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
        job.setAuditStatus("PENDING");
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
        // 编辑后重置为待审核
        job.setAuditStatus("PENDING");
        job.setAuditNote(null);
        jobMapper.updateById(job);
        return enrich(job);
    }

    @Override
    @Transactional
    public JobDto publishJob(Long hrUserId, Long jobId) {
        Job job = requireOwned(hrUserId, jobId);
        if (!"APPROVED".equals(job.getAuditStatus())) {
            throw new BusinessException(400, "职位尚未通过审核，无法上线");
        }
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
        job.setStatus("DELETED");
        jobMapper.updateById(job);
    }

    @Override
    public IPage<JobDto> listMineByStatus(Long hrUserId, String status, int pageNum, int pageSize) {
        validateHr(hrUserId);
        Page<Job> page = new Page<>(pageNum, pageSize);
        IPage<Job> raw = jobMapper.selectPageByHr(page, hrUserId, status);
        return raw.convert(this::enrich);
    }

    // ============ 候选人端 ============

    @Override
    public IPage<JobDto> listForCandidate(String keyword, Long industryId, Long cityId, String province, String sort,
                                          int pageNum, int pageSize) {
        Page<Job> page = new Page<>(pageNum, pageSize);
        IPage<Job> raw = jobMapper.selectPageForCandidate(page, keyword, industryId, cityId, province, sort);
        return raw.convert(this::enrich);
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

    // ============ Admin 端 ============

    @Override
    public IPage<JobDto> listPendingAudit(int pageNum, int pageSize) {
        Page<Job> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Job> q = new LambdaQueryWrapper<>();
        q.eq(Job::getAuditStatus, "PENDING")
                .eq(Job::getStatus, "DRAFT")
                .orderByDesc(Job::getUpdatedAt);
        IPage<Job> raw = jobMapper.selectPage(page, q);
        return raw.convert(this::enrich);
    }

    @Override
    @Transactional
    public JobDto auditJob(Long adminId, Long jobId, JobAuditRequest request) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "职位不存在");
        }
        if (!"PENDING".equals(job.getAuditStatus())) {
            throw new BusinessException(400, "该职位不处于待审核状态");
        }
        boolean approve = request.getApprove() != null && request.getApprove();
        Job update = new Job();
        update.setId(jobId);
        if (approve) {
            update.setAuditStatus("APPROVED");
            update.setAuditNote(null);
        } else {
            if (request.getNote() == null || request.getNote().isBlank()) {
                throw new BusinessException(400, "驳回必须填写理由");
            }
            update.setAuditStatus("REJECTED");
            update.setAuditNote(request.getNote());
        }
        jobMapper.updateById(update);
        auditLogService.record(adminId,
                approve ? "JOB_APPROVE" : "JOB_REJECT",
                jobId, "JOB", null,
                approve ? "APPROVED" : "REJECTED",
                request.getNote());
        Job reloaded = jobMapper.selectById(jobId);
        return enrich(reloaded);
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
        if (existing != null) return existing.getId();

        User u = userMapper.selectById(hrUserId);
        Company c = new Company();
        c.setHrUserId(hrUserId);
        c.setName(u.getUsername() != null ? u.getUsername() + " 的公司" : "未命名公司");
        c.setAuthStatus("PENDING");
        companyMapper.insert(c);
        return c.getId();
    }

    /**
     * 注入 companyName / industryName / cityName 等展示字段。
     * 列表场景下用批量查表优化：候选端 listForCandidate 一次 SELECT 全部命中。
     */
    private JobDto enrich(Job job) {
        if (job == null) return null;
        JobDto dto = JobDto.from(job);
        Company c = companyMapper.selectById(job.getCompanyId());
        if (c != null) dto.setCompanyName(c.getName());
        if (job.getIndustryId() != null) {
            DictIndustry ind = industryMapper.selectById(job.getIndustryId());
            if (ind != null) dto.setIndustryName(ind.getName());
        }
        if (job.getCityId() != null) {
            DictCity city = cityMapper.selectById(job.getCityId());
            if (city != null) dto.setCityName(city.getName());
        }
        return dto;
    }

    /**
     * 列表场景批量 enrich：减少 N+1 查询。
     */
    private List<JobDto> enrichBatch(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) return List.of();
        Set<Long> companyIds = jobs.stream().map(Job::getCompanyId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> industryIds = jobs.stream().map(Job::getIndustryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> cityIds = jobs.stream().map(Job::getCityId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> companyNames = new HashMap<>();
        if (!companyIds.isEmpty()) {
            List<Company> list = companyMapper.selectBatchIds(companyIds);
            for (Company c : list) companyNames.put(c.getId(), c.getName());
        }
        Map<Long, String> industryNames = new HashMap<>();
        if (!industryIds.isEmpty()) {
            List<DictIndustry> list = industryMapper.selectBatchIds(industryIds);
            for (DictIndustry i : list) industryNames.put(i.getId(), i.getName());
        }
        Map<Long, String> cityNames = new HashMap<>();
        if (!cityIds.isEmpty()) {
            List<DictCity> list = cityMapper.selectBatchIds(cityIds);
            for (DictCity c : list) cityNames.put(c.getId(), c.getName());
        }

        List<JobDto> result = new java.util.ArrayList<>(jobs.size());
        for (Job job : jobs) {
            JobDto dto = JobDto.from(job);
            if (job.getCompanyId() != null) dto.setCompanyName(companyNames.get(job.getCompanyId()));
            if (job.getIndustryId() != null) dto.setIndustryName(industryNames.get(job.getIndustryId()));
            if (job.getCityId() != null) dto.setCityName(cityNames.get(job.getCityId()));
            result.add(dto);
        }
        return result;
    }

    // 暴露批量 enrich 给 Controller 在列表场景使用（避免 N+1）
    public List<JobDto> enrichList(List<Job> jobs) {
        return enrichBatch(jobs);
    }
}
