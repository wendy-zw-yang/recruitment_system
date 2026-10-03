package com.example.recruitmentsystem.dto.dict;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 行业字典新增 / 修改请求。{@code parentId} 可空（一级行业）。
 */
@Data
public class IndustryRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称过长")
    private String name;

    private Long parentId;

    private Integer sortOrder;
}
