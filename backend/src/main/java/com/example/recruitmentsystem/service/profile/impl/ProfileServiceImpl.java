package com.example.recruitmentsystem.service.profile.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.profile.CandidatePreferenceDto;
import com.example.recruitmentsystem.dto.profile.ChangePasswordRequest;
import com.example.recruitmentsystem.dto.profile.CompanySelfDto;
import com.example.recruitmentsystem.dto.profile.ProfileDto;
import com.example.recruitmentsystem.dto.profile.UpdateProfileRequest;
import com.example.recruitmentsystem.entity.CandidateProfile;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.DictIndustry;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.CandidateProfileMapper;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.DictIndustryMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.profile.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 个人中心业务实现。
 *
 * <p>所有"修改"接口都基于 {@code userId} / {@code candidateId} / {@code hrUserId}，
 * 这些 ID 由 controller 通过 {@link com.example.recruitmentsystem.common.context.CurrentUserContext}
 * 拿到，service 不再校验 role（由 controller 路由上的注解保证）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final CandidateProfileMapper candidateProfileMapper;
    private final CompanyMapper companyMapper;
    private final DictIndustryMapper dictIndustryMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    // ============ 个人资料 ============

    @Override
    public ProfileDto getProfile(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(404, "用户不存在");
        }
        ProfileDto dto = new ProfileDto();
        dto.setId(u.getId());
        dto.setEmail(u.getEmail());
        dto.setUsername(u.getUsername());
        dto.setPhone(u.getPhone());
        dto.setRoleCode(u.getRoleCode());
        dto.setStatus(u.getStatus());
        return dto;
    }

    @Override
    @Transactional
    public ProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 空字符串视为"未提供"，不覆盖；null 也不更新（前端没传的字段）
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            u.setUsername(request.getUsername().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            u.setPhone(request.getPhone().trim());
        }
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        return getProfile(userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 校验旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), u.getPasswordHash())) {
            throw new BusinessException(400, "当前密码不正确");
        }
        // 旧新密码相同：直接拒绝
        if (passwordEncoder.matches(request.getNewPassword(), u.getPasswordHash())) {
            throw new BusinessException(400, "新密码不能与当前密码相同");
        }
        u.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
    }

    // ============ 候选人求职偏好 ============

    @Override
    public CandidatePreferenceDto getPreference(Long candidateId) {
        CandidatePreferenceDto dto = new CandidatePreferenceDto();
        CandidateProfile p = candidateProfileMapper.selectOne(new LambdaQueryWrapper<CandidateProfile>()
                .eq(CandidateProfile::getUserId, candidateId));
        if (p != null) {
            dto.setExpectedPosition(p.getExpectedPosition());
        }
        // expectedIndustry / expectedCity 当前为前端展示占位字段，不持久化
        return dto;
    }

    @Override
    @Transactional
    public CandidatePreferenceDto updatePreference(Long candidateId, CandidatePreferenceDto req) {
        CandidateProfile p = candidateProfileMapper.selectOne(new LambdaQueryWrapper<CandidateProfile>()
                .eq(CandidateProfile::getUserId, candidateId));
        LocalDateTime now = LocalDateTime.now();
        if (p == null) {
            p = new CandidateProfile();
            p.setUserId(candidateId);
            p.setCreatedAt(now);
        }
        // 仅持久化 expectedPosition（schema 有此字段）
        if (req.getExpectedPosition() != null) {
            p.setExpectedPosition(req.getExpectedPosition().isBlank() ? null : req.getExpectedPosition().trim());
        }
        // expectedIndustry / expectedCity 前端仅展示；后续如需持久化需加 schema 列
        p.setUpdatedAt(now);
        if (p.getId() == null) {
            candidateProfileMapper.insert(p);
        } else {
            candidateProfileMapper.updateById(p);
        }
        return getPreference(candidateId);
    }

    // ============ HR 公司自管理 ============

    @Override
    public CompanySelfDto getMyCompany(Long hrUserId) {
        Company c = companyMapper.selectOne(new LambdaQueryWrapper<Company>()
                .eq(Company::getHrUserId, hrUserId));
        if (c == null) {
            // HR 注册时一般会同步创建 company；此处兜底返回空对象（前端按空状态展示）
            CompanySelfDto empty = new CompanySelfDto();
            empty.setAuthStatus("PENDING");
            return empty;
        }
        return toDto(c);
    }

    @Override
    @Transactional
    public CompanySelfDto updateMyCompany(Long hrUserId, CompanySelfDto req) {
        Company c = companyMapper.selectOne(new LambdaQueryWrapper<Company>()
                .eq(Company::getHrUserId, hrUserId));
        LocalDateTime now = LocalDateTime.now();
        if (c == null) {
            // 兜底：HR 公司不存在则新建（防御性，应在 HR 注册时已创建）
            c = new Company();
            c.setHrUserId(hrUserId);
            c.setAuthStatus("PENDING");
            c.setCreatedAt(now);
        }
        // 仅允许 HR 改的字段
        if (req.getName() != null) c.setName(req.getName().trim());
        if (req.getIndustryId() != null) c.setIndustryId(req.getIndustryId());
        if (req.getScale() != null) c.setScale(req.getScale().trim());
        if (req.getDescription() != null) c.setDescription(req.getDescription());
        // authStatus / authNote 不允许 HR 改（仅管理员审核可写）
        c.setUpdatedAt(now);
        if (c.getId() == null) {
            companyMapper.insert(c);
        } else {
            companyMapper.updateById(c);
        }
        return toDto(c);
    }

    /** Company → CompanySelfDto（含行业名展示） */
    private CompanySelfDto toDto(Company c) {
        CompanySelfDto dto = new CompanySelfDto();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setIndustryId(c.getIndustryId());
        if (c.getIndustryId() != null) {
            DictIndustry ind = dictIndustryMapper.selectById(c.getIndustryId());
            if (ind != null) dto.setIndustryName(ind.getName());
        }
        dto.setScale(c.getScale());
        dto.setDescription(c.getDescription());
        dto.setAuthStatus(c.getAuthStatus());
        dto.setAuthNote(c.getAuthNote());
        return dto;
    }
}
