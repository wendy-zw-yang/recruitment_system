package com.example.recruitmentsystem.controller.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.RoleHR;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.application.AdvanceStatusRequest;
import com.example.recruitmentsystem.dto.application.AdvanceStatusResponse;
import com.example.recruitmentsystem.dto.application.ApplicationDetailDto;
import com.example.recruitmentsystem.dto.application.ApplicationDto;
import com.example.recruitmentsystem.dto.application.ApplicationNoteDto;
import com.example.recruitmentsystem.dto.application.HrApplicationListQuery;
import com.example.recruitmentsystem.dto.application.ResumeSnapshotDto;
import com.example.recruitmentsystem.service.ApplicationService;
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
 * HR 端投递 Controller。覆盖 UC-24 列表 / UC-25 推进状态 / UC-26 备注 / UC-27 简历快照。
 */
@RestController
@RequestMapping("/api/applications/hr")
@RequiredArgsConstructor
public class HrApplicationController {

    private final ApplicationService applicationService;

    /** UC-24 HR 投递列表。query 用 POST 走 @RequestBody，便于传复杂过滤条件。 */
    @RoleHR
    @PostMapping("/list")
    public Result<IPage<ApplicationDto>> list(@Valid @RequestBody HrApplicationListQuery query) {
        return Result.success(applicationService.listForHr(CurrentUserContext.getUserId(), query));
    }

    /** HR 查看投递详情（包含完整候选人信息 + 状态历史 + 备注）。 */
    @RoleHR
    @GetMapping("/{id}")
    public Result<ApplicationDetailDto> detail(@PathVariable Long id) {
        return Result.success(applicationService.getDetail(
                CurrentUserContext.getUserId(), "HR", id));
    }

    /** UC-25 HR 推进投递状态。 */
    @RoleHR
    @PostMapping("/{id}/status")
    public Result<AdvanceStatusResponse> pushStatus(@PathVariable Long id,
                                                      @Valid @RequestBody AdvanceStatusRequest request) {
        return Result.success("状态已更新", applicationService.pushStatus(
                CurrentUserContext.getUserId(), id, request.getToStatus(), request.getNote()));
    }

    // ============ 备注（UC-26）============

    @RoleHR
    @GetMapping("/{id}/notes")
    public Result<List<ApplicationNoteDto>> listNotes(@PathVariable Long id) {
        return Result.success(applicationService.listNotes(CurrentUserContext.getUserId(), id));
    }

    @RoleHR
    @PostMapping("/{id}/notes")
    public Result<ApplicationNoteDto> addNote(@PathVariable Long id,
                                               @Valid @RequestBody ApplicationNoteDto.CreateRequest request) {
        return Result.success("备注已保存", applicationService.addNote(
                CurrentUserContext.getUserId(), id, request.getContent()));
    }

    @RoleHR
    @PutMapping("/{id}/notes/{noteId}")
    public Result<ApplicationNoteDto> updateNote(@PathVariable Long id,
                                                  @PathVariable Long noteId,
                                                  @Valid @RequestBody ApplicationNoteDto.UpdateRequest request) {
        return Result.success("备注已更新", applicationService.updateNote(
                CurrentUserContext.getUserId(), id, noteId, request.getContent()));
    }

    @RoleHR
    @DeleteMapping("/{id}/notes/{noteId}")
    public Result<Void> deleteNote(@PathVariable Long id, @PathVariable Long noteId) {
        applicationService.deleteNote(CurrentUserContext.getUserId(), id, noteId);
        return Result.success();
    }

    // ============ 简历快照（UC-27）============

    @RoleHR
    @GetMapping("/{id}/resume-snapshot")
    public Result<ResumeSnapshotDto> resumeSnapshot(@PathVariable Long id) {
        return Result.success(applicationService.getResumeSnapshot(CurrentUserContext.getUserId(), id));
    }
}
