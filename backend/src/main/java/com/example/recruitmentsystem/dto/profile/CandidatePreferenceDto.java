package com.example.recruitmentsystem.dto.profile;

import lombok.Data;

/**
 * 候选人求职偏好 DTO。
 *
 * <p>v0.7.3：所有 4 字段全部持久化（之前 expectedIndustry / expectedCity 仅前端展示占位）。
 * 行业 / 城市存 id 关联 dict 表；省份冗余存储便于按省聚合。</p>
 */
@Data
public class CandidatePreferenceDto {

    /** 期望职位（自由输入关键词；如 "Java 工程师"，按空白拆词后 LIKE 三字段） */
    private String expectedPosition;

    /** 期望行业 → {@code dict_industry.id} */
    private Long expectedIndustryId;

    /** 期望省份（如 "浙江"，冗余存储不依赖 JOIN） */
    private String expectedProvince;

    /** 期望城市 → {@code dict_city.id} */
    private Long expectedCityId;
}