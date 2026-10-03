package com.example.recruitmentsystem.service.impl;

import com.example.recruitmentsystem.entity.AuditLog;
import com.example.recruitmentsystem.mapper.AuditLogMapper;
import com.example.recruitmentsystem.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AuditLogService 实现。{@code beforeValue} / {@code afterValue} 是 MySQL JSON 列，
 * 入库前以 {@link ObjectMapper} 序列化为合法 JSON 文本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void record(Long adminId, String actionType, Long targetId, String targetType,
                       String beforeValue, String afterValue, String note) {
        AuditLog row = new AuditLog();
        row.setAdminId(adminId);
        row.setActionType(actionType);
        row.setTargetId(targetId);
        row.setTargetType(targetType);
        row.setBeforeValue(toJson(beforeValue));
        row.setAfterValue(toJson(afterValue));
        row.setNote(note);
        auditLogMapper.insert(row);
    }

    private String toJson(String value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[AuditLog] JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
