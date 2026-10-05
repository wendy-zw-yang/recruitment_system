package com.example.recruitmentsystem.service.profile;

import com.example.recruitmentsystem.dto.profile.CandidatePreferenceDto;
import com.example.recruitmentsystem.dto.profile.ChangePasswordRequest;
import com.example.recruitmentsystem.dto.profile.CompanySelfDto;
import com.example.recruitmentsystem.dto.profile.ProfileDto;
import com.example.recruitmentsystem.dto.profile.UpdateProfileRequest;

/**
 * 个人中心业务接口（覆盖 Profile.vue 4 个 tab）。
 *
 * <p>覆盖：
 *  - 个人资料（昵称 / 手机）
 *  - 账号安全（修改密码）
 *  - 候选人：求职偏好
 *  - HR：公司自管理</p>
 */
public interface ProfileService {

    /** 获取当前登录用户资料 */
    ProfileDto getProfile(Long userId);

    /** 修改昵称 / 手机 */
    ProfileDto updateProfile(Long userId, UpdateProfileRequest request);

    /** 修改密码（验证旧密码 → BCrypt 编码新密码） */
    void changePassword(Long userId, ChangePasswordRequest request);

    /** 获取候选人求职偏好 */
    CandidatePreferenceDto getPreference(Long candidateId);

    /** 更新候选人求职偏好（仅 expectedPosition 持久化；其他字段前端展示） */
    CandidatePreferenceDto updatePreference(Long candidateId, CandidatePreferenceDto dto);

    /** 获取 HR 自管理公司信息 */
    CompanySelfDto getMyCompany(Long hrUserId);

    /** 更新 HR 公司信息（authStatus / authNote 不允许修改） */
    CompanySelfDto updateMyCompany(Long hrUserId, CompanySelfDto dto);
}
