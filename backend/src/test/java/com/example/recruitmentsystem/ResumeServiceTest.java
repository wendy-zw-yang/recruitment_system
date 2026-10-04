package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.resume.ResumeUpdateRequest;
import com.example.recruitmentsystem.dto.resume.ResumeUploadResponse;
import com.example.recruitmentsystem.entity.Resume;
import com.example.recruitmentsystem.entity.ResumeAttachment;
import com.example.recruitmentsystem.mapper.ResumeAttachmentMapper;
import com.example.recruitmentsystem.mapper.ResumeMapper;
import com.example.recruitmentsystem.service.FileStorageService;
import com.example.recruitmentsystem.service.ResumeService;
import com.example.recruitmentsystem.service.ResumeTextExtractService;
import com.example.recruitmentsystem.service.impl.ResumeServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * §2 简历 单元测试。覆盖：单一 ACTIVE 校验 / 归档切换 / 编辑鉴权 / AI 失败降级。
 *
 * <p>FileStorageService / ResumeTextExtractService / LlmResumeParseService 用 Mockito
 * 隔离，避免依赖真实 LLM 调用与磁盘 IO。</p>
 */
@SpringBootTest
class ResumeServiceTest {

    @Autowired private ResumeMapper resumeMapper;
    @Autowired private ResumeAttachmentMapper attachmentMapper;
    @Autowired private com.example.recruitmentsystem.mapper.UserMapper userMapper;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private FileStorageService fileStorageService;
    @MockitoBean private ResumeTextExtractService textExtractService;
    @MockitoBean
    private com.example.recruitmentsystem.llm.service.LlmResumeParseService llmResumeParseService;

    private ResumeService resumeService() {
        return new ResumeServiceImpl(resumeMapper, attachmentMapper, fileStorageService,
                textExtractService, llmResumeParseService, objectMapper);
    }

    private Long createCandidate() {
        com.example.recruitmentsystem.entity.User user = new com.example.recruitmentsystem.entity.User();
        user.setEmail("candidate-" + System.nanoTime() + "@test.local");
        user.setPasswordHash("x");
        user.setRoleCode("CANDIDATE");
        user.setStatus("ENABLED");
        user.setUsername("test");
        userMapper.insert(user);
        return user.getId();
    }

    @Test
    @Transactional
    void uploadAndParse_createsActiveResume_andCallsAiOnce() {
        Long candidateId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("raw resume text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "fake-pdf-content".getBytes());
        ResumeUploadResponse resp = resumeService().uploadAndParse(candidateId, file);

        assertNotNull(resp.getResume().getId());
        assertTrue(resp.isAiParsed());
        assertEquals("张三", resp.getResume().getBasicName());

        Resume saved = resumeMapper.selectById(resp.getResume().getId());
        assertNotNull(saved);
        assertEquals(candidateId, saved.getCandidateId());
        assertFalse(Boolean.TRUE.equals(saved.getArchived()));
    }

    @Test
    @Transactional
    void uploadAndParse_existingActive_autoArchives_andCreatesNew() {
        // 2026-10-04 行为变更：上传时若已有 ACTIVE，自动归档旧的并创建新的（防御遗留数据）。
        Long candidateId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "x".getBytes());
        Long firstId = resumeService().uploadAndParse(candidateId, file).getResume().getId();

        MockMultipartFile file2 = new MockMultipartFile("file", "cv2.pdf",
                "application/pdf", "x".getBytes());
        ResumeUploadResponse second = resumeService().uploadAndParse(candidateId, file2);

        // 不抛错
        assertNotNull(second.getResume().getId());
        // 旧简历被自动归档
        Resume firstAfter = resumeMapper.selectById(firstId);
        assertNotNull(firstAfter);
        assertTrue(Boolean.TRUE.equals(firstAfter.getArchived()));
        // 当前 ACTIVE 是新的
        assertEquals(second.getResume().getId(),
                resumeService().getCurrentActive(candidateId).getId());
    }

    @Test
    @Transactional
    void uploadAndParse_aiFailure_degradesToEmptyFields() {
        Long candidateId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenThrow(new BusinessException(500, "mock AI fail"));

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "x".getBytes());
        ResumeUploadResponse resp = resumeService().uploadAndParse(candidateId, file);

