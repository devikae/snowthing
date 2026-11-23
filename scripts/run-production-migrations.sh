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
# ALTER TABLE은 시작과 끝에 테이블 메타데이터 잠금을 잡는다. MySQL 기본 대기 시간은 1년이라
# 긴 트랜잭션이 테이블을 잡고 있으면 ALTER가 계속 기다리고, 그 뒤에 온 일반 조회와 쓰기까지 줄을 서서 사이트가 멈춘다.
# 짧게 기다리다 실패하게 해서 배포만 멈추고 서비스는 계속 돌게 한다. 실패하면 한가한 시간에 다시 배포한다.
SNOWTHING_MIGRATION_LOCK_WAIT_TIMEOUT_SECONDS="${SNOWTHING_MIGRATION_LOCK_WAIT_TIMEOUT_SECONDS:-10}"

if [[ ! "$SNOWTHING_MIGRATION_LOCK_WAIT_TIMEOUT_SECONDS" =~ ^[1-9][0-9]{0,3}$ ]]; then
  echo "SNOWTHING_MIGRATION_LOCK_WAIT_TIMEOUT_SECONDS must be an integer between 1 and 9999." >&2
  exit 1
fi

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
  --init-command="SET SESSION lock_wait_timeout = ${SNOWTHING_MIGRATION_LOCK_WAIT_TIMEOUT_SECONDS}"
)

if [[ -n "${SNOWTHING_MIGRATION_DB_SSL_CA:-}" ]]; then
  MYSQL_OPTIONS+=(--ssl-ca="$SNOWTHING_MIGRATION_DB_SSL_CA")
fi

if [[ "$DB_AUTH_MODE" == "iam" ]]; then
  MYSQL_OPTIONS+=(--enable-cleartext-plugin)
fi

# 이 스크립트의 출력은 SSM을 거쳐 공개 저장소의 GitHub Actions 로그에 그대로 찍힌다.
# MySQL 오류 원문에는 중복 키 값(이메일 등), 내부 호스트와 계정 이름이 들어갈 수 있어
# 화면에는 오류 번호와 줄 번호만 내보내고, 원문은 서버의 운영자 전용 디렉터리에 남긴다.
# 시험 DB를 쓰는 CI는 SNOWTHING_MIGRATION_PRINT_FULL_ERROR=true로 원문을 바로 본다.
MIGRATION_LOG_DIRECTORY="${SNOWTHING_MIGRATION_LOG_DIR:-/var/log/snowthing-deploy}"
MIGRATION_STEP="connection check"

report_mysql_failure() {
  local error_file="$1" exit_status="$2" summary saved_path
  summary="$(grep -m1 -oE '^ERROR [0-9]+ \([0-9A-Z]+\)( at line [0-9]+)?' "$error_file" || true)"
  summary="${summary:-mysql exit ${exit_status}}"

  if [[ "${SNOWTHING_MIGRATION_PRINT_FULL_ERROR:-false}" == "true" ]]; then
    cat "$error_file" >&2
  fi

  saved_path="${MIGRATION_LOG_DIRECTORY}/migration-$(date -u +'%Y%m%dT%H%M%SZ')-$$.log"
  if install -d -m 0700 "$MIGRATION_LOG_DIRECTORY" 2>/dev/null \
    && install -m 0600 "$error_file" "$saved_path" 2>/dev/null; then
    echo "MySQL failed during ${MIGRATION_STEP}: ${summary}. Full error saved on the host: ${saved_path}" >&2
  else
    echo "MySQL failed during ${MIGRATION_STEP}: ${summary}. Full error was not saved (log directory not writable)." >&2
  fi
}

run_mysql() {
  local error_file exit_status=0
  error_file="$(mktemp)"
  chmod 0600 "$error_file"
  MYSQL_PWD="$DB_PASSWORD" mysql "${MYSQL_OPTIONS[@]}" "$@" 2>"$error_file" || exit_status=$?
  if (( exit_status != 0 )); then
    report_mysql_failure "$error_file" "$exit_status"
  fi
  rm -f "$error_file"
  return "$exit_status"
}

run_mysql --execute="SELECT 1" >/dev/null
echo "Migration database connection verified."
MIGRATION_STEP="migration history setup"

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

# 여러 PR이 같은 번호를 쓰면 파일 이름 순서로 둘 다 적용되어, 실행 순서가 작성자가 의도한 것과 달라질 수 있다.
# 하나라도 적용하기 전에 이름과 번호를 모두 검사한다.
declare -A migration_number_owner=()
for migration_file in "${migration_files[@]}"; do
  migration_version="$(basename "$migration_file" .sql)"
  if [[ ! "$migration_version" =~ ^[0-9]{3}_migration_[a-z0-9_]+$ ]]; then
    echo "Invalid migration file name: ${migration_version}" >&2
    exit 1
  fi
  migration_number="${migration_version:0:3}"
  if [[ -n "${migration_number_owner[$migration_number]:-}" ]]; then
    echo "Duplicate migration number ${migration_number}: ${migration_number_owner[$migration_number]} and ${migration_version}" >&2
    exit 1
  fi
  migration_number_owner[$migration_number]="$migration_version"
done

for migration_file in "${migration_files[@]}"; do
  migration_version="$(basename "$migration_file" .sql)"
  migration_checksum="$(sha256sum "$migration_file" | awk '{print $1}')"
  MIGRATION_STEP="${migration_version}"
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
