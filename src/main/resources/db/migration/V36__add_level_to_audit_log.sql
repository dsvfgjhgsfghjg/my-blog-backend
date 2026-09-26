-- V36: 审计日志增加等级列，并回填存量数据
--
-- 背景：审计日志原先只有布尔 success，没有等级概念。管理员无法一眼看出
--       "这条只是查了一下" 还是 "这条把某人的权限改了"，只能逐条读描述。
--
-- 取值：INFO(信息) / NOTICE(记录) / WARNING(警告) / ERROR(错误) / CRITICAL(关键)
--       约束由应用层枚举 com.leo.myblog.enums.AuditLevel 保证（与项目其他枚举一致，不加 CHECK）。
--
-- 回填策略：历史行只有 operation_type / success / request_method 三个依据，因此回填结果
--      是**近似值**而非精确还原。规则与 AuditLevelResolver 保持一致：
--        够拿到账号 → 关键；能毁数据或关防护 → 警告；只读 → 信息；其余 → 记录；
--        失败一律升级（至少警告，系统异常到错误，关键不回退）。
--
-- 大表提示：ADD COLUMN ... DEFAULT 在 PostgreSQL 11+ 只改元数据、不重写表，是瞬时操作。
--      下面那条回填会全表扫描并重写需要变更的行。若 t_audit_log 超过约 500 万行，
--      请把它移出迁移、改在低峰执行 —— 该语句可安全重复执行（见下）。

ALTER TABLE t_audit_log
    ADD COLUMN IF NOT EXISTS level VARCHAR(16) NOT NULL DEFAULT 'NOTICE';

COMMENT ON COLUMN t_audit_log.level IS
    '日志等级：INFO(信息)/NOTICE(记录)/WARNING(警告)/ERROR(错误)/CRITICAL(关键)';

-- ============ 存量回填 ============
-- 用一条 CASE 从**原始列**推导最终等级，而不是「先设基础等级、再逐级升级」的多条 UPDATE。
-- 后者看着直观，但不幂等：升级语句 `WHERE level='WARNING' AND success=FALSE` 在重复执行时
-- 会把已经升级过的行再升一级（实测 INFO+失败 本应是 WARNING，重跑一次就变成 ERROR）。
-- 现在这样写是原始列的纯函数，重复执行结果恒定；配合 IS DISTINCT FROM 只更新真正需要变的行。
WITH computed AS (
    SELECT id,
           CASE
               -- 关键：凭据重置与权限变更。失败也不回退 —— 权限变更失败同样是最高优先级
               WHEN operation_type IN ('RESET_PASSWORD', 'CHANGE_ROLE', 'SET_SUPER_ADMIN',
                                       'CHANGE_ABILITY', 'CHANGE_PASSWORD', 'BAN_IP')
                   THEN 'CRITICAL'
               -- 警告：破坏性或影响可用性；失败升到错误
               WHEN operation_type IN ('DELETE', 'TOGGLE_STATUS', 'REVOKE', 'REVOKE_SESSION',
                                       'UNBAN_IP', 'MIGRATE', 'CLEANUP', 'RESET_CONFIG')
                   THEN CASE WHEN success = FALSE THEN 'ERROR' ELSE 'WARNING' END
               -- 信息：只读。历史数据没有 QUERY 这个动作值，只能按请求方法近似判断
               WHEN request_method = 'GET' OR operation_type = 'LOGOUT'
                   THEN CASE WHEN success = FALSE THEN 'WARNING' ELSE 'INFO' END
               -- 记录：普通写操作；失败升到警告
               ELSE CASE WHEN success = FALSE THEN 'WARNING' ELSE 'NOTICE' END
           END AS new_level
      FROM t_audit_log
)
UPDATE t_audit_log t
   SET level = c.new_level
  FROM computed c
 WHERE t.id = c.id
   AND t.level IS DISTINCT FROM c.new_level;
