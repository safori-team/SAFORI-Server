# 권한 설계 ERD 비교

두 설계 모두 `organization`과 `organization_member`가 이미 존재한다고 가정한다. `Permission`은 API에서 검사하는 최소 행동이고, 어르신 조회 범위는 `care_assignment`나 보호자 연결 관계로 별도 검사한다.

## 설계 A: 기관별 Role + Group 상속

기관마다 Role을 생성하고 Permission을 직접 매핑한다. 구성원은 Group에서 Role을 상속하거나 개인 Role을 추가로 받는다.

```mermaid
erDiagram
    ORGANIZATION ||--o{ ORGANIZATION_MEMBER : has
    ORGANIZATION ||--o{ ACCESS_ROLE : owns
    ORGANIZATION ||--o{ ACCESS_GROUP : owns

    ACCESS_ROLE_TEMPLATE ||--o{ ACCESS_ROLE : initializes

    ACCESS_ROLE ||--o{ ACCESS_ROLE_PERMISSION : contains
    ACCESS_PERMISSION ||--o{ ACCESS_ROLE_PERMISSION : grants

    ACCESS_GROUP ||--o{ ACCESS_GROUP_MEMBER : contains
    ORGANIZATION_MEMBER ||--o{ ACCESS_GROUP_MEMBER : joins

    ACCESS_GROUP ||--o{ ACCESS_GROUP_ROLE : receives
    ACCESS_ROLE ||--o{ ACCESS_GROUP_ROLE : assigned

    ORGANIZATION_MEMBER ||--o{ ACCESS_MEMBER_ROLE : receives_directly
    ACCESS_ROLE ||--o{ ACCESS_MEMBER_ROLE : assigned

    ORGANIZATION {
        bigint organization_id PK
        varchar name
        varchar status
    }

    ORGANIZATION_MEMBER {
        bigint organization_member_id PK
        bigint organization_id FK
        bigint account_id FK
        varchar status
    }

    ACCESS_PERMISSION {
        bigint permission_id PK
        varchar code UK
        varchar description
        boolean organization_assignable
    }

    ACCESS_ROLE_TEMPLATE {
        bigint role_template_id PK
        varchar code UK
        varchar name
    }

    ACCESS_ROLE {
        bigint role_id PK
        bigint organization_id FK
        bigint source_template_id FK
        varchar code
        varchar name
        varchar status
    }

    ACCESS_ROLE_PERMISSION {
        bigint role_id PK, FK
        bigint permission_id PK, FK
    }

    ACCESS_GROUP {
        bigint group_id PK
        bigint organization_id FK
        varchar code
        varchar name
        varchar status
    }

    ACCESS_GROUP_MEMBER {
        bigint group_id PK, FK
        bigint organization_member_id PK, FK
    }

    ACCESS_GROUP_ROLE {
        bigint group_id PK, FK
        bigint role_id PK, FK
    }

    ACCESS_MEMBER_ROLE {
        bigint organization_member_id PK, FK
        bigint role_id PK, FK
        bigint granted_by FK
        datetime granted_at
        datetime expires_at
        datetime revoked_at
        varchar reason
    }
```

권한 계산:

```text
Group에서 받은 Role의 Permission
          +
개인에게 직접 부여한 Role의 Permission
          =
구성원의 최종 Permission
```

기관마다 `access_role`과 `access_role_permission`을 보유하므로 커스텀은 직관적이다. 대신 기본 Role 변경을 여러 기관에 전파하거나 템플릿과 기관 Role의 차이를 관리해야 한다. Group이 실제 운영에 필요하지 않아도 권한 경로의 중심에 들어간다.

## 설계 B: 전역 Role + 부착 가능한 Policy

Role은 `ORG_ADMIN`, `CARE_WORKER`, `GUARDIAN` 같은 전역 의미와 기본 데이터 범위를 나타낸다. 실제 Permission 묶음은 Policy에 두고, 기관은 Role을 복제하지 않은 채 Policy 연결만 상속·추가·교체한다. 개인 예외도 Policy로 부여한다.

