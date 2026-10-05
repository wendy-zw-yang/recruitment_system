package com.example.recruitmentsystem.controller.profile;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.LoginRequired;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.profile.CandidatePreferenceDto;
import com.example.recruitmentsystem.dto.profile.ChangePasswordRequest;
import com.example.recruitmentsystem.dto.profile.CompanySelfDto;
import com.example.recruitmentsystem.dto.profile.ProfileDto;
import com.example.recruitmentsystem.dto.profile.UpdateProfileRequest;
import com.example.recruitmentsystem.service.profile.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心 Controller。
 *
 * <p>路径按"自己"语义组织（{@code /me}），区别于管理员的 {@code /admin/users/audit}。</p>
 *
 * <p>所有接口必须登录（{@link LoginRequired}），按角色自动限定：
 *  - 个人资料 / 修改密码：所有人
 *  - 求职偏好：候选人（{@code CANDIDATE}）
 *  - 公司信息：HR</p>
 */
@LoginRequired
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    // ============ 个人资料（所有人） ============

    @GetMapping("/me")
    public Result<ProfileDto> getMe() {
        return Result.success(profileService.getProfile(CurrentUserContext.getUserId()));
    }

    @PutMapping("/me")
    public Result<ProfileDto> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success("个人资料已保存",
                profileService.updateProfile(CurrentUserContext.getUserId(), request));
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(CurrentUserContext.getUserId(), request);
        return Result.success("密码已修改", null);
    }

    // ============ 候选人求职偏好 ============

    @GetMapping("/candidate/preference")
    public Result<CandidatePreferenceDto> getPreference() {
        if (!"CANDIDATE".equals(CurrentUserContext.getRole())) {
            throw new com.example.recruitmentsystem.common.exception.BusinessException(403, "仅候选人可访问");
        }
        return Result.success(profileService.getPreference(CurrentUserContext.getUserId()));
    }

    @PutMapping("/candidate/preference")
    public Result<CandidatePreferenceDto> updatePreference(@RequestBody CandidatePreferenceDto dto) {
        if (!"CANDIDATE".equals(CurrentUserContext.getRole())) {
            throw new com.example.recruitmentsystem.common.exception.BusinessException(403, "仅候选人可访问");
        }
        return Result.success("求职偏好已保存",
                profileService.updatePreference(CurrentUserContext.getUserId(), dto));
    }

    // ============ HR 公司自管理 ============

    @GetMapping("/company/me")
    public Result<CompanySelfDto> getMyCompany() {
        if (!"HR".equals(CurrentUserContext.getRole())) {
            throw new com.example.recruitmentsystem.common.exception.BusinessException(403, "仅 HR 可访问");
        }
        return Result.success(profileService.getMyCompany(CurrentUserContext.getUserId()));
    }

    @PutMapping("/company/me")
    public Result<CompanySelfDto> updateMyCompany(@RequestBody CompanySelfDto dto) {
        if (!"HR".equals(CurrentUserContext.getRole())) {
            throw new com.example.recruitmentsystem.common.exception.BusinessException(403, "仅 HR 可访问");
        }
        return Result.success("公司信息已保存",
                profileService.updateMyCompany(CurrentUserContext.getUserId(), dto));
    }
}
