#!/bin/bash
# ASG 자동 복구용 앱 헬스 자가 보고 (systemd 타이머가 30초마다 실행).
# 활성 색깔 앱의 /actuator/health 가 연속 FAIL_LIMIT 번 실패하면 이 인스턴스를 ASG 에 Unhealthy 로 보고한다.
# → ASG 가 인스턴스를 교체하고, 새 인스턴스는 부팅 때 마지막 성공 배포를 다시 띄운다.
#
# 판정하지 않는 경우 (교체가 무한 반복되지 않도록)
#   - 부팅 후 GRACE_SEC 이내 (Docker 설치·이미지 pull·JVM 기동)
#   - 배포 중 (remote-deploy.sh 가 만든 deploying 파일, 30분 넘으면 무시)
#   - 아직 한 번도 배포된 적 없음 (no-release 파일)
set -uo pipefail

: "${APP_DIR:?}" "${AWS_REGION:?}" "${CONTAINER_NAME:?}" "${DOCKER_NETWORK:?}"
FAIL_LIMIT=10
GRACE_SEC=900
STATE=/run/safori-health-fails

uptime_sec=$(cut -d. -f1 /proc/uptime)
[ "$uptime_sec" -lt "$GRACE_SEC" ] && exit 0

if [ -f "${APP_DIR}/deploying" ] && [ $(( $(date +%s) - $(stat -c %Y "${APP_DIR}/deploying") )) -lt 1800 ]; then
  exit 0
fi

color=$(cat "${APP_DIR}/active-color" 2>/dev/null || true)
if [ -z "$color" ] && [ -f "${APP_DIR}/no-release" ]; then
  exit 0
fi

ip=""
if [ -n "$color" ]; then
  ip=$(docker inspect -f "{{(index .NetworkSettings.Networks \"${DOCKER_NETWORK}\").IPAddress}}" "${CONTAINER_NAME}-${color}" 2>/dev/null || true)
fi
if [ -n "$ip" ] && curl -fsS --max-time 5 "http://${ip}:9082/actuator/health" >/dev/null 2>&1; then
  echo 0 > "$STATE"
  exit 0
fi

fails=$(( $(cat "$STATE" 2>/dev/null || echo 0) + 1 ))
echo "$fails" > "$STATE"
echo "health check failed (${fails}/${FAIL_LIMIT}, color=${color:-none})"
[ "$fails" -lt "$FAIL_LIMIT" ] && exit 0

token=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
instance_id=$(curl -fsS -H "X-aws-ec2-metadata-token: ${token}" http://169.254.169.254/latest/meta-data/instance-id)
echo "reporting ${instance_id} Unhealthy to ASG"
aws autoscaling set-instance-health --region "${AWS_REGION}" \
  --instance-id "${instance_id}" --health-status Unhealthy --no-should-respect-grace-period
