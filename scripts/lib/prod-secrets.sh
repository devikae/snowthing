#!/usr/bin/env bash
# 운영 비밀값을 Parameter Store에서 읽어 docker compose에 넘긴다(IKA-110).
# deploy-ecr-service.sh와 compose-prod.sh가 source해서 쓴다. 단독으로 실행하지 않는다.
#
# 켜는 방법: 서버에 PARAMETER_STORE_CONFIG_PATH 파일을 만들고 아래 줄을 적는다(비밀값이 아니다).
#   SNOWTHING_SSM_PARAMETER_PATH=/snowthing/prod
#   SNOWTHING_SSM_REQUIRED_NAMES=DISCORD_WEBHOOK_EVENT,SNOWTHING_KMA_SERVICE_KEY   (필수. 경로에 반드시 있어야 하는 이름, 쉼표로 구분)
#   SNOWTHING_SSM_PARAMETER_REGION=ap-northeast-2   (선택. 없으면 호출한 쪽이 준 리전)
# 파일이 없으면 아무것도 읽지 않고 prod.env만 쓰는 기존 동작 그대로다.
#
# 값은 파일로 쓰지 않고 docker compose 프로세스 환경으로만 넘긴다.
# compose는 셸 환경을 --env-file보다 먼저 보므로 같은 이름이면 Parameter Store 값이 prod.env 값을 이긴다.
# 설계와 이전 절차: docs/conception/036_secrets_parameter_store/

# 경로는 고정한다. 호출한 셸의 환경변수(예: set -a 로 다른 env 파일을 읽은 뒤)가 바꾸지 못하게 한다.
PROD_ENV_PATH="/etc/snowthing/prod.env"
IMAGE_RECORD_PATH="/etc/snowthing/images.env"
PARAMETER_STORE_CONFIG_PATH="/etc/snowthing/parameter-store.env"
PROD_SECRETS_ERROR_LOG_DIR="/var/log/snowthing-deploy"
PROD_COMPOSE_FILE="compose.prod.yml"

PROD_SECRET_NAMES=()
PROD_SECRET_VALUES=()
PROD_SECRETS_SOURCE=""

# 설정 파일에서 한 키의 값을 읽는다. 파일을 source하지 않아 다른 줄이 실행되지 않는다.
prod_secrets_config_value() {
  local key="$1" line value=""
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    if [[ "$line" == "${key}="* ]]; then
      value="${line#*=}"
    fi
  done < "$PARAMETER_STORE_CONFIG_PATH"
  printf '%s' "$value"
}

# compose 파일이 실제로 보간하는 변수 이름만 받는다. 이미지 주소는 배포 스크립트만 정한다.
prod_secrets_allowed_name() {
  local name="$1"
  [[ "$name" != *_IMAGE_URI ]] || return 1
  grep -qE "\\\$\\{${name}[:?}-]" "$PROD_COMPOSE_FILE"
}

