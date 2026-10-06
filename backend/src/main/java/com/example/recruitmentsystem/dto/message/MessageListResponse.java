package com.example.recruitmentsystem.dto.message;

import lombok.Data;

import java.util.List;

/**
 * 消息分页响应（IPage-like 简化版）。
 */
@Data
public class MessageListResponse {

    private Integer pageNum;

    private Integer pageSize;

    private Long total;

    /** 消息列表（按 createdAt DESC 倒序；前端可自行 reverse） */
    private List<MessageDto> records;
}
