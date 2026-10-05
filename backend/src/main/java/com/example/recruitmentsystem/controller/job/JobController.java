package com.example.recruitmentsystem.controller.job;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.RoleCandidate;
import com.example.recruitmentsystem.common.annotation.RoleHR;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.job.FavoriteToggleResponse;
import com.example.recruitmentsystem.dto.job.JobCreateRequest;
import com.example.recruitmentsystem.dto.job.JobDto;
import com.example.recruitmentsystem.dto.job.JobUpdateRequest;
import com.example.recruitmentsystem.llm.service.LlmJdService;
import com.example.recruitmentsystem.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 职位 Controller。覆盖 UC-21/22/23/09/10/11。
 * v0.4 修订：删除 /submit /resubmit 端点（审核流已取消，HR 创建草稿后直接上线）。
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // ============ HR 端（写） ============

    @RoleHR
    @PostMapping
    public Result<JobDto> create(@Valid @RequestBody JobCreateRequest request) {
        return Result.success(jobService.createJob(CurrentUserContext.getUserId(), request));
    }

    @RoleHR
    @PutMapping("/{id}")
    public Result<JobDto> update(@PathVariable Long id, @Valid @RequestBody JobUpdateRequest request) {
        return Result.success(jobService.updateJob(CurrentUserContext.getUserId(), id, request));
    }

    @RoleHR
    @PostMapping("/{id}/publish")
    public Result<JobDto> publish(@PathVariable Long id) {
        return Result.success(jobService.publishJob(CurrentUserContext.getUserId(), id));
    }

    @RoleHR
    @PostMapping("/{id}/offline")
    public Result<JobDto> offline(@PathVariable Long id) {
        return Result.success(jobService.offlineJob(CurrentUserContext.getUserId(), id));
    }

    @RoleHR
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.deleteJob(CurrentUserContext.getUserId(), id);
        return Result.success();
    }

    @RoleHR
    @GetMapping("/mine")
    public Result<IPage<JobDto>> listMine(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        // v0.4：仅 3 个 tab（DRAFT / ONLINE / OFFLINE），按 status 字段严格区分
        if (status != null && !status.isBlank()) {
            return Result.success(jobService.listMineByTab(CurrentUserContext.getUserId(), status.trim(), pageNum, pageSize));
        }
        // 不传 status 时默认返回 DRAFT tab
        return Result.success(jobService.listMineByTab(CurrentUserContext.getUserId(), "DRAFT", pageNum, pageSize));
    }

    @RoleHR
    @PostMapping("/polish")
    public Result<LlmJdService.PolishedJd> polish(@RequestBody PolishReq body) {
        String salaryRange = (body.salaryMin != null && body.salaryMax != null)
                ? body.salaryMin + "-" + body.salaryMax + "K"
                : (body.salaryMin != null ? body.salaryMin + "K 起"
                : (body.salaryMax != null ? body.salaryMax + "K 以内" : null));
        LlmJdService.PolishedJd r = jobService.polishJd(
                CurrentUserContext.getUserId(),
                body.title,
                body.industryName,
                body.cityName,
                salaryRange,
                body.description,
                body.requirements,
                body.keywords);
        return Result.success(r);
    }

    public static class PolishReq {
        public String title;
        public String industryName;
        public String cityName;
        public Integer salaryMin;
        public Integer salaryMax;
        public String description;
        public String requirements;
        public String keywords;
    }

    // ============ 公开 / 候选人端（读） ============

    @GetMapping
    public Result<IPage<JobDto>> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Long industryId,
                                       @RequestParam(required = false) Long cityId,
                                       @RequestParam(required = false) String province,
                                       @RequestParam(required = false) String cityName,
                                       @RequestParam(required = false) Boolean favoritedOnly,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "10") Integer pageSize) {
        // 匿名用户也能浏览，但只有候选人才能填充 favorited / 触发 favoritedOnly
        Long requesterId = CurrentUserContext.getUserId();
        String role = CurrentUserContext.getRole();
        Long candidateId = "CANDIDATE".equals(role) ? requesterId : null;
        return Result.success(jobService.listForCandidate(
                keyword, industryId, cityId, province, cityName,
                favoritedOnly, candidateId, sort, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<JobDto> detail(@PathVariable Long id) {
        return Result.success(jobService.getDetail(
                CurrentUserContext.getUserId(),
                CurrentUserContext.getRole(),
                id));
    }

    @RoleCandidate
    @PostMapping("/{id}/favorite")
    public Result<FavoriteToggleResponse> favorite(@PathVariable Long id) {
        boolean favorited = jobService.toggleFavorite(CurrentUserContext.getUserId(), id);
        return Result.success(new FavoriteToggleResponse(id, favorited));
    }
}
