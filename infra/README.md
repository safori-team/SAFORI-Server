# SAFORI 인프라 (Terraform)

새 AWS 계정(`ap-northeast-2`)의 SAFORI 인프라. 실행은 **AWS CloudShell** 에서 한다(로컬 설치 불필요).

> ⚠️ public 레포다. `*.tfstate`, 비밀번호, 계정 ID 를 커밋하지 않는다.
> state 는 새 계정 S3 에만 두고, 버킷 이름은 `-backend-config` 로 주입한다.

## 구성

```
infra/
├── shared/        공용: VPC, RDS MySQL 1대, ECR, API 문서 버킷, GitHub OIDC
├── modules/env/   환경 1개: EC2, SQS, S3, Lambda 껍데기, 배포 role, RDS 접근 규칙
├── envs/
│   ├── alpha/     EC2 1대 → 스키마 safori_alpha (평소 정지)
│   └── prod/      EC2 1대 → 스키마 safori_prod
└── edge/          Cloudflare: 파킹(점검) Worker + KV + 라우트 + 헬스 Cron (환경별)
```

| 흐름 | 경로 |
|---|---|
| 요청 | 사용자 → Cloudflare(HTTPS) → 파킹 Worker ═ Tunnel ═ EC2 의 `cloudflared` → `safori-app:8080`(앱, blue/green) |
| 인바운드 | **없음.** `cloudflared` 가 Cloudflare 로 먼저 연결한다 |
| DB | EC2 → 공용 RDS `3306` (private subnet). 환경은 스키마 + DB 사용자로 분리 |
| 접속 | SSH 없음. SSM Session Manager |

Tunnel 은 환경마다 1개(alpha / prod). 인스턴스를 늘리면 같은 토큰의 `cloudflared` 가 replica 로 붙는다.
DNS 는 Tunnel 을 가리키므로 인스턴스 IP 가 바뀌어도(정지·교체) 그대로다.
로드밸런서·EIP·ACM·Redis 없음.

## 테라폼이 하지 않는 것 (수동)

- DB 스키마·사용자 생성 (아래 절차)
- Cloudflare Tunnel 생성, 토큰 SSM 등록, 퍼블릭 호스트네임 설정 (아래 절차). 파킹 Worker 는 `edge/` 가 관리
- env-repo deploy key 등록, `Safori-Back-Env` 값 교체
- GitHub Environments(`alpha`/`prod`) vars/secrets 교체
- 옛 계정 S3 객체 복사(필요 시)
- Lambda 코드/이미지 배포, 분석 함수 환경변수(`GEMINI_API_KEY`)

---

## 1. CloudShell 준비 (새 계정, 최초 1회)

### Terraform 설치

```bash
V=$(curl -s https://checkpoint-api.hashicorp.com/v1/check/terraform | jq -r .current_version)
A=$(uname -m | sed 's/x86_64/amd64/; s/aarch64/arm64/')
mkdir -p ~/bin && curl -fsSL -o /tmp/tf.zip "https://releases.hashicorp.com/terraform/${V}/terraform_${V}_linux_${A}.zip" \
  && unzip -o /tmp/tf.zip terraform -d ~/bin && rm /tmp/tf.zip
grep -q 'HOME/bin' ~/.bashrc || echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc
export PATH="$HOME/bin:$PATH"; terraform -version
```

### state 버킷 생성

```bash
ACC=$(aws sts get-caller-identity --query Account --output text); B="safori-tfstate-${ACC}"
aws s3api create-bucket --bucket "$B" --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2
aws s3api put-bucket-versioning --bucket "$B" --versioning-configuration Status=Enabled
aws s3api put-public-access-block --bucket "$B" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
echo "state bucket: $B"
```

### 코드 받기

```bash
git clone -b feat/136-infra https://github.com/safori-team/SAFORI-Server.git ~/safori
# 갱신: git -C ~/safori pull
```

