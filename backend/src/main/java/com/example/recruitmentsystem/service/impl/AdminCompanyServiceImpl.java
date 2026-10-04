package com.example.recruitmentsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.admin.CompanyAuditItem;
import com.example.recruitmentsystem.dto.admin.CompanyRejectRequest;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.DictIndustry;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.DictIndustryMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.AdminCompanyService;
import com.example.recruitmentsystem.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AdminCompanyService 实现。详见 {@code docs/系统设计/详细设计/管理员.md §7.2.3 / §7.3.3}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCompanyServiceImpl implements AdminCompanyService {

    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;
    private final DictIndustryMapper industryMapper;
    private final AuditLogService auditLogService;

    @Override
    public IPage<CompanyAuditItem> listCompanies(String authStatus, int pageNum, int pageSize) {
        int pn = pageNum < 1 ? 1 : pageNum;
        int ps = pageSize < 1 ? 10 : pageSize;
        ps = Math.min(ps, 100); // 安全上限

        LambdaQueryWrapper<Company> q = new LambdaQueryWrapper<>();
        if (authStatus != null && !authStatus.isBlank()) {
            q.eq(Company::getAuthStatus, authStatus.trim());
        }
        q.orderByAsc(Company::getCreatedAt);

        IPage<Company> page = companyMapper.selectPage(new Page<>(pn, ps), q);

        // 批量取 HR email 与 industry name
        Set<Long> hrIds = page.getRecords().stream().map(Company::getHrUserId).collect(Collectors.toSet());
        Set<Long> indIds = page.getRecords().stream().map(Company::getIndustryId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> hrEmailMap = new HashMap<>();
        if (!hrIds.isEmpty()) {
            userMapper.selectBatchIds(hrIds).forEach(u -> hrEmailMap.put(u.getId(), u.getEmail()));
        }
        Map<Long, String> indNameMap = new HashMap<>();
        if (!indIds.isEmpty()) {
            industryMapper.selectBatchIds(indIds).forEach(i -> indNameMap.put(i.getId(), i.getName()));
        }

        List<CompanyAuditItem> records = page.getRecords().stream().map(c ->
                CompanyAuditItem.builder()
                        .id(c.getId())
                        .hrUserId(c.getHrUserId())
                        .hrEmail(hrEmailMap.get(c.getHrUserId()))
                        .name(c.getName())
                        .industryId(c.getIndustryId())
                        .industryName(c.getIndustryId() == null ? null : indNameMap.get(c.getIndustryId()))
                        .scale(c.getScale())
                        .description(c.getDescription())
                        .authStatus(c.getAuthStatus())
                        .authNote(c.getAuthNote())
                        .verifiedAt(c.getVerifiedAt())
                        .createdAt(c.getCreatedAt())
                        .build()
        ).collect(Collectors.toList());

        IPage<CompanyAuditItem> result = new Page<>(pn, ps, page.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional
    public void verify(Long adminId, Long companyId) {
        Company c = requireCompany(companyId);
        if (!"PENDING".equals(c.getAuthStatus())) {
            throw new BusinessException(400, "该公司不处于待审核状态");
        }
        c.setAuthStatus("VERIFIED");
        c.setAuthNote(null);
        c.setVerifiedAt(LocalDateTime.now());
        c.setVerifiedBy(adminId);
        companyMapper.updateById(c);

        auditLogService.record(adminId, "COMPANY_VERIFY", companyId, "COMPANY",
                "PENDING", "VERIFIED", null);
    }

    @Override
    @Transactional
    public void reject(Long adminId, Long companyId, CompanyRejectRequest request) {
        if (request.getNote() == null || request.getNote().isBlank()) {
            throw new BusinessException(400, "请填写驳回理由");
        }
        Company c = requireCompany(companyId);
        if (!"PENDING".equals(c.getAuthStatus())) {
            throw new BusinessException(400, "该公司不处于待审核状态");
        }
        c.setAuthStatus("REJECTED");
        c.setAuthNote(request.getNote());
        c.setVerifiedAt(LocalDateTime.now());
        c.setVerifiedBy(adminId);
        companyMapper.updateById(c);

        auditLogService.record(adminId, "COMPANY_REJECT", companyId, "COMPANY",
                "PENDING", "REJECTED", request.getNote());

        // 同步禁用 HR 账号
        if (Boolean.TRUE.equals(request.getDisableHr()) && c.getHrUserId() != null) {
            User hr = userMapper.selectById(c.getHrUserId());
            if (hr != null && "ENABLED".equals(hr.getStatus())) {
                hr.setStatus("DISABLED");
                userMapper.updateById(hr);
                auditLogService.record(adminId, "USER_DISABLE", hr.getId(), "USER",
                        "ENABLED", "DISABLED",
                        "公司驳回联动禁用 HR 账号：" + request.getNote());
                log.info("[AdminCompanyService.reject] adminId={} 联动禁用 HR hrUserId={}", adminId, hr.getId());
            }
        }
    }

    // ============ 私有 ============

    private Company requireCompany(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(400, "公司 id 不能为空");
        }
        Company c = companyMapper.selectById(companyId);
        if (c == null) {
            throw new BusinessException(404, "公司不存在");
        }
        return c;
    }
}