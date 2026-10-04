package com.example.recruitmentsystem.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.RoleAdmin;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.admin.CompanyAuditItem;
import com.example.recruitmentsystem.dto.admin.CompanyRejectRequest;
import com.example.recruitmentsystem.service.AdminCompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员端公司审核。覆盖 UC-36。
 *
 * <p>3 个端点：list / verify / reject</p>
 */
@RoleAdmin
@RestController
@RequestMapping("/api/admin/companies")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final AdminCompanyService adminCompanyService;

    @PostMapping("/list")
    public Result<IPage<CompanyAuditItem>> list(
            @RequestParam(required = false, defaultValue = "PENDING") String authStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(adminCompanyService.listCompanies(authStatus, pageNum, pageSize));
    }

    @PostMapping("/{id}/verify")
    public Result<Void> verify(@PathVariable Long id) {
        adminCompanyService.verify(CurrentUserContext.getUserId(), id);
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody CompanyRejectRequest request) {
        adminCompanyService.reject(CurrentUserContext.getUserId(), id, request);
        return Result.success();
    }
}