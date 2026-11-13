#!/usr/bin/env bash
set -euo pipefail

service_name="${GITEA_SECRET_SERVICE:-myblog-gitea}"
account_name="${GITEA_SECRET_ACCOUNT:-automation}"

if secret-tool lookup service "$service_name" account "$account_name" >/dev/null 2>&1; then
  secret-tool clear service "$service_name" account "$account_name"
  echo "Stored Gitea token removed"
else
  echo "No stored Gitea token found"
fi
