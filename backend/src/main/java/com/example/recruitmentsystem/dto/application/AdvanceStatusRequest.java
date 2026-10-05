package com.example.recruitmentsystem.dto.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 推进投递状态请求（HR 侧）。
 *
 * <p>8 个合法 toStatus 由服务层校验；此处只做基本格式校验。</p>
 */
@Data
public class AdvanceStatusRequest {

    @NotBlank(message = "请选择目标状态")
    @Pattern(regexp = "VIEWED_BY_HR|RESUME_PASSED|INTERVIEWING|OFFERED|HIRED|REJECTED",
            message = "无效的状态")
    private String toStatus;

    /** 备注（可选，REJECTED 时建议填写） */
    @Size(max = 500, message = "备注不能超过 500 字")
    private String note;
}
