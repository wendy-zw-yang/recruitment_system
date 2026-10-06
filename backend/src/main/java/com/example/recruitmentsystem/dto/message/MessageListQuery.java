package com.example.recruitmentsystem.dto.message;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 消息分页查询请求体（POST 用，方便传大 pageSize）。
 *
 * <p>offset/limit 模式（pageNum * pageSize 转换由 Service 计算）。</p>
 */
@Data
public class MessageListQuery {

    @Min(value = 1, message = "pageNum 必须 ≥ 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 必须 ≥ 1")
    @Max(value = 100, message = "pageSize 不能超过 100")
    private Integer pageSize = 20;
}
