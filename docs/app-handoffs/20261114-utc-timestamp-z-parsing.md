# App Handoff: UTC Timestamp `Z` Parsing

> Date: 2026-11-14
> Backend commit/version: `605d281` plus the uncommitted dashboard timestamp fix
> App impact: required
> Compatibility: coordinated release recommended; JSON remains compatible for
> clients that already parse ISO-8601 instants

## 1. Background

Database values are stored as `timestamp without time zone` with UTC semantics.
The backend previously serialized `LocalDateTime` values without an offset, for
example `2026-11-13T13:36:02`. Clients could interpret that value as local time,
causing an eight-hour error in UTC+8.

The backend now serializes every HTTP JSON `LocalDateTime` as a UTC ISO-8601
instant with a trailing `Z`, for example `2026-11-13T13:36:02Z`. Fractional
seconds are preserved when present.

This handoff also covers a dashboard-specific inconsistency: several dashboard
fields were manually converted to strings and bypassed the global serializer.
Those fields now use `LocalDateTime` too, so the App must not rely on receiving
offset-less strings.

## 2. Affected Endpoints

The rule applies globally to HTTP JSON fields whose backend type is
`LocalDateTime`. The following App endpoints are the primary verification
surface:

| Method | Path | Authentication | Verification |
|---|---|---|---|
| GET | `/api/auth/app/dashboard` | Bearer JWT | none |
| GET | `/api/auth/app/devices` | Bearer JWT | none |
| GET | `/api/auth/app/history` | Bearer JWT | none |
| GET | `/api/users/me/login-info` | Bearer JWT or authenticated Cookie | none |

Other HTTP endpoints with timestamp fields follow the same UTC `Z` rule.

The following values are not ISO timestamps and must not be sent through the
same parser:

- `expiresAt` in challenge responses is Unix time in seconds.
- WebSocket `challenge` messages use Unix seconds for `expiresAt`.

## 3. Contract

### Request

Example:

```http
GET /api/auth/app/dashboard
Authorization: Bearer <access-token>
```

No request body or timestamp input is required.

### Success Response

The response is abbreviated to the timestamp-bearing fields:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "lastLoginAt": "2026-11-13T13:36:02Z",
    "device": {
      "deviceId": "ANDROID_example",
      "boundAt": "2026-11-12T08:15:30.123Z"
    },
    "recentHistory": {
      "records": [
        {
          "challengeId": "00000000000000000000000000000001",
          "status": "approved",
          "requestedAt": "2026-11-13T13:35:55Z",
          "respondedAt": "2026-11-13T13:36:02Z"
        }
      ]
    },
    "lastAuthResult": {
      "status": "approved",
      "requestedAt": "2026-11-13T13:35:55Z"
    }
  }
}
```

### Error Responses

| HTTP status | Body code | Message | App action |
|---|---:|---|---|
| 401 | 401 | Authentication required or invalid token | Clear the invalid session and return to login |
| 403 | 403 | Forbidden | Do not retry with the same account |

## 4. Field Semantics

| Field | Type | Required | Meaning | Compatibility |
|---|---|---|---|---|
| `lastLoginAt` | ISO-8601 string, nullable | No | Last successful login in UTC | May omit fractional seconds |
| `boundAt` | ISO-8601 string, nullable | No | Device binding time in UTC | May omit fractional seconds |
| `requestedAt` | ISO-8601 string | Yes in history records | Challenge creation time in UTC | May omit fractional seconds |
| `respondedAt` | ISO-8601 string, nullable | No | App response time in UTC | `null` while pending |
| `expiresAt` | Unix seconds | Yes for challenges | Challenge expiry, not an ISO string | Do not parse with the `Z` formatter |

Accepted timestamp examples:

```text
2026-11-13T13:36:02Z
2026-11-13T13:36:02.123Z
```

The App should reject or explicitly migrate offset-less timestamp input. If a
legacy backend must be supported during rollout, an offset-less value may be
treated as UTC only through a separate, clearly named legacy path. It must not
be interpreted as device-local time.

## 5. Authentication and Encryption

- App REST calls use `Authorization: Bearer <JWT>`.
- Browser authentication may use HttpOnly Cookies; App behavior is unchanged.
- Timestamp parsing does not alter device binding or encryption.
- WebSocket challenge payloads remain encrypted under the existing protocol.
- WebSocket and pull-based challenge `expiresAt` values remain Unix seconds.
- The App must never receive or persist `correctNumber`.

## 6. Example Flow

1. The App opens the Authenticator dashboard.
2. The App calls `GET /api/auth/app/dashboard` with its Bearer JWT.
3. The backend returns `lastLoginAt: "2026-11-13T13:36:02Z"`.
4. The App parses the value as an `Instant` and displays
   `2026-11-13 21:36:02` on a device using UTC+8.
5. A pending challenge returns `expiresAt: "1763040962"` as Unix seconds and is
   handled by the existing challenge countdown path, not the ISO parser.

## 7. App Implementation Checklist

- [ ] Update timestamp models to preserve `Instant` or an equivalent UTC type.
- [ ] Accept a trailing `Z` with and without fractional seconds.
- [ ] Convert to the device time zone only when formatting for display.
- [ ] Use UTC for comparisons, ordering, and expiry checks.
- [ ] Remove any `LocalDateTime.parse(raw)` path that ignores the trailing `Z`.
- [ ] Keep challenge `expiresAt` on the Unix-seconds parser.
- [ ] Handle nullable timestamp fields without throwing.
- [ ] Add tests for UTC+8 rendering and both timestamp examples.
- [ ] Verify against the production backend.

## 8. Acceptance Criteria

- [ ] `2026-11-13T13:36:02Z` parses without error.
- [ ] `2026-11-13T13:36:02.123Z` parses without error and preserves ordering.
- [ ] A UTC+8 device displays `2026-11-13 21:36:02` for the first example.
- [ ] Dashboard, device list, and auth history display no eight-hour offset.
- [ ] WebSocket and pending-challenge `expiresAt` continue to use Unix seconds.
- [ ] Missing optional timestamps render as empty or a defined placeholder.

## 9. Rollout and Rollback

- Backend deployment order: backend timestamp fix first.
- App release order: publish before relying on the new dashboard format.
- Old App compatibility: Apps using a correct ISO-8601 parser remain compatible;
  Apps that strip or reject `Z` require the App update.
- Rollback behavior: do not restore offset-less dashboard timestamps. If an App
  rollback is unavoidable, add a legacy UTC parser that still does not apply the
  device-local time zone.

## 10. Open Questions

- None.
