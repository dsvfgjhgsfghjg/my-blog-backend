# Data Exposure Assessment: Removed PostgreSQL Dump

## Status

- Assessment completed: 2026-11-14
- Event date: before the 2026-11-13 security cutover
- Public GitHub remediation: completed by deleting and recreating both mirrors
- Public GitHub branches: sanitized histories published and scanned
- Assessment owner: MyBlog project owner

This is an incident-risk record, not a jurisdiction-specific legal opinion.
Legal or privacy review remains necessary if the deployment becomes subject to
additional regulatory obligations.

## Evidence Scope

The removed `sql/myblog_backup.dump` was inspected only in an isolated
PostgreSQL container using aggregate queries. The raw dump was not restored to
either repository and no row-level personal data was copied into engineering
documentation.

Aggregate findings:

| Category | Count |
|---|---:|
| User rows | 4 |
| Password hashes | 4 |
| Last-login IP addresses | 2 |
| Blogger email addresses | 1 |
| Posts with body content | 24 |
| Comments with body content | 23 |

The aggregate review did not identify plaintext passwords, private keys, live
access tokens, refresh tokens, TOTP secrets, or device secrets in the recorded
inventory.

## Risk Assessment

### Confidentiality

The dump contained personal data and user-generated content. The affected
population is small. The original public repository was replaced, so the old
commit and dump no longer resolve.

Residual risk: low from the public mirror after remediation. The assessed data
still existed publicly before remediation and remains part of the incident
record.

### Account Takeover

Password hashes were not recorded as plaintext. The administrator password and
all production credentials were rotated on 2026-11-13. Refresh authorizations
were revoked, OAuth clients were disabled, and non-admin test accounts were
disabled.

Residual risk: low, assuming the original passwords cannot be recovered from
the hashes and no exposed credential was reused elsewhere.

### Integrity and Abuse

The exposed posts and comments could be copied or misattributed, but the
assessment found no evidence of repository write access from the dump itself.

Residual risk: low to moderate depending on the sensitivity of the body content.

### Notification Exposure

Only one contactable email address was identified, and it belongs to the blog
owner. The aggregate user rows contained no email or phone values, so broad
direct notification is not currently feasible from the available evidence.

## Decision

1. No broad public notice is recommended based only on the aggregate evidence.
2. The project owner must retain this assessment and the GitHub remediation
   evidence.
3. If a contactable affected user is later identified, notify that user directly
   with the data categories involved and the completed credential rotations.
4. Reassess if new evidence shows plaintext credentials, a larger affected
   population, or access to additional private data.
5. The public repositories were recreated with sanitized history. The old commit
   SHA now returns HTTP 404 and fresh public-history scans pass Gitleaks.

## Required Follow-up

- [x] Push sanitized backend and frontend histories to GitHub with
  `--force-with-lease`.
- [x] Confirm the old dump URL returns 404.
- [x] Scan every public GitHub branch in Git mode with Gitleaks.
- [x] Record the public branch SHAs and scan evidence.
- [ ] Re-run this assessment if the evidence inventory changes.

Verified public SHAs after repository reconstruction and the MR #5 mirror sync:

```text
backend main    0e5c0792a776c48ba4236be087105ab306c1becb
frontend main   1219eeb5cb9ef7066013f9c5878be13d0ef9c9ab
```

The old backend SHA `39fb23317f11678c9d6205668675a10a44fc4d51` returns HTTP 404
for `sql/myblog_backup.dump` through the GitHub contents API.

## Evidence References

- `docs/engineering/SECURITY_AUDIT_20261112.md`
- `docs/engineering/PRODUCTION_CUTOVER_20261113.md`
- `memory/2026-11-13-production-security-handoff.md`
