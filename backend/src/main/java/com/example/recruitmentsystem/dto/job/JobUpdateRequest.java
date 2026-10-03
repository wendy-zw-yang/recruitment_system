package com.example.recruitmentsystem.dto.job;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobUpdateRequest {

    @NotBlank(message = "请填写职位标题")
    @Size(max = 255)
    private String title;

    private Long industryId;

    private Long cityId;

    private String province;

    @Min(0)
    private Integer salaryMin;

    @Min(0)
    private Integer salaryMax;

    @NotBlank(message = "请填写岗位职责")
    private String description;

    @NotBlank(message = "请填写任职要求")
    private String requirements;

    @Size(max = 255, message = "关键词过长")
    private String keywords;
}
