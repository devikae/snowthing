#!/usr/bin/env bash
# 운영 마이그레이션을 한 번 더 실행해도 오류가 없고 데이터 행 수가 바뀌지 않는지 시험 DB에서 확인한다.
# 실행기는 파일을 적용한 뒤 이력을 남긴다. 그 사이에 연결이 끊기거나 파일 중간에서 실패하면
# 다음 배포가 같은 파일을 처음부터 다시 실행하므로, 새 마이그레이션은 두 번 실행해도 안전해야 한다.
# 062 이하는 이미 운영에 적용돼 다시 실행될 일이 없고, 고치면 실행기의 체크섬 검사에 걸린다. 그래서 검사에서 뺀다.
# 그중 057은 두 번 실행하면 실패한다(ADD COLUMN 중복). 063부터는 새 마이그레이션이 모두 이 검사를 통과해야 한다.
# 앞 단계(verify-production-migration-007-015.sh)가 최신까지 적용한 시험 DB에서 돌린다.
set -euo pipefail

RERUN_SAFE_FROM=63
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

row_counts() {
  local table_name
  while read -r table_name; do
    printf '%s %s\n' "$table_name" "$(mysql_test --execute="SELECT COUNT(*) FROM \`${table_name}\`")"
  done < <(mysql_test --execute="SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name")
}

mapfile -t migration_files < <(
  find database/production -maxdepth 1 -type f -name '[0-9][0-9][0-9]_migration_*.sql' -print | sort
)

before="$(row_counts)"
checked=0
for migration_file in "${migration_files[@]}"; do
  migration_number="$(basename "$migration_file")"
  migration_number="$((10#${migration_number:0:3}))"
  (( migration_number >= RERUN_SAFE_FROM )) || continue
  if ! mysql_test < "$migration_file" >/dev/null; then
    echo "Migration is not safe to run twice: ${migration_file}" >&2
    exit 1
  fi
  checked=$((checked + 1))
done
after="$(row_counts)"

if [[ "$before" != "$after" ]]; then
  echo "Running migrations again changed row counts:" >&2
  grep -vxF -f <(echo "$before") <<< "$after" >&2 || true
  exit 1
fi

echo "Migration rerun check passed (${checked} files from $(printf '%03d' "$RERUN_SAFE_FROM"))."
