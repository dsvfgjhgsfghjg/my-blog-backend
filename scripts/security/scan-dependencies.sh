#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
cd "$ROOT_DIR"

command -v jq >/dev/null 2>&1 || {
  echo "jq is required for dependency scanning" >&2
  exit 2
}

COORDINATES=$(./gradlew -q dependencies --configuration runtimeClasspath --console=plain 2>/dev/null \
  | awk '
      {
        line=$0
        if (line ~ / -> /) {
          split(line, parts, " -> ")
          version=parts[2]
          sub(/[[:space:]].*/, "", version)
          if (match(parts[1], /[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+/)) {
            print substr(parts[1], RSTART, RLENGTH) ":" version
          }
        } else if (match(line, /[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[^ ()]+/)) {
          print substr(line, RSTART, RLENGTH)
        }
      }
    ' \
  | sort -u)

if [ -z "$COORDINATES" ]; then
  echo "No Maven coordinates found in runtimeClasspath" >&2
  exit 2
fi

QUERIES=$(printf '%s\n' "$COORDINATES" | jq -R -s '
  split("\n")
  | map(select(length > 0))
  | map(split(":") as $p | {
      package: {ecosystem: "Maven", name: ($p[0] + ":" + $p[1])},
      version: $p[2]
    })
  | {queries: .}
')

RESPONSE=$(curl -fsS --max-time 60 \
  -H 'Content-Type: application/json' \
  -d "$QUERIES" \
  https://api.osv.dev/v1/querybatch)

IGNORED=$(sed -n 's/^id = "\(.*\)"/\1/p' .osv-scanner.toml 2>/dev/null || true)
FINDINGS=$(printf '%s\n' "$RESPONSE" | jq -r '
  .results
  | to_entries[]
  | select((.value.vulns // []) | length > 0)
  | .key as $index
  | .value.vulns[]
  | "\($index)\t\(.id)"
')

FAILED=0
FINDINGS_FILE=$(mktemp)
trap 'rm -f "$FINDINGS_FILE"' EXIT
printf '%s\n' "$FINDINGS" > "$FINDINGS_FILE"

while IFS='	' read -r index vuln_id; do
  [ -n "$index" ] || continue
  if printf '%s\n' "$IGNORED" | grep -qx "$vuln_id"; then
    continue
  fi
  coordinate=$(printf '%s\n' "$COORDINATES" | sed -n "$((index + 1))p")
  echo "Vulnerable dependency: $coordinate -> $vuln_id" >&2
  FAILED=1
done < "$FINDINGS_FILE"

if [ "$FAILED" -ne 0 ]; then
  exit 1
fi

echo "Dependency scan passed: $(printf '%s\n' "$COORDINATES" | wc -l | tr -d ' ') Maven coordinates checked"
