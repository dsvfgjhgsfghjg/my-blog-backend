-- V35: 为认证历史表添加一次性消费标记，堵住敏感操作验证的重放漏洞
--
-- 背景：t_auth_history 中的 approved 记录是持久行。App 端应答挑战时，Redis 里的
--       挑战会被 verifyChallenge 删除（那一步是一次性的），但数据库侧没有任何
--       消费标记，于是同一条历史 approved 挑战可以被无限次复用，每次都能换到
--       一个新的 TOTP 会话 —— 即持有有效会话者可以绕过 @RequireTotp 的敏感操作验证。
--
-- 约定：敏感操作验证走「条件更新消费」——只接受 used_at IS NULL 且在时效窗口内
--       被批准的挑战（见 AppAuthService.consumeApprovedChallenge），
--       同一行只可能被一个并发请求消费成功。

ALTER TABLE t_auth_history ADD COLUMN IF NOT EXISTS used_at TIMESTAMP;
COMMENT ON COLUMN t_auth_history.used_at IS '被敏感操作消费的时间；NULL=尚未使用（可用于验证），非 NULL=已使用（不可再次用于验证）';
