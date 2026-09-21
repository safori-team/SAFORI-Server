# FCM 푸시 알림 구현 TODO

> 이슈: [#115 FCM 인프라 세팅](https://github.com/safori-team/SAFORI-Server/issues/115) → [#116 이벤트 기반 알림 비즈니스 로직](https://github.com/safori-team/SAFORI-Server/issues/116)
> 브랜치: `feat/#115`
> 진행 방식: 태스크 하나 완료 → 검토 → 다음 태스크

---

## Phase 1 — FCM 인프라 세팅 (#115)

### Task 1. Firebase Admin SDK 의존성 + 초기화 설정 ✅
- [x] `build.gradle`에 `firebase-admin:9.4.3` 의존성 추가
- [x] `FirebaseConfig` 작성 (`common/config`)
  - `FIREBASE_CREDENTIALS_BASE64` (서비스 계정 키 JSON base64) 주입 — S3Config 패턴
  - 키 미설정 시 `FirebaseApp`/`FirebaseMessaging` 빈 미생성 (로컬/테스트에서 비활성)
- [x] `application.yml`에 `firebase.credentials-base64` 프로퍼티 추가
- [x] `.env.example`에 Firebase 환경변수 문서화 (base64 생성 명령 포함)
- [x] 검증: compileJava 통과, 키 없이 컨텍스트 로드 성공

**검토 포인트**: 키 없이 부트 정상 기동하는지, 키 주입 방식(파일 경로 vs base64 JSON)이 배포 환경(docker)과 맞는지

### Task 2. 디바이스 토큰 엔티티 + 저장소 ✅
- [x] `DeviceToken` 엔티티 (`domain/notification/entity`) — #116 알림 도메인도 이 패키지로 확장
  - 사용자 1:N (멀티 디바이스), 토큰 전역 unique (length 512)
  - `BaseTimeEntity` 상속, `@SuperBuilder`/`@EqualsAndHashCode(of="id")` 기존 컨벤션
  - `reassignTo(User)` — 기기 양도 시 소유자 교체
- [x] `DeviceTokenRepository` — findByToken, findAllByUser_Id, deleteByToken, deleteAllByTokenIn(무효 토큰 일괄 삭제용)
- [x] 사용자 탈퇴 시 cascade: `@OnDelete(CASCADE)` — Voice/VoiceContent 동일 패턴 (DB FK 레벨)
- [x] 검증: 컨텍스트 로드 시 `device_token` DDL 생성 확인

**검토 포인트**: 테이블/컬럼 네이밍 기존 컨벤션 일치 여부, 동일 토큰 재등록(다른 유저로 기기 양도) 시 처리

### Task 3. 토큰 등록/삭제 API ✅
- [x] `POST /v1/api/users/device-tokens` — 등록/갱신 (upsert: 동일 토큰 존재 시 소유자 교체)
- [x] `DELETE /v1/api/users/device-tokens?token=` — 삭제, 본인 소유만 + 멱등 (기존 sign-out처럼 @RequestParam)
- [x] `api/notification/{controller,service,dto}` — 기존 레이어 구조, `@UserCode`로 인증 사용자 추출
- [x] 레이어 수정: repository 접근을 `DeviceTokenDomainService`(@DomainService, @Transactional)로 이동 — UseCase는 UserAdaptor + DomainService 조합만 수행
- [x] Swagger 문서화 (@Tag/@Operation/@ApiResponse 컨벤션)
- [x] 테스트 5개 (신규 저장/upsert 소유자 교체/본인 삭제/타인 무시/멱등) — 전체 테스트 통과
- [x] 시큐리티: permitAll 목록에 없음 → JWT 보호 확인

**검토 포인트**: URL 컨벤션(`/v1/api/users/...`) 일치, 인증 사용자 컨텍스트에서 user 추출 방식 기존과 동일한지

### Task 4. 푸시 전송 포트 + FCM 어댑터 ✅
- [x] 포트: `domain/notification/port/PushNotificationSender` + `PushMessage`/`PushSendResult` record
- [x] FCM 어댑터 (`infra/fcm/FcmPushNotificationSender`)
  - `sendEachForMulticast` 500개 배치 분할 (FCM 상한)
  - UNREGISTERED/INVALID_ARGUMENT → invalidTokens 수집, UNAVAILABLE 등 일시 오류는 보존
  - 전송 실패는 예외 전파 없이 집계 + 로깅
- [x] Firebase 비활성: `ObjectProvider<FirebaseMessaging>` — 빈 없으면 전송 건너뜀 (별도 no-op 클래스 불필요)
- [x] `SendPushNotificationUseCase.execute(userId, message)` — 토큰 조회(Adaptor)→전송(Port)→무효 토큰 삭제(DomainService) 조합. #116 이벤트 리스너 진입점
- [x] 테스트 (UseCase 3 + 어댑터 4 + DomainService deleteByTokens 2, FirebaseMessaging mock) — 전체 통과

**레이어 재배치 (리뷰 반영)**
- 포트/값객체 `domain/notification/port` → `api/notification/port` (DB 밀접 아님 → domain 제외, 의존 방향 infra→api 정방향)
- 오케스트레이션 domain service → `api/notification/service/SendPushNotificationUseCase` (usecase가 service 조합)
- 토큰 조회 `DeviceTokenAdaptor`(query) 신설, 무효 토큰 삭제 `DeviceTokenDomainService.deleteByTokens`(command)
- FCM 가독성: 상수 → `common/consts/FcmStaticValues`, SDK 변환 헬퍼 → `infra/fcm/FcmMessageMapper`(util)

**검토 포인트**: 포트 시그니처가 #116 이벤트 로직에서 쓰기 충분한지 (title/body/data payload), 무효 토큰 삭제 트랜잭션 처리

### Task 5. 문서 + 배포 반영 ✅
- [x] `docs/CICD-SETUP.md`에 "푸시 알림 (Firebase FCM)" 섹션 추가 — env-repo `.env`에 `FIREBASE_CREDENTIALS_BASE64` 추가 + 키 발급/base64 생성 절차
- [x] `deploy.yml` 변경 불필요 확인 — env는 env-repo `{profile}.env` → `--env-file`로 앱 컨테이너에 주입. Sentry/Gemini 시크릿과 동일 경로
- [ ] 실기기/테스트 토큰 전송 스모크 테스트 — **Firebase 프로젝트+실토큰 필요, 배포 후 수동 확인** (env-repo에 키 세팅 후)

**검토 결과**: 시크릿 관리 = base64 env, env-repo `.env` 단일 소스. Sentry DSN/Gemini API 키와 동일 방식 → 일관적

---

## Phase 2 — 이벤트 기반 알림 비즈니스 로직 (#116)

> Phase 1 완료 + PR 머지 후 착수. 브랜치 `feat/#116` 새로 분기.

### Task 6. 알림 이벤트 추상화
- [ ] 알림 트리거 이벤트 추상 타입 정의 (`common/event` 기존 인프라 활용 검토)
- [ ] `ApplicationEventPublisher` 발행 + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 리스너
- [ ] 비동기 실행 설정 (Executor) — 기존 async 설정 있는지 확인 후 재사용/신설

**검토 포인트**: 본 트랜잭션 실패 시 알림 미발송 보장, 리스너 예외가 호출부에 전파되지 않는지

### Task 7. 이벤트별 알림 정책 (확장 포인트)
- [ ] 이벤트 타입 → 알림 메시지(title/body/deeplink data) 구성 전략 인터페이스
- [ ] 전략 구현체 등록 방식 (Spring bean 주입으로 자동 수집) — 신규 이벤트는 구현체 추가만으로 확장
- [ ] 샘플 이벤트 1개로 e2e 검증 (이벤트 확정 전 placeholder)

**검토 포인트**: 새 이벤트 추가 시 수정 범위가 전략 구현체 1개로 닫히는지 (OCP)

### Task 8. 알림 이력 (범위 확정 후)
- [ ] 프론트 요구사항 확인: 알림함 필요 여부
- [ ] 필요 시: 발송 이력 엔티티 + 알림함 조회 API
- [ ] 발송 실패 재시도 정책 결정

**검토 포인트**: 이력 저장이 발송 경로 성능에 영향 없는지

---

## 미확정 사항 (진행 중 결정)
- [ ] 알림 트리거 이벤트 목록 — 후보: 감정 분석 완료, 주간/월간 리포트 생성
- [x] Firebase 키 주입 방식 → **base64 env** 채택 (기존 시크릿 전부 env 변수 방식, 파일 마운트 없음)
- [ ] 알림함(이력 조회) 스코프 포함 여부