이후 명령에서 공통으로 쓰는 값. **CloudShell 을 서울이 아닌 리전에서 열었으면 `aws` CLI 명령이 그 리전으로 간다**
(Terraform 은 코드에 리전이 고정돼 있어 무관). 리전을 반드시 고정한다:

```bash
grep -q 'AWS_DEFAULT_REGION=ap-northeast-2' ~/.bashrc || echo 'export AWS_REGION=ap-northeast-2 AWS_DEFAULT_REGION=ap-northeast-2' >> ~/.bashrc
export AWS_REGION=ap-northeast-2 AWS_DEFAULT_REGION=ap-northeast-2
export PATH="$HOME/bin:$PATH" TF_PLUGIN_CACHE_DIR=/tmp/tf-plugin-cache; mkdir -p /tmp/tf-plugin-cache
TFSTATE="safori-tfstate-$(aws sts get-caller-identity --query Account --output text)"
```

CloudShell 홈(1GB)에는 AWS provider 를 여러 벌 둘 공간이 없어서 provider 캐시를 `/tmp` 에 둔다.

## 2. shared

```bash
cd ~/safori/infra/shared
terraform init -backend-config="bucket=${TFSTATE}"
terraform apply
```

RDS 생성에 10분 안팎 걸린다.

## 3. Cloudflare Tunnel 토큰 등록 (환경별, EC2 만들기 전에)

Cloudflare 대시보드 → Zero Trust → Networks → Tunnels 에서 환경별 Tunnel(`safori-alpha`, `safori-prod`)을 만들고 토큰을 복사한다.
기존 alpha Tunnel 을 그대로 쓰면 그 토큰을 넣고, 옛 서버의 `cloudflared` 는 멈춘다.

```bash
E=alpha   # prod 도 반복
read -rs TOKEN   # 토큰 붙여넣기 (화면·히스토리에 남지 않음)
aws ssm put-parameter --name "/safori/${E}/cloudflared-token" --type SecureString --value "$TOKEN"
unset TOKEN
```

Tunnel 의 **Public Hostname** 에 서비스 도메인 → `http://safori-app:8080` 을 등록한다
(`terraform output` 의 `tunnel_service_url`).

## 4. env-repo deploy key 등록 (환경별, EC2 만들기 전에)

EC2 가 부팅 때 `Safori-Back-Env` 를 clone 하는 데 쓴다. 공개키는 GitHub `Safori-Back-Env` → Settings → Deploy keys 에 read-only 로, 비공개키는 SSM 에 넣는다.

```bash
E=alpha   # prod 도 반복
ssh-keygen -t ed25519 -N "" -C "safori-${E}-env-repo" -f "/tmp/${E}-env-key"
cat "/tmp/${E}-env-key.pub"   # ← GitHub Deploy key 에 등록
aws ssm put-parameter --name "/safori/${E}/env-repo-deploy-key" --type SecureString \
  --value "file:///tmp/${E}-env-key"
rm -f "/tmp/${E}-env-key" "/tmp/${E}-env-key.pub"
```

## 5. alpha / prod

```bash
cd ~/safori/infra/envs/alpha     # prod 는 envs/prod
terraform init -backend-config="bucket=${TFSTATE}"
terraform apply
terraform output -json safori | jq
```

output:

- `tunnel_service_url` → Tunnel Public Hostname 의 서비스 URL
- `github_environment` → GitHub Environment 의 vars/secrets
- `env_file_values` → `Safori-Back-Env` 의 `<profile>.env` 교체 값

## 5-1. edge (파킹 Worker)

서버가 꺼져 있거나 점검 중일 때 웹·앱이 받을 응답을 Cloudflare Worker 가 준다. 상태·사유는 서버 DB 가 아니라
Workers KV 에 있으므로 서버 없이도 등록·해제된다. 동작은 아래 "파킹(점검)" 참고.

**사전 준비 (환경별 1회)**

