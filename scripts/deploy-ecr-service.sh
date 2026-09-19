#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:?service is required}"
IMAGE_URI="${2:?image URI is required}"
AWS_REGION="${3:?AWS region is required}"

case "$SERVICE" in
  backend)
    IMAGE_VARIABLE="BACKEND_IMAGE_URI"
    CONTAINER_NAME="snowthing-backend"
    HEALTH_URL="http://127.0.0.1:8080/api/v1/master/resorts"
    EXPECTED_STATUS='200'
    ;;
  frontend)
    IMAGE_VARIABLE="FRONTEND_IMAGE_URI"
    CONTAINER_NAME="snowthing-frontend"
    HEALTH_URL="http://127.0.0.1:3000"
    EXPECTED_STATUS='200|304'
    ;;
  *)
    echo "지원하지 않는 서비스입니다: $SERVICE" >&2
    exit 2
    ;;
esac

if [[ ! "$IMAGE_URI" =~ @sha256:[0-9a-f]{64}$ ]]; then
  echo "digest로 고정된 ECR 이미지 주소만 배포할 수 있습니다: $IMAGE_URI" >&2
  exit 2
fi

REGISTRY="${IMAGE_URI%%/*}"
PREVIOUS_IMAGE="$(docker inspect --format '{{.Config.Image}}' "$CONTAINER_NAME" 2>/dev/null || true)"

check_health() {
  local attempt status
  for attempt in $(seq 1 12); do
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --connect-timeout 2 --max-time 5 "$HEALTH_URL" || true)"
    if [[ "$status" =~ ^($EXPECTED_STATUS)$ ]]; then
      echo "$SERVICE 헬스체크 성공 (HTTP $status)"
      return 0
    fi
    echo "$SERVICE 시작 대기 중 ($attempt/12, HTTP $status)"
    sleep 5
  done
  return 1
}

collect_diagnostics() {
  local timestamp diagnostic_file
  timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
  mkdir -p /var/log/snowthing-deploy
  diagnostic_file="/var/log/snowthing-deploy/${SERVICE}-${timestamp}.log"

  {
    echo "timestamp_utc=$timestamp"
    echo "service=$SERVICE"
    echo "target_image=$IMAGE_URI"
    echo "=== docker ps -a ==="
    docker ps -a --filter "name=^/${CONTAINER_NAME}$" --no-trunc || true
    echo "=== docker inspect (secrets excluded) ==="
    docker inspect --format 'image={{.Config.Image}} status={{.State.Status}} running={{.State.Running}} exit_code={{.State.ExitCode}} error={{.State.Error}} started_at={{.State.StartedAt}} finished_at={{.State.FinishedAt}} restart_count={{.RestartCount}} network_mode={{.HostConfig.NetworkMode}}' "$CONTAINER_NAME" || true
    echo "=== container logs (last 100 lines) ==="
    docker logs --tail 100 --timestamps "$CONTAINER_NAME" 2>&1 || true
    echo "=== nginx error log (last 100 lines) ==="
    tail -n 100 /var/log/nginx/error.log 2>&1 || true
  } | tee "$diagnostic_file"

  echo "진단 로그 저장 위치: $diagnostic_file" >&2
}

echo "ECR 로그인 및 digest 이미지 pull"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"
docker pull "$IMAGE_URI"

echo "$SERVICE 컨테이너를 새 digest로 교체"
if env "$IMAGE_VARIABLE=$IMAGE_URI" docker compose \
  --env-file /etc/snowthing/prod.env \
  -f compose.prod.yml \
  up -d --no-build "$SERVICE" && check_health; then
  echo "$SERVICE 배포 성공: $IMAGE_URI"
  exit 0
fi

echo "$SERVICE 배포 실패" >&2
collect_diagnostics

if [[ -z "$PREVIOUS_IMAGE" ]]; then
  echo "복구할 이전 이미지가 없습니다." >&2
  exit 1
fi

echo "이전 이미지로 자동 복구: $PREVIOUS_IMAGE" >&2
env "$IMAGE_VARIABLE=$PREVIOUS_IMAGE" docker compose \
  --env-file /etc/snowthing/prod.env \
  -f compose.prod.yml \
  up -d --no-build "$SERVICE"

if check_health; then
  echo "이전 이미지 복구 성공" >&2
else
  echo "이전 이미지 복구 후에도 헬스체크 실패" >&2
fi

exit 1
