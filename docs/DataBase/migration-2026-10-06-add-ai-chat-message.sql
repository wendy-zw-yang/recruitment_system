-- ============================================================
-- 2026-10-06 v0.6 §6 AI-4 智能客服 - 增量迁移
-- 仅创建 ai_chat_message 表；不影响其他表与数据
-- 可重复执行（CREATE TABLE IF NOT EXISTS）
-- ============================================================

CREATE TABLE IF NOT EXISTS `ai_chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户（CANDIDATE / HR，ADMIN 拒收）',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话 ID（前端生成 UUID，跨页面刷新保持）',
    `role` VARCHAR(16) NOT NULL COMMENT 'USER 用户提问 / ASSISTANT AI 回复',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_aichat_user_session_created` (`user_id`, `session_id`, `created_at`),
    KEY `idx_aichat_user_created` (`user_id`, `created_at`),
    CONSTRAINT `fk_aichat_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI-4 智能客服历史消息';

-- ============================================================
-- 验证：执行后应返回 1 行（表元数据）
-- ============================================================
SELECT 'ai_chat_message table created/verified' AS status;
SHOW CREATE TABLE ai_chat_message\G