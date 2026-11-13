#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
. "$script_dir/load-gitea-env.sh"

export GIT_ASKPASS="$script_dir/gitea-askpass.sh"
export GIT_TERMINAL_PROMPT=0

exec git "$@"
