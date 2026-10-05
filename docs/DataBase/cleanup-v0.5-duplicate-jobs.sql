-- ============================================================
-- v0.6：清理重复职位 / 测试数据脚本
-- 编制日期：2026-10-05
-- 用途：诊断与清理 HR 测试期间或开发过程中遗留的重复职位数据
-- 适用：用户多次反馈"前端有重复 Java 工程师职位"
-- 重要：去重 key 是 (title, province, city_id)，不同城市的同名职位**可以共存**
-- ============================================================

USE recruitment_system;

-- ============================================================
-- 第一步：诊断（只读，不会修改数据；先跑这一段看输出）
-- ============================================================

-- 1.1 列出所有职位，按 title + 城市排序
SELECT id, hr_user_id, title, province, city_id, status, created_at
FROM job
WHERE is_deleted = 0
ORDER BY title, province, city_id, created_at DESC;

-- 1.2 按 (title, province, city_id) 分组统计重复数（>1 即同岗位同城市重复发布）
SELECT title, province, city_id, COUNT(*) AS cnt
FROM job
WHERE is_deleted = 0
GROUP BY title, province, city_id
HAVING COUNT(*) > 1
ORDER BY cnt DESC;

-- 1.3 列出所有 ONLINE 状态职位（候选人首页会看到的）
SELECT id, hr_user_id, title, province, city_id, status, published_at
FROM job
WHERE is_deleted = 0 AND status = 'ONLINE'
ORDER BY published_at DESC;

-- 1.4 按 HR 账号分组看哪些账号发布了最多职位
SELECT u.id AS hr_id, u.email, u.username, COUNT(j.id) AS job_count
FROM users u
LEFT JOIN job j ON j.hr_user_id = u.id AND j.is_deleted = 0
WHERE u.role_code = 'HR'
GROUP BY u.id, u.email, u.username
ORDER BY job_count DESC;

-- ============================================================
-- 第二步：清理（按需执行；先备份数据库！）
-- 关键：去重 key 是 (title, province, city_id)，不同城市同名职位不被视为重复
-- ============================================================

-- 2.1【推荐】软删除同 (title, province, city_id) 的重复职位，保留最早一条
--     即：同岗位 + 同省份 + 同城市 = 视为重复
--     不同城市的同名职位（如"Java 工程师-深圳"和"Java 工程师-北京"）不受影响
UPDATE job j
JOIN (
    SELECT MIN(id) AS keep_id, title, province, city_id
    FROM job
    WHERE is_deleted = 0
    GROUP BY title, province, city_id
    HAVING COUNT(*) > 1
) dup ON dup.title = j.title
     AND COALESCE(dup.province, '') = COALESCE(j.province, '')
     AND COALESCE(dup.city_id, 0) = COALESCE(j.city_id, 0)
     AND j.id != dup.keep_id
SET j.is_deleted = 1
WHERE j.is_deleted = 0;

-- 2.2【更安全】只清理发布时间 < 2026-10-05 的职位（保留新数据）
UPDATE job
SET is_deleted = 1
WHERE is_deleted = 0
  AND created_at < '2026-10-05 00:00:00';

-- 2.3【一键清空】所有测试职位 + 投递 + 收藏（极端情况，全清重新演示）
--     执行前请确保：所有重要数据已备份；候选人无未完成演示
-- DELETE FROM application_status_history WHERE application_id IN (SELECT id FROM application);
-- DELETE FROM application_note WHERE application_id IN (SELECT id FROM application);
-- DELETE FROM application;
-- DELETE FROM favorite_job;
-- DELETE FROM resume_attachment;
-- DELETE FROM resume;
-- DELETE FROM job_attachment;
-- DELETE FROM job;

-- ============================================================
-- 第三步：验证（清理后跑一遍，看是否还有重复）
-- ============================================================

-- 验证：按 (title, province, city_id) 分组应无重复
SELECT title, province, city_id, COUNT(*) AS cnt
FROM job
WHERE is_deleted = 0
GROUP BY title, province, city_id
HAVING COUNT(*) > 1;

-- 验证：全局统计
SELECT COUNT(*) AS total_active_jobs FROM job WHERE is_deleted = 0;
SELECT COUNT(*) AS total_online_jobs FROM job WHERE is_deleted = 0 AND status = 'ONLINE';
