package com.example.recruitmentsystem.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.RoleAdmin;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.admin.ChangeRoleRequest;
import com.example.recruitmentsystem.dto.admin.ResetPasswordResponse;
import com.example.recruitmentsystem.dto.admin.UserListItem;
import com.example.recruitmentsystem.dto.admin.UserListQuery;
import com.example.recruitmentsystem.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员端用户管理。覆盖 UC-34。
 *
 * <p>5 个端点：分页 / enable / disable / resetPassword / changeRole</p>
 */
@RoleAdmin
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/list")
    public Result<IPage<UserListItem>> list(@RequestBody(required = false) UserListQuery query) {
        if (query == null) {
            query = new UserListQuery();
        }
        return Result.success(adminUserService.listUsers(query));
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        adminUserService.enable(CurrentUserContext.getUserId(), id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        adminUserService.disable(CurrentUserContext.getUserId(), id);
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    public Result<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return Result.success(adminUserService.resetPassword(CurrentUserContext.getUserId(), id));
    }

    @PutMapping("/{id}/role")
    public Result<Void> changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request) {
        adminUserService.changeRole(CurrentUserContext.getUserId(), id, request);
        return Result.success();
    }
}