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

run_migrations() {
  DB_HOST="$DB_HOST" \
  DB_PORT="$DB_PORT" \
  DB_NAME="$DB_NAME" \
  DB_USER="$DB_USERNAME" \
  DB_PASSWORD="$DB_PASSWORD" \
  DB_AUTH_MODE=password \
  DB_SSL_MODE=DISABLED \
  scripts/run-production-migrations.sh database/production
}

run_migrations
second_run_output="$(run_migrations)"
grep -q 'Migration already applied: 004_migration_used_market' <<< "$second_run_output"

applied_count="$(
  mysql_test --execute="SELECT COUNT(*) FROM schema_migration WHERE version = '004_migration_used_market'"
)"
if [[ "$applied_count" != "1" ]]; then
  echo "Migration 004 history was not recorded exactly once." >&2
  exit 1
fi

market_category_count="$(mysql_test --execute="SELECT COUNT(*) FROM market_category")"
if [[ "$market_category_count" != "9" ]]; then
  echo "Expected 9 market categories, found ${market_category_count}." >&2
  exit 1
fi

market_post_category_count="$(
  mysql_test --execute="SELECT COUNT(*) FROM post_category WHERE code = 'MARKET' AND name = '중고장터'"
)"
if [[ "$market_post_category_count" != "1" ]]; then
  echo "MARKET post category was not seeded correctly." >&2
  exit 1
fi

constraints="$(
  mysql_test --execute="SELECT constraint_name FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'market_listing' ORDER BY constraint_name"
)"
for constraint in \
  chk_market_listing_condition \
  chk_market_listing_contact \
  chk_market_listing_price_mode \
  chk_market_listing_price_non_negative \
  chk_market_listing_trade_status \
  chk_market_listing_transaction_method \
  fk_market_listing_category \
  fk_market_listing_post \
  uk_market_listing_post; do
  grep -qx "$constraint" <<< "$constraints"
done

indexes="$(
  mysql_test --execute="SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'market_listing' ORDER BY index_name"
)"
for index in \
  idx_market_listing_category_trade_created \
  idx_market_listing_condition_trade_created \
  idx_market_listing_trade_created; do
  grep -qx "$index" <<< "$indexes"
done

mysql_test <<'SQL'
INSERT INTO `member` (`public_id`, `email`, `password`, `nickname`, `role`)
VALUES ('migration-004-member', 'migration-004@snowthing.test', 'test-password', 'migration004', 'ROLE_USER')
ON DUPLICATE KEY UPDATE `nickname` = VALUES(`nickname`);

INSERT INTO `post` (
    `public_id`, `member_id`, `category_id`, `title`, `content`, `writer_ip`,
    `is_anonymous`, `view_count`, `comment_count`, `like_count`,
    `dislike_count`, `has_image`, `status`, `is_deleted`
)
SELECT
    'migration-004-post', m.member_id, pc.category_id, 'migration 004',
    'migration 004', '127.0.0.1', FALSE, 0, 0, 0, 0, FALSE, 'NORMAL', FALSE
FROM `member` m
JOIN `post_category` pc ON pc.code = 'MARKET'
WHERE m.public_id = 'migration-004-member'
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `market_listing` (
    `post_id`, `market_category_id`, `product_condition`, `transaction_method`,
    `trade_status`, `price`, `is_negotiable`, `is_free`, `contact`
)
SELECT p.post_id, mc.market_category_id, 'GOOD', 'BOTH', 'ON_SALE', 20000000, TRUE, FALSE, '010-1234-5678'
FROM `post` p
JOIN `market_category` mc ON mc.code = 'SNOWBOARD'
WHERE p.public_id = 'migration-004-post'
ON DUPLICATE KEY UPDATE `contact` = VALUES(`contact`);
SQL

if mysql_test --execute="UPDATE market_listing SET price = 20000001 WHERE post_id = (SELECT post_id FROM post WHERE public_id = 'migration-004-post')"; then
  echo "Price above 20,000,000 unexpectedly passed the market listing CHECK." >&2
  exit 1
fi

if mysql_test --execute="UPDATE market_listing SET is_free = TRUE, price = 0, is_negotiable = TRUE WHERE post_id = (SELECT post_id FROM post WHERE public_id = 'migration-004-post')"; then
  echo "Negotiable free listing unexpectedly passed the market listing CHECK." >&2
  exit 1
fi

echo "Production migration 004 verification passed."
