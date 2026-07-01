# [Refactor][Backend] 회원 성별·별명, 1일 1일기 제약, 감정 분석 응답 구조 개선, 신고 재분석

## 개요
SAFORI 스펙에 맞춰 회원 정보(성별·별명)를 추가하고, 1일 1마음일기 작성 제약을 도입하며, 감정 분석 결과 응답을 대감정/세부감정 2배열 구조로 개선한다.

## 변경 사항

### 1. 회원 성별(필수)·별명(선택)
- `User` 엔티티에 `gender`(MALE/FEMALE) + `nickname` 추가
- 회원가입 시 성별 필수·별명 선택 저장
- 내 정보 조회 응답에 성별·별명 포함

### 2. 1일 1마음일기 제약
- 같은 날 작성한 일기가 있으면 등록 거부 (삭제 후 같은 날 재작성은 허용)
- `VOICE_ALREADY_EXISTS_TODAY`(4154) 추가

### 3. 감정 분석 결과 응답 구조 개선
- 기존 단일 `breakdown`(세부 감정 전체 나열) → **대감정/세부감정 2배열로 분리**
- `majorEmotions`: 6대 감정 비율 (composite 분포 기반, 합 ≈ 100)
- `subEmotions`: 세부 감정 상위 6개 비율 (전체 세부 감정 대비) + 탐색 질문

### 4. 감정 신고 시 재분석 (결과 덮어쓰기)
- 신고 직후 정정 감정·사유를 반영해 **마음일기를 비동기 재분석**하고 기존 분석 결과를 덮어쓴다
- 매 신고마다 트리거. **재분석 성공 시에만** 덮어쓰기 → 실패 시 기존 결과 보존
- 분석 결과(composite/content/label) 일괄 교체

## API 변경 (프론트 확인 필요)

### `POST /v1/api/users/sign-up` — 요청 변경
- **추가**: `gender` (필수, `MALE`/`FEMALE`), `nickname` (선택)
- 기존 `name`/`username`/`password` + 위 2개. `gender` 누락 시 400

### `GET /v1/api/users` (내 정보) — 응답 변경
- **추가**: `gender`, `nickname`

```json
{ "username": "...", "name": "...", "gender": "MALE", "nickname": "길동이" }
```

### `POST /v1/api/users/voices` (일기 등록) — 동작 변경
- 같은 날 이미 작성한 일기가 있으면 **`4154`(VOICE_ALREADY_EXISTS_TODAY)** 반환
- 정상 등록 경로/응답(voiceId)은 동일

### `POST /v1/api/users/voices/{voiceId}/report` (감정 신고) — 동작 변경
- 신고 저장 후 **마음일기 재분석을 비동기 트리거** (응답은 즉시 200, 재분석은 백그라운드)
- 재분석 완료 시 해당 일기의 감정 분석 결과가 정정 반영되어 갱신됨 (조회 시 변경 확인)
- 요청/응답 형상은 동일

### `GET /v1/api/users/voices/{voiceId}/analysis` — 응답 구조 변경 (Breaking)
- **제거**: `breakdown`
- **추가**: `majorEmotions`, `subEmotions`

```json
{
  "voiceId": 42,
  "topEmotion": "HAPPY",
  "summary": "...",
  "majorEmotions": [ { "emotion": "HAPPY", "percentage": 42.5 } ],
  "subEmotions": [ { "label": "기쁨", "percentage": 35.7, "question": "오늘 행복한 감정의 원천은?" } ],
  "chatSessionId": null,
  "chatStatus": "pending"
}
```

- `majorEmotions`: 항상 6개, 비율 내림차순, 합 ≈ 100
- `subEmotions`: 세부 감정 intensity 상위 6개, 전체 대비 비율(합 ≤ 100), 카테고리별 탐색 질문 포함

## 테스트
- 회원가입 도메인/유스케이스 테스트 보강 (성별·별명, 별명 null)
- 1일 1일기 제약 테스트 (중복 거부 / 정상 / 잘못된 질문)
- 감정 신고 재분석 트리거 테스트 (최초/재신고/타인 차단, 미설정 스킵)
- 전체 `clean build` + JaCoCo 통과

## 체크할 사항
- **DB**: 이번 변경으로 `users`에 `gender`/`nickname` 컬럼 추가. 기존 행은 null
- **감정 분석 응답 구조 변경**은 Breaking — 프론트가 `majorEmotions`/`subEmotions`로 받게 수정 필요
- **재분석 덮어쓰기 원자성**: 덮어쓰기(삭제 3 + 저장 3)가 어댑터별 개별 트랜잭션. Gemini 실패는 삭제 이전이라 안전하나, DB 저장 중간 실패 시 부분 갱신 가능(드묾) → 단일 트랜잭션 묶기는 후속

## 후속 처리 (이번 PR 미포함)
- 분석 프롬프트에 성별·별명 반영(개인화)
- 1일 1일기 DB 레벨 유니크 제약 (현재는 애플리케이션 레벨 검증)
- 재분석 덮어쓰기 단일 트랜잭션화
