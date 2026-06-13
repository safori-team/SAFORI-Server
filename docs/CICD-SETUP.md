# CI/CD 설정 가이드

2-환경 배포 파이프라인. 브랜치별로 분리된 서버/DB에 배포한다.

| 브랜치 | GitHub Environment | Spring 프로파일 | 서버 | DB |
|---|---|---|---|---|
| `main` | `prod` | `prod` | prod EC2 | prod RDS |
| `develop` | `alpha` | `alpha` | alpha EC2 (별도 인스턴스) | alpha RDS |

## 워크플로

- `.github/workflows/deploy.yml` — `main`/`develop` push 시 test → ECR 이미지 push → SSM 배포
- `.github/workflows/test.yml` — 그 외 브랜치 push + PR 시 테스트만

배포 잡은 `github.ref_name` 으로 `prod`/`alpha` Environment 를 선택하고, 컨테이너에 `SPRING_PROFILES_ACTIVE` 를 주입한다.

## 환경 변수 관리 (Safori-Back-Env)

런타임 환경 변수(`.env`)는 조직 하위 **`Safori-Back-Env`** private 레포에서 단일 소스로 관리한다. alpha / prod 를 디렉토리로 분리한다.

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

## 현재 미포함 (추후 도입 예정)

- **datasource 설정**: JPA 미도입 상태라 `application-{alpha,prod}.yml` 에 DB 접속 설정 없음. JPA 도입 시 환경별 RDS 엔드포인트 연결 추가.
- **API 문서 배포(OpenAPI/Redocly)**: springdoc 미도입.
- **redis 등 부가 인프라**: 도입 시 docker network 연결 추가.
