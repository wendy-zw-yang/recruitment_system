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
    void uploadAndParse_existingActive_throws() {
        Long candidateId = createCandidate();
        when(fileStorageService.save(any(), anyString())).thenReturn("resume/2026-10/abc.pdf");
        when(fileStorageService.resolveAbsolute(anyString()))
                .thenReturn(java.nio.file.Paths.get("uploads").resolve("resume/2026-10/abc.pdf"));
        when(textExtractService.extractText(anyString())).thenReturn("text");
        when(llmResumeParseService.parseToJson(anyString(), anyLong()))
                .thenReturn(buildParsedJson());

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf",
                "application/pdf", "x".getBytes());
        resumeService().uploadAndParse(candidateId, file);

        MockMultipartFile file2 = new MockMultipartFile("file", "cv2.pdf",
                "application/pdf", "x".getBytes());
        assertThrows(BusinessException.class, () -> resumeService().uploadAndParse(candidateId, file2));
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
