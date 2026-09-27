-- 只读评估：t_audit_log 里是否残留明文凭据。
--
-- 背景：审计切面此前只判断「形参名是否含 password」，导致登录、刷新令牌等接口的
-- 明文凭据被原样写进了 request_params / extra_data。AuditParamSanitizer 是后来才补的，
-- 所以**历史行**里可能有残留，需要先评估规模再决定怎么处理。
--
-- 本脚本**只读**：全部是 SELECT / CTE，不修改任何数据。
--
-- ⚠️ 执行前必读：
--   1. 不要用 psql 的 \set 来定义下面这段正则 —— 引号会被吞掉，导致匹配不到任何东西、
--      输出全为 0，得出「很安全」的假结论。必须像这里一样内联写。
--   2. 判断依据是 AuditParamSanitizer 的硬掩码 ***MASKED***：只有「键名命中 且 值不等于
--      该掩码 且 值非空」才是真正的明文残留。只按键名统计会把已脱敏的行也算进来，数字虚高。
--   3. 清理是不可逆写操作。真要动手前先 pg_dump 备份。
--
-- 样本验证（本地 PG，7 行已知样本）：
--   命中敏感键总数=6、已脱敏=2、空值=1、明文残留条数=3、涉及审计行数=3 —— 逐项吻合。

-- ============ 查询 1：总体规模 ============
WITH sensitive(key_re) AS (
    VALUES ('(password|passwd|pwd|old_?password|new_?password|confirm_?password|'
         || 'client_?secret|secret|secret_?key|private_?key|credential|credentials|'
         || 'token|access_?token|refresh_?token|id_?token|authorization|cookie|backup_?code)')
),
hits AS (
    SELECT l.id, l.operation_type, l.module, 'request_params' AS col,
           m.matches[1] AS key, m.matches[2] AS val, l.create_time
      FROM t_audit_log l
      CROSS JOIN LATERAL regexp_matches(
             l.request_params,
             '"' || (SELECT key_re FROM sensitive) || '"[[:space:]]*:[[:space:]]*"([^"]*)"',
             'gi') AS m(matches)
     WHERE l.request_params IS NOT NULL
    UNION ALL
    SELECT l.id, l.operation_type, l.module, 'extra_data',
           m.matches[1], m.matches[2], l.create_time
      FROM t_audit_log l
      CROSS JOIN LATERAL regexp_matches(
             l.extra_data,
             '"' || (SELECT key_re FROM sensitive) || '"[[:space:]]*:[[:space:]]*"([^"]*)"',
             'gi') AS m(matches)
     WHERE l.extra_data IS NOT NULL
)
SELECT count(*)                                                      AS 命中敏感键总数,
       count(*) FILTER (WHERE val = '***MASKED***')                  AS 已脱敏,
       count(*) FILTER (WHERE val = '')                              AS 空值,
       count(*) FILTER (WHERE val <> '***MASKED***' AND val <> '')   AS 明文残留条数,
       count(DISTINCT id) FILTER (WHERE val <> '***MASKED***' AND val <> '') AS 涉及审计行数
  FROM hits;

-- ============ 查询 2：按操作类型 / 所在列 / 敏感键分布（判断爆炸半径）============
-- 与查询 1 共用同一段 CTE，单独执行时把上面那段 CTE 一起带上即可。
WITH sensitive(key_re) AS (
    VALUES ('(password|passwd|pwd|old_?password|new_?password|confirm_?password|'
         || 'client_?secret|secret|secret_?key|private_?key|credential|credentials|'
         || 'token|access_?token|refresh_?token|id_?token|authorization|cookie|backup_?code)')
),
hits AS (
    SELECT l.id, l.operation_type, l.module, 'request_params' AS col,
           m.matches[1] AS key, m.matches[2] AS val, l.create_time
      FROM t_audit_log l
      CROSS JOIN LATERAL regexp_matches(
             l.request_params,
             '"' || (SELECT key_re FROM sensitive) || '"[[:space:]]*:[[:space:]]*"([^"]*)"',
             'gi') AS m(matches)
     WHERE l.request_params IS NOT NULL
    UNION ALL
    SELECT l.id, l.operation_type, l.module, 'extra_data',
           m.matches[1], m.matches[2], l.create_time
      FROM t_audit_log l
      CROSS JOIN LATERAL regexp_matches(
             l.extra_data,
             '"' || (SELECT key_re FROM sensitive) || '"[[:space:]]*:[[:space:]]*"([^"]*)"',
             'gi') AS m(matches)
     WHERE l.extra_data IS NOT NULL
)
SELECT operation_type AS 操作类型, col AS 所在列, key AS 敏感键,
       count(*) AS 条数, min(create_time)::date AS 最早, max(create_time)::date AS 最晚
  FROM hits
 WHERE val <> '***MASKED***' AND val <> ''
 GROUP BY 1, 2, 3
 ORDER BY 4 DESC;

-- ============ 拿到数字之后怎么定 ============
--   行数少（比如几十行）→ 只需要收紧 t_audit_log 的读权限即可，不必动数据。
--   行数多              → 分批清理（先 pg_dump！），并轮换 OAuth2 client secret ——
--                         明文一旦落过库，就该当作已泄露处理，清理历史不等于凭据变安全。
