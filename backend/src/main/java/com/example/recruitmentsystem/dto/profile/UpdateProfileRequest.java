package com.example.recruitmentsystem.dto.profile;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求（昵称 / 手机）。
 *
 * <p>邮箱不允许修改（唯一标识符）。</p>
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 64, message = "昵称不能超过 64 字")
    private String username;

    @Size(max = 32, message = "手机号不能超过 32 字")
    private String phone;
}
