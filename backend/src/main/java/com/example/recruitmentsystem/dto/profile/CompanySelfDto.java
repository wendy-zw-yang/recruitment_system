package com.example.recruitmentsystem.dto.profile;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * HR 自管理公司信息 DTO。
 *
 * <p>字段与 {@code company} 表对应：
 *  - name / industryId / scale / description 由 HR 自行维护
 *  - authStatus 由管理员审核写入，HR 端只读展示</p>
 */
@Data
public class CompanySelfDto {

    private Long id;
    private String name;

    /** 行业 → {@code dict_industry.id}；HR 端可不选（保留 null） */
    private Long industryId;

    /** 行业名（用于展示，HR 不传则后端根据 industryId 查） */
    private String industryName;

    @Size(max = 32)
    private String scale;

    private String description;

    /** PENDING / VERIFIED / REJECTED —— 仅展示，HR 不能修改 */
    private String authStatus;

    /** 驳回理由（VERIFIED/REJECTED 时有值） */
    private String authNote;
}
