#!/usr/bin/env bash
# 운영 서버에서 compose.prod.yml을 손으로 다룰 때 쓴다(IKA-110).
# 배포 스크립트와 같은 값(prod.env, 실행 이미지 기록, 설정돼 있으면 Parameter Store)으로 docker compose를 실행한다.
#
#   sudo AWS_REGION=ap-northeast-2 scripts/compose-prod.sh up -d            전체를 지금 값으로 다시 맞춘다
#   sudo AWS_REGION=ap-northeast-2 scripts/compose-prod.sh up -d backend    한 서비스만
#   sudo AWS_REGION=ap-northeast-2 scripts/compose-prod.sh config --quiet   값이 다 채워지는지만 확인
#
# Parameter Store 설정 파일이 없으면 AWS_REGION 없이도 되고, 아래와 같은 명령이다.
#   docker compose --env-file /etc/snowthing/prod.env --env-file /etc/snowthing/images.env -f compose.prod.yml ...
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=scripts/lib/prod-secrets.sh
source scripts/lib/prod-secrets.sh

if (( $# == 0 )); then
  echo "사용법: scripts/compose-prod.sh <docker compose 하위 명령> [인자...]" >&2
  exit 2
fi

# 하위 명령은 전역 옵션(-p X, --profile X, --env-file X, -f X 등) 뒤의 첫 인자다.
# 값을 받는 전역 옵션은 다음 인자까지 건너뛴다. --name=값 모양은 한 인자다.
subcommand=""
subcommand_index=0
index=1
while (( index <= $# )); do
  argument="${!index}"
  case "$argument" in
    -f|--file|-p|--project-name|--profile|--env-file|--project-directory|--ansi|--parallel|--progress)
      index=$((index + 2))
      continue
      ;;
    -*)
      index=$((index + 1))
      continue
      ;;
  esac
  subcommand="$argument"
  subcommand_index=$index
  break
done

# config와 convert는 비밀값이 채워진 설정 전체를 출력한다. 값이 터미널 기록이나 SSM 세션 로그에 남지 않게 막는다.
if [[ "$subcommand" == "config" || "$subcommand" == "convert" ]]; then
  quiet=false
  for argument in "${@:subcommand_index+1}"; do
    case "$argument" in
      -q|--quiet|--services|--images|--volumes|--profiles) quiet=true ;;
    esac
  done
  if [[ "$quiet" == false ]]; then
    echo "$subcommand 는 비밀값을 그대로 출력합니다. --quiet, --services, --images 중 하나와 함께 쓰세요." >&2
    exit 2
  fi
fi

# 컨테이너 환경을 통째로 출력하는 가장 흔한 실수(exec 서비스 env, exec 서비스 printenv)만 막는다.
# 이름을 붙인 printenv <이름>은 막지 않는다. sh -c 'env' 같은 우회나 docker inspect는 막지 못한다.
if [[ "$subcommand" == "exec" || "$subcommand" == "run" ]] && (( $# > subcommand_index )); then
  case "${!#}" in
    env|printenv|/usr/bin/env|/usr/bin/printenv|/bin/env|/bin/printenv)
      echo "$subcommand 로 환경변수 전체를 출력하지 않습니다. 값 비교는 printenv <이름> | sha256sum 처럼 해시로 하세요." >&2
      exit 2
      ;;
  esac
fi

prod_secrets_load "${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
prod_compose "" "$@"
