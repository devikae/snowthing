#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${SNOWTHING_TEST_DB_HOST:-127.0.0.1}"
DB_PORT="${SNOWTHING_TEST_DB_PORT:-3306}"
DB_NAME="${SNOWTHING_TEST_DB_NAME:-snowthing_test}"
DB_USERNAME="${SNOWTHING_TEST_DB_USERNAME:?SNOWTHING_TEST_DB_USERNAME is required}"
DB_PASSWORD="${SNOWTHING_TEST_DB_PASSWORD:?SNOWTHING_TEST_DB_PASSWORD is required}"

mysql_test() {
  MYSQL_PWD="$DB_PASSWORD" mysql \
    --protocol=TCP --host="$DB_HOST" --port="$DB_PORT" \
    --user="$DB_USERNAME" --database="$DB_NAME" \
    --batch --skip-column-names "$@"
}

mysql_test < database/production/001_initial_schema.sql
mysql_test < database/production/002_reference_data.sql

run_migrations() {
  SNOWTHING_MIGRATION_DB_HOST="$DB_HOST" \
  SNOWTHING_MIGRATION_DB_PORT="$DB_PORT" \
  SNOWTHING_MIGRATION_DB_NAME="$DB_NAME" \
  SNOWTHING_MIGRATION_DB_USERNAME="$DB_USERNAME" \
  SNOWTHING_MIGRATION_DB_PASSWORD="$DB_PASSWORD" \
  SNOWTHING_MIGRATION_DB_AUTH_MODE=password \
  SNOWTHING_MIGRATION_DB_SSL_MODE=DISABLED \
  scripts/run-production-migrations.sh database/production
}

run_migrations
second_run_output="$(run_migrations)"
grep -q 'Migration already applied: 005_migration_resort_cam' <<< "$second_run_output"

[[ "$(mysql_test --execute="SELECT COUNT(*) FROM schema_migration WHERE version = '005_migration_resort_cam'")" == "1" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM resort WHERE code IS NOT NULL AND is_active = TRUE")" == "13" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM resort_camera WHERE is_active = TRUE")" == "87" ]]

assert_source_count() {
  local source_type="$1"
  local expected_count="$2"
  local actual_count
  actual_count="$(mysql_test --execute="SELECT COUNT(*) FROM resort_camera WHERE source_type = '${source_type}'")"
  if [[ "$actual_count" != "$expected_count" ]]; then
    echo "Expected ${expected_count} ${source_type} resort cameras, found ${actual_count}." >&2
    exit 1
  fi
}

assert_source_count HLS 67
assert_source_count IFRAME 18
assert_source_count YOUTUBE 2
assert_source_count EXTERNAL_LINK 0

echo "Production migration 005 verification passed."
