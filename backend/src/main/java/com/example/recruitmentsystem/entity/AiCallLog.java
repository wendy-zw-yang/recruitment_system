package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志。每次 LlmClient 调用必写一行，便于答辩展示与问题追溯。
 *
 * <p>对应表：{@code ai_call_log}（详见 {@code docs/DataBase/schema.sql}）。</p>
 *
 * <p>详见 {@code docs/系统设计/详细设计/AI集成.md §6.3.7}。</p>
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** AI-1 / AI-2 / AI-3 / AI-4 / AI-5 / AI-6 */
    private String functionCode;

    /** 完整 prompt（含 system + user） */
    private String prompt;

    /** LLM 原始返回（截断保留前 4KB） */
    private String response;

    /** 端到端耗时（毫秒） */
    private Integer latencyMs;

    private Integer promptTokens;

    private Integer completionTokens;

    /** SUCCESS / FAIL / TIMEOUT */
    private String status;

    private String errorMessage;

    /** 调用方 user.id（演示态可空） */
    private Long callerUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
