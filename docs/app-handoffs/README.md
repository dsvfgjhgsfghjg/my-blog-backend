# App Handoff Documents

This directory contains backend-to-App change requests. The backend agent writes the document; the App agent implements from it.

Rules:

- One document per App-visible change.
- Use `docs/engineering/APP_HANDOFF_TEMPLATE.md`.
- Include exact endpoints, fields, examples, compatibility, and acceptance criteria.
- State explicitly whether old App versions remain compatible.
- Do not include production secrets, private keys, real tokens, or personal data.
- Link the backend commit or version once the implementation is complete.
- The App agent reports completion against the checklist and acceptance criteria.

Naming:

```text
YYYYMMDD-short-kebab-case-title.md
```

The App workspace is external and must not be edited from the backend repository.
