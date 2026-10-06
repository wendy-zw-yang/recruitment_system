USE recruitment_system;

-- 0.1 究竟有几条 REJECTED 公司？
SELECT COUNT(*) AS rejected_companies
FROM company WHERE auth_status = 'REJECTED';

-- 0.2 这些公司对应的 HR 账号是否还在 users 表里？
SELECT u.id, u.email, u.status
FROM users u
JOIN company c ON c.hr_user_id = u.id
WHERE c.auth_status = 'REJECTED';

-- 0.3 这些 HR 创建的职位数？投递数？
SELECT
  (SELECT COUNT(*) FROM job
    WHERE hr_user_id IN (SELECT hr_user_id FROM company WHERE auth_status='REJECTED')) AS jobs,
  (SELECT COUNT(*) FROM application
    WHERE job_id IN
      (SELECT id FROM job WHERE hr_user_id IN
         (SELECT hr_user_id FROM company WHERE auth_status='REJECTED'))) AS applications;
