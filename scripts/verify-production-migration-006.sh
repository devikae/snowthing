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
grep -q 'Migration already applied: 006_migration_fix_stream_proxy_path' <<< "$second_run_output"

[[ "$(mysql_test --execute="SELECT COUNT(*) FROM schema_migration WHERE version = '006_migration_fix_stream_proxy_path'")" == "1" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM resort_camera WHERE source_url LIKE '/api/%-stream/%'")" == "0" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM resort_camera WHERE source_url LIKE '/stream-proxy/%'")" == "34" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM resort_camera rc JOIN resort r ON rc.resort_id = r.resort_id WHERE r.code = 'PHOENIX' AND rc.source_url = 'https://streaming.phoenixhnr.co.kr/hls/yh_02_02.m3u8'")" == "1" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM resort_camera rc JOIN resort r ON rc.resort_id = r.resort_id WHERE r.code = 'VIVALDI' AND rc.code = 'CAM_09' AND rc.source_url LIKE '%TW0014A15451%'")" == "1" ]]

echo "Production migration 006 verification passed."
