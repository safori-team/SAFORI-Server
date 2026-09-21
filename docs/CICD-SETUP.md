# CI/CD 설정 가이드

2-환경 배포 파이프라인. 브랜치별로 분리된 서버/DB에 배포한다.

| 브랜치 | GitHub Environment | Spring 프로파일 | 서버 | DB |
|---|---|---|---|---|
| `main` | `prod` | `prod` | prod EC2 | prod RDS |
| `develop` | `alpha` | `alpha` | alpha EC2 | alpha RDS |

## 워크플로

- `.github/workflows/deploy.yml` — `main`/`develop` push 시 test → ECR 이미지 push → SSM 배포
- `.github/workflows/test.yml` — 그 외 브랜치 push + PR 시 테스트만

배포 잡은 `github.ref_name` 으로 `prod`/`alpha` Environment 를 선택하고, 컨테이너에 `SPRING_PROFILES_ACTIVE` 를 주입한다.

## 환경 변수 관리 (Safori-Back-Env)

```
Safori-Back-Env/
├── alpha/.env
└── prod/.env
```

- 레포의 `.env.example` 은 **로컬 개발용 템플릿**일 뿐, 실제 alpha/prod 값은 `Safori-Back-Env` 가 소스 오브 트루스.
- 타겟 EC2 의 `${APP_DIR}/.env` 는 `Safori-Back-Env` 의 해당 환경 디렉토리에서 동기화한다. (배포 시 시크릿이 워크플로 로그에 노출되지 않도록, 타겟 서버가 `Safori-Back-Env` 를 직접 pull 해 사용하는 방식 권장.)

## GitHub Environments 설정

Settings → Environments 에서 **`prod`**, **`alpha`** 두 개 생성 후 각각 아래 값 입력.

### Variables (환경별로 다른 값)

| 키 | 설명 | 예시(alpha) |
|---|---|---|
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` |
| `ECR_REPOSITORY` | ECR 리포지토리 이름 | `safori-server` |
| `SSM_TARGET_KEY` | SSM 타겟 필터 키 | `tag:Name` |
| `SSM_TARGET_VALUE` | SSM 타겟 필터 값 (대상 EC2) | `safori-alpha` |
| `APP_DIR` | 타겟 서버의 앱 디렉토리 (`.env` 위치) | `/opt/safori` |
| `CONTAINER_NAME` | 컨테이너 이름 | `safori-server` |
| `APP_PORT` | 호스트 앱 포트 → 컨테이너 8080 | `8080` |
| `MGMT_PORT` | 호스트 actuator 포트 → 컨테이너 9082 | `9082` |
| `DOCKER_NETWORK` | docker 네트워크 이름 | `safori-net` |

### Secrets

| 키 | 설명 |
|---|---|
| `AWS_ROLE_TO_ASSUME` | GitHub OIDC 로 assume 할 IAM Role ARN (ECR push + SSM 권한) |

## 타겟 EC2 사전 준비 (prod / alpha 각각)

1. SSM Agent 설치 + IAM 인스턴스 프로파일 연결
2. Docker 설치
3. `Safori-Back-Env` 접근 수단(배포 키 등) 설정 → 해당 환경 `.env` 를 `${APP_DIR}/.env` 로 동기화
4. ECR pull 권한 (인스턴스 IAM 또는 `aws ecr get-login-password`)

## IAM Role (OIDC) 권한

- ECR: `GetAuthorizationToken`, push/pull
- SSM: `SendCommand`, `ListCommandInvocations`, `GetCommandInvocation`
- 신뢰 정책: `token.actions.githubusercontent.com`, 레포 + 환경(`prod`/`alpha`) 조건

## 모니터링 (Sentry + OpenTelemetry)

에러와 트레이스를 **하이브리드**로 수집한다.

| 신호 | 경로 | 근거 |
|---|---|---|
| 에러(Issue) | 앱 → Sentry SDK → DSN 직결 | Issue 그룹핑/스택트레이스/알림은 SDK 만 제대로 됨 |
| 트레이스(응답시간·처리량·에러율) | 앱 → OTLP → `otel-collector` 사이드카 → Sentry OTLP | collector 가 앱과 별도 프로세스라 앱이 죽어도 이미 받은 스팬 유실 안 됨. 영속 큐로 재시작에도 버퍼 보존 |

배포 시 `deploy.yml` 이 앱 컨테이너와 함께 `otel-collector` 컨테이너를 **같은 `DOCKER_NETWORK`** 에 띄운다.
앱은 `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector:4318/v1/traces` 로 스팬을 보내고,
collector 가 `x-sentry-auth` 헤더를 붙여 Sentry 로 forward 한다.

### Safori-Back-Env 에 추가할 것 (환경별)

1. **`otel-collector-config.yaml`** — 이 레포 `docker/otel-collector-config.yaml` 을 env-repo **루트**에 복사(비밀 아님, `${env:}` 치환). alpha/prod 공용.
2. 각 `${SPRING_PROFILE}.env` 에 아래 키 추가 (`.env.example` 의 "모니터링" 섹션 참고):

| 키 | 설명 | 예시 |
|---|---|---|
| `SENTRY_DSN` | Sentry 프로젝트 DSN | `https://<key>@o<org>.ingest.us.sentry.io/<proj>` |
| `SENTRY_ENVIRONMENT` | Sentry environment 태그 | `alpha` / `prod` |
| `OTEL_SDK_DISABLED` | OTel 마스터 스위치 | `false` |
| `OTEL_SERVICE_NAME` | 서비스명 | `safori-server` |
| `OTEL_PROPAGATORS` | 전파기 | `sentry` |
| `OTEL_TRACES_EXPORTER` | 익스포터 | `otlp` |
| `OTEL_LOGS_EXPORTER` / `OTEL_METRICS_EXPORTER` | 로그/메트릭 익스포터 | `none` |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | collector 주소 | `http://otel-collector:4318/v1/traces` |
| `OTEL_EXPORTER_OTLP_TRACES_PROTOCOL` | 프로토콜 | `http/protobuf` |
| `OTEL_TRACES_SAMPLER` / `OTEL_TRACES_SAMPLER_ARG` | 샘플러/비율 | `parentbased_traceidratio` / `1.0` |
| `SENTRY_OTLP_TRACES_ENDPOINT` | collector→Sentry OTLP endpoint | `https://o<org>.ingest.us.sentry.io/api/<proj>/integration/otlp/v1/traces` |
| `SENTRY_OTLP_AUTH_HEADER` | `x-sentry-auth` 헤더값 | `sentry sentry_key=<publicKey>` |

