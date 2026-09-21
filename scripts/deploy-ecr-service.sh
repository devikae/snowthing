#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:?service is required}"
IMAGE_URI="${2:?image URI is required}"
AWS_REGION="${3:?AWS region is required}"
CHAT_CONNECTIONS_PER_IP=5
CHAT_HANDSHAKES_PER_SECOND=3
CHAT_HANDSHAKE_BURST=6
NGINX_MANAGED_CONFIG_PATH="/etc/nginx/conf.d/snowthing-upload-limit.conf"
NGINX_MAIN_CONFIG_PATH="/etc/nginx/nginx.conf"
NGINX_CONF_D_PATH="/etc/nginx/conf.d"
NGINX_SITES_ENABLED_PATH="/etc/nginx/sites-enabled"
NGINX_WEBSOCKET_SNIPPET_PATH="/etc/nginx/snippets/snowthing-websocket.conf"
NGINX_WEBSOCKET_INCLUDE="include /etc/nginx/snippets/snowthing-websocket.conf;"
CLOUDFLARE_IPV4_RANGES=(
  "173.245.48.0/20"
  "103.21.244.0/22"
  "103.22.200.0/22"
  "103.31.4.0/22"
  "141.101.64.0/18"
  "108.162.192.0/18"
  "190.93.240.0/20"
  "188.114.96.0/20"
  "197.234.240.0/22"
  "198.41.128.0/17"
  "162.158.0.0/15"
  "104.16.0.0/13"
  "104.24.0.0/14"
  "172.64.0.0/13"
  "131.0.72.0/22"
)

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

configure_nginx_request_limits() {
  local config_path backup_path real_ip_header_exists real_ip_recursive_exists
  config_path="$NGINX_MANAGED_CONFIG_PATH"
  backup_path=""
  real_ip_header_exists=false
  real_ip_recursive_exists=false

  if nginx_directive_exists_outside_managed_file 'real_ip_header'; then
    real_ip_header_exists=true
  fi
  if nginx_directive_exists_outside_managed_file 'real_ip_recursive'; then
    real_ip_recursive_exists=true
  fi

  if [[ -f "$config_path" ]]; then
    backup_path="$(mktemp)"
    cp "$config_path" "$backup_path"
  fi

  {
    printf '%s\n' 'client_max_body_size 6m;'
    for cloudflare_range in "${CLOUDFLARE_IPV4_RANGES[@]}"; do
      printf 'set_real_ip_from %s;\n' "$cloudflare_range"
    done
    if [[ "$real_ip_header_exists" == false ]]; then
      printf '%s\n' 'real_ip_header CF-Connecting-IP;'
    fi
    if [[ "$real_ip_recursive_exists" == false ]]; then
      printf '%s\n' 'real_ip_recursive on;'
    fi
    printf '%s\n' \
      'map $uri $snowthing_chat_limit_key {' \
      '  default "";' \
      '  ~^/ws-chat $binary_remote_addr;' \
      '}' \
      'limit_conn_zone $snowthing_chat_limit_key zone=snowthing_chat_connections:10m;' \
      'limit_req_zone $snowthing_chat_limit_key zone=snowthing_chat_handshakes:10m rate='"${CHAT_HANDSHAKES_PER_SECOND}"'r/s;' \
      'limit_conn snowthing_chat_connections '"${CHAT_CONNECTIONS_PER_IP}"';' \
      'limit_conn_status 429;' \
      'limit_req zone=snowthing_chat_handshakes burst='"${CHAT_HANDSHAKE_BURST}"' nodelay;' \
      'limit_req_status 429;'
  } > "$config_path"
  if nginx -t; then
    systemctl reload nginx
    [[ -n "$backup_path" ]] && rm -f "$backup_path"
    echo "Nginx upload, trusted proxy, and live chat limits configured"
    return 0
  fi

  if [[ -n "$backup_path" ]]; then
    cp "$backup_path" "$config_path"
    rm -f "$backup_path"
  else
    rm -f "$config_path"
  fi
  nginx -t || true
  echo "Failed to configure Nginx request limits" >&2
  return 1
}

nginx_directive_exists_outside_managed_file() {
  local directive="$1" managed_filename
  managed_filename="$(basename "$NGINX_MANAGED_CONFIG_PATH")"

  grep -Eq "^[[:space:]]*${directive}[[:space:]]" "$NGINX_MAIN_CONFIG_PATH" \
    || grep -R -E -q \
      --include='*.conf' \
      --exclude="$managed_filename" \
      "^[[:space:]]*${directive}[[:space:]]" \
      "$NGINX_CONF_D_PATH" "$NGINX_SITES_ENABLED_PATH" 2>/dev/null
}

