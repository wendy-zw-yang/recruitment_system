-- ============================================================
-- migration-add-job-keywords.sql
-- 用途：给 job 表增加 keywords 列（AI 一键润色结果持久化）
-- 何时执行：批 4 切换至新代码前手动跑一次
-- ============================================================

USE recruitment_system;

-- 检查列是否已存在（幂等保护）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'recruitment_system'
      AND TABLE_NAME = 'job'
      AND COLUMN_NAME = 'keywords'
);

SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `job` ADD COLUMN `keywords` VARCHAR(255) NULL COMMENT "关键词（逗号分隔）" AFTER `requirements`',
    'SELECT "keywords column already exists" AS info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'recruitment_system'
  AND TABLE_NAME = 'job'
  AND COLUMN_NAME = 'keywords';
