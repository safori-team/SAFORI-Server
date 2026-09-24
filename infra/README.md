# SAFORI 인프라 (Terraform)

새 AWS 계정(`ap-northeast-2`)의 SAFORI 인프라. 실행은 **AWS CloudShell** 에서 한다(로컬 설치 불필요).

> ⚠️ public 레포다. `*.tfstate`, 비밀번호, 계정 ID 를 커밋하지 않는다.
> state 는 새 계정 S3 에만 두고, 버킷 이름은 `-backend-config` 로 주입한다.

## 구성

```
infra/
├── shared/        공용: VPC, RDS MySQL 1대, ECR, API 문서 버킷, GitHub OIDC
├── modules/env/   환경 1개: EC2(+cloudflared), SQS, S3, Lambda 껍데기, 배포 role, RDS 접근 규칙
└── envs/
    ├── alpha/     EC2 1대 → 스키마 safori_alpha (평소 정지)
    └── prod/      EC2 1대 → 스키마 safori_prod
```

| 흐름 | 경로 |
|---|---|
| 요청 | 사용자 → Cloudflare(HTTPS) ═ Tunnel ═ EC2 의 `cloudflared` → `127.0.0.1:8080`(앱) |
| 인바운드 | **없음.** `cloudflared` 가 Cloudflare 로 먼저 연결한다 |
| DB | EC2 → 공용 RDS `3306` (private subnet). 환경은 스키마 + DB 사용자로 분리 |
| 접속 | SSH 없음. SSM Session Manager |

Tunnel 은 환경마다 1개(alpha / prod). 인스턴스를 늘리면 같은 토큰의 `cloudflared` 가 replica 로 붙는다.
DNS 는 Tunnel 을 가리키므로 인스턴스 IP 가 바뀌어도(정지·교체) 그대로다.
로드밸런서·EIP·ACM·Redis 없음.

## 테라폼이 하지 않는 것 (수동)

- DB 스키마·사용자 생성 (아래 절차)
- Cloudflare Tunnel 생성, 토큰 SSM 등록, 퍼블릭 호스트네임 설정 (아래 절차)
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

Tunnel 의 **Public Hostname** 에 서비스 도메인 → `http://localhost:8080` 을 등록한다
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
- 코드·이미지·환경변수는 테라폼이 되돌리지 않는다(`ignore_changes`).

### Cloudflare Tunnel

- Tunnel 구간은 암호화되어 오리진 인증서·SSL 모드 설정이 필요 없다. `cloudflared` 는 `127.0.0.1:8080`(앱)으로 평문 전달.
  앞단 nginx 는 없다. 앱 포트는 `127.0.0.1` 에만 바인딩된다(`.github/scripts/remote-deploy.sh`).
- 원 클라이언트 IP 는 `CF-Connecting-IP` / `X-Forwarded-For` 헤더로 온다.
  배포 후 Swagger 의 서버 URL 이 `https` 로 나오는지 확인한다(앱은 `forward-headers-strategy: framework`).

#### 경로 제한 (옛 nginx 역할)

Tunnel → Public Hostname 에 경로 규칙을 위에서부터 매칭 순서대로 둔다. 판정은 EC2 의 `cloudflared` 가 한다.

| 순서 | 호스트 | 경로(정규식) | 서비스 |
|---|---|---|---|
| 1 | prod 도메인 | `^/v1/api/dev/.*` | `http_status:404` |
| 2 | 환경 도메인 | `^/v1/api/.*` | `http://localhost:8080` |
| 3 | alpha 도메인만 | `^/(swagger-ui.*\|v3/api-docs.*)` | `http://localhost:8080` |
| 4 | (catch-all) | — | `http_status:404` |

액추에이터(9082)는 Tunnel 에 연결하지 않으므로 규칙과 무관하게 외부에서 닿지 않는다.
- `cloudflared` 는 부팅 시 user-data 가 띄운다(`--restart unless-stopped`). 앱 배포와 독립적이다.
  토큰을 나중에 등록했다면 Session Manager 로 접속해 user-data 의 cloudflared 블록을 수동 실행한다.
- 인스턴스를 늘리면 같은 토큰으로 replica 가 붙는다(장애 시 넘김). 균등 분산이 필요하면 Cloudflare Load Balancing.

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
