-- ============================================================
-- AI 集成的智能招聘系统 - 数据库 schema
-- 编制日期：2026-09-28
-- 文档：docs/DataBase/数据库设计.md
-- 字符集：utf8mb4 / 排序：utf8mb4_0900_ai_ci / 引擎：InnoDB
-- 建表顺序：见 数据库设计.md §5
-- 使用方式：
--   1. 登录 MySQL：mysql -u root -p
--   2. 执行：source /path/to/schema.sql
--   3. 或在 MySQL Workbench / Navicat 中导入
-- 注：本脚本不包含种子数据，由用户自行注入
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 创建数据库（如不存在）
CREATE DATABASE IF NOT EXISTS recruitment_system
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE recruitment_system;

-- ============================================================
-- 1. user（用户账号）
-- ============================================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `email` VARCHAR(255) NOT NULL COMMENT '登录邮箱（唯一）',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
    `role_code` VARCHAR(32) NOT NULL COMMENT '角色: CANDIDATE / HR / ADMIN',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
    `username` VARCHAR(64) NULL COMMENT '昵称 / 显示名（可选，默认=email@前缀）',
    `phone` VARCHAR(32) NULL COMMENT '手机号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_email` (`email`),
    KEY `idx_user_role` (`role_code`),
    KEY `idx_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号（CANDIDATE / HR / ADMIN）';

-- ============================================================
-- 2. dict_industry（行业字典，一级 + 二级）
-- ============================================================
DROP TABLE IF EXISTS `dict_industry`;
CREATE TABLE `dict_industry` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(128) NOT NULL COMMENT '行业名',
    `parent_id` BIGINT NULL COMMENT '父级行业（NULL=一级）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_dict_industry_parent` (`parent_id`),
    KEY `idx_dict_industry_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行业字典（一级 + 二级树形）';

-- ============================================================
-- 3. dict_city（城市字典）
-- ============================================================
DROP TABLE IF EXISTS `dict_city`;
CREATE TABLE `dict_city` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(128) NOT NULL COMMENT '城市名',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_city_name` (`name`),
    KEY `idx_dict_city_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='城市字典（强制下拉）';

-- ============================================================
-- 4. dict_skill_suggestion（技能建议池）
-- ============================================================
DROP TABLE IF EXISTS `dict_skill_suggestion`;
CREATE TABLE `dict_skill_suggestion` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(128) NOT NULL COMMENT '技能名',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_skill_name` (`name`),
    KEY `idx_dict_skill_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能建议池（自动补全）';

-- ============================================================
-- 5. email_code（邮箱验证码）
-- ============================================================
DROP TABLE IF EXISTS `email_code`;
CREATE TABLE `email_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `email` VARCHAR(255) NOT NULL COMMENT '接收方邮箱',
    `code` VARCHAR(16) NOT NULL COMMENT '6 位验证码',
    `code_type` VARCHAR(32) NOT NULL COMMENT 'register / login / reset',
    `used` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已使用',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间（5 分钟）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_email_code_email_type` (`email`, `code_type`),
    KEY `idx_email_code_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱验证码';

-- ============================================================
-- 6. candidate_profile（求职者扩展资料 + 求职偏好）
-- ============================================================
DROP TABLE IF EXISTS `candidate_profile`;
CREATE TABLE `candidate_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '关联 user.id（CANDIDATE）',
    `expected_position` VARCHAR(128) NULL COMMENT '期望职位（自由输入）',
    `expected_industry_id` BIGINT NULL COMMENT '期望行业',
    `expected_city_id` BIGINT NULL COMMENT '期望城市',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_candidate_profile_user` (`user_id`),
    KEY `idx_candidate_profile_user` (`user_id`),
    CONSTRAINT `fk_candidate_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_candidate_profile_industry` FOREIGN KEY (`expected_industry_id`) REFERENCES `dict_industry` (`id`),
    CONSTRAINT `fk_candidate_profile_city` FOREIGN KEY (`expected_city_id`) REFERENCES `dict_city` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='求职者扩展资料（含求职偏好）';

-- ============================================================
-- 7. company（HR 所属公司）
-- ============================================================
DROP TABLE IF EXISTS `company`;
CREATE TABLE `company` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `hr_user_id` BIGINT NOT NULL COMMENT '关联 user.id（HR），一个 HR 一个公司',
    `name` VARCHAR(255) NOT NULL COMMENT '公司名',
    `industry_id` BIGINT NULL COMMENT '行业',
    `scale` VARCHAR(64) NULL COMMENT '规模',
    `description` TEXT NULL COMMENT '公司简介',
    `auth_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / VERIFIED / REJECTED',
    `auth_note` VARCHAR(512) NULL COMMENT '拒绝理由',
    `verified_at` DATETIME NULL COMMENT '审核通过时间',
    `verified_by` BIGINT NULL COMMENT '审核管理员 user.id',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_company_hr_user` (`hr_user_id`),
    KEY `idx_company_hr_user` (`hr_user_id`),
    KEY `idx_company_auth_status` (`auth_status`),
    KEY `idx_company_industry` (`industry_id`),
    KEY `idx_company_verified_by` (`verified_by`),
    CONSTRAINT `fk_company_hr` FOREIGN KEY (`hr_user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_company_industry` FOREIGN KEY (`industry_id`) REFERENCES `dict_industry` (`id`),
    CONSTRAINT `fk_company_verified_by` FOREIGN KEY (`verified_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HR 所属公司（异步审核）';

