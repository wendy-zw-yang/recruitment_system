package com.example.recruitmentsystem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.dto.job.JobAuditRequest;
import com.example.recruitmentsystem.dto.job.JobCreateRequest;
import com.example.recruitmentsystem.dto.job.JobDto;
import com.example.recruitmentsystem.dto.job.JobUpdateRequest;
import com.example.recruitmentsystem.llm.service.LlmJdService;

/**
 * 职位业务接口。覆盖 UC-20 公司信息维护 / UC-21 发布 / UC-22 编辑+AI-3 / UC-23 下架 / UC-09 浏览 / UC-10 搜索 / UC-11 收藏。
 */
public interface JobService {

    /** UC-21 HR 创建职位（草稿） */
    JobDto createJob(Long hrUserId, JobCreateRequest request);

    /** UC-22 HR 编辑职位（仅 DRAFT / OFFLINE 可编辑） */
    JobDto updateJob(Long hrUserId, Long jobId, JobUpdateRequest request);

    /** 上线：要求 audit_status=APPROVED */
    JobDto publishJob(Long hrUserId, Long jobId);

    /** UC-23 HR 下架职位：仅 ONLINE 可下架 */
    JobDto offlineJob(Long hrUserId, Long jobId);

    /** HR 删除职位（软删）：仅 DRAFT / OFFLINE */
    void deleteJob(Long hrUserId, Long jobId);

    /** 候选人 / HR / Admin 查看职位详情 */
    JobDto getDetail(Long requesterId, String requesterRole, Long jobId);

    /** HR 端按状态分页查询自己发布的职位 */
    IPage<JobDto> listMineByStatus(Long hrUserId, String status, int pageNum, int pageSize);

    /** 候选人端分页查询（仅 ONLINE + APPROVED） */
    IPage<JobDto> listForCandidate(String keyword, Long industryId, Long cityId, String province, String sort,
                                   int pageNum, int pageSize);

    /** UC-11 候选人收藏/取消收藏 */
    boolean toggleFavorite(Long candidateId, Long jobId);

    /** 候选人是否已收藏某职位（用于列表展示） */
    boolean isFavorited(Long candidateId, Long jobId);

    /** Admin 端：分页查询待审核职位 */
    IPage<JobDto> listPendingAudit(int pageNum, int pageSize);

    /** Admin 端：审核通过 / 驳回 */
    JobDto auditJob(Long adminId, Long jobId, JobAuditRequest request);

    /** UC-22 HR 一键润色（AI-3，润色所有内容字段） */
    LlmJdService.PolishedJd polishJd(Long hrUserId,
                                       String title,
                                       String industryName,
                                       String cityName,
                                       String salaryRange,
                                       String description,
                                       String requirements,
                                       String keywords);
}
