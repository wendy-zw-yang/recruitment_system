package com.example.recruitmentsystem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.recruitmentsystem.dto.admin.CompanyAuditItem;
import com.example.recruitmentsystem.dto.admin.CompanyRejectRequest;

/**
 * 管理员公司审核服务。覆盖 UC-36。
 *
 * <p>所有写操作通过 {@link AuditLogService} 记录。</p>
 */
public interface AdminCompanyService {

    /** 分页列出公司（authStatus 可空，空 = 全部）。 */
    IPage<CompanyAuditItem> listCompanies(String authStatus, int pageNum, int pageSize);

    /** 默认列表：PENDING 状态。 */
    default IPage<CompanyAuditItem> listPending(int pageNum, int pageSize) {
        return listCompanies("PENDING", pageNum, pageSize);
    }

    /** 通过审核。 */
    void verify(Long adminId, Long companyId);

    /** 拒绝审核。disableHr=true 联动禁用 HR 账号。 */
    void reject(Long adminId, Long companyId, CompanyRejectRequest request);
}