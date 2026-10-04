package com.example.recruitmentsystem.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改角色请求。仅允许 CANDIDATE ↔ HR 切换，ADMIN 不可改。
 */
@Data
public class ChangeRoleRequest {

    @NotBlank
    @Pattern(regexp = "CANDIDATE|HR", message = "仅允许在 CANDIDATE 与 HR 之间切换")
    private String roleCode;
}