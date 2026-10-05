package com.example.recruitmentsystem.dto.application;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 撤回投递响应。
 */
@Data
public class WithdrawResponse {

    private Long applicationId;
    private String status;
    private LocalDateTime updatedAt;
}
