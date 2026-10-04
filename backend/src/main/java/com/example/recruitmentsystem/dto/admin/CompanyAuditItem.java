package com.example.recruitmentsystem.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员公司审核列表项。覆盖 UC-36。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyAuditItem {

    private Long id;

    private Long hrUserId;

    private String hrEmail;

    private String name;

    private Long industryId;

    private String industryName;

    private String scale;

    private String description;

    /** PENDING / VERIFIED / REJECTED */
    private String authStatus;

    private String authNote;

    private LocalDateTime verifiedAt;

    private LocalDateTime createdAt;
}