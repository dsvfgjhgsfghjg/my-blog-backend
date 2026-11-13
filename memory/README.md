# MyBlog Local Memory

This directory stores sanitized session handoffs that should survive context
loss and local restarts.

Rules:

- Never store passwords, private keys, tokens, production `.env` contents, or
  personal data here.
- Store paths to protected secret files, not the secret values.
- Record current production state, verified commands, blockers, and the exact
  next actions.
- Keep facts authoritative and date-stamped.

The local vector index is built with:

```bash
python3 scripts/memory/build_vectors.py
```

It indexes `memory/`, `docs/engineering/`, `docs/app-handoffs/`, and `AGENTS.md`.
It does not index the whole repository, `.git`, backups, generated assets, or
local editor state.
