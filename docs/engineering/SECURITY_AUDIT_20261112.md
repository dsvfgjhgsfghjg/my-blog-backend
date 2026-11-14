# Security Audit Baseline: 2026-11-12

## Scope

Reviewed:

- backend authentication, authorization, Cookie, JWT, TOTP, OAuth2, WebSocket
- CORS, CSRF, public paths, proxy headers, Actuator
- dependency versions with OSV
- Git history and repository exposure
- frontend token storage and request interceptor
- test/build baseline

This is a takeover baseline, not a claim that every endpoint has been penetration-tested.

## Verification Summary

- Backend tests: 229 passed with PostgreSQL and Redis test containers.
- Frontend production build: passed.
- GitHub backend and frontend repositories are publicly accessible.
- The public GitHub backend `main` still contains `sql/myblog_backup.dump`.
- The self-hosted Gitea `origin/main` contains a committed OpenSSH private key at commit `3c0cc90`; the current public GitHub `main` does not.
- OSV reports multiple advisories against the current backend dependency set.

## P0: Fix Before Further Production Changes

### SEC-001: Refresh token stored in browser-readable storage

Location:

- `/home/leo/WebstormProjects/myblog/src/stores/user.ts`
- `/home/leo/WebstormProjects/myblog/src/utils/request.ts`

The frontend stores a 30-day refresh token in `localStorage`. Any XSS can read and exfiltrate it.

Impact:

- The HttpOnly access-token Cookie does not protect the long-lived credential.
- XSS becomes durable account takeover rather than a short-lived session problem.

Fix direction:

- Move refresh token into an HttpOnly/Secure Cookie.
- Keep only non-sensitive session metadata in browser storage.
- Rotate refresh tokens and revoke all authorization records after password reset.

### SEC-002: Production still exposes tokens in JSON and does not require Secure Cookie

Location:

- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`

Current defaults:

```yaml
jwt:
  cookie:
    secure: false
    expose-token-in-body: true
```

The production profile does not override either value.

Impact:

- Tokens can be returned in JSON even though the intended design is HttpOnly Cookie auth.
- Cookies can be sent over plain HTTP if the deployment permits it.

Fix direction:

- Production: `jwt.cookie.secure=true` and `jwt.cookie.expose-token-in-body=false`.
- Fail startup if production enables insecure cookies or body token exposure.
- Update frontend refresh flow to rely on HttpOnly refresh Cookie.

### SEC-003: OpenSSH private key committed in Git history

Location:

- commit `3c0cc90`
- file name begins with `# 或者使用 RSA`
- reachable from self-hosted `origin/main`

The committed file is a valid OpenSSH Ed25519 private key, not a placeholder.

Impact:

- Anyone with access to the self-hosted repository history can use the key.
- Deleting the file in a later commit is insufficient.

Fix direction:

1. Identify where the key was authorized.
2. Remove that public key from servers and Git hosting.
3. Generate replacement deployment credentials.
4. Purge the object from remote history or delete/archive the stale `main` branch.
5. Do not force-push or rewrite history without explicit approval.

### SEC-004: Dependency security baseline is outdated

Current resolved versions include:

- Spring Boot `3.2.5`
- Spring Framework `6.1.6`
- Spring Security `6.2.4`
- Tomcat embed `10.1.20`
- Jackson `2.15.4`
- MinIO Java client `8.5.7`
- jsoup `1.17.2`
- Bouncy Castle `1.78.1`

OSV flags advisories in Spring, Spring Security, Tomcat, Jackson, MinIO, jsoup, Bouncy Castle, Netty, Guava, HttpClient, and PostgreSQL driver components.

Not every advisory is exploitable in this application, but the current framework baseline is no longer acceptable for a security-first service.

Fix direction:

- Upgrade to a currently supported Spring Boot line.
- Pin patched Tomcat, Jackson, MinIO, jsoup, and Bouncy Castle versions.
- Add dependency scanning to CI and fail on new critical advisories.
- Re-run the full test suite and targeted auth/upload/WebSocket tests after the upgrade.

### SEC-005: Public infrastructure exposure must be verified

The generated production compose file publishes:

- PostgreSQL `5432`
- Redis `6379`
- MinIO `9000` and `9001`
- backend `8080`
- frontend `8081`

If firewall rules or a reverse proxy do not block direct access, this is critical. Redis is configured without authentication in the application defaults.

Fix direction:

- Publish only the public reverse proxy port.
- Keep database, Redis, MinIO console/API, and backend ports on the private network.
- Require Redis authentication or a private authenticated network.
- Verify from outside the host with port scans.

