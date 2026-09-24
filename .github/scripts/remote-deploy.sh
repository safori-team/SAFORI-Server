#!/usr/bin/env bash
# 대상 EC2 에서 SSM(AWS-RunShellScript, root)으로 실행되는 배포 스크립트.
# deploy.yml 이 base64 로 실어 보내고, 아래 환경변수를 export 한 뒤 실행한다.
#
# 한 대 안에서 blue/green 으로 무중단 배포한다.
#   색깔마다 [앱 + 전용 cloudflared] 한 쌍을 띄우고, 둘 다 같은 Cloudflare Tunnel 에 replica 로 붙는다.
#   cloudflared 는 색깔 전용 네트워크(safori-<color>)에서 별칭 safori-app 으로 자기 앱만 찾는다.
#   → Tunnel 서비스 URL 은 http://safori-app:8080 (Cloudflare 대시보드)
#
#   1. 새 색깔 앱 기동 → 헬스체크 통과
#   2. 새 색깔 cloudflared 기동 → Tunnel 연결 확인 (이때부터 두 색깔이 트래픽을 나눠 받는다)
#   3. 이전 색깔 cloudflared 를 graceful 종료(진행 중 요청 마무리) → 이전 앱 종료
# 새 앱이 헬스체크에 실패하면 새 색깔만 지우고 끝낸다(이전 색깔은 계속 서비스).
set -euo pipefail

: "${IMAGE_URI:?}" "${AWS_REGION:?}" "${APP_DIR:?}" "${CONTAINER_NAME:?}"
: "${DOCKER_NETWORK:?}" "${SPRING_PROFILE:?}"

ECR_REGISTRY="${IMAGE_URI%%/*}"
ENV_REPO="${APP_DIR}/env-repo"
OTEL_IMAGE="otel/opentelemetry-collector-contrib:0.155.0"
CLOUDFLARED_IMAGE="cloudflare/cloudflared:latest"
TUNNEL_ENV="/root/cloudflared.env"
ACTIVE_FILE="${APP_DIR}/active-color"
JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:MaxMetaspaceSize=256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/heapdumps -XX:+ExitOnOutOfMemoryError"

remove_container() {
  if docker ps -a --format '{{.Names}}' | grep -Fxq "$1"; then
    docker rm -f "$1"
  fi
}

# 컨테이너의 특정 네트워크 IP (호스트에서 헬스체크용)
container_ip() {
  docker inspect -f "{{(index .NetworkSettings.Networks \"$2\").IPAddress}}" "$1"
}

wait_http_ok() {
  local url_fn="$1" tries="$2"
  for _ in $(seq 1 "$tries"); do
    if curl -fsS "$($url_fn)" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}

# ── 1. 사전 준비 ───────────────────────────────────────────────────────────
# 실패해도 기존 컨테이너는 그대로 살아 있도록, 컨테이너를 건드리기 전에 끝낸다.
mkdir -p "${APP_DIR}"

# 배포 중 표시. ASG 헬스 보고(prod)가 이 동안은 판정하지 않는다.
touch "${APP_DIR}/deploying"
trap 'rm -f "${APP_DIR}/deploying"' EXIT

if [ ! -d "${ENV_REPO}/.git" ]; then
  echo "env-repo not cloned at ${ENV_REPO} (deploy key 설정 필요)"
  exit 1
fi
git -C "${ENV_REPO}" pull --ff-only

for f in "${SPRING_PROFILE}.env" otel-collector-config.yaml; do
  if [ ! -f "${ENV_REPO}/${f}" ]; then
    echo "Missing ${ENV_REPO}/${f}"
    exit 1
  fi
done

if [ ! -s "${TUNNEL_ENV}" ]; then
  TOKEN=$(aws ssm get-parameter --region "${AWS_REGION}" --name "/safori/${SPRING_PROFILE}/cloudflared-token" \
    --with-decryption --query Parameter.Value --output text)
  (umask 077; printf 'TUNNEL_TOKEN=%s\n' "$TOKEN" > "${TUNNEL_ENV}")
  unset TOKEN
fi

aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"
docker pull "${IMAGE_URI}"
docker pull "${OTEL_IMAGE}"
docker pull "${CLOUDFLARED_IMAGE}"

cp "${ENV_REPO}/${SPRING_PROFILE}.env" "${APP_DIR}/.env"
cp "${ENV_REPO}/otel-collector-config.yaml" "${APP_DIR}/otel-collector-config.yaml"
for net in "${DOCKER_NETWORK}" safori-blue safori-green; do
  docker network create "$net" 2>/dev/null || true