1. 운영자 키 SSM 등록 — 서버의 `operator.api-key` 와 다른 값으로 만든다.

   ```bash
   E=alpha   # prod 도 반복
   aws ssm put-parameter --name "/safori/${E}/parking-operator-key" --type SecureString \
     --value "$(openssl rand -base64 32)"
   ```

2. Tunnel 퍼블릭 호스트네임에 헬스 경로 규칙 추가 — "경로 제한" 표의 1번 위에 둔다.
   액추에이터는 `show-details: never` 라 `{"status":"UP"}` 만 나간다.

   | 호스트 | 경로(정규식) | 서비스 |
   |---|---|---|
   | 환경 도메인 | `^/actuator/health$` | `http://safori-app:9082` |

3. Cloudflare API 토큰 — 권한 `Account › Workers Scripts: Edit`, `Account › Workers KV Storage: Edit`,
   `Zone › Workers Routes: Edit`.

```bash
cd ~/safori/infra/edge
cp terraform.tfvars.example terraform.tfvars   # 도메인 채우기 (커밋 안 됨)
export CLOUDFLARE_API_TOKEN=...                 # 위 토큰
export TF_VAR_cloudflare_account_id=...         # 대시보드 → 계정 홈 오른쪽
export TF_VAR_cloudflare_zone_id=...            # 대시보드 → 도메인 개요 오른쪽
terraform init -backend-config="bucket=${TFSTATE}"
terraform apply
```

apply 후 대시보드 → Workers Routes 에서 라우트의 **요청 한도 실패 모드가 fail open** 인지 확인한다.
무료 플랜 Worker 요청 한도(하루 10만)를 넘었을 때 Worker 를 건너뛰고 원본으로 보내 API 가 멈추지 않게 한다.

### GitHub Actions로 edge 배포

`infra/edge/**` 또는 `.github/workflows/edge.yml`을 수정하면 PR에서 Worker 테스트, Terraform 포맷·스키마 검증을 실행한다.
`main`에 머지하면 **Deploy parking Worker**가 `infra/edge`에서 plan을 만들고 같은 plan을 적용한다.
재실행은 Actions → Deploy parking Worker → Run workflow에서 `main`을 선택한다.
로컬 Worker 테스트는 `cd infra/edge/worker && npm test`로 실행한다.

처음 사용하기 전에 GitHub Environment `edge`를 만들고 배포 브랜치를 `main`으로 지정한다.
사람의 승인을 거쳐 배포하려면 이 Environment에 Required reviewers를 설정한다.
Environment secrets에는 다음 값을 등록한다.

| Secret | 값 |
|---|---|
| `AWS_ROLE_TO_ASSUME` | edge 배포용 AWS OIDC 역할 ARN |
| `TF_STATE_BUCKET` | 기존 `edge/terraform.tfstate`가 있는 S3 버킷(신규 배포는 준비한 state 버킷) |
| `CLOUDFLARE_API_TOKEN` | 위 사전 준비에서 만든 배포 토큰 |
| `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_ZONE_ID` | 대상 Cloudflare 계정과 zone |
| `EDGE_ENVIRONMENTS_JSON` | terraform.tfvars의 `environments` 맵을 JSON 객체로 입력. 예: `{"alpha":{"hostname":"alpha-api.example.com","allowed_origins":["https://alpha.example.com"]}}` |

alpha와 prod는 state를 공유한다. 이미 관리하는 환경을 모두 JSON에 포함하고 prod를 추가할 때는 alpha 항목도 유지한다.
환경 누락이나 리소스 교체로 삭제가 계획되면 Action이 적용 전에 중단한다. 의도한 삭제·교체는 위의 수동 Terraform 절차에서 계획을 검토한다.