### SEC-006: Public repository contains a database dump

Location:

- `sql/myblog_backup.dump`
- public GitHub URL currently returns HTTP 200

Impact:

- The custom PostgreSQL archive defines 16 data-loading sections.
- It may expose schema, accounts, content, or operational metadata depending on the archived rows.

Fix direction:

- Remove the dump from the public repository.
- Replace it with schema-only migrations if initialization is required.
- Inspect the archive with `pg_restore` in an isolated environment before deciding whether data was sensitive.
- If personal data or credentials are present, rotate credentials and assess disclosure obligations.

## P1: Fix in the Next Security Batch

### SEC-101: Credentialed CORS allows every HTTPS origin

Location: `src/main/java/com/leo/myblog/config/SecurityConfig.java`

`allowedOriginPatterns` contains `https://*` with `allowCredentials=true`.

`SameSite=Strict` mitigates ordinary cross-site requests, but does not protect against a compromised or attacker-controlled same-site origin.

Fix direction:

- Use an explicit environment-driven allowlist.
- Reject unknown origins in production.

### SEC-102: CSRF is disabled for Cookie-authenticated endpoints

Location: `src/main/java/com/leo/myblog/config/SecurityConfig.java`

`csrf.disable()` is used because the API also supports bearer tokens. The browser path uses Cookies.

Fix direction:

- Keep CSRF protection for Cookie-authenticated state-changing requests, or add strict Origin/Referer validation.
- Verify all state-changing endpoints are POST/PUT/PATCH/DELETE.

### SEC-103: `client_credentials` tokens are not scope-limited

Location: `src/main/java/com/leo/myblog/service/OAuth2TokenService.java`

M2M tokens carry user ID `-1` and pass the general `authenticated()` rule. There is no endpoint-level scope enforcement.

Impact:

- A configured M2M client can reach authenticated business endpoints that do not verify user existence.

Fix direction:

- Give client tokens a distinct authentication type.
- Enforce scopes per endpoint.
- Reject client principals from user-only operations.

### SEC-104: TOTP and backup-code attack resistance is incomplete

Locations:

- `src/main/java/com/leo/myblog/service/TotpService.java`
- `src/main/java/com/leo/myblog/aspect/VerificationAspect.java`

Issues:

- Backup codes use `Math.random()`.
- Failed TOTP/backup-code attempts are not rate-limited per user/device.
- Invalid TOTP and backup codes are written to logs.
- TOTP secrets are stored in Redis in plaintext.

Fix direction:

- Use `SecureRandom`.
- Rotate backup-code storage to hashed, one-time records.
- Add attempt throttling and lockout.
- Never log the submitted code.
- Encrypt TOTP secrets at rest or use a dedicated secret store.

### SEC-105: Challenge answers and captcha answers are logged

Locations:

- `src/main/java/com/leo/myblog/service/LoginService.java`
- `src/main/java/com/leo/myblog/service/AppAuthService.java`
- `src/main/java/com/leo/myblog/service/SliderCaptchaService.java`

Logs include correct challenge numbers or information from which the answer can be derived.

Fix direction:

- Log only challenge ID, user ID, result, and expiry.
- Keep answer material out of application logs and audit parameters.

### SEC-106: Third-party OAuth state is not a security nonce

Location: `src/main/java/com/leo/myblog/controller/OAuth2Controller.java`

The `state` parameter is populated from caller-provided `redirectUri`; no server-side nonce is generated or validated.

Fix direction:

- Generate a random `state`.
- Store it with a short TTL and bind it to the browser session.
- Validate and consume it in the callback.

### SEC-107: Optional identity is lost on public paths

Locations:

- `src/main/java/com/leo/myblog/config/JwtAuthenticationFilter.java`
- `src/main/java/com/leo/myblog/config/OAuth2AuthenticationFilter.java`
- `src/main/java/com/leo/myblog/config/PublicPaths.java`

Public paths skip both authentication filters. A request with a valid Cookie is still treated as anonymous.

Impact:

- Authors may not see their own non-public posts through `GET /api/posts/{postId}`.
- Public comment lists cannot determine current-user like state.

Fix direction:

- On public paths, attempt authentication when a token exists, but do not reject invalid/missing tokens.
- Preserve `permitAll()` authorization semantics.

### SEC-108: Forwarded client-IP headers are trusted directly

Multiple services read the first `X-Forwarded-For` value without verifying the immediate proxy.

Impact:

- Direct backend access can bypass IP bans and rate limits by spoofing headers.
- Attackers can cause another IP to be banned.

Fix direction:

