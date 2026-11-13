#!/bin/sh
set -eu

flyway \
  -url="$FLYWAY_URL" \
  -user="$FLYWAY_USER" \
  -password="$FLYWAY_PASSWORD" \
  -connectRetries=30 \
  baseline \
  -baselineVersion=38 \
  -baselineDescription="existing schema baseline" || true

flyway \
  -url="$FLYWAY_URL" \
  -user="$FLYWAY_USER" \
  -password="$FLYWAY_PASSWORD" \
  -connectRetries=30 \
  migrate
