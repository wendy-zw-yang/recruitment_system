-- ============================================================
-- 清理单元测试残留的"@test.local"账号（候选端 + HR 端 + admin）
-- 编制日期：2026-10-05
-- 背景：上版漏了 ai_call_log / audit_log / conversation / message 4 张表
--       这 4 张表都通过 FK 引用 users.id，必须先清干净才能删 users
-- 模式：unit tests 用 `<label>-<nanoTime>@test.local` 创建账号
-- ============================================================

USE recruitment_system;

-- ============================================================
-- 步骤 0：先看一眼要清的范围（只读，不会改数据）
-- ============================================================

-- 0.1 测试账号总数（按角色）
SELECT role_code, COUNT(*) AS cnt
FROM users
WHERE email LIKE '%@test.local'
GROUP BY role_code;

-- 0.2 这些账号关联的依赖数据规模（11 张子表）
SELECT
  (SELECT COUNT(*) FROM message
    WHERE sender_id IN (SELECT id FROM users WHERE email LIKE '%@test.local')) AS msg_to_delete,
  (SELECT COUNT(*) FROM conversation
    WHERE hr_user_id IN (SELECT id FROM users WHERE email LIKE '%@test.local')
       OR candidate_id IN (SELECT id FROM users WHERE email LIKE '%@test.local')) AS conv_to_delete,
  (SELECT COUNT(*) FROM application_status_history
    WHERE application_id IN
      (SELECT id FROM application WHERE candidate_id IN
         (SELECT id FROM users WHERE email LIKE '%@test.local'))) AS history_to_delete,
  (SELECT COUNT(*) FROM application_note
    WHERE application_id IN
      (SELECT id FROM application WHERE candidate_id IN
         (SELECT id FROM users WHERE email LIKE '%@test.local'))) AS notes_to_delete,
  (SELECT COUNT(*) FROM application
    WHERE candidate_id IN
      (SELECT id FROM users WHERE email LIKE '%@test.local')) AS apps_to_delete,
  (SELECT COUNT(*) FROM favorite_job
    WHERE candidate_id IN
      (SELECT id FROM users WHERE email LIKE '%@test.local')) AS favs_to_delete,
  (SELECT COUNT(*) FROM resume_attachment
    WHERE resume_id IN
      (SELECT id FROM resume WHERE candidate_id IN
         (SELECT id FROM users WHERE email LIKE '%@test.local'))) AS atts_to_delete,
  (SELECT COUNT(*) FROM resume
    WHERE candidate_id IN
      (SELECT id FROM users WHERE email LIKE '%@test.local')) AS resumes_to_delete,
  (SELECT COUNT(*) FROM candidate_profile
    WHERE user_id IN
      (SELECT id FROM users WHERE email LIKE '%@test.local')) AS profiles_to_delete,
  (SELECT COUNT(*) FROM ai_call_log
    WHERE caller_user_id IN
      (SELECT id FROM users WHERE email LIKE '%@test.local')) AS aicall_to_delete,
  (SELECT COUNT(*) FROM audit_log
    WHERE admin_id IN
      (SELECT id FROM users WHERE email LIKE '%@test.local')) AS audit_to_delete;

-- 0.3 看具体前 10 个测试账号（核对数据）
SELECT id, email, username, role_code, status, created_at
FROM users
WHERE email LIKE '%@test.local'
ORDER BY id LIMIT 10;

-- ============================================================
-- 步骤 1：硬删（按 FK 依赖顺序）
-- 全部用会话变量 + FIND_IN_SET（避免临时表跨语句丢失的坑）
-- ============================================================

USE recruitment_system;

-- 1.0 调大 group_concat 上限（防 ID 列表被截断；默认 1024 不够用）
SET SESSION group_concat_max_len = 1000000;

-- 1.1 一次性捕获所有"@test.local"用户 ID
SELECT GROUP_CONCAT(id) INTO @test_user_ids
FROM users WHERE email LIKE '%@test.local';

-- ★ 立即看一下捕获结果（id=0 或 csv 为空 → 没有数据可清）
SELECT
  LENGTH(@test_user_ids)               AS csv_length,
  (SELECT COUNT(*) FROM users
    WHERE email LIKE '%@test.local')    AS test_user_count;
-- 上面数字 > 0 才继续

START TRANSACTION;

-- 1.2 message（FK → sender_id → users）
DELETE FROM message
WHERE sender_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.3 conversation（FK → hr_user_id / candidate_id → users）
--     先删 message_attchment (FK → message, conversation) 也包含在 message 级联后处理
DELETE FROM conversation
WHERE hr_user_id IN (SELECT id FROM users WHERE email LIKE '%@test.local')
   OR candidate_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.4 application_status_history（FK → application → users；changed_by → users）
DELETE FROM application_status_history
WHERE application_id IN (
    SELECT a.id FROM application a
    JOIN users c ON a.candidate_id = c.id
    WHERE c.email LIKE '%@test.local'
);

-- 1.5 application_note
DELETE FROM application_note
WHERE application_id IN (
    SELECT a.id FROM application a
    JOIN users c ON a.candidate_id = c.id
    WHERE c.email LIKE '%@test.local'
);

-- 1.6 application
DELETE FROM application
WHERE candidate_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.7 favorite_job
DELETE FROM favorite_job
WHERE candidate_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.8 resume_attachment
DELETE FROM resume_attachment
WHERE resume_id IN (
    SELECT id FROM resume
    WHERE candidate_id IN (SELECT id FROM users WHERE email LIKE '%@test.local')
);