-- ============================================================
-- 8. job（职位 / JD）
-- ============================================================
DROP TABLE IF EXISTS `job`;
CREATE TABLE `job` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `hr_user_id` BIGINT NOT NULL COMMENT '关联 user.id（HR）',
    `company_id` BIGINT NOT NULL COMMENT '关联 company.id',
    `title` VARCHAR(255) NOT NULL COMMENT '职位标题',
    `industry_id` BIGINT NULL COMMENT '行业',
    `city_id` BIGINT NULL COMMENT '城市',
    `salary_min` INT NULL COMMENT '薪资下限（元/月）',
    `salary_max` INT NULL COMMENT '薪资上限（元/月）',
    `description` TEXT NOT NULL COMMENT 'JD 岗位职责',
    `requirements` TEXT NOT NULL COMMENT 'JD 任职要求',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / ONLINE / OFFLINE / DELETED',
    `audit_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    `audit_note` VARCHAR(512) NULL COMMENT '审核驳回理由',
    `published_at` DATETIME NULL COMMENT '发布时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_job_status_audit_published` (`status`, `audit_status`, `is_deleted`, `published_at`),
    KEY `idx_job_hr` (`hr_user_id`),
    KEY `idx_job_company` (`company_id`),
    KEY `idx_job_industry` (`industry_id`),
    KEY `idx_job_city` (`city_id`),
    CONSTRAINT `fk_job_hr` FOREIGN KEY (`hr_user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_job_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`),
    CONSTRAINT `fk_job_industry` FOREIGN KEY (`industry_id`) REFERENCES `dict_industry` (`id`),
    CONSTRAINT `fk_job_city` FOREIGN KEY (`city_id`) REFERENCES `dict_city` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位 / JD';

-- ============================================================
-- 9. resume（简历主体）
-- ============================================================
DROP TABLE IF EXISTS `resume`;
CREATE TABLE `resume` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `candidate_id` BIGINT NOT NULL COMMENT '关联 user.id（CANDIDATE）',
    `basic_name` VARCHAR(64) NULL COMMENT '真实姓名（简历基本信息）',
    `basic_phone` VARCHAR(32) NULL COMMENT '电话',
    `basic_email` VARCHAR(255) NULL COMMENT '邮箱',
    `education` JSON NULL COMMENT '教育经历 JSON 数组',
    `work` JSON NULL COMMENT '工作经历 JSON 数组',
    `projects` JSON NULL COMMENT '项目经历 JSON 数组',
    `skills` TEXT NULL COMMENT '技能（自由输入）',
    `self_intro` TEXT NULL COMMENT '自我介绍',
    `is_archived` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'false=ACTIVE / true=ARCHIVED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_resume_candidate_archived` (`candidate_id`, `is_archived`),
    KEY `idx_resume_candidate` (`candidate_id`),
    CONSTRAINT `fk_resume_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历主体；单一 ACTIVE 由应用层强制';

-- ============================================================
-- 10. resume_attachment（简历附件元数据）
-- ============================================================
DROP TABLE IF EXISTS `resume_attachment`;
CREATE TABLE `resume_attachment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `resume_id` BIGINT NOT NULL COMMENT '关联 resume.id',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(512) NOT NULL COMMENT '存储路径（相对 UPLOAD_DIR）',
    `file_size` BIGINT NOT NULL COMMENT '字节',
    `mime_type` VARCHAR(128) NULL COMMENT 'MIME 类型',
    `uploaded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_resume_attachment_resume` (`resume_id`),
    CONSTRAINT `fk_resume_attachment_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历附件元数据';

-- ============================================================
-- 11. application（投递记录）
-- ============================================================
DROP TABLE IF EXISTS `application`;
CREATE TABLE `application` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `candidate_id` BIGINT NOT NULL COMMENT '关联 user.id（CANDIDATE）',
    `job_id` BIGINT NOT NULL COMMENT '关联 job.id',
    `resume_snapshot_id` BIGINT NOT NULL COMMENT '投递时的 ACTIVE 简历快照 id',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW / VIEWED_BY_HR / RESUME_PASSED / INTERVIEWING / OFFERED / HIRED / REJECTED / WITHDRAWN',
    `ai_score` INT NULL COMMENT '0-100；NULL=待评分',
    `ai_reason` VARCHAR(512) NULL COMMENT 'AI 评分理由',
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_app_candidate_job` (`candidate_id`, `job_id`),
    KEY `idx_app_job_status` (`job_id`, `status`, `is_deleted`),
    KEY `idx_app_candidate_status` (`candidate_id`, `status`, `is_deleted`),
    KEY `idx_app_resume_snapshot` (`resume_snapshot_id`),
    CONSTRAINT `fk_app_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_app_job` FOREIGN KEY (`job_id`) REFERENCES `job` (`id`),
    CONSTRAINT `fk_app_resume` FOREIGN KEY (`resume_snapshot_id`) REFERENCES `resume` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投递记录；ai_score NULL 表示待评分';

