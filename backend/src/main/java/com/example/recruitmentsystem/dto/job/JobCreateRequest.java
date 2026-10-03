package com.example.recruitmentsystem.dto.job;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobCreateRequest {

    @NotBlank(message = "请填写职位标题")
    @Size(max = 255, message = "标题过长")
    private String title;

    private Long industryId;

    /** 城市 ID；同时需传 {@link #province} */
    private Long cityId;

    /** 省份 / 直辖市（与 cityId 所属省份必须一致） */
    private String province;

    @Min(value = 0, message = "薪资下限不能为负")
    private Integer salaryMin;

    @Min(value = 0, message = "薪资上限不能为负")
    private Integer salaryMax;

    @NotBlank(message = "请填写岗位职责")
    private String description;

    @NotBlank(message = "请填写任职要求")
    private String requirements;

    /** 关键词（可选，逗号分隔，10 个以内） */
    @Size(max = 255, message = "关键词过长")
    private String keywords;
}
