package com.example.recruitmentsystem.service;

import com.example.recruitmentsystem.dto.dict.CityRequest;
import com.example.recruitmentsystem.dto.dict.IndustryRequest;
import com.example.recruitmentsystem.dto.dict.SkillSuggestionRequest;
import com.example.recruitmentsystem.entity.DictCity;
import com.example.recruitmentsystem.entity.DictIndustry;
import com.example.recruitmentsystem.entity.DictSkillSuggestion;

import java.util.List;

/**
 * 字典维护接口。覆盖 UC-38（行业）/ UC-39（城市）/ UC-40（技能建议池）。
 *
 * <p>所有写操作通过 {@link com.example.recruitmentsystem.service.AuditLogService} 记录。</p>
 */
public interface DictService {

    /** 列出所有未软删行业（按 sort_order 升序），含一级 + 二级 */
    List<DictIndustry> listIndustries();

    /** 列出所有未软删城市 */
    List<DictCity> listCities();

    /** 列出所有未软删技能建议 */
    List<DictSkillSuggestion> listSkillSuggestions();

    Long addIndustry(Long adminId, IndustryRequest request);

    Long addCity(Long adminId, CityRequest request);

    Long addSkillSuggestion(Long adminId, SkillSuggestionRequest request);

    void updateIndustry(Long adminId, Long id, IndustryRequest request);

    void updateCity(Long adminId, Long id, CityRequest request);

    void updateSkillSuggestion(Long adminId, Long id, SkillSuggestionRequest request);

    /** 软删。已关联数据保留原值（仅新选择隐藏）。 */
    void deleteIndustry(Long adminId, Long id);

    void deleteCity(Long adminId, Long id);

    void deleteSkillSuggestion(Long adminId, Long id);
}
