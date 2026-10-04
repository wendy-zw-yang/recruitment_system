package com.example.recruitmentsystem.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公司审核驳回请求。必填理由；可选同步禁用 HR 账号。
 * 覆盖 {@code docs/系统设计/详细设计/管理员.md §7.2.3}。
 */
@Data
public class CompanyRejectRequest {

    @NotBlank
    @Size(max = 512, message = "理由过长")
    private String note;

    /** true = 同步禁用 HR 账号（{@code users.status = DISABLED}） */
    private Boolean disableHr;
}