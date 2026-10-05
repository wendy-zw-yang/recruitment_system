package com.example.recruitmentsystem.controller.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.RoleCandidate;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.application.ApplicationDetailDto;
import com.example.recruitmentsystem.dto.application.ApplicationDto;
import com.example.recruitmentsystem.dto.application.ApplyRequest;
import com.example.recruitmentsystem.dto.application.ApplyResponse;
import com.example.recruitmentsystem.dto.application.WithdrawResponse;
import com.example.recruitmentsystem.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 候选人投递 Controller。覆盖 UC-12 投递 / UC-13 我的投递 / UC-14 撤回。
 *
 * <p>HR 端入口在 {@link HrApplicationController}。</p>
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    /** UC-12 投递职位。AI-2 评分异步触发，立即返回。 */
    @RoleCandidate
    @PostMapping
    public Result<ApplyResponse> apply(@Valid @RequestBody ApplyRequest request) {
        return Result.success("投递成功，AI 评分中", applicationService.apply(
                CurrentUserContext.getUserId(), request));
    }

    /** UC-13 我的投递列表。候选人侧隐藏 VIEWED_BY_HR。 */
    @RoleCandidate
    @GetMapping("/mine")
    public Result<IPage<ApplicationDto>> listMine(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(applicationService.listMine(CurrentUserContext.getUserId(), pageNum, pageSize));
    }

    /** 投递详情（候选人本人 / HR / 管理员可访问）。 */
    @GetMapping("/{id}")
    public Result<ApplicationDetailDto> detail(@PathVariable Long id) {
        return Result.success(applicationService.getDetail(
                CurrentUserContext.getUserId(),
                CurrentUserContext.getRole(),
                id));
    }

    /** UC-14 撤回投递（仅候选人本人 + 非终态）。 */
    @RoleCandidate
    @PostMapping("/{id}/withdraw")
    public Result<WithdrawResponse> withdraw(@PathVariable Long id) {
        return Result.success("已撤回", applicationService.withdraw(CurrentUserContext.getUserId(), id));
    }
}
