package com.example.recruitmentsystem.service;

import com.example.recruitmentsystem.dto.dict.CityRequest;
import com.example.recruitmentsystem.dto.dict.IndustryRequest;
import com.example.recruitmentsystem.dto.dict.SkillSuggestionRequest;

/**
 * 管理员操作日志写入抽象。每次字典 / 用户 / 职位 / 公司 / 简历审核写操作必调一次。
 */
public interface AuditLogService {

    void record(Long adminId, String actionType, Long targetId, String targetType,
                String beforeValue, String afterValue, String note);
}
