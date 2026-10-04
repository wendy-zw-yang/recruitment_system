package com.example.recruitmentsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.recruitmentsystem.common.exception.BusinessException;
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
import com.example.recruitmentsystem.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    private final LlmJdService llmJdService;

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