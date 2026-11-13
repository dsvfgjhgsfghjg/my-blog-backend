# Engineering Takeover Baseline

> Baseline date: 2026-11-12
> Last verification: 2026-11-13
> Backend: Spring Boot 3.5.16 / Java 21
> Frontend: Vue 3 / TypeScript / Vite 8
> App: external client, coordinated through handoff documents

## 1. Repository Map

| Area | Canonical path | Git remote | Ownership |
|---|---|---|---|
| Backend | `/home/leo/IdeaProjects/myblog` | Gitea `origin`, GitHub public mirror | Backend agent |
| Frontend | `/home/leo/WebstormProjects/myblog` | Gitea `origin`, GitHub public mirror | Frontend work only |
| App | External App workspace | External | App agent only |

Rules:

- Backend and frontend are separate Git repositories.
- `/home/leo/IdeaProjects/myblog/frontend` is a legacy ignored clone. Do not edit it.
- App source code is never modified from this repository.
- App-impacting changes must produce a document under `docs/app-handoffs/`.

## 2. Backend Shape

Current backend scale:

- About 32.6k Java source lines, 5k test lines
- 28 controllers, 49 services, 27 mappers, 28 entities, 74 DTOs
- Authentication: JWT access token, refresh token, HttpOnly Cookie, TOTP, App approval challenge
- Infrastructure: PostgreSQL, Redis, MinIO
- Persistence: MyBatis-Plus plus Flyway migrations
- Real-time channel: WebSocket for App authentication and admin dashboard

Primary package areas:

| Package | Responsibility |
|---|---|
| `controller` | HTTP endpoints and request binding |
| `service` | Business logic, authentication, storage, rate limits |
| `config` | Security, JWT filters, CORS, Redis, MinIO, Jackson |
| `aspect` | Admin checks, second-factor verification, audit logging |
| `filter` | Request ID and User-Agent security |
| `websocket` | App device binding, challenge push, encrypted messages |
| `mapper` / `entity` | Database access and persistence models |
| `dto` | API request/response contracts |

## 3. Current Baseline Verification

Backend full test suite:

```text
243 tests completed, 0 failed
```

The suite requires local PostgreSQL and Redis. The existing test containers are:

- `myblog-test-pg`: `localhost:5433`
- `myblog-test-redis`: `localhost:6380`

Frontend:

```text
vue-tsc -b: passed
vite build: passed
Playwright E2E: 2 passed
npm audit --omit=dev: 0 vulnerabilities
```

Run `npm run build` and `npm run test:e2e` for frontend changes.

## 4. Division of Work

### Backend Agent

- API and contract evolution
- Authentication and authorization
- Database migrations and data integrity
- Redis/MinIO/WebSocket integration
- Security fixes and audit evidence
- Deployment compatibility and rollback notes

### Frontend Scope

- Consume backend contracts without weakening authentication
- Keep frontend commits in the frontend repository
- Run `npm run build` for every frontend change
- Coordinate any response-shape change with backend tests and API docs

### App Agent

- Receives written handoff documents only
- Implements App-side compatibility independently
- Reports completion against the acceptance criteria in the document

## 5. Workflow Documents

- Development workflow: `docs/engineering/DEVELOPMENT_WORKFLOW.md`
- App handoff template: `docs/engineering/APP_HANDOFF_TEMPLATE.md`
- Security audit: `docs/engineering/SECURITY_AUDIT_20261112.md`
- Security release runbook: `docs/engineering/SECURITY_RELEASE_RUNBOOK_20261113.md`
- Deployment follow-ups: `docs/engineering/DEPLOYMENT_FOLLOWUPS.md`

## 6. Local Search and Vector Store Decision

A local vector database is not enabled yet.

Reasons:

- The backend is still small enough for IDE semantic indexing plus `rg`.
- Existing documentation is large but inconsistent; indexing stale documents before curation would amplify wrong conclusions.
- A vector store adds another local service and may accidentally index secrets, dumps, logs, or generated artifacts.

Use this order:

1. IDE symbol/navigation and inspections
2. `rg` for exact code and configuration search
3. Curated documents under `docs/engineering/`
4. Feature/security documents only after checking their date and code references

Revisit the decision when either condition becomes true:

- Cross-module questions repeatedly require broad exploratory searches.
- The curated documentation exceeds what keyword search can navigate reliably.

If enabled later, the index must be local-only, gitignored, rebuildable, and explicitly exclude credentials, database dumps, logs, frontend build output, and generated assets.
