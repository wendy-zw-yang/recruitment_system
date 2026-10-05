package com.example.recruitmentsystem.dto.profile;

import lombok.Data;

/**
 * 候选人求职偏好 DTO。
 *
 * <p>v0.5：只持久化 expectedPosition（schema 现有 String 字段）。
 * expectedIndustry / expectedCity 当前仅文本展示（不做 dict 关联），后续如需联动职位推荐
 * 可在 schema 加 industry_name / city_name 冗余字段并迁移。</p>
 */
@Data
public class CandidatePreferenceDto {

    private String expectedPosition;

    /** 展示用：当前候选人期望行业（仅前端展示，不持久化） */
    private String expectedIndustry;

    /** 展示用：当前候选人期望城市（仅前端展示，不持久化） */
    private String expectedCity;
}
