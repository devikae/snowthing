#!/usr/bin/env bash
# 운영 마이그레이션 실행기(run-production-migrations.sh)의 안전장치를 시험 DB에서 확인한다.
# 시험용 테이블과 이력은 끝날 때 지운다. 운영 DB에는 절대 연결하지 않는다.
set -euo pipefail

DB_HOST="${SNOWTHING_TEST_DB_HOST:-127.0.0.1}"
DB_PORT="${SNOWTHING_TEST_DB_PORT:-3306}"
DB_NAME="${SNOWTHING_TEST_DB_NAME:-snowthing_test}"
DB_USERNAME="${SNOWTHING_TEST_DB_USERNAME:?SNOWTHING_TEST_DB_USERNAME is required}"
DB_PASSWORD="${SNOWTHING_TEST_DB_PASSWORD:?SNOWTHING_TEST_DB_PASSWORD is required}"
PROBE_TABLE="migration_runner_probe"

mysql_test() {
  MYSQL_PWD="$DB_PASSWORD" mysql \
    --protocol=TCP --host="$DB_HOST" --port="$DB_PORT" \
    --user="$DB_USERNAME" --database="$DB_NAME" \
    --batch --skip-column-names "$@"
}

run_runner() {
  SNOWTHING_MIGRATION_DB_HOST="$DB_HOST" \
  SNOWTHING_MIGRATION_DB_PORT="$DB_PORT" \
  SNOWTHING_MIGRATION_DB_NAME="$DB_NAME" \
  SNOWTHING_MIGRATION_DB_USERNAME="$DB_USERNAME" \
  SNOWTHING_MIGRATION_DB_PASSWORD="$DB_PASSWORD" \
  SNOWTHING_MIGRATION_DB_AUTH_MODE=password \
  SNOWTHING_MIGRATION_DB_SSL_MODE=DISABLED \
  scripts/run-production-migrations.sh "$1"
}

fail() {
  echo "verify-production-migration-runner: $1" >&2
  exit 1
}

applied_count() {
  mysql_test --execute="SELECT COUNT(*) FROM schema_migration WHERE version = '$1'"
}

WORK_DIRECTORY="$(mktemp -d)"
HOLDER_PID=""

kill_lock_holder() {
  local thread_id
  thread_id="$(mysql_test --execute="SELECT id FROM information_schema.processlist WHERE info = 'SELECT SLEEP(60)' LIMIT 1" || true)"
  if [[ -n "$thread_id" ]]; then
    mysql_test --execute="KILL ${thread_id}" >/dev/null 2>&1 || true
  fi
  if [[ -n "$HOLDER_PID" ]]; then
    wait "$HOLDER_PID" 2>/dev/null || true
    HOLDER_PID=""
  fi
}

cleanup() {
  kill_lock_holder
  mysql_test --execute="DROP TABLE IF EXISTS ${PROBE_TABLE}" >/dev/null 2>&1 || true
  mysql_test --execute="DELETE FROM schema_migration WHERE version LIKE '99%_migration_runner_probe%'" >/dev/null 2>&1 || true
  rm -rf "$WORK_DIRECTORY"
}
trap cleanup EXIT

mysql_test --execute="DROP TABLE IF EXISTS ${PROBE_TABLE}"
mysql_test --execute="CREATE TABLE ${PROBE_TABLE} (id BIGINT NOT NULL PRIMARY KEY, email VARCHAR(100) NOT NULL UNIQUE)"

# 1. 다른 트랜잭션이 테이블을 잡고 있으면 ALTER는 오래 기다리지 않고 실패해야 한다.
#    기다리는 동안 그 테이블의 다른 조회와 쓰기도 모두 막히기 때문이다.
mkdir "$WORK_DIRECTORY/lock"
echo "ALTER TABLE ${PROBE_TABLE} ADD COLUMN lock_probe INT NULL;" \
  > "$WORK_DIRECTORY/lock/990_migration_runner_probe_lock.sql"

mysql_test --execute="START TRANSACTION; SELECT COUNT(*) FROM ${PROBE_TABLE}; SELECT SLEEP(60); COMMIT;" >/dev/null 2>&1 &
HOLDER_PID=$!
for _ in $(seq 1 50); do
  if [[ "$(mysql_test --execute="SELECT COUNT(*) FROM information_schema.processlist WHERE info = 'SELECT SLEEP(60)'")" == "1" ]]; then
    break
  fi
  sleep 0.2