OIDC 역할은 GitHub OIDC 공급자에 대해 `sts:AssumeRoleWithWebIdentity`를 허용하고,
`aud=sts.amazonaws.com`, `sub=repo:safori-team/SAFORI-Server:environment:edge`로 신뢰 범위를 제한한다.
state 버킷의 `s3:ListBucket`, `edge/terraform.tfstate`의 `s3:GetObject`·`s3:PutObject`,
`edge/terraform.tfstate.tflock`의 `s3:GetObject`·`s3:PutObject`·`s3:DeleteObject`,
대상 환경별 `/safori/<env>/parking-operator-key`의 `ssm:GetParameter` 권한이 필요하다.
고객 관리형 KMS 키를 사용하면 해당 SSM 키의 복호화와 S3 state 암복호화에 필요한 권한도 부여한다.

## 6. DB 스키마·사용자 생성 (최초 1회)

앱은 환경별 사용자로 접속하고, 각 사용자는 자기 스키마 권한만 가진다(alpha 에서 prod 데이터를 건드릴 수 없게).

1. 마스터 비밀번호 확인: Secrets Manager 콘솔 → `shared` output `rds_master_secret_arn` 의 비밀 → 값 검색
2. 앱 사용자 비밀번호 생성(CloudShell): `openssl rand -base64 24 | tr -d '/+='` 를 환경별로 하나씩
3. alpha EC2 에 Session Manager 로 접속 후:

```bash
sudo docker run -it --rm mysql:8.4 mysql -h <rds_endpoint> -u admin -p
```

```sql
CREATE DATABASE safori_alpha CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE safori_prod  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'safori_alpha'@'%' IDENTIFIED BY '<alpha 비밀번호>';
CREATE USER 'safori_prod'@'%'  IDENTIFIED BY '<prod 비밀번호>';

GRANT ALL PRIVILEGES ON safori_alpha.* TO 'safori_alpha'@'%';
GRANT ALL PRIVILEGES ON safori_prod.*  TO 'safori_prod'@'%';
```

4. `Safori-Back-Env` 의 `alpha.env` / `prod.env` 에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 반영

테이블은 앱 기동 시 `ddl-auto: update` 가 만든다.

---

## 참고

### 수동 clone (deploy key 를 EC2 생성 후 등록한 경우)

Session Manager 로 접속해서:

```bash
sudo -i
E=alpha
aws ssm get-parameter --name "/safori/${E}/env-repo-deploy-key" --with-decryption \
  --query Parameter.Value --output text > /root/.ssh/env-repo-key && chmod 600 /root/.ssh/env-repo-key
ssh-keyscan github.com >> /root/.ssh/known_hosts
GIT_SSH_COMMAND="ssh -i /root/.ssh/env-repo-key -o IdentitiesOnly=yes" \
  git clone git@github.com:safori-team/Safori-Back-Env.git /opt/safori/env-repo
git -C /opt/safori/env-repo config core.sshCommand "ssh -i /root/.ssh/env-repo-key -o IdentitiesOnly=yes"
```

### Lambda

- 오케스트레이터는 항상 실패하는 placeholder 코드로 만들어지고, 요청 큐 트리거는 꺼져 있다.
  실코드 배포 후 모듈 변수 `emotion_pipeline_enabled = true` 로 켠다.
- 분석 함수는 이미지가 있어야 만들 수 있다. `safori-emotion-native` 에 push 후
  `-var emotion_analysis_image_uri=<이미지 URI>` 로 apply.
  - prod 는 `envs/prod/main.tf` 에 이미지 digest 를 고정해 두어 `-var` 없이 apply 한다
    (빈 값으로 apply 하면 함수가 삭제된다). 다른 이미지로 새로 만들 때만 `-var emotion_analysis_image_digest=sha256:...`.
- 코드·이미지·환경변수는 테라폼이 되돌리지 않는다(`ignore_changes`).

### Cloudflare Tunnel

