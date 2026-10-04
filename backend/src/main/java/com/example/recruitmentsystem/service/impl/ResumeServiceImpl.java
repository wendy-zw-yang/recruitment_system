package com.example.recruitmentsystem.service.impl;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.dto.resume.EducationItem;
import com.example.recruitmentsystem.dto.resume.ProjectItem;
import com.example.recruitmentsystem.dto.resume.ResumeDto;
import com.example.recruitmentsystem.dto.resume.ResumeUpdateRequest;
import com.example.recruitmentsystem.dto.resume.ResumeUploadResponse;
import com.example.recruitmentsystem.dto.resume.WorkItem;
import com.example.recruitmentsystem.entity.Resume;
import com.example.recruitmentsystem.entity.ResumeAttachment;
import com.example.recruitmentsystem.llm.service.LlmResumeParseService;
import com.example.recruitmentsystem.mapper.ResumeAttachmentMapper;
import com.example.recruitmentsystem.mapper.ResumeMapper;
import com.example.recruitmentsystem.service.FileStorageService;
import com.example.recruitmentsystem.service.ResumeService;
import com.example.recruitmentsystem.service.ResumeTextExtractService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * ResumeService 实现。详见 {@code docs/系统设计/详细设计/简历.md §2.3}。
 *
 * <p>单一 ACTIVE 原则：候选人查询 ACTIVE 必须唯一；上传前校验；否则拒绝。</p>
 *
 * <p>AI-1 失败降级：简历记录仍创建，字段为空；前端 toast 提示手动填写。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeMapper resumeMapper;
    private final ResumeAttachmentMapper attachmentMapper;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractService textExtractService;
    private final LlmResumeParseService llmResumeParseService;
    private final ObjectMapper objectMapper;

    @Override
    public ResumeDto getCurrentActive(Long candidateId) {
        Resume resume = resumeMapper.selectActiveByCandidate(candidateId);
        if (resume == null) return null;
        ResumeAttachment att = attachmentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ResumeAttachment>()
                        .eq("resume_id", resume.getId())
                        .eq("is_deleted", 0)
                        .orderByDesc("uploaded_at")
                        .last("limit 1"));
        return toDto(resume, att);
    }

    @Override
    @Transactional
    public ResumeUploadResponse uploadAndParse(Long candidateId, MultipartFile file) {
        // 防御：把候选人所有现存 ACTIVE（理论上仅 1 条，但遗留数据可能多条）一次性归档，
        // 保证新上传的简历成为唯一 ACTIVE。这样候选人无感知地从「可能有多份 ACTIVE」过渡到「全新一份」。
        List<Resume> existingActives = resumeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Resume>()
                        .eq("candidate_id", candidateId)
                        .eq("is_archived", false));
        for (Resume old : existingActives) {
            Resume update = new Resume();
            update.setId(old.getId());
            update.setArchived(true);
            resumeMapper.updateById(update);
            log.warn("[ResumeService] 上传时发现遗留 ACTIVE，已自动归档: resumeId={}, candidateId={}",
                    old.getId(), candidateId);
        }

        String relativePath = fileStorageService.save(file, "resume");
        Path absolute = fileStorageService.resolveAbsolute(relativePath);

        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setArchived(false);
        resumeMapper.insert(resume);

        ResumeAttachment attachment = new ResumeAttachment();
        attachment.setResumeId(resume.getId());
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFilePath(relativePath);
        attachment.setFileSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachmentMapper.insert(attachment);

        boolean aiParsed = false;
        try {
            String text = textExtractService.extractText(absolute.toString());
            JsonNode parsed = llmResumeParseService.parseToJson(text, candidateId);
            applyParsedTo(resume, parsed);
            resumeMapper.updateById(resume);
            aiParsed = true;
        } catch (BusinessException e) {
            log.warn("[ResumeService] AI-1 解析失败降级: candidateId={}, msg={}", candidateId, e.getMessage());
        } catch (Exception e) {
            log.warn("[ResumeService] 简历文本抽取失败降级: candidateId={}", candidateId, e);
        }

        Resume reloaded = resumeMapper.selectById(resume.getId());
        return new ResumeUploadResponse(toDto(reloaded, attachment), aiParsed);
    }

    @Override
    @Transactional
    public ResumeDto updateResume(Long candidateId, Long resumeId, ResumeUpdateRequest request) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!resume.getCandidateId().equals(candidateId)) {
            throw new BusinessException(403, "无权操作他人简历");
        }
        if (Boolean.TRUE.equals(resume.getArchived())) {
            throw new BusinessException(400, "已归档简历不可编辑");
        }

        if (request.getBasicName() != null) resume.setBasicName(request.getBasicName());
        if (request.getBasicPhone() != null) resume.setBasicPhone(request.getBasicPhone());
        if (request.getBasicEmail() != null) resume.setBasicEmail(request.getBasicEmail());
        if (request.getSelfIntro() != null) resume.setSelfIntro(request.getSelfIntro());

        resume.setEducation(toJson(request.getEducation()));
        resume.setWork(toJson(request.getWork()));
        resume.setProjects(toJson(request.getProjects()));
        resume.setSkills(toSkillsText(request.getSkills()));
        resumeMapper.updateById(resume);

        Resume reloaded = resumeMapper.selectById(resumeId);
        ResumeAttachment att = latestAttachment(resumeId);
        return toDto(reloaded, att);
    }

    @Override
    @Transactional
    public void archiveResume(Long candidateId, Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!resume.getCandidateId().equals(candidateId)) {
            throw new BusinessException(403, "无权操作他人简历");
        }
        if (Boolean.TRUE.equals(resume.getArchived())) {
            throw new BusinessException(400, "简历已归档");
        }
        Resume update = new Resume();
        update.setId(resumeId);
        update.setArchived(true);
        resumeMapper.updateById(update);
    }

    @Override
    @Transactional
    public void deleteResume(Long candidateId, Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!resume.getCandidateId().equals(candidateId)) {
            throw new BusinessException(403, "无权操作他人简历");
        }

        // 1) 删除磁盘附件文件（收集后再删，避免部分失败留下脏数据）
        List<ResumeAttachment> attachments = attachmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ResumeAttachment>()
                        .eq("resume_id", resumeId));
        for (ResumeAttachment att : attachments) {
            try {
                Path abs = fileStorageService.resolveAbsolute(att.getFilePath());
                Files.deleteIfExists(abs);
            } catch (Exception e) {
                log.warn("[ResumeService] 附件文件删除失败: path={}, msg={}", att.getFilePath(), e.getMessage());
            }
        }

        // 2) 软删 resume_attachment 行（@TableLogic 自动处理）
        for (ResumeAttachment att : attachments) {
            attachmentMapper.deleteById(att.getId());
        }

        // 3) 软删 resume 行（@TableLogic 自动处理 is_deleted=1）
        resumeMapper.deleteById(resumeId);

        // 4) 防御：把同候选人其他 ACTIVE 简历一并归档（避免遗留数据导致「删了一条还有一条」看起来像没删）。
        //    单一 ACTIVE 原则不应被破坏，但万一数据库被手动改过 / 历史 bug 留下多条 ACTIVE，
        //    用户的「删除」动作应让他真正回到「无 ACTIVE」状态。
        List<Resume> remainingActives = resumeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Resume>()
                        .eq("candidate_id", candidateId)
                        .eq("is_archived", false)
                        .ne("id", resumeId));
        for (Resume r : remainingActives) {
            Resume update = new Resume();
            update.setId(r.getId());
            update.setArchived(true);
            resumeMapper.updateById(update);
            log.warn("[ResumeService] 删除时连带归档遗留 ACTIVE: resumeId={}, candidateId={}",
                    r.getId(), candidateId);
        }

        log.info("[ResumeService] 简历已删除: resumeId={}, candidateId={}, attachments={}, 连带归档={}",
                resumeId, candidateId, attachments.size(), remainingActives.size());
    }

    @Override
    public ResumeDto getById(Long requesterId, String requesterRole, Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!"ADMIN".equals(requesterRole) && !resume.getCandidateId().equals(requesterId)) {
            throw new BusinessException(403, "无权查看他人简历");
        }
        return toDto(resume, latestAttachment(resumeId));
    }

    @Override
    public byte[] loadAttachment(Long requesterId, String requesterRole, Long attachmentId) {
        ResumeAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(404, "附件不存在");
        }
        Resume resume = resumeMapper.selectById(attachment.getResumeId());
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        boolean allowed = "ADMIN".equals(requesterRole)
                || resume.getCandidateId().equals(requesterId);
        if (!allowed) {
            throw new BusinessException(403, "无权访问该附件");
        }
        return fileStorageService.load(attachment.getFilePath());
    }

    // ============ 私有辅助 ============

    private ResumeAttachment latestAttachment(Long resumeId) {
        return attachmentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ResumeAttachment>()
                        .eq("resume_id", resumeId)
                        .eq("is_deleted", 0)
                        .orderByDesc("uploaded_at")
                        .last("limit 1"));
    }

    private ResumeDto toDto(Resume r, ResumeAttachment att) {
        ResumeDto dto = new ResumeDto();
        dto.setId(r.getId());
        dto.setCandidateId(r.getCandidateId());
        dto.setBasicName(r.getBasicName());
        dto.setBasicPhone(r.getBasicPhone());
        dto.setBasicEmail(r.getBasicEmail());
        dto.setSelfIntro(r.getSelfIntro());
        dto.setArchived(r.getArchived());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());
        dto.setEducation(parseList(r.getEducation(), EducationItem.class));
        dto.setWork(parseList(r.getWork(), WorkItem.class));
        dto.setProjects(parseList(r.getProjects(), ProjectItem.class));
        dto.setSkills(parseSkillsText(r.getSkills()));
        if (att != null) {
            dto.setAttachmentId(att.getId());
            dto.setAttachmentFileName(att.getFileName());
        }
        return dto;
    }

    private <T> List<T> parseList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            log.warn("[ResumeService] JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseSkillsText(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] parts = text.split("[,，;；\\s]+");
        return java.util.Arrays.stream(parts).filter(s -> !s.isBlank()).toList();
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "字段序列化失败: " + e.getMessage());
        }
    }

    private String toSkillsText(List<String> skills) {
        if (skills == null || skills.isEmpty()) return null;
        return String.join(",", skills);
    }

    private void applyParsedTo(Resume resume, JsonNode parsed) {
        if (parsed == null) return;
        JsonNode name = parsed.get("name");
        if (name != null && name.isTextual()) resume.setBasicName(name.asText());
        JsonNode phone = parsed.get("phone");
        if (phone != null && phone.isTextual()) resume.setBasicPhone(phone.asText());
        JsonNode email = parsed.get("email");
        if (email != null && email.isTextual()) resume.setBasicEmail(email.asText());

        resume.setEducation(toJsonString(parsed.get("education")));
        resume.setWork(toJsonString(parsed.get("work")));
        resume.setProjects(toJsonString(parsed.get("projects")));

        JsonNode skills = parsed.get("skills");
        if (skills != null && skills.isArray()) {
            StringBuilder sb = new StringBuilder();
            skills.forEach(child -> {
                if (child.isTextual()) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(child.asText());
                }
            });
            resume.setSkills(sb.length() == 0 ? null : sb.toString());
        }

        JsonNode selfIntro = parsed.get("selfIntro");
        if (selfIntro != null && selfIntro.isTextual()) resume.setSelfIntro(selfIntro.asText());
    }

    private String toJsonString(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.toString();
    }

    /** 给测试使用，避免 File import 警告 */
    @SuppressWarnings("unused")
    private static File noop(File f) { return f; }
}
