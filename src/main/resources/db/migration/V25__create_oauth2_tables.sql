-- ============================================================
-- OAuth 2.0 认证系统表结构
-- ============================================================

-- 1. OAuth2 客户端表（第三方应用注册）
CREATE TABLE IF NOT EXISTS oauth2_clients (
    id              BIGINT PRIMARY KEY,
    client_id       VARCHAR(64)  NOT NULL UNIQUE,
    client_secret   VARCHAR(256) NOT NULL,
    client_name     VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    redirect_uris   TEXT         NOT NULL,  -- JSON 数组 ["http://localhost:3000/callback"]
    grant_types     TEXT         NOT NULL,  -- JSON 数组 ["password","refresh_token","authorization_code","client_credentials"]
    scopes          TEXT         NOT NULL,  -- JSON 数组 ["read","write"]
    logo_url        VARCHAR(512),
    homepage_url    VARCHAR(512),
    is_confidential BOOLEAN     DEFAULT true,
    is_enabled      BOOLEAN     DEFAULT true,
    created_by      BIGINT,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_oauth2_clients_client_id ON oauth2_clients(client_id);

COMMENT ON TABLE  oauth2_clients IS 'OAuth2 客户端注册表';
COMMENT ON COLUMN oauth2_clients.client_id IS '客户端ID（唯一标识）';
COMMENT ON COLUMN oauth2_clients.client_secret IS '客户端密钥（BCrypt加密）';
COMMENT ON COLUMN oauth2_clients.redirect_uris IS '允许的回调地址列表（JSON数组）';
COMMENT ON COLUMN oauth2_clients.grant_types IS '支持的授权类型（JSON数组）';
COMMENT ON COLUMN oauth2_clients.scopes IS '允许的权限范围（JSON数组）';
COMMENT ON COLUMN oauth2_clients.is_confidential IS '是否为机密型客户端（需要client_secret）';
COMMENT ON COLUMN oauth2_clients.is_enabled IS '是否启用';
COMMENT ON COLUMN oauth2_clients.created_by IS '创建者用户ID';

-- 2. OAuth2 授权记录表（授权码、Token 生命周期跟踪）
CREATE TABLE IF NOT EXISTS oauth2_authorizations (
    id                  BIGINT PRIMARY KEY,
    client_id           VARCHAR(64),            -- password grant 可能为空
    user_id             BIGINT,
    authorization_code  VARCHAR(128) UNIQUE,  -- 授权码（authorization_code grant用）
    access_token_hash   VARCHAR(128) UNIQUE,  -- access_token 的 SHA-256 哈希
    refresh_token_hash  VARCHAR(128) UNIQUE,  -- refresh_token 的 SHA-256 哈希
    grant_type          VARCHAR(32)  NOT NULL,
    scope               TEXT,                 -- 实际授予的权限范围（JSON数组）
    redirect_uri        VARCHAR(512),         -- 授权码模式用的回调地址
    auth_time            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    access_token_expires_at  TIMESTAMP,
    refresh_token_expires_at TIMESTAMP,
    is_revoked          BOOLEAN     DEFAULT false,
    revoked_at          TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_oauth2_auth_client_id ON oauth2_authorizations(client_id);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_user_id ON oauth2_authorizations(user_id);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_code ON oauth2_authorizations(authorization_code);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_access_hash ON oauth2_authorizations(access_token_hash);
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_refresh_hash ON oauth2_authorizations(refresh_token_hash);

COMMENT ON TABLE  oauth2_authorizations IS 'OAuth2 授权记录表';
COMMENT ON COLUMN oauth2_authorizations.authorization_code IS '授权码（一次性使用）';
COMMENT ON COLUMN oauth2_authorizations.access_token_hash IS 'Access Token的哈希值（查询用）';
COMMENT ON COLUMN oauth2_authorizations.refresh_token_hash IS 'Refresh Token的哈希值（查询用）';
COMMENT ON COLUMN oauth2_authorizations.grant_type IS '授权类型：password/authorization_code/client_credentials/refresh_token';
COMMENT ON COLUMN oauth2_authorizations.is_revoked IS '是否已撤销';

-- 3. 第三方账户关联表（GitHub/Google 登录）
CREATE TABLE IF NOT EXISTS third_party_accounts (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    provider        VARCHAR(32)  NOT NULL,  -- github, google, wechat 等
    provider_user_id VARCHAR(128) NOT NULL,  -- 第三方平台的用户ID
    provider_username VARCHAR(128),           -- 第三方平台的用户名
    provider_avatar   VARCHAR(512),           -- 第三方头像URL
    provider_email    VARCHAR(256),           -- 第三方邮箱
    access_token      TEXT,                   -- 第三方 access_token（加密存储）
    refresh_token     TEXT,                   -- 第三方 refresh_token（加密存储）
    token_expires_at  TIMESTAMP,              -- 第三方 token 过期时间
    linked_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_user_id),
    UNIQUE(user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_third_party_user_id ON third_party_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_third_party_provider ON third_party_accounts(provider, provider_user_id);

COMMENT ON TABLE  third_party_accounts IS '第三方账户关联表';
COMMENT ON COLUMN third_party_accounts.provider IS '第三方平台名称（github/google/wechat）';
COMMENT ON COLUMN third_party_accounts.provider_user_id IS '第三方平台的用户唯一标识';
