# Security Release Runbook: 2026-11-13

This runbook covers the destructive external actions that remain after the
security-hardening branches pass local verification.

Do not place real credentials, private keys, database dumps, or production
backups inside either Git repository.

## 1. Preconditions

- Announce a maintenance window and freeze deployments.
- Keep a tested rollback image for the backend, migrations, and frontend.
- Confirm the current database backup can be restored in an isolated environment.
- Confirm access to Gitea, the public GitHub mirror, the server, and the secret
  manager.
- Record the commit SHA currently deployed for backend and frontend.

Run before changing history:

```bash
cd /home/leo/IdeaProjects/myblog
git status --short --branch
git rev-parse HEAD
git bundle create /secure-backups/myblog-backend-before-rewrite.bundle --all

cd /home/leo/WebstormProjects/myblog
git status --short --branch
git rev-parse HEAD
git bundle create /secure-backups/myblog-frontend-before-rewrite.bundle --all
```

Back up the database with `pg_dump` in custom format, the deployment `.env`,
TLS material, reverse-proxy configuration, and current container image tags.

## 2. Credential Rotation Order

Rotate and revoke credentials before the sanitized history is deployed:

1. Revoke the exposed Git Runner token and issue a replacement.
2. Remove the exposed SSH public keys from servers and Git hosting, then issue
   replacement deployment keys.
3. Rotate PostgreSQL administrator, application, and migration passwords.
4. Rotate Redis and MinIO root/application credentials.
5. Rotate JWT signing and TOTP encryption keys. This intentionally invalidates
   existing sessions and pending challenge material.
6. Reset administrator passwords through the controlled admin flow.
7. Review and revoke any personal access tokens, webhooks, CI secrets, and deploy
   keys associated with the repositories.

After rotating JWT keys, revoke all active sessions and refresh tokens.

## 3. Rewrite Backend History

Install `git-filter-repo` from the operating-system package or a pinned release.
Verify its version before use.

```bash
cd /home/leo/IdeaProjects/myblog
git filter-repo --force \
  --invert-paths \
  --path sql/myblog_backup.dump \
  --path config/.env \
  --path-glob 'config/secrets_config_*.txt' \
  --path .runner \
  --path '# 或者使用 RSA'
```

Do not allow-list private keys, database dumps, `.env` files, Runner tokens, or
`secrets_config_*` files.

Run the secret scan:

```bash
podman run --rm \
  -v /home/leo/IdeaProjects/myblog:/repo:ro,Z \
  docker.io/zricethezav/gitleaks:latest git /repo \
  --config /repo/.gitleaks.toml --redact --verbose
```

The scan must report no findings.

## 4. Rewrite Frontend History

The historical frontend findings were placeholders or a public reCAPTCHA test
site key, but the obsolete security documents are removed from the sanitized
history to keep the public mirror clean.

```bash
cd /home/leo/WebstormProjects/myblog
git filter-repo --force \
  --invert-paths \
  --path SSH_KEY_SETUP.md \
  --path CONFIG_MANAGEMENT.md \
  --path SECURITY_QUICK_REF.md
```

Run the same read-only Gitleaks command against the frontend repository. The scan
must report no findings.

## 5. Publish Sanitized History

Push only after both local scans, backend tests, frontend build, and E2E tests pass.

Use `--force-with-lease` for every rewritten branch. Push Gitea first, then the
sanitized GitHub mirror. Do not use an unconditional force push.

Delete stale remote branches that still expose old objects, including the old
`main` branch when it has been intentionally retired. In Gitea and GitHub:

- remove stale forks or mirrors;
- expire Actions/CI caches and artifacts containing old checkouts;
- remove release assets or source archives generated from unsanitized commits;
- invalidate deployment caches that retain repository archives.

On the Git server, run garbage collection after every reachable reference has
been removed:

```bash
git gc --prune=now
git reflog expire --expire=now --all
git gc --prune=now
```

Garbage collection is only effective after all old references and server-side
caches are gone.

## 6. Deploy Order

1. Restore the verified database backup if the migration needs a rehearsal.
2. Apply Flyway migrations through the one-shot migration image.
3. Deploy the backend image with rotated secrets and production profile.
4. Verify `/actuator/health`, login, refresh, logout, invite-only registration,
   TOTP, App native WebSocket, and public content access.
5. Deploy the frontend image.
6. Verify browser login uses HttpOnly cookies and no token appears in response
   bodies or browser-readable storage.
7. Verify PostgreSQL, Redis, MinIO, and backend ports are unreachable externally.

Required smoke checks:

- A normal user can log in, refresh, and log out.
- A disabled user and an invalid token receive 401.
- A non-admin receives 403 for admin-only operations.
- Redis unavailability returns 503 rather than bypassing the blacklist.
- A used, expired, or revoked invite code cannot register another account.
- TOTP failures lock after the configured threshold and backup codes are one-time.
- The App native WebSocket path remains available; the browser SockJS path is not.
- Public draft access still allows the author while anonymous users cannot see it.

## 7. Rollback

If deployment verification fails:

1. Stop promotion and keep the database backup.
2. Redeploy the prior backend and frontend images.
3. Restore the database only when a migration is not backward compatible and
   the rollback has been explicitly approved.
4. Keep rotated credentials; do not restore credentials exposed in old history.
5. Restore repository history from the encrypted bundle only as a last resort,
   then reapply the removal before any public push.

If a secret was exposed during the incident window, rotate it again rather than
assuming the history rewrite removed the risk.

## 8. Completion Evidence

Record:

- sanitized backend and frontend branch SHAs;
- Gitleaks output for Gitea and GitHub branches;
- database backup checksum and restore test result;
- migration version before and after deployment;
- image digests deployed;
- smoke-test results;
- credential rotation time and owner, without recording credential values;
- external port-scan result;
- decision and evidence for the removed database dump.