        assertFalse(resp.isAiParsed());
        assertNotNull(resp.getResume().getId());
        Resume saved = resumeMapper.selectById(resp.getResume().getId());
        assertNotNull(saved);
        assertNull(saved.getBasicName());
        assertNull(saved.getSkills());
    }

    @Test
    @Transactional
    void archiveResume_flipsFlag_andBlocksUpdate() {
        Long candidateId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "x".getBytes());
        Long resumeId = resumeService().uploadAndParse(candidateId, file).getResume().getId();

        resumeService().archiveResume(candidateId, resumeId);

        Resume archived = resumeMapper.selectById(resumeId);
        assertTrue(Boolean.TRUE.equals(archived.getArchived()));

        ResumeUpdateRequest update = new ResumeUpdateRequest();
        update.setBasicName("改名");
        assertThrows(BusinessException.class,
                () -> resumeService().updateResume(candidateId, resumeId, update));
    }

    @Test
    @Transactional
    void updateResume_rejectsForeignOwnership() {
        Long ownerId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "x".getBytes());
        Long resumeId = resumeService().uploadAndParse(ownerId, file).getResume().getId();

        ResumeUpdateRequest req = new ResumeUpdateRequest();
        req.setBasicName("hacker");
        assertThrows(BusinessException.class,
                () -> resumeService().updateResume(9999L, resumeId, req));
    }

    @Test
    @Transactional
    void deleteResume_softDeletesRow_andAllowsReupload() {
        Long candidateId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "x".getBytes());
        Long resumeId = resumeService().uploadAndParse(candidateId, file).getResume().getId();

        // 执行删除
        resumeService().deleteResume(candidateId, resumeId);

        // getCurrentActive 应返回 null（软删已生效）
        assertNull(resumeService().getCurrentActive(candidateId));

        // 重新上传应成功（不再被「已有 ACTIVE」拦截）
        MockMultipartFile file2 = new MockMultipartFile("file", "cv2.pdf",
                "application/pdf", "y".getBytes());
        ResumeUploadResponse resp = resumeService().uploadAndParse(candidateId, file2);
        assertNotNull(resp.getResume().getId());
        assertTrue(resp.isAiParsed());
    }

    @Test
    @Transactional
    void deleteResume_rejectsForeignOwnership() {
        Long ownerId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "x".getBytes());
        Long resumeId = resumeService().uploadAndParse(ownerId, file).getResume().getId();

        assertThrows(BusinessException.class,
                () -> resumeService().deleteResume(9999L, resumeId));
    }

    @Test
    @Transactional
    void deleteResume_notFound_throws() {
        Long candidateId = createCandidate();
        assertThrows(BusinessException.class,
                () -> resumeService().deleteResume(candidateId, 99999L));
    }

    @Test
    @Transactional
    void deleteResume_autoArchivesOtherActiveResume_legacyDataGuard() {
        // 模拟遗留数据：同候选人已有 2 条 ACTIVE。删除其中一条时，另一条应被自动归档。
        Long candidateId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());

        MockMultipartFile f1 = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "x".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("file", "cv2.pdf",
                "application/pdf", "y".getBytes());
        Long id1 = resumeService().uploadAndParse(candidateId, f1).getResume().getId();
        // 绕过应用层校验，直接插入第二条 ACTIVE（模拟遗留数据）
        com.example.recruitmentsystem.entity.Resume manual = new com.example.recruitmentsystem.entity.Resume();
        manual.setCandidateId(candidateId);
        manual.setArchived(false);
        resumeMapper.insert(manual);
        Long id2 = manual.getId();

        // 删除其中一条
        resumeService().deleteResume(candidateId, id1);

        // 两条都应不再是 ACTIVE：id1 软删，id2 被自动归档
        assertNull(resumeService().getCurrentActive(candidateId));
        com.example.recruitmentsystem.entity.Resume after1 = resumeMapper.selectById(id1);
        com.example.recruitmentsystem.entity.Resume after2 = resumeMapper.selectById(id2);
        // id1: 软删后 selectById 因 @TableLogic 返回 null
        assertNull(after1);
        // id2: 仍可见（没软删），但应已归档
        assertNotNull(after2);
        assertTrue(Boolean.TRUE.equals(after2.getArchived()));
    }

    @Test
    @Transactional
    void uploadAndParse_autoArchivesLegacyActiveResume() {
        // 模拟遗留数据：插入 1 条 ACTIVE 后直接调 upload，期望自动归档旧 + 创建新。
        Long candidateId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());

        // 直接 SQL 插入遗留 ACTIVE
        com.example.recruitmentsystem.entity.Resume legacy = new com.example.recruitmentsystem.entity.Resume();
        legacy.setCandidateId(candidateId);
        legacy.setArchived(false);
        resumeMapper.insert(legacy);

        MockMultipartFile file = new MockMultipartFile("file", "new.pdf",
                "application/pdf", "z".getBytes());
        ResumeUploadResponse resp = resumeService().uploadAndParse(candidateId, file);

        // 旧 ACTIVE 应被自动归档
        com.example.recruitmentsystem.entity.Resume legacyAfter = resumeMapper.selectById(legacy.getId());
        assertTrue(Boolean.TRUE.equals(legacyAfter.getArchived()));

        // 新简历应为唯一 ACTIVE
        assertNotNull(resp.getResume().getId());
        assertEquals(resp.getResume().getId(), resumeService().getCurrentActive(candidateId).getId());
    }

    private com.fasterxml.jackson.databind.JsonNode buildParsedJson() {
        try {
            return objectMapper.readTree("""
                    {
                      "name": "张三",
                      "phone": "13800000000",
                      "email": "zhang@test.local",
                      "education": [{"school": "Test U", "major": "CS"}],
                      "work": [{"company": "Acme", "position": "Engineer"}],
                      "projects": [],
                      "skills": ["Java", "Spring"],
                      "selfIntro": "Hello"
                    }
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
