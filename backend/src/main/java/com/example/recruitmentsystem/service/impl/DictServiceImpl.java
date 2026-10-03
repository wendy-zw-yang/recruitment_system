package com.example.recruitmentsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.dict.CityRequest;
import com.example.recruitmentsystem.dto.dict.IndustryRequest;
import com.example.recruitmentsystem.dto.dict.SkillSuggestionRequest;
import com.example.recruitmentsystem.entity.DictCity;
import com.example.recruitmentsystem.entity.DictIndustry;
import com.example.recruitmentsystem.entity.DictSkillSuggestion;
import com.example.recruitmentsystem.mapper.DictCityMapper;
import com.example.recruitmentsystem.mapper.DictIndustryMapper;
import com.example.recruitmentsystem.mapper.DictSkillSuggestionMapper;
import com.example.recruitmentsystem.service.AuditLogService;
import com.example.recruitmentsystem.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DictService 实现。详见 {@code docs/系统设计/详细设计/管理员.md §7.3.5 / §7.4.5}。
 */
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictIndustryMapper industryMapper;
    private final DictCityMapper cityMapper;
    private final DictSkillSuggestionMapper skillMapper;
    private final AuditLogService auditLogService;

    @Override
    public List<DictIndustry> listIndustries() {
        return industryMapper.selectList(new LambdaQueryWrapper<DictIndustry>()
                .orderByAsc(DictIndustry::getSortOrder)
                .orderByAsc(DictIndustry::getId));
    }

    @Override
    public List<DictCity> listCities() {
        return cityMapper.selectList(new LambdaQueryWrapper<DictCity>()
                .orderByAsc(DictCity::getSortOrder)
                .orderByAsc(DictCity::getId));
    }

    @Override
    public List<DictSkillSuggestion> listSkillSuggestions() {
        return skillMapper.selectList(new LambdaQueryWrapper<DictSkillSuggestion>()
                .orderByAsc(DictSkillSuggestion::getSortOrder)
                .orderByAsc(DictSkillSuggestion::getId));
    }

    @Override
    @Transactional
    public Long addIndustry(Long adminId, IndustryRequest request) {
        validateIndustryName(request.getName());
        validateIndustryParent(request.getParentId());

        DictIndustry entity = new DictIndustry();
        entity.setName(request.getName().trim());
        entity.setParentId(request.getParentId());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        industryMapper.insert(entity);

        auditLogService.record(adminId, "DICT_INDUSTRY_ADD", entity.getId(), "DICT_INDUSTRY",
                null, entity.getName(), null);
        return entity.getId();
    }

    @Override
    @Transactional
    public Long addCity(Long adminId, CityRequest request) {
        validateCityName(request.getName());

        DictCity entity = new DictCity();
        entity.setName(request.getName().trim());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        cityMapper.insert(entity);

        auditLogService.record(adminId, "DICT_CITY_ADD", entity.getId(), "DICT_CITY",
                null, entity.getName(), null);
        return entity.getId();
    }

    @Override
    @Transactional
    public Long addSkillSuggestion(Long adminId, SkillSuggestionRequest request) {
        validateSkillName(request.getName());

        DictSkillSuggestion entity = new DictSkillSuggestion();
        entity.setName(request.getName().trim());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        skillMapper.insert(entity);

        auditLogService.record(adminId, "DICT_SKILL_ADD", entity.getId(), "DICT_SKILL",
                null, entity.getName(), null);
        return entity.getId();
    }

    @Override
    @Transactional
    public void updateIndustry(Long adminId, Long id, IndustryRequest request) {
        DictIndustry existing = industryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "行业不存在");
        }
        validateIndustryName(request.getName());
        validateIndustryParent(request.getParentId());
        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw new BusinessException(400, "父级行业不能为自身");
        }

        DictIndustry updated = new DictIndustry();
        updated.setId(id);
        updated.setName(request.getName().trim());
        updated.setParentId(request.getParentId());
        updated.setSortOrder(request.getSortOrder() == null ? existing.getSortOrder() : request.getSortOrder());
        industryMapper.updateById(updated);

        auditLogService.record(adminId, "DICT_INDUSTRY_UPDATE", id, "DICT_INDUSTRY",
                existing.getName(), updated.getName(), null);
    }

    @Override
    @Transactional
    public void updateCity(Long adminId, Long id, CityRequest request) {
        DictCity existing = cityMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "城市不存在");
        }
        validateCityName(request.getName());

        DictCity updated = new DictCity();
        updated.setId(id);
        updated.setName(request.getName().trim());
        updated.setSortOrder(request.getSortOrder() == null ? existing.getSortOrder() : request.getSortOrder());
        cityMapper.updateById(updated);

        auditLogService.record(adminId, "DICT_CITY_UPDATE", id, "DICT_CITY",
                existing.getName(), updated.getName(), null);
    }

    @Override
    @Transactional
    public void updateSkillSuggestion(Long adminId, Long id, SkillSuggestionRequest request) {
        DictSkillSuggestion existing = skillMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "技能不存在");
        }
        validateSkillName(request.getName());

        DictSkillSuggestion updated = new DictSkillSuggestion();
        updated.setId(id);
        updated.setName(request.getName().trim());
        updated.setSortOrder(request.getSortOrder() == null ? existing.getSortOrder() : request.getSortOrder());
        skillMapper.updateById(updated);

        auditLogService.record(adminId, "DICT_SKILL_UPDATE", id, "DICT_SKILL",
                existing.getName(), updated.getName(), null);
    }

    @Override
    @Transactional
    public void deleteIndustry(Long adminId, Long id) {
        DictIndustry existing = industryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "行业不存在");
        }
        industryMapper.deleteById(id);
        auditLogService.record(adminId, "DICT_INDUSTRY_DELETE", id, "DICT_INDUSTRY",
                existing.getName(), null, "软删：已关联数据保留原值");
    }

    @Override
    @Transactional
    public void deleteCity(Long adminId, Long id) {
        DictCity existing = cityMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "城市不存在");
        }
        cityMapper.deleteById(id);
        auditLogService.record(adminId, "DICT_CITY_DELETE", id, "DICT_CITY",
                existing.getName(), null, "软删：已关联数据保留原值");
    }

    @Override
    @Transactional
    public void deleteSkillSuggestion(Long adminId, Long id) {
        DictSkillSuggestion existing = skillMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "技能不存在");
        }
        skillMapper.deleteById(id);
        auditLogService.record(adminId, "DICT_SKILL_DELETE", id, "DICT_SKILL",
                existing.getName(), null, "软删：已关联数据保留原值");
    }

    // ============ 私有校验 ============

    private void validateIndustryName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "名称不能为空");
        }
    }

    private void validateIndustryParent(Long parentId) {
        if (parentId == null) return;
        DictIndustry parent = industryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(400, "父级行业无效");
        }
    }

    private void validateCityName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "名称不能为空");
        }
    }

    private void validateSkillName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "名称不能为空");
        }
    }
}
