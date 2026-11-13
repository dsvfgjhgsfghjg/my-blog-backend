# App Handoff: <Feature or Change Name>

> Date:
> Backend commit/version:
> App impact: required / optional / none
> Compatibility: backward compatible / breaking / coordinated release

## 1. Background

Describe the user flow and why the App must change.

## 2. Affected Endpoints

| Method | Path | Authentication | Verification |
|---|---|---|---|
|  |  |  |  |

## 3. Contract

### Request

```json
{}
```

### Success Response

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### Error Responses

| HTTP status | Body code | Message | App action |
|---|---:|---|---|
|  |  |  |  |

## 4. Field Semantics

| Field | Type | Required | Meaning | Compatibility |
|---|---|---|---|---|
|  |  |  |  |  |

## 5. Authentication and Encryption

Document:

- Cookie/token usage
- Device binding requirements
- WebSocket message names
- DH/AES/SM4 requirements
- Challenge timeout and one-time consumption behavior
- What must never be returned to the App

## 6. Example Flow

1. App calls:
2. Backend responds:
3. App displays:
4. App submits:
5. Backend verifies:

## 7. App Implementation Checklist

- [ ] Update API models
- [ ] Update request/response handling
- [ ] Update storage and migration behavior
- [ ] Update error handling
- [ ] Update UI flow
- [ ] Add or update App tests
- [ ] Verify against a real backend environment

## 8. Acceptance Criteria

- [ ]
- [ ]
- [ ]

## 9. Rollout and Rollback

- Backend deployment order:
- App release order:
- Old App compatibility:
- Rollback behavior:

## 10. Open Questions

- None
