package com.example.recruitmentsystem.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.RoleAdmin;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.job.JobAuditRequest;
import com.example.recruitmentsystem.dto.job.JobDto;
import com.example.recruitmentsystem.service.JobService;
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
 * 管理员端职位审核。覆盖 UC-35。
 */
@RoleAdmin
@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

    private final JobService jobService;

    @GetMapping("/audit/pending")
    public Result<IPage<JobDto>> pending(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(jobService.listPendingAudit(pageNum, pageSize));
    }

    @PostMapping("/{id}/audit")
    public Result<JobDto> audit(@PathVariable Long id, @Valid @RequestBody JobAuditRequest request) {
        return Result.success(jobService.auditJob(CurrentUserContext.getUserId(), id, request));
    }
}
