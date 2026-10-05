package com.example.recruitmentsystem.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求（需提供旧密码做身份校验）。
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "请输入当前密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 64, message = "新密码长度需在 8-64 之间")
    private String newPassword;
}