-- ============================================================
-- 12. application_status_history（状态时间线）
-- ============================================================
DROP TABLE IF EXISTS `application_status_history`;
CREATE TABLE `application_status_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `application_id` BIGINT NOT NULL COMMENT '关联 application.id',
    `from_status` VARCHAR(32) NULL COMMENT '原状态（NULL=初次创建）',
    `to_status` VARCHAR(32) NOT NULL COMMENT '目标状态',
    `changed_by` BIGINT NOT NULL COMMENT '变更人 user.id',
    `note` VARCHAR(512) NULL COMMENT '备注（如驳回理由）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_hist_app_changed` (`application_id`, `created_at`),
    KEY `idx_hist_changed_by` (`changed_by`),
    CONSTRAINT `fk_hist_app` FOREIGN KEY (`application_id`) REFERENCES `application` (`id`),
    CONSTRAINT `fk_hist_user` FOREIGN KEY (`changed_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='状态时间线';

-- ============================================================
-- 13. application_note（HR 内部备注）
-- ============================================================
DROP TABLE IF EXISTS `application_note`;
CREATE TABLE `application_note` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `application_id` BIGINT NOT NULL COMMENT '关联 application.id',
    `hr_user_id` BIGINT NOT NULL COMMENT 'HR user.id',
    `content` TEXT NOT NULL COMMENT '备注内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_note_app` (`application_id`),
    KEY `idx_note_hr` (`hr_user_id`),
    CONSTRAINT `fk_note_app` FOREIGN KEY (`application_id`) REFERENCES `application` (`id`),
    CONSTRAINT `fk_note_hr` FOREIGN KEY (`hr_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HR 内部备注（仅 HR 自己可见）';

-- ============================================================
-- 14. favorite_job（求职者收藏职位）
-- ============================================================
DROP TABLE IF EXISTS `favorite_job`;
CREATE TABLE `favorite_job` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `candidate_id` BIGINT NOT NULL COMMENT '关联 user.id（CANDIDATE）',
    `job_id` BIGINT NOT NULL COMMENT '关联 job.id',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_favorite_candidate_job` (`candidate_id`, `job_id`),
    KEY `idx_favorite_candidate` (`candidate_id`),
    KEY `idx_favorite_job` (`job_id`),
    CONSTRAINT `fk_favorite_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_favorite_job` FOREIGN KEY (`job_id`) REFERENCES `job` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='求职者收藏职位（联合唯一）';

-- ============================================================
-- 15. conversation（消息会话，二元组唯一）
-- ============================================================
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `hr_user_id` BIGINT NOT NULL COMMENT '关联 user.id（HR）',
    `candidate_id` BIGINT NOT NULL COMMENT '关联 user.id（CANDIDATE）',
    `last_message_at` DATETIME NULL COMMENT '最后消息时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_hr_candidate` (`hr_user_id`, `candidate_id`, `is_deleted`),
    KEY `idx_conversation_hr_updated` (`hr_user_id`, `last_message_at`),
    KEY `idx_conversation_candidate_updated` (`candidate_id`, `last_message_at`),
    CONSTRAINT `fk_conversation_hr` FOREIGN KEY (`hr_user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_conversation_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息会话；二元组唯一';

-- ============================================================
-- 16. message（消息）
-- ============================================================
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id` BIGINT NOT NULL COMMENT '关联 conversation.id',
    `sender_id` BIGINT NOT NULL COMMENT '发送人 user.id；SYSTEM=0 特殊值',
    `sender_role` VARCHAR(32) NOT NULL COMMENT 'CANDIDATE / HR / SYSTEM',
    `job_id` BIGINT NULL COMMENT '关联职位（可空）',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `read_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '已读标记：0 未读 / 1 已读',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_message_conv_created` (`conversation_id`, `created_at`),
    KEY `idx_message_sender` (`sender_id`),
    KEY `idx_message_job` (`job_id`),
    CONSTRAINT `fk_message_conv` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`),
    CONSTRAINT `fk_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_message_job` FOREIGN KEY (`job_id`) REFERENCES `job` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息；SYSTEM 角色用于系统通知';

-- ============================================================
-- 17. message_attachment（消息附件）
-- ============================================================
DROP TABLE IF EXISTS `message_attachment`;
CREATE TABLE `message_attachment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id` BIGINT NOT NULL COMMENT '关联 message.id',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(512) NOT NULL COMMENT '存储路径',
    `file_size` BIGINT NOT NULL COMMENT '字节',
    `mime_type` VARCHAR(128) NULL COMMENT 'MIME 类型',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_message_attachment_message` (`message_id`),
    CONSTRAINT `fk_message_attachment_message` FOREIGN KEY (`message_id`) REFERENCES `message` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息附件';

-- ============================================================
-- 18. ai_call_log（AI 调用日志）
-- ============================================================
DROP TABLE IF EXISTS `ai_call_log`;
CREATE TABLE `ai_call_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `function_code` VARCHAR(32) NOT NULL COMMENT 'AI-1 / AI-2 / AI-3 / AI-4 / AI-5 / AI-6',
    `prompt` TEXT NOT NULL COMMENT '输入提示词',
    `response` TEXT NULL COMMENT '输出响应',
    `latency_ms` INT NOT NULL COMMENT '耗时（毫秒）',
    `prompt_tokens` INT NULL COMMENT '输入 token 数',
    `completion_tokens` INT NULL COMMENT '输出 token 数',
    `status` VARCHAR(32) NOT NULL COMMENT 'SUCCESS / FAIL / TIMEOUT',
    `error_message` VARCHAR(512) NULL COMMENT '错误信息',
    `caller_user_id` BIGINT NULL COMMENT '调用方 user.id',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_ailog_func_created` (`function_code`, `created_at`),
    KEY `idx_ailog_caller` (`caller_user_id`),
    KEY `idx_ailog_status` (`status`),
    CONSTRAINT `fk_ailog_caller` FOREIGN KEY (`caller_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 调用日志';

-- ============================================================
-- 19. audit_log（管理员操作日志）
-- ============================================================
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admin_id` BIGINT NOT NULL COMMENT '管理员 user.id（ADMIN 角色）',
    `action_type` VARCHAR(64) NOT NULL COMMENT 'USER_ENABLE / USER_DISABLE / USER_RESET_PASSWORD / JOB_APPROVE / JOB_REJECT / COMPANY_VERIFY / COMPANY_REJECT / RESUME_DISABLE / DICT_*',
    `target_id` BIGINT NULL COMMENT '操作目标 id',
    `target_type` VARCHAR(64) NULL COMMENT 'USER / JOB / COMPANY / RESUME / DICT_INDUSTRY / DICT_CITY / DICT_SKILL',
    `before_value` JSON NULL COMMENT '操作前值',
    `after_value` JSON NULL COMMENT '操作后值',
    `note` VARCHAR(512) NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_audit_admin_created` (`admin_id`, `created_at`),
    KEY `idx_audit_target` (`target_type`, `target_id`),
    CONSTRAINT `fk_audit_admin` FOREIGN KEY (`admin_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员操作日志';

-- ============================================================
-- 启用外键检查
-- ============================================================
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 建表完成
-- 19 张表全部创建
-- 后续步骤：
--   1. 注入种子数据（admin + 字典）
--   2. 验证 by `SHOW TABLES;` 和 `SHOW CREATE TABLE user;`
-- ============================================================