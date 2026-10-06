package com.example.recruitmentsystem.dto.chat;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI-4 单条消息 DTO。前端消息流渲染单元。
 */
@Data
public class AiChatMessageDto {

    private Long id;

    /** USER / ASSISTANT */
    private String role;

    /** 消息内容（已完整渲染，无流式中间状态） */
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}