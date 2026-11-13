# Development Workflow

## 1. Start of Task

Before changing anything:

```bash
git rev-parse --show-toplevel
git status --short --branch
git remote -v
```

Then read:

- `AGENTS.md`
- the relevant files under `docs/engineering/`
- the relevant feature/security document
- impacted backend controller, service, DTO, migration, and frontend service/type

Do not assume old documentation matches current code. The code and tests are authoritative.

## 2. Change Classification

Classify every task before implementation:

| Type | Required coordination |
|---|---|
| Backend-only internal fix | Backend tests only |
| Backend API change visible to frontend | Backend + frontend contract update |
| Backend API change visible to App | Backend + App handoff document |
| Auth/crypto/push/WebSocket change | Backend + frontend/App compatibility review |
| Database schema change | Flyway migration + rollback/backfill plan |
| Deployment/security change | Explicit rollout and rollback notes |

## 3. Contract-First Rule

For any client-visible change, define the contract before code:

- HTTP method and path
- authentication and verification requirements
- request fields and validation
- success response and error responses
- timestamp format and timezone
- pagination shape
- compatibility behavior during rolling deployment

Backend success responses use `Result<T>` with `code`, `message`, and `data`.
Backend `PageResult<T>` uses `records`, `total`, `size`, and `current`.

Do not introduce a second response shape for the same concept.

## 4. Repository Rules

### Backend

```bash
cd /home/leo/IdeaProjects/myblog
```

- Keep Flyway migrations under `src/main/resources/db/migration/`.
- Do not add new executable SQL to `sql/` unless it is an operational script.
- Do not edit the ignored `frontend/` directory.
- Do not commit local `.env`, dumps, logs, or generated build output.

### Frontend

```bash
cd /home/leo/WebstormProjects/myblog
```

- Keep Git operations separate from backend.
- Update `src/types/api/` and `src/services/` with backend contract changes.
- Run `npm run build`.
- Do not weaken DOMPurify rules or store long-lived credentials in browser-readable storage.

### App

- Never edit App source.
- Write a handoff document using `docs/engineering/APP_HANDOFF_TEMPLATE.md`.
- Include exact examples and acceptance criteria so the App agent can work independently.

## 5. Security Gate

A change must not merge if it introduces any of the following without explicit review:

- Token/password/TOTP/device secret in logs or response bodies
- Non-secure production cookies
- Wildcard credentialed CORS
- Unreviewed public path or WebSocket origin expansion
- Trust of arbitrary forwarding headers
- Raw HTML rendered without sanitization
- Weak randomness for security values
- New publicly exposed infrastructure port
- Dependency versions with known critical advisories

Security-sensitive changes require focused regression tests. If an exploit is plausible but cannot be tested automatically, document the manual verification performed.

## 6. Test Matrix

### Backend

Start test dependencies:

```bash
podman start myblog-test-pg myblog-test-redis
```

Run:

```bash
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/myblog_test' \
SPRING_DATASOURCE_USERNAME=testuser \
SPRING_DATASOURCE_PASSWORD=testpass \
SPRING_DATA_REDIS_HOST=localhost \
SPRING_DATA_REDIS_PORT=6380 \
MINIO_ACCESS_KEY=testaccesskey \
MINIO_SECRET_KEY=testsecretkey123 \
./gradlew test --rerun-tasks --no-daemon
```

Stop:

```bash
podman stop myblog-test-pg myblog-test-redis
```

### Frontend

```bash
cd /home/leo/WebstormProjects/myblog
npm run build
```

### Cross-Client

When backend and frontend change together:

1. Backend tests pass.
2. Frontend type check/build passes.
3. Affected API request/response examples are updated.
4. App handoff is written if the App is affected.
5. Deployment order and compatibility window are documented.

## 7. Commit and Release Flow

- Use focused commits with `feat`, `fix`, `docs`, `test`, `refactor`, or `chore`.
- Do not mix backend and frontend commits.
- Push to Gitea `origin` first.
- GitHub is a sanitized public mirror, never the source of secrets or private operational files.
- Do not force-push shared branches.
- Do not deploy a security-sensitive change without an explicit rollback path.

## 8. Definition of Done

- Scope and ownership are explicit.
- Security impact is reviewed.
- Backend tests pass, or environment limitations are documented precisely.
- Frontend build passes when frontend changes exist.
- App handoff exists when the App contract changes.
- No secret or sensitive runtime artifact is staged.
- Changed files and remaining risks are reported.