- Tunnel 구간은 암호화되어 오리진 인증서·SSL 모드 설정이 필요 없다. `cloudflared` 는 같은 색깔 네트워크의 `safori-app:8080`(앱)으로 평문 전달.
  앞단 nginx 는 없고 앱 포트를 호스트에 열지 않는다. 배포는 blue/green (`.github/scripts/remote-deploy.sh`).
- 원 클라이언트 IP 는 `CF-Connecting-IP` / `X-Forwarded-For` 헤더로 온다.
  배포 후 Swagger 의 서버 URL 이 `https` 로 나오는지 확인한다(앱은 `forward-headers-strategy: framework`).

#### 경로 제한 (옛 nginx 역할)

Tunnel → Public Hostname 에 경로 규칙을 위에서부터 매칭 순서대로 둔다. 판정은 EC2 의 `cloudflared` 가 한다.

| 순서 | 호스트 | 경로(정규식) | 서비스 |
|---|---|---|---|
| 1 | prod 도메인 | `^/v1/api/dev/.*` | `http_status:404` |
| 2 | 환경 도메인 | `^/v1/api/.*` | `http://safori-app:8080` |
| 3 | alpha 도메인만 | `^/(swagger-ui.*\|v3/api-docs.*)` | `http://safori-app:8080` |
| 4 | (catch-all) | — | `http_status:404` |

액추에이터(9082)는 Tunnel 에 연결하지 않으므로 규칙과 무관하게 외부에서 닿지 않는다.

#### blue/green 배포

- 배포 스크립트가 색깔마다 [앱 `safori-server-<color>` + `cloudflared-<color>`] 한 쌍을 띄운다.
  `cloudflared` 는 색깔 전용 네트워크 `safori-<color>` 에서 별칭 `safori-app` 으로 자기 앱만 찾는다.
- 새 색깔 앱 헬스체크 통과 → 새 `cloudflared` 가 Tunnel 에 replica 로 합류 → 이전 `cloudflared` graceful 종료 → 이전 앱 종료.
  새 앱이 실패하면 새 색깔만 지우고 이전 색깔이 계속 서비스한다.
- 현재 색깔은 `/opt/safori/active-color`. 앱 메모리 상한 2GB(두 색깔이 잠깐 겹쳐도 4GB 안).
- 인스턴스를 늘리면 같은 토큰으로 replica 가 더 붙는다(장애 시 넘김). 균등 분산이 필요하면 Cloudflare Load Balancing.

### 파킹(점검)

```
사용자 → Cloudflare → 파킹 Worker ─┬─ 수동 파킹 ON  → 503 + 운영자 사유 (원본에 안 감)
                                    ├─ 원본 정상     → 원본 응답 그대로
                                    └─ 원본 실패     → 503 + 기본 문구 (자동 파킹)
Cron(1분) → https://<호스트>/actuator/health → 상태가 바뀔 때만 KV(auto) 에 기록
```

| 요청 | 설명 |
|---|---|
| `GET /v1/api/parking` | 누구나. 서버와 무관하게 Worker 가 답한다. `result.parking`, `type`(`MANUAL`/`AUTO`), `reason`, `startAt`, `endAt`, `scheduled`(예약 점검) |
| `PUT /v1/api/parking` | `X-Parking-Operator-Key` 필요. 본문 `{"reason": "...", "startAt"?: ISO-8601, "endAt"?: ISO-8601}`. `startAt` 이 미래면 예약 |
| `DELETE /v1/api/parking` | `X-Parking-Operator-Key` 필요. 수동 파킹 해제 |

- **파킹 응답**: HTTP `503`, 헤더 `X-Safori-Parking: MANUAL|AUTO`, `Retry-After`(초),
  본문은 서버와 같은 `{"isSuccess": false, "code": 503, "message": 사유, "result": {...}}`.
  웹·앱은 공통 인터셉터에서 이 헤더를 보고 파킹 화면을 띄우고, 앱 시작·복귀 때 `GET /v1/api/parking` 을 부른다.
