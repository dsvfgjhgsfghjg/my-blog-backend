#!/usr/bin/env bash
set -euo pipefail

if ! command -v secret-tool >/dev/null 2>&1; then
  echo "secret-tool is required; install libsecret-tools first" >&2
  exit 1
fi

if ! { exec 3<>/dev/tty; } 2>/dev/null; then
  echo "Run this command from an interactive local terminal" >&2
  exit 1
fi

service_name="${GITHUB_SECRET_SERVICE:-myblog-github}"
account_name="${GITHUB_SECRET_ACCOUNT:-automation}"

printf 'GitHub access token (input hidden): ' >&3
IFS= read -r -s token <&3
printf '\n' >&3
exec 3>&-

if [ "${#token}" -lt 20 ]; then
  unset token
  echo "Token is empty or too short; nothing was stored" >&2
  exit 1
fi

if ! printf '%s' "$token" | secret-tool store \
    --label="MyBlog GitHub mirror token" \
    service "$service_name" account "$account_name"; then
  unset token
  echo "Failed to store token in the system keyring" >&2
  exit 1
fi
unset token

if ! secret-tool lookup service "$service_name" account "$account_name" >/dev/null; then
  echo "Token verification failed after storage" >&2
  exit 1
fi

echo "GitHub token stored in the system keyring"
echo "service=$service_name account=$account_name"
