#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: scripts/security/gitea-api.sh <GET|POST|DELETE> <path>

Authentication uses the system keyring token when available, otherwise the
Gitea username/password loaded from the private environment file. Secrets are
never printed or placed in command-line arguments. Plain HTTP is accepted only
for loopback addresses.
EOF
}

if [ "$#" -ne 2 ]; then
  usage
  exit 2
fi

method=$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')
path=$2
case "$method" in
  GET|POST|DELETE) ;;
  *)
    usage
    exit 2
    ;;
esac

case "$path" in
  /api/v1/*) ;;
  *)
    echo "Path must start with /api/v1/" >&2
    exit 2
    ;;
esac

env_file="${GITEA_ENV_FILE:-${XDG_CONFIG_HOME:-$HOME/.config}/myblog/gitea.env}"
if [ -r "$env_file" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$env_file"
  set +a
fi

base_url="${GITEA_API_URL:-${GITEA_URL:-https://leo-blog.top/gitea}}"
base_url=${base_url%/}

case "$base_url" in
  https://*) ;;
  http://127.0.0.1|http://127.0.0.1:*|http://localhost|http://localhost:*|http://\[::1\]|http://\[::1\]:*)
    ;;
  http://*)
    echo "Refusing plain HTTP to a non-loopback Gitea endpoint" >&2
    exit 2
    ;;
  *)
    echo "GITEA_API_URL must use https:// or loopback http://" >&2
    exit 2
    ;;
esac

service_name="${GITEA_SECRET_SERVICE:-myblog-gitea}"
account_name="${GITEA_SECRET_ACCOUNT:-automation}"
token=$(secret-tool lookup service "$service_name" account "$account_name" 2>/dev/null || true)

if [ -n "$token" ] && ! printf '%s' "$token" | grep -Eq '^[A-Za-z0-9._~-]+$'; then
  unset token
  echo "Stored Gitea token has an unexpected format" >&2
  exit 1
fi

if [ -z "$token" ] && { [ -z "${GITEA_USERNAME:-}" ] || [ -z "${GITEA_PASSWORD:-}" ]; }; then
  echo "No Gitea authentication is configured" >&2
  echo "Run scripts/security/store-gitea-account.sh or store-gitea-token.sh first" >&2
  exit 1
fi

escape_curl_value() {
  local value=$1
  value=${value//\\/\\\\}
  value=${value//\"/\\\"}
  printf '%s' "$value"
}

{
  printf 'url = "%s%s"\n' "$(escape_curl_value "$base_url")" "$(escape_curl_value "$path")"
  printf 'request = "%s"\n' "$method"
  printf 'header = "Accept: application/json"\n'
  if [ -n "$token" ]; then
    printf 'header = "Authorization: token %s"\n' "$(escape_curl_value "$token")"
  else
    printf 'user = "%s:%s"\n' \
      "$(escape_curl_value "$GITEA_USERNAME")" \
      "$(escape_curl_value "$GITEA_PASSWORD")"
  fi
  printf 'silent\nshow-error\nfail-with-body\nmax-time = 30\n'
} | curl --config -
unset token
