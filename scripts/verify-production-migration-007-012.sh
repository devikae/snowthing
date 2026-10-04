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

for version in \
  007_migration_carpool \
  008_migration_carpool_cost_mode \
  009_migration_carpool_equipment_load \
  010_migration_carpool_query_index_and_resort_coordinates \
  011_migration_carpool_manual_fallback_snapshots \
  012_migration_carpool_latest_sort_index; do
  grep -q "Migration already applied: $version" <<< "$second_run_output"
  [[ "$(mysql_test --execute="SELECT COUNT(*) FROM schema_migration WHERE version = '$version'")" == "1" ]]
done

[[ "$(mysql_test --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB_NAME' AND table_name = 'carpool_detail'")" == "1" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = '$DB_NAME' AND table_name = 'carpool_detail' AND column_name IN ('cost_mode', 'equipment_load_available')")" == "2" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM post_category WHERE code = 'CARPOOL'")" == "1" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = '$DB_NAME' AND table_name = 'resort' AND column_name IN ('route_latitude', 'route_longitude')")" == "2" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM resort WHERE code IN ('PHOENIX', 'VIVALDI', 'HIGH1', 'YONGPYONG', 'WELLI_HILLI', 'JISAN', 'KONJIAM', 'MUJU', 'EDEN_VALLEY', 'ELYSIAN', 'ALPENSIA', 'OAK_VALLEY', 'O2_RESORT') AND route_latitude IS NOT NULL AND route_longitude IS NOT NULL")" == "13" ]]
[[ "$(mysql_test --execute="SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') FROM information_schema.statistics WHERE table_schema = '$DB_NAME' AND table_name = 'carpool_detail' AND index_name = 'idx_carpool_detail_departure_created_post'")" == "departure_at,created_at,post_id" ]]
[[ "$(mysql_test --execute="SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') FROM information_schema.statistics WHERE table_schema = '$DB_NAME' AND table_name = 'carpool_detail' AND index_name = 'idx_carpool_detail_created_post'")" == "created_at,post_id" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = '$DB_NAME' AND table_name = 'carpool_detail' AND column_name IN ('destination_latitude', 'destination_longitude', 'fuel_price_source')")" == "3" ]]
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM carpool_detail WHERE destination_latitude IS NULL OR destination_longitude IS NULL")" == "0" ]]

echo "Production migrations 007-012 verification passed."
