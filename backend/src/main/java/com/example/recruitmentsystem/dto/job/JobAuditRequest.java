package com.example.recruitmentsystem.dto.job;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员审核请求：驳回时必填理由；通过时允许备注但非必填。
 */
@Data
public class JobAuditRequest {

    /** true=通过 / false=驳回 */
    private Boolean approve;

    @Size(max = 512, message = "备注过长")
    private String note;
}
