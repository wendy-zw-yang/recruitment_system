package com.example.recruitmentsystem.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员用户列表项。覆盖 {@code docs/系统设计/详细设计/管理员.md §7.2.1}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListItem {

    private Long id;

    private String email;

    private String username;

    /** CANDIDATE / HR / ADMIN */
    private String roleCode;

    /** ENABLED / DISABLED */
    private String status;

    private String phone;

    private LocalDateTime createdAt;
}