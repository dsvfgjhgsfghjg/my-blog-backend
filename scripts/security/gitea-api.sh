#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: GITEA_API_URL=<url> scripts/security/gitea-api.sh <GET|POST> <path>

The token is read from the system keyring and is never printed or placed in
command-line arguments. Plain HTTP is accepted only for loopback addresses.
EOF
}

if [ "$#" -ne 2 ]; then
  usage
  exit 2
fi

method=$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')
path=$2
case "$method" in
  GET|POST) ;;
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

base_url="${GITEA_API_URL:-}"
if [ -z "$base_url" ]; then
  echo "GITEA_API_URL is required" >&2
  exit 2
fi
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
if [ -z "$token" ]; then
  echo "No Gitea token is stored; run scripts/security/store-gitea-token.sh first" >&2
  exit 1
fi

if ! printf '%s' "$token" | grep -Eq '^[A-Za-z0-9._~-]+$'; then
  unset token
  echo "Stored Gitea token has an unexpected format" >&2
  exit 1
fi

curl_config=$(
  printf 'url = "%s%s"\n' "$base_url" "$path"
  printf 'request = "%s"\n' "$method"
  printf 'header = "Accept: application/json"\n'
  printf 'header = "Authorization: token %s"\n' "$token"
  printf 'silent\nshow-error\nfail-with-body\nmax-time = 30\n'
)
unset token

printf '%s\n' "$curl_config" | curl --config -
