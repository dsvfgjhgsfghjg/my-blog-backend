# Production Security Cutover: 2026-11-13

## Result

The security-hardening backend and frontend are deployed in production.

Production entrypoints:

- Frontend/API: `https://leo-blog.top/`
- Gitea: `https://leo-blog.top/gitea/`
- Git SSH: `ssh://git@leo-blog.top:222/...`

## Credential Rotation

Rotated:

- PostgreSQL administrator password
- PostgreSQL application and migration role passwords
- Redis password
- MinIO root and application secrets
- JWT signing secret
- TOTP encryption key
- MyBlog administrator password
- Gitea runner registration token
- Ubuntu deployment SSH key

The new MyBlog administrator password is stored outside Git at:

```text
~/.local/share/myblog-security-backups/20261113/admin-password.txt
```

Local file mode is `0600`.

The retired deployment private key is archived under:

```text
~/.local/share/myblog-security-backups/20261113/ssh/
```

## Database

- Flyway V39 is installed successfully.
- `t_invite_code` exists.
- The application connects as `myblog_app`, not the PostgreSQL superuser.
- Tables are owned by `myblog_migrator`; the app role has DML and sequence access.
- All OAuth refresh-token authorization records were revoked.
- OAuth clients were disabled.
- Obvious test/pentest accounts were disabled.

## Runtime

- Backend uses the app database role, authenticated Redis, and the restricted
  MinIO application account.
- Existing plaintext TOTP secret was migrated to AES-GCM with the new key.
- Invalid refresh token now returns HTTP 401.
- Public ports:
  - `80`, `443`: frontend
  - `222`: Gitea Git SSH
- Private/no host ports:
  - PostgreSQL
  - Redis
  - MinIO
  - backend `18080`
  - Gitea PostgreSQL
  - Gitea HTTP `3000`

## Verification

- Backend actuator health: UP.
- MyBlog frontend and public posts API: 200.
- Gitea HTTPS/API: 200.
- Unauthorized API request: HTTP 401.
- Invalid refresh token: HTTP 401.
- Credentialed cross-origin request from an unknown origin: rejected, no
  `Access-Control-Allow-Origin`.
- PostgreSQL app-role connections observed.
- Redis authentication: PONG.
- MinIO application user: enabled.
- Database administrator password hash matches the rotated password.
- TOTP secret: encrypted, zero plaintext remaining.
- OAuth refresh authorizations remaining active: zero.
- Non-admin enabled users: zero after disabling obvious test accounts.
- External port scan confirms infrastructure ports are closed.

## Rollback Anchors

The production host retains:

- Pre-cutover database custom dump
- Previous `.env`
- Previous Compose file
- Previous image digests
- Previous Gitea, Runner, and Gitea PostgreSQL containers
- Pre-cutover crontab

These rollback artifacts contain secrets and must be encrypted or destroyed after
the observation window.
