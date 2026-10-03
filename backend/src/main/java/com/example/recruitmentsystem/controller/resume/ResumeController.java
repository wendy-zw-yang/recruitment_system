package com.example.recruitmentsystem.controller.resume;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.RoleCandidate;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.resume.ResumeDto;
import com.example.recruitmentsystem.dto.resume.ResumeUpdateRequest;
import com.example.recruitmentsystem.dto.resume.ResumeUploadResponse;
import com.example.recruitmentsystem.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历 Controller。覆盖 UC-05 / UC-06 / UC-07 / UC-08。
 */
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /** 当前 ACTIVE 简历 */
    @RoleCandidate
    @GetMapping("/current")
    public Result<ResumeDto> getCurrent() {
        ResumeDto dto = resumeService.getCurrentActive(CurrentUserContext.getUserId());
        return Result.success(dto);
    }

    /** UC-05 上传 + UC-06 AI-1 解析 */
    @RoleCandidate
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(resumeService.uploadAndParse(CurrentUserContext.getUserId(), file));
    }

    /** UC-07 编辑 */
    @RoleCandidate
    @PutMapping("/{id}")
    public Result<ResumeDto> update(@PathVariable Long id, @Valid @RequestBody ResumeUpdateRequest request) {
        return Result.success(resumeService.updateResume(CurrentUserContext.getUserId(), id, request));
    }

    /** UC-08 Step 1：归档 */
    @RoleCandidate
    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        resumeService.archiveResume(CurrentUserContext.getUserId(), id);
        return Result.success();
    }

    /** 按 ID 查询（候选人本人 / 管理员） */
    @GetMapping("/{id}")
    public Result<ResumeDto> getById(@PathVariable Long id) {
        return Result.success(resumeService.getById(
                CurrentUserContext.getUserId(),
                CurrentUserContext.getRole(),
                id));
    }

    /** 附件下载 */
    @GetMapping("/attachment/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long attachmentId) {
        byte[] bytes = resumeService.loadAttachment(
                CurrentUserContext.getUserId(),
                CurrentUserContext.getRole(),
                attachmentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", "resume-" + attachmentId + ".bin");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(bytes, headers, 200);
    }
}