> alpha/prod 를 Sentry 프로젝트로 분리하려면 프로젝트 2개 만들어 각 `.env` 에 서로 다른 DSN/OTLP 값을 넣는다.

### 타겟 EC2 참고

- collector 는 docker hub `otel/opentelemetry-collector-contrib:0.155.0` 를 pull(공개 이미지, ECR 불필요).
- 영속 큐 디렉토리 `${APP_DIR}/otelcol-storage` 를 컨테이너 uid 10001 소유로 생성(스크립트가 처리).
- collector 는 4318(HTTP)/4317(gRPC) 를 docker network 내부에서만 노출, 호스트 포트 매핑 없음.

## 푸시 알림 (Firebase FCM)

FCM 서비스 계정 키를 base64 인코딩해 환경변수로 주입한다. 파일 마운트 없이 `--env-file` 로만 전달되며, 키가 비면 푸시가 비활성화될 뿐 서버는 정상 기동한다.

### Safori-Back-Env 에 추가할 것 (환경별)

각 `${SPRING_PROFILE}.env` 에 아래 키 추가 (`.env.example` 의 "Firebase" 섹션 참고):

| 키 | 설명 | 예시 |
|---|---|---|
| `FIREBASE_CREDENTIALS_BASE64` | 서비스 계정 키 JSON 을 base64 인코딩한 값 | `ewogICJ0eXBlIjogInNlcnZpY2VfYWNjb3VudCIsCiAg...` |

### 키 발급/생성 절차

1. Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → **새 비공개 키 생성** → `serviceAccountKey.json` 다운로드
2. base64 인코딩 (개행 제거):
   ```bash
   base64 -i serviceAccountKey.json | tr -d '\n'
   ```
3. 출력값을 env-repo 해당 환경 `.env` 의 `FIREBASE_CREDENTIALS_BASE64=` 에 붙여넣기
4. `serviceAccountKey.json` 원본은 저장소/이미지에 커밋 금지 — env-repo(비공개)의 `.env` 값으로만 관리

> alpha/prod 를 다른 Firebase 프로젝트로 분리하려면 프로젝트별 서비스 계정 키를 각 `.env` 에 넣는다. 추가 배포 스크립트 변경은 없다(기존 `--env-file` 로 앱 컨테이너에 그대로 주입).

## 현재 미포함 (추후 도입 예정)

- **datasource 설정**: JPA 미도입 상태라 `application-{alpha,prod}.yml` 에 DB 접속 설정 없음. JPA 도입 시 환경별 RDS 엔드포인트 연결 추가.
- **API 문서 배포(OpenAPI/Redocly)**: springdoc 미도입.
- **redis 등 부가 인프라**: 도입 시 docker network 연결 추가.