- Accept forwarding headers only from trusted proxy addresses.
- Configure Spring `server.forward-headers-strategy` and a trusted-proxy boundary.
- Ensure the reverse proxy overwrites client-supplied headers.

### SEC-109: MFA and sensitive endpoints lack uniform attempt throttling

Sensitive operations protected by TOTP or App approval need a per-user attempt/rate limit that is independent of password login limits.

Fix direction:

- Add a shared verification-attempt limiter.
- Record failures without logging the secret value.

### SEC-110: WebSocket origin policy is fully open

Location: `src/main/java/com/leo/myblog/websocket/WebSocketConfig.java`

Both browser and native endpoints accept every origin.

Fix direction:

- Apply an explicit origin allowlist to the browser endpoint.
- Keep a separate native endpoint only if device-secret authentication fully covers the risk.

## P2: Engineering and Defense-in-Depth

- `SliderCaptchaUtils` and several non-security random generators use `java.util.Random`; security-relevant values should use `SecureRandom`.
- Token session IDs use `String.hashCode()` rather than SHA-256.
- The access-token JWT lifetime is 24 hours while the OAuth response advertises one hour.
- `TokenBlacklistService` fails open when Redis is unavailable.
- Registration has no captcha or rate limit.
- File-stream endpoints buffer complete objects in memory.
- WebSocket, public-path, and UA-risk whitelists are separate concepts and need explicit tests to prevent drift.

## Implementation Status: 2026-11-13

Backend branch: `security-hardening-20261112`

Frontend branch: `security-hardening-20261112`

Local verification:

- Backend: 243 tests passed with PostgreSQL and Redis.
- Frontend: production build and 2 Playwright E2E tests passed.
- Frontend production dependencies: `npm audit --omit=dev` reports 0 vulnerabilities.
- Backend dependencies: OSV scan passed for 123 Maven coordinates.
- Backend local and Gitea remote-mirror Gitleaks scans report no leaks.
- Frontend historical Gitleaks scan passes after allowlisting three frozen
  documentation commits and one vendored public reCAPTCHA test-site key.

### Code and Configuration

| Item | Status |
|---|---|
| SEC-001 browser-readable refresh token | Fixed: access and refresh credentials use separate HttpOnly cookies |
| SEC-002 production token exposure | Fixed: production requires Secure cookies and browser responses omit tokens |
| SEC-003 committed SSH private key | Fixed: public repositories rebuilt with sanitized history; old SHA returns 404 |
| SEC-004 vulnerable dependency baseline | Fixed: Spring Boot 3.5.16 plus pinned patched transitive dependencies; OSV gate added |
| SEC-005 public infrastructure ports | Fixed in `deploy/docker-compose.prod.yml`: only the frontend ports are public |
| SEC-006 public database dump | Fixed: public repositories rebuilt, old SHA returns 404, public history scans clean |
| SEC-101 wildcard credentialed CORS | Fixed: explicit origin allowlist |
| SEC-102 Cookie-authenticated CSRF | Fixed: Origin/Referer validation for Cookie-authenticated writes |
| SEC-103 client-credentials scope gap | Fixed: `client_credentials` is disabled |
| SEC-104 TOTP attack resistance | Fixed: encrypted secrets, hashed one-time backup codes, attempt lockout |
| SEC-105 challenge/captcha answer logging | Fixed: answer material is no longer logged |
| SEC-106 third-party OAuth state | Fixed by removing the incomplete third-party OAuth flow |
| SEC-107 optional identity on public paths | Fixed: valid identity is attached when present without rejecting anonymous access |
| SEC-108 spoofable forwarding headers | Fixed: forwarding headers are accepted only from trusted proxy CIDRs |
| SEC-109 MFA throttling | Fixed: shared per-user failure window and lockout |
| SEC-110 WebSocket origin exposure | Fixed: browser SockJS WebSocket removed; only the native App endpoint remains |

### Additional Hardening

- Registration is invite-only. Invite codes are 128-bit, single-use, expire in
  24 hours, can be revoked, and are stored only as SHA-256 hashes.
- Password policy is unified at 10-128 characters and rejects all-numeric
  passwords and passwords containing the username.
- Authentication failures return HTTP 401, authorization failures return 403,
  and Redis blacklist outages fail closed with 503.
- Client IP parsing is centralized through `ClientIpResolver`.
- Production deployment templates use separate PostgreSQL application and
  migration roles, authenticated Redis, and a least-privilege MinIO user.
- Dependency and secret scans are wired into the backend CI workflow.
- The public database dump, root compose symlinks, legacy frontend Dockerfile,
  and backend-to-frontend source coupling were removed.

## Execution Record: 2026-11-13

