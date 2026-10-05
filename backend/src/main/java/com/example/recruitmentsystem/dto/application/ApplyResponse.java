package com.example.recruitmentsystem.dto.application;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递响应（POST /api/applications 返回）。
 *
 * <p>投递成功后立即返回，{@code aiScore} 通常为 NULL（AI-2 异步评分中）。
 * 前端通过轮询 GET /api/applications/{id} 获取最终分数。</p>
 */
@Data
public class ApplyResponse {

    private Long applicationId;
    private String status;
    private Integer aiScore;
    private LocalDateTime appliedAt;
}