done

OLD=$(cat "${ACTIVE_FILE}" 2>/dev/null || echo "")
if [ "$OLD" = "blue" ]; then NEW=green; else NEW=blue; fi
NEW_APP="${CONTAINER_NAME}-${NEW}"
NEW_TUNNEL="cloudflared-${NEW}"
echo "active=${OLD:-none} → deploying ${NEW}"

# 지난 배포가 중간에 실패해 남은 새 색깔 잔여물 정리
remove_container "${NEW_TUNNEL}"
remove_container "${NEW_APP}"

# ── 2. otel-collector (트레이스 → Sentry). 재시작 동안의 스팬 몇 개는 유실될 수 있다 ──
mkdir -p "${APP_DIR}/otelcol-storage"
chown -R 10001:10001 "${APP_DIR}/otelcol-storage"
remove_container otel-collector
docker run -d --name otel-collector --restart unless-stopped --memory=192m \
  --network "${DOCKER_NETWORK}" \
  --env-file "${APP_DIR}/.env" \
  -v "${APP_DIR}/otel-collector-config.yaml:/etc/otelcol-contrib/config.yaml:ro" \
  -v "${APP_DIR}/otelcol-storage:/var/lib/otelcol/storage" \
  "${OTEL_IMAGE}" --config /etc/otelcol-contrib/config.yaml

# ── 3. 새 색깔 앱 ──────────────────────────────────────────────────────────
# 포트는 호스트에 열지 않는다. 입구는 같은 색깔 네트워크의 cloudflared 뿐이다.
mkdir -p "${APP_DIR}/heapdumps"
docker run -d --name "${NEW_APP}" --restart unless-stopped --memory=2g \
  --network "${DOCKER_NETWORK}" \
  --env-file "${APP_DIR}/.env" \
  -e SPRING_PROFILES_ACTIVE="${SPRING_PROFILE}" \
  -e JAVA_OPTS="${JAVA_OPTS}" \
  -v "${APP_DIR}/heapdumps:/heapdumps" \
  "${IMAGE_URI}"
docker network connect --alias safori-app "safori-${NEW}" "${NEW_APP}"

app_health_url() { echo "http://$(container_ip "${NEW_APP}" "${DOCKER_NETWORK}"):9082/actuator/health"; }
if ! wait_http_ok app_health_url 30; then
  echo "New app (${NEW}) health check failed — keeping ${OLD:-none}"
  docker logs "${NEW_APP}" --tail 100
  remove_container "${NEW_APP}"
  exit 1
fi

# ── 4. 새 색깔 cloudflared → Tunnel 에 replica 로 합류 ───────────────────────
docker run -d --name "${NEW_TUNNEL}" --restart unless-stopped --memory=128m \
  --network "safori-${NEW}" \
  --env-file "${TUNNEL_ENV}" \
  "${CLOUDFLARED_IMAGE}" tunnel --no-autoupdate --metrics 0.0.0.0:2000 run

tunnel_ready_url() { echo "http://$(container_ip "${NEW_TUNNEL}" "safori-${NEW}"):2000/ready"; }
if ! wait_http_ok tunnel_ready_url 12; then
  echo "New cloudflared (${NEW}) not connected — keeping ${OLD:-none}"
  docker logs "${NEW_TUNNEL}" --tail 50
  remove_container "${NEW_TUNNEL}"
  remove_container "${NEW_APP}"
  exit 1
fi
echo "${NEW}" > "${ACTIVE_FILE}"

# ── 5. 이전 색깔 정리 ──────────────────────────────────────────────────────
# cloudflared 는 SIGTERM 을 받으면 새 요청을 끊고 진행 중 요청을 마무리한다(기본 30초).
if [ -n "$OLD" ]; then
  docker stop -t 40 "cloudflared-${OLD}" 2>/dev/null || true
  remove_container "cloudflared-${OLD}"
  docker stop -t 30 "${CONTAINER_NAME}-${OLD}" 2>/dev/null || true
  remove_container "${CONTAINER_NAME}-${OLD}"
fi

# blue/green 이전 구조의 잔여물(호스트 cloudflared, 색깔 없는 앱, nginx)
remove_container cloudflared
remove_container "${CONTAINER_NAME}"
remove_container nginx

echo "Deployed ${IMAGE_URI} (${SPRING_PROFILE}, ${NEW})"