# 인자: 기본 리전. 설정 파일이 없으면 아무것도 하지 않고 0을 돌려준다.
# 실패하면 0이 아닌 값을 돌려주고 PROD_SECRET_* 배열은 비운다. 일부만 읽힌 상태로 남기지 않는다.
prod_secrets_load() {
  local -
  set +x
  local default_region="${1:-}" path region required_raw error_log fields=() status index name value
  local names=() values=() skipped=() required=() missing=()
  local -A returned=()

  PROD_SECRET_NAMES=()
  PROD_SECRET_VALUES=()
  PROD_SECRETS_SOURCE=""

  if [[ ! -e "$PARAMETER_STORE_CONFIG_PATH" ]]; then
    return 0
  fi
  if [[ ! -r "$PARAMETER_STORE_CONFIG_PATH" ]]; then
    echo "Parameter Store 설정 파일을 읽을 수 없습니다: $PARAMETER_STORE_CONFIG_PATH" >&2
    return 1
  fi

  path="$(prod_secrets_config_value SNOWTHING_SSM_PARAMETER_PATH)"
  region="$(prod_secrets_config_value SNOWTHING_SSM_PARAMETER_REGION)"
  region="${region:-$default_region}"
  path="${path%/}"

  if [[ ! "$path" =~ ^(/[A-Za-z0-9_.-]+)+$ ]]; then
    echo "SNOWTHING_SSM_PARAMETER_PATH 형식이 잘못됐습니다(예: /snowthing/prod): $PARAMETER_STORE_CONFIG_PATH" >&2
    return 1
  fi
  if [[ ! "$region" =~ ^[a-z]{2}(-[a-z]+)+-[0-9]+$ ]]; then
    echo "Parameter Store 리전을 알 수 없습니다. 설정 파일의 SNOWTHING_SSM_PARAMETER_REGION 또는 AWS_REGION을 확인하세요." >&2
    return 1
  fi
  # 경로에 꼭 있어야 하는 이름. 값 하나가 빠진 채 읽혀도(권한, 오타, 삭제) 배포가 시작되지 않게 한다.
  required_raw="$(prod_secrets_config_value SNOWTHING_SSM_REQUIRED_NAMES)"
  required_raw="${required_raw//[[:space:]]/}"
  if [[ ! "$required_raw" =~ ^[A-Z][A-Z0-9_]*(,[A-Z][A-Z0-9_]*)*$ ]]; then
    echo "SNOWTHING_SSM_REQUIRED_NAMES가 없거나 형식이 잘못됐습니다(예: A_KEY,B_SECRET. 대문자, 숫자, 밑줄을 쉼표로 구분): $PARAMETER_STORE_CONFIG_PATH" >&2
    return 1
  fi
  IFS=, read -r -a required <<< "$required_raw"
  if ! command -v python3 >/dev/null 2>&1; then
    echo "Parameter Store 응답을 읽으려면 python3가 필요합니다." >&2
    return 1
  fi
  if [[ ! -f "$PROD_COMPOSE_FILE" ]]; then
    echo "compose 파일이 없습니다: $PROD_COMPOSE_FILE" >&2
    return 1
  fi

  # aws 오류문에는 계정 ID가 든 ARN이 나올 수 있어 Actions 로그로 보내지 않고 서버의 제한 경로에만 남긴다.
  install -d -m 0700 "$PROD_SECRETS_ERROR_LOG_DIR"
  error_log="$PROD_SECRETS_ERROR_LOG_DIR/parameter-store-error.log"
  : > "$error_log"
  chmod 0600 "$error_log"

  # aws CLI는 --no-paginate를 주지 않으면 10개씩 나뉜 페이지를 끝까지 읽어 한 JSON으로 합친다.
  # 응답에 NextToken이 남아 있으면 덜 읽은 것이므로 실패로 본다.
  # 이름과 값은 NUL로 구분해 받는다. 값에 공백, 따옴표, $, #, 개행이 있어도 그대로다.
  mapfile -d '' -t fields < <(
    set -o pipefail
    aws ssm get-parameters-by-path \
      --region "$region" \
      --path "$path" \
      --with-decryption \
      --output json 2> "$error_log" \
      | python3 -c '
import json, sys
try:
    document = json.loads(sys.stdin.buffer.read())
    if document.get("NextToken"):
        sys.exit(4)
    output = []
    for parameter in document["Parameters"]:
        name, value = parameter["Name"], parameter["Value"]
        if "\0" in name or "\0" in value:
            sys.exit(5)
        output.append(name)
        output.append(value)
    sys.stdout.buffer.write("".join(item + "\0" for item in output).encode("utf-8"))
except SystemExit:
    raise
except Exception:
    sys.exit(3)
' 2>> "$error_log"
  )
  status=0
  wait "$!" || status=$?

  if (( status != 0 )); then
    echo "Parameter Store 읽기 실패(종료 코드 $status). 배포를 시작하지 않습니다. 서버의 $error_log 를 확인하세요." >&2
    return 1
  fi
  rm -f "$error_log"
  if (( ${#fields[@]} == 0 || ${#fields[@]} % 2 != 0 )); then
    echo "Parameter Store 경로에서 읽은 값이 없거나 응답이 깨졌습니다. 경로와 권한을 확인하세요." >&2
    return 1
  fi

  for (( index = 0; index < ${#fields[@]}; index += 2 )); do
    name="${fields[index]}"
    value="${fields[index + 1]}"
    if [[ "$name" != "$path/"* ]]; then
      echo "경로 밖의 파라미터 이름이 응답에 있습니다. 배포를 시작하지 않습니다." >&2
      return 1
    fi
    name="${name#"$path"/}"
    # /snowthing/prod/SNOWTHING_KMA_SERVICE_KEY -> SNOWTHING_KMA_SERVICE_KEY
    if [[ ! "$name" =~ ^[A-Z][A-Z0-9_]*$ ]]; then
      echo "환경변수 이름으로 쓸 수 없는 파라미터가 있습니다(대문자, 숫자, 밑줄만): $name" >&2
      return 1
    fi
    returned["$name"]=1
    if ! prod_secrets_allowed_name "$name"; then
      # 과거 커밋으로 되돌릴 때 그 시점 compose에 없는 이름이 있을 수 있어 실패시키지 않고 건너뛴다.
      skipped+=("$name")
      continue
    fi
    names+=("$name")
    values+=("$value")
  done

  # 필수 이름은 compose 사용 여부와 상관없이 경로가 돌려준 이름 전체와 비교한다.
  for name in "${required[@]}"; do
    [[ -n "${returned[$name]:-}" ]] || missing+=("$name")
  done
  if (( ${#missing[@]} > 0 )); then
    echo "Parameter Store 경로에 필수 이름이 없습니다: ${missing[*]}. 배포를 시작하지 않습니다." >&2
    return 1
  fi

  if (( ${#names[@]} == 0 )); then
    echo "Parameter Store에 $PROD_COMPOSE_FILE 이 쓰는 이름이 하나도 없습니다. 이름을 확인하세요." >&2
    return 1
  fi

  PROD_SECRET_NAMES=("${names[@]}")
  PROD_SECRET_VALUES=("${values[@]}")
  # shellcheck disable=SC2034  # 배포 스크립트가 Parameter Store를 썼는지 볼 때 읽는다.
  PROD_SECRETS_SOURCE="$path"
  echo "Parameter Store에서 ${#names[@]}개 값을 읽었습니다(같은 이름의 prod.env 값보다 우선)."
  if (( ${#skipped[@]} > 0 )); then
    echo "compose가 쓰지 않아 건너뛴 파라미터: ${skipped[*]}" >&2
  fi
}

# 읽어 둔 값을 현재 셸에 export한다. 값이 다른 명령에 새지 않게 서브셸 안에서만 부른다.
prod_secrets_export() {
  local -
  set +x
  local index
  for (( index = 0; index < ${#PROD_SECRET_NAMES[@]}; index++ )); do
    export "${PROD_SECRET_NAMES[index]}=${PROD_SECRET_VALUES[index]}"
  done
}

# 인자: "이름=값" 한 개(없으면 빈 문자열), 그 뒤는 docker compose 하위 명령과 인자.
# prod.env, 실행 이미지 기록, Parameter Store 값을 같은 순서로 넘겨 배포, 롤백, 수동 실행이 같은 값을 쓴다.
# export는 셸 내장 명령이라 값이 다른 프로세스의 인자 목록(ps)에 나타나지 않는다.
prod_compose() {
  local override="$1"
  shift
  local env_files=(--env-file "$PROD_ENV_PATH")
  if [[ -f "$IMAGE_RECORD_PATH" ]]; then
    env_files+=(--env-file "$IMAGE_RECORD_PATH")
  fi
  (
    set +x
    prod_secrets_export
    # 대상 서비스 이미지 주소는 마지막에 넣어 어떤 값도 덮어쓰지 못하게 한다.
    if [[ -n "$override" ]]; then
      # shellcheck disable=SC2163  # "이름=값" 문자열을 그대로 export한다.
      export "$override"
    fi
    exec docker compose "${env_files[@]}" -f "$PROD_COMPOSE_FILE" "$@"
  )
}
