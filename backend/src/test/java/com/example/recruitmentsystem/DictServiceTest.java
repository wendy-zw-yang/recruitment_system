package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.dict.IndustryRequest;
import com.example.recruitmentsystem.entity.AuditLog;
import com.example.recruitmentsystem.entity.DictIndustry;
import com.example.recruitmentsystem.mapper.AuditLogMapper;
import com.example.recruitmentsystem.mapper.DictIndustryMapper;
import com.example.recruitmentsystem.service.DictService;
import com.example.recruitmentsystem.service.impl.DictServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §7 字典 CRUD 单元测试。
 */
@SpringBootTest
class DictServiceTest {

    @Autowired private DictIndustryMapper industryMapper;
    @Autowired private com.example.recruitmentsystem.mapper.DictCityMapper cityMapper;
    @Autowired private com.example.recruitmentsystem.mapper.DictSkillSuggestionMapper skillMapper;
    @Autowired private AuditLogMapper auditLogMapper;
    @Autowired private com.example.recruitmentsystem.service.AuditLogService auditLogService;
    @Autowired private com.example.recruitmentsystem.mapper.UserMapper userMapper;

    private Long adminId;

    @BeforeEach
    void setupAdmin() {
        com.example.recruitmentsystem.entity.User admin = new com.example.recruitmentsystem.entity.User();
        admin.setEmail("admin-" + System.nanoTime() + "@test.local");
        admin.setPasswordHash("x");
        admin.setRoleCode("ADMIN");
        admin.setStatus("ENABLED");
        admin.setUsername("admin");
        userMapper.insert(admin);
        adminId = admin.getId();
    }

    private DictService dictService() {
        return new DictServiceImpl(industryMapper, cityMapper, skillMapper, auditLogService);
    }

    @Test
    @Transactional
    void addIndustry_writesRowAndAuditLog() {
        IndustryRequest req = new IndustryRequest();
        req.setName("互联网/AI-" + System.nanoTime());
        req.setSortOrder(10);

        Long id = dictService().addIndustry(adminId, req);
        assertNotNull(id);

        DictIndustry saved = industryMapper.selectById(id);
        assertNotNull(saved);
        assertEquals(req.getName(), saved.getName());

        List<AuditLog> logs = auditLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog>()
                        .eq(AuditLog::getActionType, "DICT_INDUSTRY_ADD")
                        .eq(AuditLog::getTargetId, id));
        assertEquals(1, logs.size());
    }

    @Test
    @Transactional
    void addIndustry_rejectsBlankName() {
        IndustryRequest req = new IndustryRequest();
        req.setName("  ");
        assertThrows(BusinessException.class, () -> dictService().addIndustry(adminId, req));
    }

    @Test
    @Transactional
    void updateIndustry_preservesAuditTrail() {
        IndustryRequest addReq = new IndustryRequest();
        addReq.setName("Original-" + System.nanoTime());
        Long id = dictService().addIndustry(adminId, addReq);

        IndustryRequest updReq = new IndustryRequest();
        updReq.setName("Updated-" + System.nanoTime());
        dictService().updateIndustry(adminId, id, updReq);

        DictIndustry after = industryMapper.selectById(id);
        assertEquals(updReq.getName(), after.getName());

        long updateLogs = auditLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog>()
                        .eq(AuditLog::getActionType, "DICT_INDUSTRY_UPDATE")
                        .eq(AuditLog::getTargetId, id));
        assertTrue(updateLogs >= 1);
    }

    @Test
    @Transactional
    void updateIndustry_rejectsSelfAsParent() {
        IndustryRequest addReq = new IndustryRequest();
        addReq.setName("Self-" + System.nanoTime());
        Long id = dictService().addIndustry(adminId, addReq);

        IndustryRequest updReq = new IndustryRequest();
        updReq.setName("SelfUpdated");
        updReq.setParentId(id);
        assertThrows(BusinessException.class, () -> dictService().updateIndustry(adminId, id, updReq));
    }

    @Test
    @Transactional
    void deleteIndustry_isSoftDelete() {
        IndustryRequest addReq = new IndustryRequest();
        addReq.setName("ToDelete-" + System.nanoTime());
        Long id = dictService().addIndustry(adminId, addReq);

        dictService().deleteIndustry(adminId, id);

        DictIndustry viaService = industryMapper.selectById(id);
        assertTrue(viaService == null || Integer.valueOf(1).equals(viaService.getIsDeleted()),
                "@TableLogic 应过滤已删除行");

        long deleteLogs = auditLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog>()
                        .eq(AuditLog::getActionType, "DICT_INDUSTRY_DELETE")
                        .eq(AuditLog::getTargetId, id));
        assertTrue(deleteLogs >= 1);
    }

    @Test
    @Transactional
    void deleteIndustry_notFound_throws() {
        assertThrows(BusinessException.class, () -> dictService().deleteIndustry(adminId, 99999999L));
    }

    @Test
    @Transactional
    void listIndustries_excludesSoftDeleted() {
        IndustryRequest addReq = new IndustryRequest();
        addReq.setName("Listed-" + System.nanoTime());
        Long id = dictService().addIndustry(adminId, addReq);

        List<DictIndustry> before = dictService().listIndustries();
        assertTrue(before.stream().anyMatch(d -> d.getId().equals(id)));

        dictService().deleteIndustry(adminId, id);

        List<DictIndustry> after = dictService().listIndustries();
        assertFalse(after.stream().anyMatch(d -> d.getId().equals(id)));
    }
}