done

started_at=$SECONDS
if lock_output="$(SNOWTHING_MIGRATION_LOCK_WAIT_TIMEOUT_SECONDS=2 run_runner "$WORK_DIRECTORY/lock" 2>&1)"; then
  fail "ALTER succeeded while another transaction held the table."
fi
elapsed=$((SECONDS - started_at))
kill_lock_holder
(( elapsed < 20 )) || fail "ALTER waited ${elapsed}s for the metadata lock instead of failing fast."
grep -q 'ERROR 1205' <<< "$lock_output" || fail "Lock wait failure was not reported: ${lock_output}"
[[ "$(applied_count 990_migration_runner_probe_lock)" == "0" ]] || fail "A failed migration was recorded as applied."

# 잠금이 풀리면 같은 파일이 정상 적용되고 이력에 남는다.
run_runner "$WORK_DIRECTORY/lock" >/dev/null
[[ "$(applied_count 990_migration_runner_probe_lock)" == "1" ]] || fail "Migration was not recorded after the lock was released."

# 2. 실패한 SQL의 오류 원문은 공개 Actions 로그에 나가지 않고 서버의 운영자 전용 파일에만 남아야 한다.
#    중복 키 오류 원문에는 실제 데이터 값(여기서는 이메일)이 들어간다.
mkdir "$WORK_DIRECTORY/redact"
cat > "$WORK_DIRECTORY/redact/991_migration_runner_probe_redact.sql" <<SQL
INSERT INTO ${PROBE_TABLE} (id, email) VALUES (1, 'leak-check@example.test');
INSERT INTO ${PROBE_TABLE} (id, email) VALUES (2, 'leak-check@example.test');
SQL
log_directory="$WORK_DIRECTORY/logs"
if redact_output="$(SNOWTHING_MIGRATION_PRINT_FULL_ERROR=false SNOWTHING_MIGRATION_LOG_DIR="$log_directory" \
  run_runner "$WORK_DIRECTORY/redact" 2>&1)"; then
  fail "A failing migration exited successfully."
fi
grep -q 'MySQL failed during 991_migration_runner_probe_redact: ERROR 1062 (23000) at line 2' <<< "$redact_output" \
  || fail "Failure summary is missing: ${redact_output}"
if grep -q 'leak-check' <<< "$redact_output"; then
  fail "Raw MySQL error with data values reached the console."
fi
saved_logs=("$log_directory"/migration-*.log)
[[ -f "${saved_logs[0]}" ]] || fail "Full error was not saved for operators."
grep -q "leak-check@example.test" "${saved_logs[0]}" || fail "Saved error does not contain the original message."
[[ "$(stat -c '%a' "$log_directory")" == "700" ]] || fail "Error log directory is not operator-only."
[[ "$(stat -c '%a' "${saved_logs[0]}")" == "600" ]] || fail "Error log file is not operator-only."
[[ "$(applied_count 991_migration_runner_probe_redact)" == "0" ]] || fail "A failed migration was recorded as applied."

# 3. 두 PR이 같은 번호를 쓰면 하나도 적용하지 않고 멈춰야 한다.
mkdir "$WORK_DIRECTORY/duplicate"
echo "ALTER TABLE ${PROBE_TABLE} ADD COLUMN duplicate_a INT NULL;" \
  > "$WORK_DIRECTORY/duplicate/992_migration_runner_probe_a.sql"
echo "ALTER TABLE ${PROBE_TABLE} ADD COLUMN duplicate_b INT NULL;" \
  > "$WORK_DIRECTORY/duplicate/992_migration_runner_probe_b.sql"
if duplicate_output="$(run_runner "$WORK_DIRECTORY/duplicate" 2>&1)"; then
  fail "Migrations with the same number were applied."
fi
grep -q 'Duplicate migration number 992' <<< "$duplicate_output" \
  || fail "Duplicate number was not reported: ${duplicate_output}"
[[ "$(mysql_test --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '${PROBE_TABLE}' AND column_name LIKE 'duplicate_%'")" == "0" ]] \
  || fail "A migration ran before the duplicate number check."

echo "Migration runner safety checks passed."
