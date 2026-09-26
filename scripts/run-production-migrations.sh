#!/usr/bin/env bash
set -euo pipefail

MIGRATION_DIRECTORY="${1:-database/production}"
MIGRATION_PATTERN='[0-9][0-9][0-9]_migration_*.sql'

SNOWTHING_MIGRATION_DB_HOST="${SNOWTHING_MIGRATION_DB_HOST:-${DB_HOST:-}}"
SNOWTHING_MIGRATION_DB_PORT="${SNOWTHING_MIGRATION_DB_PORT:-${DB_PORT:-3306}}"
SNOWTHING_MIGRATION_DB_NAME="${SNOWTHING_MIGRATION_DB_NAME:-${DB_NAME:-}}"
SNOWTHING_MIGRATION_DB_USERNAME="${SNOWTHING_MIGRATION_DB_USERNAME:-${DB_USER:-}}"
SNOWTHING_MIGRATION_DB_PASSWORD="${SNOWTHING_MIGRATION_DB_PASSWORD:-${DB_PASSWORD:-}}"
SNOWTHING_MIGRATION_DB_AUTH_MODE="${SNOWTHING_MIGRATION_DB_AUTH_MODE:-${DB_AUTH_MODE:-iam}}"
SNOWTHING_MIGRATION_DB_SSL_MODE="${SNOWTHING_MIGRATION_DB_SSL_MODE:-${DB_SSL_MODE:-VERIFY_IDENTITY}}"
SNOWTHING_MIGRATION_DB_SSL_CA="${SNOWTHING_MIGRATION_DB_SSL_CA:-${DB_SSL_CA:-}}"

required_variables=(
  SNOWTHING_MIGRATION_DB_HOST
  SNOWTHING_MIGRATION_DB_NAME
  SNOWTHING_MIGRATION_DB_USERNAME
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Required migration variable is missing: ${variable_name}" >&2
    exit 1
  fi
done

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql client is required to run production migrations." >&2
  exit 1
fi

if ! command -v sha256sum >/dev/null 2>&1; then
  echo "sha256sum is required to verify migration checksums." >&2
  exit 1
fi

if [[ ! -d "$MIGRATION_DIRECTORY" ]]; then
  echo "Migration directory does not exist: ${MIGRATION_DIRECTORY}" >&2
  exit 1
fi

DB_PORT="$SNOWTHING_MIGRATION_DB_PORT"
DB_AUTH_MODE="$SNOWTHING_MIGRATION_DB_AUTH_MODE"
DB_SSL_MODE="$SNOWTHING_MIGRATION_DB_SSL_MODE"

case "$DB_AUTH_MODE" in
  password)
    if [[ -z "${SNOWTHING_MIGRATION_DB_PASSWORD:-}" ]]; then
      echo "SNOWTHING_MIGRATION_DB_PASSWORD is required for password authentication." >&2
      exit 1
    fi
    DB_PASSWORD="$SNOWTHING_MIGRATION_DB_PASSWORD"
    ;;
  iam)
    if ! command -v aws >/dev/null 2>&1; then
      echo "aws CLI is required for IAM database authentication." >&2
      exit 1
    fi
    if [[ -z "${AWS_REGION:-}" ]]; then
      echo "AWS_REGION is required for IAM database authentication." >&2
      exit 1
    fi
    DB_PASSWORD="$(aws rds generate-db-auth-token \
      --hostname "$SNOWTHING_MIGRATION_DB_HOST" \
      --port "$DB_PORT" \
      --region "$AWS_REGION" \
      --username "$SNOWTHING_MIGRATION_DB_USERNAME")"
    ;;
  *)
    echo "Unsupported SNOWTHING_MIGRATION_DB_AUTH_MODE: ${DB_AUTH_MODE}" >&2
    exit 1
    ;;
esac

MYSQL_OPTIONS=(
  --protocol=TCP
  --host="$SNOWTHING_MIGRATION_DB_HOST"
  --port="$DB_PORT"
  --user="$SNOWTHING_MIGRATION_DB_USERNAME"
  --database="$SNOWTHING_MIGRATION_DB_NAME"
  --ssl-mode="$DB_SSL_MODE"
  --batch
  --skip-column-names
)

if [[ -n "${SNOWTHING_MIGRATION_DB_SSL_CA:-}" ]]; then
  MYSQL_OPTIONS+=(--ssl-ca="$SNOWTHING_MIGRATION_DB_SSL_CA")
fi

if [[ "$DB_AUTH_MODE" == "iam" ]]; then
  MYSQL_OPTIONS+=(--enable-cleartext-plugin)
fi

run_mysql() {
  MYSQL_PWD="$DB_PASSWORD" mysql "${MYSQL_OPTIONS[@]}" "$@"
}

run_mysql --execute="SELECT 1" >/dev/null
echo "Migration database connection verified."

run_mysql <<'SQL'
CREATE TABLE IF NOT EXISTS `schema_migration` (
    `version` VARCHAR(100) NOT NULL PRIMARY KEY,
    `checksum` CHAR(64) NOT NULL,
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

mapfile -t migration_files < <(
  find "$MIGRATION_DIRECTORY" -maxdepth 1 -type f -name "$MIGRATION_PATTERN" -print | sort
)

if (( ${#migration_files[@]} == 0 )); then
  echo "No versioned production migrations found."
  exit 0
fi

for migration_file in "${migration_files[@]}"; do
  migration_version="$(basename "$migration_file" .sql)"
  if [[ ! "$migration_version" =~ ^[0-9]{3}_migration_[a-z0-9_]+$ ]]; then
    echo "Invalid migration file name: ${migration_version}" >&2
    exit 1
  fi
  migration_checksum="$(sha256sum "$migration_file" | awk '{print $1}')"
  applied_checksum="$(
    run_mysql --execute="SELECT checksum FROM schema_migration WHERE version = '${migration_version}'" \
      | tr -d '\r'
  )"

  if [[ -n "$applied_checksum" ]]; then
    if [[ "$applied_checksum" != "$migration_checksum" ]]; then
      echo "Migration checksum mismatch: ${migration_version}" >&2
      exit 1
    fi
    echo "Migration already applied: ${migration_version}"
    continue
  fi

  echo "Applying migration: ${migration_version}"
  run_mysql < "$migration_file"
  run_mysql --execute="INSERT INTO schema_migration (version, checksum) VALUES ('${migration_version}', '${migration_checksum}')"
  echo "Migration applied: ${migration_version}"
done
