#!/usr/bin/env bash
set -euo pipefail

service_name="${GITHUB_SECRET_SERVICE:-myblog-github}"
account_name="${GITHUB_SECRET_ACCOUNT:-automation}"

case "${1:-}" in
  *Username*)
    printf '%s\n' "${GITHUB_USERNAME:-x-access-token}"
    ;;
  *Password*)
    if ! command -v secret-tool >/dev/null 2>&1; then
      echo "secret-tool is required; install libsecret-tools first" >&2
      exit 1
    fi

    token=$(secret-tool lookup service "$service_name" account "$account_name" 2>/dev/null || true)
    if [ -z "$token" ]; then
      echo "No GitHub token stored in the system keyring" >&2
      exit 1
    fi
    printf '%s\n' "$token"
    unset token
    ;;
  *)
    exit 1
    ;;
esac
