package com.example.recruitmentsystem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.dto.application.AdvanceStatusResponse;
import com.example.recruitmentsystem.dto.application.ApplicationDetailDto;
import com.example.recruitmentsystem.dto.application.ApplicationDto;
import com.example.recruitmentsystem.dto.application.ApplicationNoteDto;
import com.example.recruitmentsystem.dto.application.ApplyRequest;
import com.example.recruitmentsystem.dto.application.ApplyResponse;
import com.example.recruitmentsystem.dto.application.HrApplicationListQuery;
import com.example.recruitmentsystem.dto.application.ResumeSnapshotDto;
import com.example.recruitmentsystem.dto.application.WithdrawResponse;

/**
 * 投递业务接口。覆盖 UC-12 投递 / UC-13 我的投递 / UC-14 撤回 / UC-24 HR 列表 / UC-25 推进状态 / UC-26 HR 备注 / UC-27 HR 查看简历快照。
 *
 * <p>详见 {@code docs/系统设计/详细设计/投递.md}。</p>
 */
public interface ApplicationService {

    // ============ 候选人端 ============

    /** UC-12 投递职位。立即返回；AI-2 评分异步触发。 */
    ApplyResponse apply(Long candidateId, ApplyRequest request);

    /** UC-13 候选人分页查询自己的投递列表（候选人侧隐藏 VIEWED_BY_HR）。 */
    IPage<ApplicationDto> listMine(Long candidateId, int pageNum, int pageSize);

    /** 投递详情。候选人本人 / 投递对应职位的 HR / 管理员可查看。 */
    ApplicationDetailDto getDetail(Long requesterId, String requesterRole, Long applicationId);

    /** UC-14 候选人撤回投递（仅候选人本人 + 非终态）。 */
    WithdrawResponse withdraw(Long candidateId, Long applicationId);

    // ============ HR 端 ============

    /** UC-24 HR 投递列表（按职位过滤，默认排除 WITHDRAWN）。 */
    IPage<ApplicationDto> listForHr(Long hrUserId, HrApplicationListQuery query);

    /** UC-25 HR 推进投递状态（含合法转换校验 + 写状态历史）。 */
    AdvanceStatusResponse pushStatus(Long hrUserId, Long applicationId, String toStatus, String note);

    /** UC-26 HR 备注 CRUD。 */
    java.util.List<ApplicationNoteDto> listNotes(Long hrUserId, Long applicationId);

    ApplicationNoteDto addNote(Long hrUserId, Long applicationId, String content);

    ApplicationNoteDto updateNote(Long hrUserId, Long applicationId, Long noteId, String content);

    void deleteNote(Long hrUserId, Long applicationId, Long noteId);

    /** UC-27 HR 查看候选人投递时的简历快照。 */
    ResumeSnapshotDto getResumeSnapshot(Long hrUserId, Long applicationId);

    // ============ 内部：AI-2 评分完成回调 ============

    /** AI-2 异步评分完成后由 LlmScoreService 调用，写回 application.ai_score / ai_reason。 */
    void onAiScoreCompleted(Long applicationId, int score, String reason);
}
