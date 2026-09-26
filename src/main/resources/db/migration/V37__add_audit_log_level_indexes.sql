-- V37: 审计日志按等级查询的索引
--
-- 用途：审计页新增「等级」筛选（默认按等级倒序看最严重的），以及等级分布统计。
-- 说明：这里用普通 CREATE INDEX 而非 CONCURRENTLY —— CONCURRENTLY 不能跑在事务里，
--      需要额外的脚本级配置（.conf 声明 executeInTransaction=false），代价是写法复杂且
--      失败会留下 INVALID 索引需手工 DROP 重建。审计表通常不会大到必须并发建索引，
--      先按简单方式建；若线上实测锁窗口不可接受，再改成 CONCURRENTLY 并配套回滚脚本。

CREATE INDEX IF NOT EXISTS idx_audit_log_level ON t_audit_log (level);

-- 配合「先看最严重的、同等级按时间倒序」这一默认排序
CREATE INDEX IF NOT EXISTS idx_audit_log_level_time ON t_audit_log (level, create_time DESC);
