-- 把 t_audit_log 历史行里的明文凭据就地打码。
--
-- 背景：审计切面早期只判断「形参名是否含 password」，导致登录/注册/换令牌等接口的
-- 明文口令被原样写进了 request_params。AuditParamSanitizer 是后来才补的
-- （2026-09-26 的提交 3597f93），**在此之前**写入的行需要单独清理。
-- 该提交上线后写入即已自动脱敏，本脚本只处理历史遗留。
--
-- 已于 2026-09-27 在生产执行过一次：UPDATE 281。
-- 执行前已在本地用真实备份完整演练（281 行变更、非敏感叶子改动 0），执行后复核
-- 明文残留为 0、总行数不变（2025）。
--
-- ⚠️ 执行前必做：
--   1. 先备份。整表才 2000 行左右，直接整表导出即可：
--        COPY (SELECT * FROM t_audit_log ORDER BY id) TO STDOUT WITH CSV HEADER;
--      注意：**清理线上表不会清理已有备份/dump**，历史备份里同样躺着这些明文。
--   2. 先跑 audit_credential_assessment.sql 看清规模与分布。
--
-- 本脚本只改 request_params / extra_data 两列里**敏感键对应的值**，其余参数原样保留，
-- 不删除任何审计记录。替换是幂等的：值已经是 ***MASKED*** 的再替换一次结果不变。

WITH sensitive(key_re) AS (
    VALUES ('(password|passwd|pwd|old_?password|new_?password|confirm_?password|'
         || 'client_?secret|secret|secret_?key|private_?key|credential|credentials|'
         || 'token|access_?token|refresh_?token|id_?token|authorization|cookie|backup_?code)')
),
-- 只挑「确有明文」的行：键名命中 且 值不是掩码 且 值非空
targets AS (
    SELECT DISTINCT l.id
      FROM t_audit_log l
      CROSS JOIN LATERAL regexp_matches(l.request_params,
             '"' || (SELECT key_re FROM sensitive) || '"[[:space:]]*:[[:space:]]*"([^"]*)"', 'gi') AS m(matches)
     WHERE l.request_params IS NOT NULL
       AND m.matches[2] <> '***MASKED***' AND m.matches[2] <> ''
    UNION
    SELECT DISTINCT l.id
      FROM t_audit_log l
      CROSS JOIN LATERAL regexp_matches(l.extra_data,
             '"' || (SELECT key_re FROM sensitive) || '"[[:space:]]*:[[:space:]]*"([^"]*)"', 'gi') AS m(matches)
     WHERE l.extra_data IS NOT NULL
       AND m.matches[2] <> '***MASKED***' AND m.matches[2] <> ''
)
UPDATE t_audit_log t
   SET request_params = regexp_replace(t.request_params,
         '("(password|passwd|pwd|old_?password|new_?password|confirm_?password|'
      || 'client_?secret|secret|secret_?key|private_?key|credential|credentials|'
      || 'token|access_?token|refresh_?token|id_?token|authorization|cookie|backup_?code)"'
      || '[[:space:]]*:[[:space:]]*")([^"]*)(")',
         '\1***MASKED***\4', 'gi'),
       extra_data = regexp_replace(t.extra_data,
         '("(password|passwd|pwd|old_?password|new_?password|confirm_?password|'
      || 'client_?secret|secret|secret_?key|private_?key|credential|credentials|'
      || 'token|access_?token|refresh_?token|id_?token|authorization|cookie|backup_?code)"'
      || '[[:space:]]*:[[:space:]]*")([^"]*)(")',
         '\1***MASKED***\4', 'gi')
 WHERE t.id IN (SELECT id FROM targets);

-- 执行后复核：期望「明文残留条数」为 0
-- 直接跑 audit_credential_assessment.sql 即可。
