# MyBlog Production Security Handoff

> Date: 2026-11-13
> Status: production deployed and healthy
> Resume: next working day

## Production

- Frontend/API: `https://leo-blog.top/`
- Gitea: `https://leo-blog.top/gitea/`
- Gitea Git SSH: `ssh://git@leo-blog.top:222/...`
- Backend image tag: `07f89ec`
- Frontend image tag: `d78cb70`
- Flyway version: `39`
- Backend health: UP
- Automatic image pull cron is restored.
- Image publishing remains manual through `workflow_dispatch`; merging an MR does
  not publish images.

## Security State

- Rotated JWT, TOTP, PostgreSQL admin/app/migration, Redis, MinIO root/app,
  MyBlog administrator, Gitea runner registration, and Ubuntu deploy SSH
  credentials.
- Backend uses `myblog_app`; tables are owned by `myblog_migrator`.
- All refresh authorizations were revoked and OAuth clients disabled.
- Non-admin test/pentest accounts are disabled; only `admin` is enabled.
- Plaintext TOTP secret was migrated to AES-GCM.
- Invalid refresh token returns HTTP 401.
- Unknown CORS origins are rejected.
- Public ports: `80`, `443`, `222`.
- Closed ports: `3000`, `5432`, `5433`, `6379`, `9000`, `9001`, `18080`.

## Protected Local Files

- MyBlog admin password:
  `~/.local/share/myblog-security-backups/20261113/admin-password.txt`
- Gitea account environment:
  `~/.config/myblog/gitea.env`
- Retired deployment key:
  `~/.local/share/myblog-security-backups/20261113/ssh/`
- Pre-cutover history bundles and old SSH key:
  `~/.local/share/myblog-security-backups/20261113/`

These files are outside Git and mode `0600`.

## Gitea

- Both `master` branches are protected against direct push, force push, outdated
  branches, and rejected-review merges.
- Runner `myblog-native-runner` is online through HTTPS.
- Temporary tokens were removed; only two CI package-push tokens and one host
  package-pull token remain.
- Git HTTPS helpers:
  `scripts/security/gitea-git.sh`
  `scripts/security/gitea-api.sh`
- Gitea package storage was cleaned and currently retains current, previous, and
  empty latest tags as appropriate.

## Backups and Rollback

- Production backup directory on host:
  `/home/ubuntu/myblog-security-backups/20261113211239`
- Previous Gitea, Runner, and Gitea PostgreSQL containers remain stopped as
  rollback anchors.
- Previous MyBlog backend/frontend image tags remain in the Gitea registry.
- Do not delete rollback artifacts until the observation window closes.

## Pending Work

1. Resume GitHub sanitized-history cleanup.
   - GitHub backend `main` is still `39fb233`.
   - GitHub frontend `master` is still `6da1a1a`.
   - The old `sql/myblog_backup.dump` is still publicly present.
   - A GitHub write credential is still required.
2. Complete the data-exposure notification assessment.
   - Removed dump contained 4 users, 4 password hashes, 2 last-login IPs,
     1 blogger email, 24 posts, and 23 comments with body content.
3. After a stable observation window:
   - verify production again;
   - remove stopped rollback containers;
   - encrypt or destroy pre-cutover secret bundles.
4. App handoff remains for trailing-`Z` timestamp parsing. Do not edit App source
   from the backend repository.

## Hard Constraints

- Frontend source of truth:
  `/home/leo/WebstormProjects/myblog`
- Backend source of truth:
  `/home/leo/IdeaProjects/myblog`
- Do not edit the App repository.
- Do not commit secrets, dumps, private keys, `.env`, or generated vector data.
- Do not push unsigned/sanitized production history to GitHub without a fresh
  Gitleaks scan.
