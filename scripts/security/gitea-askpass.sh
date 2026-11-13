#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
. "$script_dir/load-gitea-env.sh"

case "${1:-}" in
  *Username*)
    printf '%s\n' "$GITEA_USERNAME"
    ;;
  *Password*)
    printf '%s\n' "$GITEA_PASSWORD"
    ;;
  *)
    exit 1
    ;;
esac
