-- 创建审计日志表
CREATE TABLE IF NOT EXISTS t_audit_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    operation_type VARCHAR(50) NOT NULL,
    module VARCHAR(50) NOT NULL,
    description TEXT,
    ip_address VARCHAR(100),
    user_agent TEXT,
    request_url VARCHAR(500),
    request_method VARCHAR(10),
    request_params TEXT,
    response_status INTEGER,
    response_message VARCHAR(500),
    execution_time BIGINT,
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    extra_data TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON t_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_username ON t_audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_log_operation_type ON t_audit_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_module ON t_audit_log(module);
CREATE INDEX IF NOT EXISTS idx_audit_log_success ON t_audit_log(success);
CREATE INDEX IF NOT EXISTS idx_audit_log_create_time ON t_audit_log(create_time);
CREATE INDEX IF NOT EXISTS idx_audit_log_module_time ON t_audit_log(module, create_time);

-- 添加注释
COMMENT ON TABLE t_audit_log IS '审计日志表，记录所有敏感操作和系统事件';
COMMENT ON COLUMN t_audit_log.id IS '主键ID';
COMMENT ON COLUMN t_audit_log.user_id IS '操作用户ID';
COMMENT ON COLUMN t_audit_log.username IS '操作用户名';
COMMENT ON COLUMN t_audit_log.operation_type IS '操作类型（LOGIN, LOGOUT, CREATE, UPDATE, DELETE, RESET_PASSWORD, CHANGE_ROLE等）';
COMMENT ON COLUMN t_audit_log.module IS '操作模块（USER, POST, COMMENT, CATEGORY, TAG, SYSTEM等）';
COMMENT ON COLUMN t_audit_log.description IS '操作描述';
COMMENT ON COLUMN t_audit_log.ip_address IS '请求IP地址';
COMMENT ON COLUMN t_audit_log.user_agent IS '请求User-Agent';
COMMENT ON COLUMN t_audit_log.request_url IS '请求URL';
COMMENT ON COLUMN t_audit_log.request_method IS '请求方法（GET, POST, PUT, DELETE等）';
COMMENT ON COLUMN t_audit_log.request_params IS '请求参数（JSON格式，敏感信息已脱敏）';
COMMENT ON COLUMN t_audit_log.response_status IS '响应状态码';
COMMENT ON COLUMN t_audit_log.response_message IS '响应消息';
COMMENT ON COLUMN t_audit_log.execution_time IS '执行时长（毫秒）';
COMMENT ON COLUMN t_audit_log.success IS '是否成功';
COMMENT ON COLUMN t_audit_log.error_message IS '错误信息（失败时记录）';
COMMENT ON COLUMN t_audit_log.extra_data IS '额外数据（JSON格式，存储业务相关数据）';
COMMENT ON COLUMN t_audit_log.create_time IS '创建时间';
