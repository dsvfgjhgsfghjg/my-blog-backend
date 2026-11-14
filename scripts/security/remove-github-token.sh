#!/usr/bin/env bash
set -euo pipefail

service_name="${GITHUB_SECRET_SERVICE:-myblog-github}"
account_name="${GITHUB_SECRET_ACCOUNT:-automation}"

if secret-tool lookup service "$service_name" account "$account_name" >/dev/null 2>&1; then
  secret-tool clear service "$service_name" account "$account_name"
  echo "Stored GitHub token removed"
else
  echo "No stored GitHub token found"
fi