configure_nginx_websocket_proxy() {
  local candidate_path server_config_path server_backup_path snippet_backup_path rendered_config_path
  server_config_path=""
  server_backup_path=""
  snippet_backup_path=""

  while IFS= read -r candidate_path; do
    if grep -Eq '^[[:space:]]*server_name[[:space:]].*snowthing\.org' "$candidate_path" \
      && grep -Eq 'proxy_pass[[:space:]]+http://127\.0\.0\.1:3000|listen[[:space:]].*443' "$candidate_path"; then
      server_config_path="$(readlink -f "$candidate_path")"
      break
    fi
  done < <(find "$NGINX_SITES_ENABLED_PATH" "$NGINX_CONF_D_PATH" \
    -maxdepth 1 -type f -o -type l 2>/dev/null | sort)

  if [[ -z "$server_config_path" ]]; then
    echo "Unable to find the Nginx server block for snowthing.org" >&2
    return 1
  fi

  if grep -Eq '^[[:space:]]*location[[:space:]]+(/ws-chat|\^~[[:space:]]+/ws-chat)' "$server_config_path"; then
    echo "Nginx WebSocket proxy is already configured"
    return 0
  fi

  install -d -m 0755 "$(dirname "$NGINX_WEBSOCKET_SNIPPET_PATH")"
  server_backup_path="$(mktemp)"
  cp "$server_config_path" "$server_backup_path"
  if [[ -f "$NGINX_WEBSOCKET_SNIPPET_PATH" ]]; then
    snippet_backup_path="$(mktemp)"
    cp "$NGINX_WEBSOCKET_SNIPPET_PATH" "$snippet_backup_path"
  fi

  cat > "$NGINX_WEBSOCKET_SNIPPET_PATH" <<'EOF'
location /ws-chat {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
    proxy_buffering off;
}
EOF

  rendered_config_path="$(mktemp)"
  if ! awk -v include_line="    $NGINX_WEBSOCKET_INCLUDE" '
    function brace_delta(line, opened, closed) {
      opened = gsub(/\{/, "{", line)
      closed = gsub(/\}/, "}", line)
      return opened - closed
    }
    /^[[:space:]]*server[[:space:]]*\{/ && !in_server {
      in_server = 1
      depth = 0
      has_domain = 0
      serves_https_or_frontend = 0
      has_include = 0
    }
    {
      if (in_server) {
        if ($0 ~ /^[[:space:]]*server_name[[:space:]].*snowthing\.org/) has_domain = 1
        if ($0 ~ /listen[[:space:]].*443/ || $0 ~ /proxy_pass[[:space:]]+http:\/\/127\.0\.0\.1:3000/) serves_https_or_frontend = 1
        if (index($0, include_line) > 0) has_include = 1
        depth += brace_delta($0)
        if (depth == 0) {
          if (has_domain && serves_https_or_frontend && !has_include) {
            print include_line
            inserted = 1
          }
          if (has_domain && serves_https_or_frontend && has_include) {
            target_already_configured = 1
          }
          in_server = 0
        }
      }
      print
    }
    END {
      if (!inserted && !target_already_configured) exit 42
    }
  ' "$server_config_path" > "$rendered_config_path"; then
    rm -f "$rendered_config_path"
    restore_nginx_websocket_config "$server_config_path" "$server_backup_path" "$snippet_backup_path"
    echo "Unable to add the WebSocket include to the snowthing.org HTTPS server block" >&2
    return 1
  fi

  cp "$rendered_config_path" "$server_config_path"
  rm -f "$rendered_config_path"
  if nginx -t; then
    systemctl reload nginx
    rm -f "$server_backup_path"
    [[ -n "$snippet_backup_path" ]] && rm -f "$snippet_backup_path"
    echo "Nginx WebSocket proxy configured"
    return 0
  fi

  restore_nginx_websocket_config "$server_config_path" "$server_backup_path" "$snippet_backup_path"
  nginx -t || true
  echo "Failed to configure the Nginx WebSocket proxy" >&2
  return 1
}

restore_nginx_websocket_config() {
  local server_config_path="$1" server_backup_path="$2" snippet_backup_path="$3"
  cp "$server_backup_path" "$server_config_path"
  rm -f "$server_backup_path"
  if [[ -n "$snippet_backup_path" ]]; then
    cp "$snippet_backup_path" "$NGINX_WEBSOCKET_SNIPPET_PATH"
    rm -f "$snippet_backup_path"
  else
    rm -f "$NGINX_WEBSOCKET_SNIPPET_PATH"
  fi
}

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

prepare_diagnostic_directory() {
  install -d -m 0700 /var/log/snowthing-deploy
  find /var/log/snowthing-deploy -type f -name '*.log' -mtime +14 -delete
}

prepare_chat_audit_directory() {
  install -d -o 1001 -g 1001 -m 0700 /var/log/snowthing-chat
}

collect_diagnostics() {
  local timestamp diagnostic_file
  timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
  prepare_diagnostic_directory
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
  } > "$diagnostic_file" 2>&1
  chmod 0600 "$diagnostic_file"

  echo "Deployment diagnostics saved on EC2: $diagnostic_file" >&2
  echo "service=$SERVICE container=$CONTAINER_NAME target_image=$IMAGE_URI" >&2
  docker inspect --format 'status={{.State.Status}} running={{.State.Running}} exit_code={{.State.ExitCode}} restart_count={{.RestartCount}}' "$CONTAINER_NAME" 2>/dev/null >&2 || true
}

echo "ECR 로그인 및 digest 이미지 pull"
prepare_diagnostic_directory
if [[ "$SERVICE" == "backend" ]]; then
  configure_nginx_request_limits
  configure_nginx_websocket_proxy
  prepare_chat_audit_directory
fi
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
