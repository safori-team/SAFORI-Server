#!/usr/bin/env bash
# 대상 EC2 에서 SSM(AWS-RunShellScript, root)으로 실행되는 배포 스크립트.
# deploy.yml 이 base64 로 실어 보내고, 아래 환경변수를 export 한 뒤 실행한다.
#
# 앞단은 Cloudflare Tunnel 이다. EC2 의 cloudflared(host network)가 localhost:APP_PORT 로 넘기므로
# 앱 포트는 127.0.0.1 에만 연다. 외부에서 들어오는 입구는 cloudflared 뿐이다.
set -euo pipefail

: "${IMAGE_URI:?}" "${AWS_REGION:?}" "${APP_DIR:?}" "${CONTAINER_NAME:?}"
: "${APP_PORT:?}" "${MGMT_PORT:?}" "${DOCKER_NETWORK:?}" "${SPRING_PROFILE:?}"

ECR_REGISTRY="${IMAGE_URI%%/*}"
ENV_REPO="${APP_DIR}/env-repo"
OTEL_IMAGE="otel/opentelemetry-collector-contrib:0.155.0"
JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:MaxMetaspaceSize=256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/heapdumps -XX:+ExitOnOutOfMemoryError"

remove_container() {
  if docker ps -a --format '{{.Names}}' | grep -Fxq "$1"; then
    docker rm -f "$1"
  fi
}

# ── 1. 사전 준비 ───────────────────────────────────────────────────────────
# 실패해도 기존 컨테이너는 그대로 살아 있도록, 컨테이너를 건드리기 전에 끝낸다.
mkdir -p "${APP_DIR}"

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

aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"
docker pull "${IMAGE_URI}"
docker pull "${OTEL_IMAGE}"

cp "${ENV_REPO}/${SPRING_PROFILE}.env" "${APP_DIR}/.env"
cp "${ENV_REPO}/otel-collector-config.yaml" "${APP_DIR}/otel-collector-config.yaml"
docker network create "${DOCKER_NETWORK}" 2>/dev/null || true

# 이전 구조의 nginx 가 남아 있으면 APP_PORT 를 잡고 있어 앱이 뜨지 못한다.
remove_container nginx

# ── 2. otel-collector (트레이스 → Sentry) ──────────────────────────────────
mkdir -p "${APP_DIR}/otelcol-storage"
chown -R 10001:10001 "${APP_DIR}/otelcol-storage"
remove_container otel-collector
docker run -d --name otel-collector --restart unless-stopped --memory=192m \
  --network "${DOCKER_NETWORK}" \
  --env-file "${APP_DIR}/.env" \
  -v "${APP_DIR}/otel-collector-config.yaml:/etc/otelcol-contrib/config.yaml:ro" \
  -v "${APP_DIR}/otelcol-storage:/var/lib/otelcol/storage" \
  "${OTEL_IMAGE}" --config /etc/otelcol-contrib/config.yaml

# ── 3. 앱 ──────────────────────────────────────────────────────────────────
mkdir -p "${APP_DIR}/heapdumps"
remove_container "${CONTAINER_NAME}"
docker run -d --name "${CONTAINER_NAME}" --restart unless-stopped --memory=3g \
  --network "${DOCKER_NETWORK}" \
  --env-file "${APP_DIR}/.env" \
  -e SPRING_PROFILES_ACTIVE="${SPRING_PROFILE}" \
  -e JAVA_OPTS="${JAVA_OPTS}" \
  -v "${APP_DIR}/heapdumps:/heapdumps" \
  -p "127.0.0.1:${APP_PORT}:8080" \
  -p "127.0.0.1:${MGMT_PORT}:9082" \
  "${IMAGE_URI}"

# ── 4. 헬스체크 ────────────────────────────────────────────────────────────
for _ in $(seq 1 30); do
  if curl -fsS "http://localhost:${MGMT_PORT}/actuator/health" >/dev/null; then
    echo "Deployed ${IMAGE_URI} (${SPRING_PROFILE})"
    exit 0
  fi
  sleep 5
done

echo "Application health check failed"
docker logs "${CONTAINER_NAME}" --tail 100
exit 1
