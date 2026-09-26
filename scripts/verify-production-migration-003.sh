#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${SNOWTHING_TEST_DB_HOST:-127.0.0.1}"
DB_PORT="${SNOWTHING_TEST_DB_PORT:-3306}"
DB_NAME="${SNOWTHING_TEST_DB_NAME:-snowthing_test}"
DB_USERNAME="${SNOWTHING_TEST_DB_USERNAME:?SNOWTHING_TEST_DB_USERNAME is required}"
DB_PASSWORD="${SNOWTHING_TEST_DB_PASSWORD:?SNOWTHING_TEST_DB_PASSWORD is required}"

mysql_test() {
  MYSQL_PWD="$DB_PASSWORD" mysql \
    --protocol=TCP \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USERNAME" \
    --database="$DB_NAME" \
    --batch \
    --skip-column-names \
    "$@"
}

mysql_test < database/production/001_initial_schema.sql
mysql_test < database/production/002_reference_data.sql
mysql_test <<'SQL'
INSERT INTO `post` (
    `public_id`, `category_id`, `title`, `content`, `writer_ip`,
    `is_anonymous`, `view_count`, `comment_count`, `like_count`,
    `dislike_count`, `has_image`, `status`, `is_deleted`
)
SELECT
    'migration-003-verification-post', category_id, 'migration verification',
    'migration verification', '127.0.0.1', FALSE, 0, 0, 7, 3, FALSE, 'NORMAL', FALSE
FROM `post_category`
ORDER BY category_id
LIMIT 1
ON DUPLICATE KEY UPDATE
    like_count = 7,
    dislike_count = 3,
    updated_at = updated_at;
SQL

SNOWTHING_MIGRATION_DB_HOST="$DB_HOST" \
SNOWTHING_MIGRATION_DB_PORT="$DB_PORT" \
SNOWTHING_MIGRATION_DB_NAME="$DB_NAME" \
SNOWTHING_MIGRATION_DB_USERNAME="$DB_USERNAME" \
SNOWTHING_MIGRATION_DB_PASSWORD="$DB_PASSWORD" \
SNOWTHING_MIGRATION_DB_AUTH_MODE=password \
SNOWTHING_MIGRATION_DB_SSL_MODE=DISABLED \
scripts/run-production-migrations.sh database/production

reconciled_counts="$(
  mysql_test --execute="SELECT CONCAT(like_count, ':', dislike_count) FROM post WHERE public_id = 'migration-003-verification-post'"
)"
if [[ "$reconciled_counts" != "0:0" ]]; then
  echo "Migration did not reconcile reaction counters: ${reconciled_counts}" >&2
  exit 1
fi

constraints="$(
  mysql_test --execute="SELECT constraint_name FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'post' AND constraint_type = 'CHECK' ORDER BY constraint_name"
)"
grep -qx 'chk_post_dislike_count_non_negative' <<< "$constraints"
grep -qx 'chk_post_like_count_non_negative' <<< "$constraints"

show_create_table="$(mysql_test --execute="SHOW CREATE TABLE post")"
grep -q 'CONSTRAINT `chk_post_like_count_non_negative` CHECK' <<< "$show_create_table"
grep -q 'CONSTRAINT `chk_post_dislike_count_non_negative` CHECK' <<< "$show_create_table"

if mysql_test --execute="UPDATE post SET like_count = -1 WHERE public_id = 'migration-003-verification-post'"; then
  echo "Negative like_count update unexpectedly succeeded." >&2
  exit 1
fi

applied_count="$(
  mysql_test --execute="SELECT COUNT(*) FROM schema_migration WHERE version = '003_migration_post_reaction_count_checks'"
)"
if [[ "$applied_count" != "1" ]]; then
  echo "Migration history was not recorded exactly once." >&2
  exit 1
fi

echo "Production migration 003 verification passed."
