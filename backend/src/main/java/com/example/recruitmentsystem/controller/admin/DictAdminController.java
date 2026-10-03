package com.example.recruitmentsystem.controller.admin;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.RoleAdmin;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.dict.CityRequest;
import com.example.recruitmentsystem.dto.dict.IndustryRequest;
import com.example.recruitmentsystem.dto.dict.SkillSuggestionRequest;
import com.example.recruitmentsystem.entity.DictCity;
import com.example.recruitmentsystem.entity.DictIndustry;
import com.example.recruitmentsystem.entity.DictSkillSuggestion;
import com.example.recruitmentsystem.service.DictService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典接口。GET 端点公开（HR / 候选人也要读取行业/城市/技能），
 * 写操作要求 admin。仅审计 / 删除时加 {@link RoleAdmin} 注解。
 *
 * <p>覆盖 UC-38 / UC-39 / UC-40。</p>
 */
@RestController
@RequestMapping("/api/admin/dict")
@RequiredArgsConstructor
public class DictAdminController {

    private final DictService dictService;

    // ============ 读：公开 ============

    @GetMapping("/industries")
    public Result<List<DictIndustry>> listIndustries() {
        return Result.success(dictService.listIndustries());
    }

    @GetMapping("/cities")
    public Result<List<DictCity>> listCities() {
        return Result.success(dictService.listCities());
    }

    @GetMapping("/skill-suggestions")
    public Result<List<DictSkillSuggestion>> listSkillSuggestions() {
        return Result.success(dictService.listSkillSuggestions());
    }

    // ============ 写：admin ============

    @RoleAdmin
    @PostMapping("/industries")
    public Result<Long> addIndustry(@Valid @RequestBody IndustryRequest request) {
        return Result.success(dictService.addIndustry(CurrentUserContext.getUserId(), request));
    }

    @RoleAdmin
    @PutMapping("/industries/{id}")
    public Result<Void> updateIndustry(@PathVariable Long id, @Valid @RequestBody IndustryRequest request) {
        dictService.updateIndustry(CurrentUserContext.getUserId(), id, request);
        return Result.success();
    }

    @RoleAdmin
    @DeleteMapping("/industries/{id}")
    public Result<Void> deleteIndustry(@PathVariable Long id) {
        dictService.deleteIndustry(CurrentUserContext.getUserId(), id);
        return Result.success();
    }

    @RoleAdmin
    @PostMapping("/cities")
    public Result<Long> addCity(@Valid @RequestBody CityRequest request) {
        return Result.success(dictService.addCity(CurrentUserContext.getUserId(), request));
    }

    @RoleAdmin
    @PutMapping("/cities/{id}")
    public Result<Void> updateCity(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        dictService.updateCity(CurrentUserContext.getUserId(), id, request);
        return Result.success();
    }

    @RoleAdmin
    @DeleteMapping("/cities/{id}")
    public Result<Void> deleteCity(@PathVariable Long id) {
        dictService.deleteCity(CurrentUserContext.getUserId(), id);
        return Result.success();
    }

    @RoleAdmin
    @PostMapping("/skill-suggestions")
    public Result<Long> addSkillSuggestion(@Valid @RequestBody SkillSuggestionRequest request) {
        return Result.success(dictService.addSkillSuggestion(CurrentUserContext.getUserId(), request));
    }

    @RoleAdmin
    @PutMapping("/skill-suggestions/{id}")
    public Result<Void> updateSkillSuggestion(@PathVariable Long id, @Valid @RequestBody SkillSuggestionRequest request) {
        dictService.updateSkillSuggestion(CurrentUserContext.getUserId(), id, request);
        return Result.success();
    }

    @RoleAdmin
    @DeleteMapping("/skill-suggestions/{id}")
    public Result<Void> deleteSkillSuggestion(@PathVariable Long id) {
        dictService.deleteSkillSuggestion(CurrentUserContext.getUserId(), id);
        return Result.success();
    }
}
