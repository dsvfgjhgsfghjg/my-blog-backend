#!/usr/bin/env bash

env_file="${GITEA_ENV_FILE:-${XDG_CONFIG_HOME:-$HOME/.config}/myblog/gitea.env}"

if [ ! -r "$env_file" ]; then
  echo "Gitea credential file not found: $env_file" >&2
  return 1 2>/dev/null || exit 1
fi

set -a
# shellcheck disable=SC1090
. "$env_file"
set +a