Completed:

- Backend and frontend full refs were backed up outside their repositories under
  `/home/leo/.local/share/myblog-security-backups/20261113`.
- Backend history was rewritten to remove `sql/myblog_backup.dump`, `config/.env`,
  both `config/secrets_config_*.txt` files, `.runner`, and `# 或者使用 RSA`.
- Frontend history was rewritten to remove `SSH_KEY_SETUP.md`,
  `CONFIG_MANAGEMENT.md`, and `SECURITY_QUICK_REF.md`.
- Local and freshly cloned Gitea mirror scans report no secrets.
- Gitea backend `master` is `649829d`; the security branch is `166924b` before
  this status-document commit.
- Gitea frontend `master` is `1219eeb`; the security branch is `c6cc41f`.
- The App handoff-only commit `1c79923` was pushed to the App repository.
- The removed dump was restored in a one-off isolated PostgreSQL container and
  inspected using aggregate queries only. It contains 4 users, 4 password hashes,
  2 last-login IP addresses, 1 blogger email address, 24 posts, and 23 comments
  with body content. User rows contain no email or phone values.
- The temporary dump copy and inspection clone were securely removed after the
  aggregate assessment.
- Gitea storage cleanup reduced package data from approximately `1.8G` to
  `453M`; old image versions and archive/deleted-branch state were removed.
- Both `master` branches now deny direct and force pushes and block outdated or
  rejected-review merges.
- Seventeen temporary all-scope Gitea tokens were deleted by ID. Only the two CI
  package-push tokens and the host package-pull token remain.

Not completed:

- GitHub Git upload is reset by the current network and no usable GitHub write
  credential is configured. The public mirror still points at the old history,
  so the public exposure is not closed.
- Gitea server-side garbage collection and cache/artifact cleanup require server
  administrator access.
- Production still serves the old CORS behavior: a request with
  `Origin: https://evil.example` returned that origin in
  `Access-Control-Allow-Origin`. The new backend is not deployed.
- Production database, Redis, MinIO, JWT, TOTP, SSH, Runner, and administrator
  credentials have not been rotated.

## Execution Record: 2026-11-14

Completed:

- Force-pushing the sanitized branches removed them from the normal branch
  namespace, but the old backend commit still served the removed dump by SHA.
- The owner deleted and recreated both public GitHub repositories, then pushed
  the sanitized histories into the new repositories.
- GitHub branch inventory contains only:
  - backend `main`: `605d28101a96c30f800ba3ed3d8daf2a1e23d8ab`
  - frontend `main`: `1219eeb5cb9ef7066013f9c5878be13d0ef9c9ab`
- GitHub contents API checks return HTTP 404 for `sql/myblog_backup.dump` at:
  - current backend `main`
  - current backend commit `605d281`
  - old backend commit `39fb233`
- Fresh mirror clones from the rebuilt GitHub repositories were scanned in Git
  mode with Gitleaks:
  - backend: 62 commits, no leaks
  - frontend: 222 commits, no leaks
- Both repositories are public with default branch `main`.
- Temporary deploy keys were deleted through the GitHub API and the stored
  GitHub PAT was removed from the local system keyring.

Residual:

- Repository-level settings such as branch protection, webhooks, Actions
  settings, releases, and secrets were not recreated by deleting the mirrors.
  Reapply any settings that the public mirrors require.

## Remaining External Actions

These items cannot be completed by source changes alone:

1. Backups are complete for repositories, database, deployment configuration,
   and current images.
2. Public mirror remediation is complete through repository replacement. Reapply
   any desired GitHub repository settings that did not survive recreation.
3. Gitea server-side Git GC, archive cleanup, deleted-branch cleanup, old package
   removal, and package cleanup are complete. Continue to expire any external CI
   caches or source archives that are outside the Gitea data directory.
4. Credential rotation and production deployment are complete. See
   `PRODUCTION_CUTOVER_20261113.md`.
5. The disclosure assessment is complete; see
   `docs/engineering/DATA_EXPOSURE_ASSESSMENT_20261113.md`. Do not copy the dump
   back into either repository.
6. Backend migrations and both application images are deployed. Continue with
   authenticated browser/App smoke tests when real user credentials are used.
7. External port verification is complete: PostgreSQL, Redis, MinIO, backend, and
   Gitea HTTP ports are not reachable.
8. Gitleaks was rerun against fresh GitHub mirror clones for every public branch;
   backend and frontend scans report no leaks.

The maintenance procedure and rollback checkpoints are documented in
`docs/engineering/SECURITY_RELEASE_RUNBOOK_20261113.md`.
