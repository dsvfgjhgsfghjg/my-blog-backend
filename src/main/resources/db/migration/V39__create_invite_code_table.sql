CREATE TABLE IF NOT EXISTS t_invite_code (
    id BIGINT PRIMARY KEY,
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    used_by BIGINT,
    revoked_at TIMESTAMP,
    CONSTRAINT fk_invite_created_by FOREIGN KEY (created_by) REFERENCES t_user(id),
    CONSTRAINT fk_invite_used_by FOREIGN KEY (used_by) REFERENCES t_user(id)
);

CREATE INDEX IF NOT EXISTS idx_invite_code_expires_at ON t_invite_code(expires_at);
CREATE INDEX IF NOT EXISTS idx_invite_code_created_by ON t_invite_code(created_by);
CREATE INDEX IF NOT EXISTS idx_invite_code_used_by ON t_invite_code(used_by);

COMMENT ON TABLE t_invite_code IS '一次性注册邀请码（仅保存哈希）';
COMMENT ON COLUMN t_invite_code.code_hash IS '邀请码 SHA-256 哈希';
COMMENT ON COLUMN t_invite_code.revoked_at IS '撤销时间；非空表示已撤销';
