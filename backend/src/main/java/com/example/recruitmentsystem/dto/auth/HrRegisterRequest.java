package com.example.recruitmentsystem.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * UC-02 HR 注册请求（邮箱 + 密码 + 公司信息同步）。
 */
@Data
public class HrRegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码至少 8 位")
    private String password;

    // 公司信息（HR 注册时同步创建）
    @NotBlank(message = "公司名不能为空")
    private String companyName;

    private String industry;
    private String scale;
    private String description;
}
