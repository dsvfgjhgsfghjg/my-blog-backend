# MyBlog Engineering Rules

This file applies to the whole repository. Read it before changing code, deployment files, or Git state.

## Repository Boundaries

- Backend canonical workspace: `/home/leo/IdeaProjects/myblog`
- Frontend canonical workspace: `/home/leo/WebstormProjects/myblog`
- App workspace is external and must not be edited by backend agents.
- `/home/leo/IdeaProjects/myblog/frontend` is a legacy local duplicate. It is ignored by the backend repository and must not be used as the frontend source of truth.
- Never create a monorepo by adding the frontend or app into the backend Git repository.

Before any Git command:

```bash
git rev-parse --show-toplevel
git status --short --branch
```

Do not use `git add .`. Stage only files intentionally changed in the current task.

## Remotes

- Gitea `origin` is the normal integration remote for both backend and frontend.
- GitHub is a public mirror and must only receive sanitized history/content.
- Do not push secrets, dumps, private keys, production `.env` files, or internal credentials to any remote.
- Do not rewrite, delete, or force-push remote history without explicit user approval.

## Ownership

### Backend

Own:

- Spring Boot API, security configuration, persistence, Redis, MinIO, WebSocket, Flyway migrations
- API contracts and compatibility
- deployment and operational documentation
- backend tests and security regression tests

### Frontend

Own only when the task explicitly requires frontend work:

- Vue 3 + TypeScript + Vite changes in `/home/leo/WebstormProjects/myblog`
- Keep frontend Git operations in the frontend repository
- Keep backend and frontend commits separate
- Verify contract changes against backend DTOs and services

### App

Never edit the App repository. When an API, authentication flow, encryption format, push challenge, timestamp, or response field affects the App:

1. Create a handoff document under `docs/app-handoffs/`.
2. Use `docs/engineering/APP_HANDOFF_TEMPLATE.md`.
3. Include compatibility impact, exact request/response examples, acceptance criteria, and rollback notes.
4. The App agent implements from that document.

## Required Workflow

1. Read `docs/engineering/README.md` and the relevant feature/security document.
2. Inspect current code and Git state before making assumptions.
3. For cross-client changes, define the API contract before implementation.
4. Keep changes scoped to the owning repository and module.
5. Add or update tests proportional to risk.
6. Run backend tests and, when frontend changes are involved, the frontend build.
7. Update the handoff document when the App contract changes.
8. Report changed files, verification results, and remaining risks.

## Security Invariants

- Never log passwords, JWTs, refresh tokens, device secrets, TOTP codes, backup codes, challenge answers, or private keys.
- Do not expose access or refresh tokens in JSON when HttpOnly Cookie authentication is available.
- Production cookies must use `Secure`, `HttpOnly`, and an intentional `SameSite` policy.
- Do not trust `X-Forwarded-For` or `X-Real-IP` unless the immediate proxy is trusted and strips client-supplied values.
- Do not broaden CORS, WebSocket origins, public paths, or Actuator exposure without a documented reason.
- Do not add CSRF exemptions for cookie-authenticated state-changing endpoints without an equivalent origin/CSRF control.
- Database, Redis, MinIO, and backend management ports must not be publicly reachable.
- Never commit `.env`, dumps, backups, credentials, private keys, or generated runtime data.
- Treat Markdown with embedded HTML as untrusted. Every rendering client must sanitize before insertion into the DOM/WebView.
- Prefer `SecureRandom` for security tokens, challenges, codes, captcha answers, and cryptographic material.

## Testing Baseline

Backend full tests require PostgreSQL and Redis:

```bash
podman start myblog-test-pg myblog-test-redis

SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/myblog_test' \
SPRING_DATASOURCE_USERNAME=testuser \
SPRING_DATASOURCE_PASSWORD=testpass \
SPRING_DATA_REDIS_HOST=localhost \
SPRING_DATA_REDIS_PORT=6380 \
MINIO_ACCESS_KEY=testaccesskey \
MINIO_SECRET_KEY=testsecretkey123 \
./gradlew test --rerun-tasks --no-daemon

podman stop myblog-test-pg myblog-test-redis
```

Frontend:

```bash
cd /home/leo/WebstormProjects/myblog
npm run build
```

## Local Code Search and Memory

Start with IDE indexing and `rg` for exact code/configuration lookup.

For cross-session context, maintain sanitized handoffs in `memory/`. The local
vector index is stored outside Git at:

```text
~/.local/share/myblog-memory/knowledge.sqlite3
```

It indexes only `memory/`, `docs/engineering/`, `docs/app-handoffs/`, and
`AGENTS.md`. It must never index secrets, `.env`, backups, `.claude`, dumps,
logs, generated assets, or the whole repository.

Rebuild and search:

```bash
python3 scripts/memory/build_vectors.py
python3 scripts/memory/search_vectors.py "next production security action"
```
