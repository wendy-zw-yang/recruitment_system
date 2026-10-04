package com.example.recruitmentsystem.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重置密码响应。仅此一次返回明文临时密码，管理员需自行转告用户。
 * 详见 {@code docs/系统设计/详细设计/管理员.md §7.3.1}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordResponse {

    private Long userId;

    /** 明文临时密码（6 位随机） */
    private String tempPassword;
}