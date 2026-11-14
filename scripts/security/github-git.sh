#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)

export GIT_ASKPASS="$script_dir/github-askpass.sh"
export GIT_ASKPASS_REQUIRE=force
export GIT_TERMINAL_PROMPT=0

exec git "$@"