- **자동 파킹 판정**: 원본이 `502/503/504/520~527/530` 을 주거나 연결이 안 되면. 앱의 4xx·500 은 그대로 둔다.
- **운영자 우회**: 수동 파킹 중에도 유효한 `X-Parking-Operator-Key` 를 보내면 Worker 가 이 헤더를 제거한 뒤 원본으로 전달한다.
  서버 운영자 API를 호출할 때는 서버용 `X-Operator-Key` 도 함께 보낸다.
- **반영 지연**: KV 는 다른 지역까지 최대 약 60초, Worker 는 isolate 마다 15초 캐시. 등록·해제 후 1분 정도 걸릴 수 있다.
- **alpha** 는 평소 정지라 항상 자동 파킹으로 보인다(정상).
- 예시

  ```bash
  H=api.example.com; K=$(aws ssm get-parameter --name /safori/prod/parking-operator-key --with-decryption --query Parameter.Value --output text)
  curl -X PUT "https://${H}/v1/api/parking" -H "X-Parking-Operator-Key: ${K}" -H 'Content-Type: application/json' \
    -d '{"reason":"DB 점검으로 02:00~03:00 이용이 제한됩니다.","endAt":"2026-11-20T03:00:00+09:00"}'
  curl -X DELETE "https://${H}/v1/api/parking" -H "X-Parking-Operator-Key: ${K}"
  ```

### prod 자동 복구 (ASG)

prod 는 `auto_recovery = true` 로 ASG(최소·최대 1)가 인스턴스를 관리한다.

- 인스턴스의 systemd 타이머(`safori-health.timer`)가 30초마다 활성 색깔 앱의 `/actuator/health` 를 확인하고,
  10번 연속(약 5분) 실패하면 자기 인스턴스를 ASG 에 Unhealthy 로 보고 → ASG 가 교체한다.
  앱 컨테이너가 죽는 정도는 Docker `--restart` 가 먼저 살린다. ASG 교체는 그래도 안 될 때의 마지막 수단.
- 판정하지 않는 경우: 부팅 후 15분, 배포 중(`/opt/safori/deploying`), 아직 배포된 적 없음(`/opt/safori/no-release`).
- `deploy.yml` 은 배포에 성공할 때마다 이미지와 배포 스크립트를 SSM(`/safori/<env>/deploy/image`, `/deploy/script`)에 기록한다.
  ASG 가 띄운 새 인스턴스는 부팅 때 이 값으로 마지막 성공 배포를 다시 띄운다.
  배포 스크립트는 gzip+base64 로 Standard 파라미터(4KB) 에 넣는다 — 스크립트가 커져 4KB 를 넘으면 Advanced 로 바꿔야 한다.
- **출시 전**: `auto_recovery_suspended = true` 로 ASG 헬스체크·교체를 멈춰 둔다. 이때는 인스턴스를 정지/시작해도 교체되지 않는다.
  출시할 때 `false` 로 바꿔 apply. (멈춘 동안 Unhealthy 로 보고된 인스턴스는 재개 직후 교체될 수 있다.)
- 로그: `journalctl -u safori-health --since -10min`

### alpha 켜고 끄기

정지 중에는 EC2·public IP 과금이 없다(EBS 만). 켜면 `cloudflared` 가 다시 Tunnel 에 붙으므로 DNS 는 그대로 둔다.

```bash
ID=$(aws ec2 describe-instances --filters Name=tag:Name,Values=safori-alpha Name=instance-state-name,Values=stopped,running \
  --query 'Reservations[].Instances[].InstanceId' --output text)
aws ec2 start-instances --instance-ids "$ID"   # 끌 때는 stop-instances
```

### RDS 공유의 한계

- alpha 의 무거운 쿼리가 prod 성능에 영향을 준다.
- 유지보수 창·재시작이 두 환경에 동시에 걸린다.
- 스냅샷 복원은 인스턴스 단위. 스키마 하나만 되돌리려면 `mysqldump` 로 한다.
