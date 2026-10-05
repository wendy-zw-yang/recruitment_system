package com.example.recruitmentsystem.dto.application;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推进状态响应。
 */
@Data
public class AdvanceStatusResponse {

    private Long applicationId;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime updatedAt;
}
