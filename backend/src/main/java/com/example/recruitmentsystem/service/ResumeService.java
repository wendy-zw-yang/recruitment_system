package com.example.recruitmentsystem.service;

import com.example.recruitmentsystem.dto.resume.ResumeDto;
import com.example.recruitmentsystem.dto.resume.ResumeUpdateRequest;
import com.example.recruitmentsystem.dto.resume.ResumeUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历业务接口。覆盖 UC-05 / UC-06 / UC-07 / UC-08。
 */
public interface ResumeService {

    /** 获取当前 ACTIVE 简历（含附件元数据）。无 ACTIVE 返回 null。 */
    ResumeDto getCurrentActive(Long candidateId);

    /** UC-05 上传简历附件 + UC-06 触发 AI-1 解析 */
    ResumeUploadResponse uploadAndParse(Long candidateId, MultipartFile file);

    /** UC-07 编辑简历（不触发 AI） */
    ResumeDto updateResume(Long candidateId, Long resumeId, ResumeUpdateRequest request);

    /** UC-08 Step 1：归档当前 ACTIVE 简历（保留行数据，{@code is_archived=true}） */
    void archiveResume(Long candidateId, Long resumeId);

    /** 删除候选人本人的简历。软删行 + 删除磁盘附件文件 + 删除 resume_attachment 行。 */
    void deleteResume(Long candidateId, Long resumeId);

    /** 按 ID 查询（鉴权：候选人本人 / 管理员） */
    ResumeDto getById(Long requesterId, String requesterRole, Long resumeId);

    /** 加载简历附件字节（鉴权：候选人本人 / HR 已投递 / 管理员） */
    byte[] loadAttachment(Long requesterId, String requesterRole, Long attachmentId);
}