```mermaid
erDiagram
    ORGANIZATION ||--o{ ORGANIZATION_MEMBER : has
    ORGANIZATION o|--o{ ACCESS_POLICY : owns_custom

    ACCESS_POLICY ||--o{ ACCESS_POLICY_PERMISSION : contains
    ACCESS_PERMISSION ||--o{ ACCESS_POLICY_PERMISSION : grants

    ACCESS_ROLE ||--o{ ACCESS_ROLE_DEFAULT_POLICY : has_default
    ACCESS_POLICY ||--o{ ACCESS_ROLE_DEFAULT_POLICY : attached

    ORGANIZATION ||--o{ ORGANIZATION_ROLE_PROFILE : configures
    ACCESS_ROLE ||--o{ ORGANIZATION_ROLE_PROFILE : customized_for

    ORGANIZATION_ROLE_PROFILE ||--o{ ORGANIZATION_ROLE_POLICY : attaches
    ACCESS_POLICY ||--o{ ORGANIZATION_ROLE_POLICY : attached

    ORGANIZATION_MEMBER ||--o{ ORGANIZATION_MEMBER_ROLE : has
    ACCESS_ROLE ||--o{ ORGANIZATION_MEMBER_ROLE : identifies_as

    ORGANIZATION_MEMBER ||--o{ ORGANIZATION_MEMBER_POLICY : receives_directly
    ACCESS_POLICY ||--o{ ORGANIZATION_MEMBER_POLICY : attached

    ORGANIZATION {
        bigint organization_id PK
        varchar name
        varchar status
    }

    ORGANIZATION_MEMBER {
        bigint organization_member_id PK
        bigint organization_id FK
        bigint account_id FK
        varchar status
    }

    ACCESS_ROLE {
        bigint role_id PK
        varchar code UK
        varchar name
        varchar data_scope
        varchar status
    }

    ACCESS_PERMISSION {
        bigint permission_id PK
        varchar code UK
        varchar description
        boolean organization_assignable
    }

    ACCESS_POLICY {
        bigint policy_id PK
        varchar owner_type
        bigint organization_id FK
        varchar name
        varchar status
    }

    ACCESS_POLICY_PERMISSION {
        bigint policy_id PK, FK
        bigint permission_id PK, FK
    }

    ACCESS_ROLE_DEFAULT_POLICY {
        bigint role_id PK, FK
        bigint policy_id PK, FK
    }

    ORGANIZATION_ROLE_PROFILE {
        bigint organization_id PK, FK
        bigint role_id PK, FK
        varchar policy_mode
        bigint version
    }

    ORGANIZATION_ROLE_POLICY {
        bigint organization_id PK, FK
        bigint role_id PK, FK
        bigint policy_id PK, FK
    }

    ORGANIZATION_MEMBER_ROLE {
        bigint organization_member_id PK, FK
        bigint role_id PK, FK
    }

    ORGANIZATION_MEMBER_POLICY {
        bigint organization_member_id PK, FK
        bigint policy_id PK, FK
        bigint granted_by FK
        datetime granted_at
        datetime expires_at
        datetime revoked_at
        varchar reason
    }
```

`ACCESS_POLICY.organization_id`는 `owner_type = SYSTEM`이면 `NULL`, `owner_type = ORGANIZATION`이면 소유 기관 ID다. `ORGANIZATION_ROLE_PROFILE`은 기관이 기본 설정을 변경할 때만 만든다.

권한 계산:

```text
Role의 System 기본 Policy
          │
          ├─ profile 없음  : 기본 Policy 사용
          ├─ INHERIT       : 기본 Policy + 기관 Policy
          └─ REPLACE       : 기관 Policy만 사용
                                  +
                         개인 추가 Policy
                                  =
                         최종 Permission
```

Role의 `data_scope`는 Permission과 별도로 적용한다.

```text
ORG_ADMIN   → ORGANIZATION
CARE_WORKER → ASSIGNED_RECIPIENT
GUARDIAN    → LINKED_RECIPIENT
```

## 구조 비교

| 비교 기준 | 설계 A: 기관별 Role + Group | 설계 B: 전역 Role + Policy |
|---|---|---|
| 기관별 Role 행 | 기관 생성 시 Role 생성 또는 복사 | 생성하지 않음 |
| 커스텀 단위 | 기관 Role의 Permission 매핑 | 기관 Policy 및 Role-Policy 연결 |
| 기본값 제공 | Role Template 복사 | System Policy 연결 |
| 기본값 변경 전파 | 복사본 동기화 정책 필요 | 커스텀하지 않은 기관은 기본 Policy를 바로 사용 |
| 개인 추가 권한 | 개인에게 추가 Role 부여 | 개인에게 추가 Policy 부여 |
| Group 필요성 | 핵심 권한 경로에 포함 | 필요할 때 Policy 부착 대상으로 추가 가능 |
| Role의 의미 | 기관별 권한 묶음 | 전역 사용자 유형과 데이터 범위 |
| IAM 유사성 | 일반적인 다중 기관 RBAC | 관리 Policy를 부착하는 IAM형 모델에 가까움 |
| 초기 구현 난이도 | 비교적 단순 | Policy/profile 해석 로직이 추가됨 |
| 장기 확장 | 기관 Role 복사본 관리 필요 | Group·개인·서비스 계정으로 Policy 부착 확장 가능 |

## 권장안

요구사항의 중심이 기관별 Role 생성보다 **기본 권한을 제공하면서 Role의 권한 구성을 커스텀하고, 개인 예외를 추가하는 것**이라면 설계 B가 더 적합하다.

초기 구현에서는 다음 범위만 활성화할 수 있다.

1. System Policy와 전역 Role을 기본 연결한다.
2. 구성원에게 전역 Role을 부여한다.
3. API는 최종 Permission과 별도 데이터 scope를 검사한다.
4. 기관 Policy, `INHERIT`/`REPLACE`, 개인 Policy 테이블은 구조를 마련하되 관리 API와 화면은 후속 개발한다.
5. Group이 필요해지면 `organization_group`, `organization_group_member`, `organization_group_policy`를 추가한다. 기존 Role·Policy·Permission 구조는 변경하지 않는다.

`DENY` 정책과 조건식, 리소스 패턴은 초기 범위에서 제외한다. 추후 도입한다면 명시적 `DENY`가 모든 `ALLOW`보다 우선하도록 별도의 Policy Statement 모델과 충돌 규칙을 함께 설계해야 한다.