-- 1.9 resume
DELETE FROM resume
WHERE candidate_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.10 candidate_profile
DELETE FROM candidate_profile
WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.11 job（兜底：清测试 HR 拥有的 job）
DELETE FROM job
WHERE hr_user_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.12 company（兜底：清测试 HR 拥有的 company）
--     注意：company.verified_by 也是 FK to users.id（管理员审核人），所以要清掉这个外键引用
--     先把 verified_by 置 NULL，再清 company
UPDATE company
SET verified_by = NULL
WHERE verified_by IN (SELECT id FROM users WHERE email LIKE '%@test.local');

DELETE FROM company
WHERE hr_user_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.13 ai_call_log（FK → caller_user_id → users）—— 上版漏的
DELETE FROM ai_call_log
WHERE caller_user_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.14 audit_log（FK → admin_id → users）—— 上版漏的
DELETE FROM audit_log
WHERE admin_id IN (SELECT id FROM users WHERE email LIKE '%@test.local');

-- 1.15 users（最后：所有"@test.local"账号本身）
DELETE FROM users
WHERE email LIKE '%@test.local';

-- 跑完上面所有 DELETE 后看 SELECT ROW_COUNT() 累计是否 > 0
-- ★ 确认无误手敲：COMMIT;
-- 想撤销：ROLLBACK;
-- 跑完记得：SET @test_user_ids = NULL;

-- ============================================================
-- 步骤 2：验证清理结果
-- ============================================================

-- 2.1 测试账号应全清
SELECT COUNT(*) AS remaining_test_users
FROM users
WHERE email LIKE '%@test.local';
-- 期望 0

-- 2.2 11 张子表的孤儿引用检查（所有 FK 不应悬挂）
SELECT 'orphan_apps' AS check_name, COUNT(*) AS cnt
  FROM application a LEFT JOIN job j ON a.job_id = j.id
 WHERE j.id IS NULL
UNION ALL
SELECT 'orphan_favs', COUNT(*)
  FROM favorite_job f LEFT JOIN job j ON f.job_id = j.id
 WHERE j.id IS NULL
UNION ALL
SELECT 'orphan_history', COUNT(*)
  FROM application_status_history h LEFT JOIN application a ON h.application_id = a.id
 WHERE a.id IS NULL
UNION ALL
SELECT 'orphan_note', COUNT(*)
  FROM application_note n LEFT JOIN application a ON n.application_id = a.id
 WHERE a.id IS NULL
UNION ALL
SELECT 'orphan_resume', COUNT(*)
  FROM resume r LEFT JOIN users u ON r.candidate_id = u.id
 WHERE u.id IS NULL
UNION ALL
SELECT 'orphan_candprof', COUNT(*)
  FROM candidate_profile cp LEFT JOIN users u ON cp.user_id = u.id
 WHERE u.id IS NULL
UNION ALL
SELECT 'orphan_msg_sender', COUNT(*)
  FROM message m LEFT JOIN users u ON m.sender_id = u.id
 WHERE u.id IS NULL
UNION ALL
SELECT 'orphan_msg_conv', COUNT(*)
  FROM message m LEFT JOIN conversation c ON m.conversation_id = c.id
 WHERE c.id IS NULL
UNION ALL
SELECT 'orphan_msg_att_msg', COUNT(*)
  FROM message_attachment ma LEFT JOIN message m ON ma.message_id = m.id
 WHERE m.id IS NULL
UNION ALL
SELECT 'orphan_conv_hr', COUNT(*)
  FROM conversation c LEFT JOIN users u ON c.hr_user_id = u.id
 WHERE u.id IS NULL
UNION ALL
SELECT 'orphan_conv_cand', COUNT(*)
  FROM conversation c LEFT JOIN users u ON c.candidate_id = u.id
 WHERE u.id IS NULL
UNION ALL
SELECT 'orphan_job_company', COUNT(*)
  FROM job j LEFT JOIN company c ON j.company_id = c.id
 WHERE c.id IS NULL
UNION ALL
SELECT 'orphan_job_hr', COUNT(*)
  FROM job j LEFT JOIN users u ON j.hr_user_id = u.id
 WHERE u.id IS NULL
UNION ALL
SELECT 'orphan_company_hr', COUNT(*)
  FROM company c LEFT JOIN users u ON c.hr_user_id = u.id
 WHERE u.id IS NULL
UNION ALL
SELECT 'orphan_company_verified_by', COUNT(*)
  FROM company c LEFT JOIN users u ON c.verified_by = u.id
 WHERE c.verified_by IS NOT NULL AND u.id IS NULL
UNION ALL
SELECT 'orphan_aicall_caller', COUNT(*)
  FROM ai_call_log l LEFT JOIN users u ON l.caller_user_id = u.id
 WHERE u.id IS NULL
UNION ALL
SELECT 'orphan_audit_admin', COUNT(*)
  FROM audit_log a LEFT JOIN users u ON a.admin_id = u.id
 WHERE u.id IS NULL;
-- 期望全部 0

-- 2.3 整体数据完整性统计
SELECT
  (SELECT COUNT(*) FROM users)                 AS total_users,
  (SELECT COUNT(*) FROM users
    WHERE email NOT LIKE '%@test.local')       AS real_users,
  (SELECT COUNT(*) FROM company)              AS total_companies,
  (SELECT COUNT(*) FROM company
    WHERE auth_status = 'PENDING')             AS pending_companies,
  (SELECT COUNT(*) FROM company
    WHERE auth_status = 'REJECTED')             AS rejected_companies,
  (SELECT COUNT(*) FROM job)                   AS total_jobs,
  (SELECT COUNT(*) FROM job
    WHERE status = 'ONLINE')                   AS online_jobs,
  (SELECT COUNT(*) FROM application)            AS total_applications,
  (SELECT COUNT(*) FROM ai_call_log)            AS total_ai_calls,
  (SELECT COUNT(*) FROM audit_log)             AS total_audit_logs;
