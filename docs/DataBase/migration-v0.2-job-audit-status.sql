-- ============================================================
-- v0.2 迁移：职位草稿状态机修复
-- ============================================================
-- 【v0.3 已作废】本文件保留作为历史参考。
-- v0.3 UC-35 职位审核完全取消后：
--   - audit_status 字段保留但停用（固定 NONE），不再写入 PENDING/APPROVED/REJECTED
--   - HR 端 3 tab（DRAFT / ONLINE / OFFLINE），不再有审核中 tab
--   - 管理员侧不再有职位审核入口
--   - 候选人投递校验「职位 ONLINE」不再要求 audit_status=APPROVED
-- 如需保留历史 audit_status 数据一致性，可选执行下方的 UPDATE。
-- ============================================================
-- 问题：原 schema `audit_status` 默认值为 'PENDING'，
--       导致新建草稿自动进入审核队列（草稿/审核中两个 tab 重叠）。
-- 修复：默认值改为 'NONE'，HR 显式调 submitForAudit 后才进入 PENDING。
-- 同时把已存在的脏数据（草稿状态但 audit_status=PENDING）降级为 NONE。

-- 1) 修改默认值（仅影响之后插入的新行）
ALTER TABLE job MODIFY COLUMN audit_status VARCHAR(32) NOT NULL DEFAULT 'NONE'
    COMMENT 'NONE=未提交审核 / PENDING=待审核 / APPROVED=已通过 / REJECTED=已驳回';

-- 2) 现有数据迁移
--    把"草稿状态但 audit_status=PENDING"的脏数据修复为 NONE。
--    判定标准：status=DRAFT AND audit_status=PENDING AND audit_note IS NULL。
--    （audit_note 非空说明管理员已经实际处理过，保留 PENDING 状态避免破坏审计链）
UPDATE job
SET audit_status = 'NONE', audit_note = NULL
WHERE status = 'DRAFT'
  AND audit_status = 'PENDING'
  AND audit_note IS NULL;

-- 3) 验证迁移结果
SELECT id, title, status, audit_status, audit_note
FROM job
WHERE is_deleted = 0
ORDER BY id DESC
LIMIT 20;

-- 期望：
-- - 草稿职位显示 NONE → 它们只会进 HR 的"草稿"Tab，不会进 admin 待审列表
-- - 任何 audit_status=PENDING 的职位都应是真正 HR 提交过审核的（HR 在新版本会主动 submit）