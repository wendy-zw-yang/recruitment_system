package com.example.recruitmentsystem.dto.dict;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CityRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 128)
    private String name;

    private Integer sortOrder;
}
