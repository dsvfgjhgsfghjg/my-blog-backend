-- 为用户表添加登录相关字段
ALTER TABLE t_user ADD COLUMN last_login_ip VARCHAR(45);
ALTER TABLE t_user ADD COLUMN last_login_time TIMESTAMP;
ALTER TABLE t_user ADD COLUMN login_count BIGINT DEFAULT 0;

COMMENT ON COLUMN t_user.last_login_ip IS '上次登录IP地址';
COMMENT ON COLUMN t_user.last_login_time IS '上次登录时间';
COMMENT ON COLUMN t_user.login_count IS '登录次数';
