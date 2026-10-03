package com.example.recruitmentsystem.dto.resume;

import com.example.recruitmentsystem.entity.Resume;
import com.example.recruitmentsystem.entity.ResumeAttachment;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历响应 DTO。教育 / 工作 / 项目以 List 形式返（前端渲染时反序列化）。
 */
@Data
public class ResumeDto {

    private Long id;
    private Long candidateId;
    private String basicName;
    private String basicPhone;
    private String basicEmail;
    private List<EducationItem> education;
    private List<WorkItem> work;
    private List<ProjectItem> projects;
    private List<String> skills;
    private String selfIntro;
    private Boolean archived;
    private Long attachmentId;
    private String attachmentFileName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ResumeDto from(Resume r, ResumeAttachment att) {
        ResumeDto dto = new ResumeDto();
        dto.id = r.getId();
        dto.candidateId = r.getCandidateId();
        dto.basicName = r.getBasicName();
        dto.basicPhone = r.getBasicPhone();
        dto.basicEmail = r.getBasicEmail();
        dto.selfIntro = r.getSelfIntro();
        dto.archived = r.getArchived();
        dto.createdAt = r.getCreatedAt();
        dto.updatedAt = r.getUpdatedAt();
        if (att != null) {
            dto.attachmentId = att.getId();
            dto.attachmentFileName = att.getFileName();
        }
        return dto;
    }
}
