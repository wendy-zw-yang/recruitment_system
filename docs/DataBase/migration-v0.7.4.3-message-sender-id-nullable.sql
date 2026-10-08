-- ============================================================
-- 迁移 v0.7.4.3：message.sender_id 改为可空
-- 用途：SYSTEM 消息的 sender_id 由 0 改为 NULL
--      原值 0 会触发 fk_message_sender FK 异常（users.id=0 不存在）
--      改用 NULL 走 FK 旁路（MySQL FK 对 NULL 不做存在性校验）
-- 兼容性：完全向后兼容——历史数据的 sender_id 仍为非 NULL（来自真实用户）
-- 安全性：幂等（重复执行无副作用）；仅修改列可空性，不丢数据
-- ============================================================

USE recruitment_system;

-- 1) 修改列可空性
ALTER TABLE `message`
    MODIFY COLUMN `sender_id` BIGINT NULL COMMENT '发送人 users.id；SYSTEM 消息此列为 NULL';

-- 2) 验证：列出列定义
SHOW CREATE TABLE `message`\G

-- 3) 验证：SYSTEM 消息（sender_role='SYSTEM'）的 sender_id 应为 NULL
SELECT id, sender_id, sender_role FROM `message` WHERE sender_role = 'SYSTEM' LIMIT 5;
