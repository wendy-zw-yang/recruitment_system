package com.example.recruitmentsystem.dto.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 投递请求（候选人侧）。
 *
 * <p>校验：jobId 必填；coverLetter 可选，≤ 1000 字。</p>
 */
@Data
public class ApplyRequest {

    @NotNull(message = "请选择要投递的职位")
    private Long jobId;

    /** 求职信（可选） */
    @Size(max = 1000, message = "求职信不能超过 1000 字")
    private String coverLetter;
}
