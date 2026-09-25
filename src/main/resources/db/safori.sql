create table if not exists users
(
    user_id            bigint auto_increment
    primary key,
    created_date       datetime(6)                  null,
    last_modified_date datetime(6)                  null,
    name               varchar(255)                 null,
    password           varchar(255)                 null,
    role               enum ('NOT_ALLOWED', 'USER') null,
    user_uuid          varchar(255)                 null,
    username           varchar(255)                 null,
    gender             enum ('FEMALE', 'MALE')      null,
    nickname           varchar(255)                 null,
    birth_date         date                         null,
    phone              varchar(11)                  null,
    constraint UK4mcg6l0va97nbd8o9tqpeg104
    unique (user_uuid),
    constraint UKr43af9ap4edm43mmtq01oddj6
    unique (username)
    );

create table if not exists chat_session
(
    id                 char(36)    not null
    primary key,
    created_date       datetime(6) null,
    last_modified_date datetime(6) null,
    last_message_at    datetime(6) null,
    -- 위기 가드레일 발동 기록. null이면 정상 세션.
    -- HIGH_RISK_KEYWORD | AI_CRISIS_CLASSIFIER | CRISIS_DISTORTION
    crisis_trigger     varchar(24) null,
    crisis_detected_at datetime(6) null,
    -- "더 이야기하기"로 턴 제한을 해제한 시각. null이면 턴 제한 적용.
    extended_at        datetime(6) null,
    user_id            bigint      not null,
    constraint FKd17ahlgqfllxn81i7mf3iiurs
    foreign key (user_id) references users (user_id)
    );

create index idx_chat_session_user_last_message
    on chat_session (user_id asc, last_message_at desc);

create table if not exists device_token
(
    device_token_id    bigint auto_increment
    primary key,
    created_date       datetime(6)  null,
    last_modified_date datetime(6)  null,
    token              varchar(512) not null,
    user_id            bigint       null,
    constraint UKoaccue9kxei35rbe5thnv18ye
    unique (token),
    constraint FKdklq4fbedbwx14v2varmsjeb5
    foreign key (user_id) references users (user_id)
    on delete cascade
    );

create table if not exists monthly_emotion_report
(
    monthly_emotion_report_id bigint auto_increment
    primary key,
    created_date              datetime(6) null,
    last_modified_date        datetime(6) null,
    latest_voice_composite_id bigint      null,
    report_message            mediumtext  not null,
    report_month              varchar(7)  not null,
    user_id                   bigint      not null,
    constraint uq_monthly_report_user_month
    unique (user_id, report_month),
    constraint fk_monthly_report_user
    foreign key (user_id) references users (user_id)
    );

create table if not exists voice
(
    voice_id              bigint auto_increment
    primary key,
    created_date          datetime(6)                     null,
    last_modified_date    datetime(6)                     null,
    analysis_completed_at datetime(6)                     null,
    analysis_status       varchar(16) default 'COMPLETED' not null,
    bit_rate              int                             not null,
    duration              int                             not null,
    sample_rate           int                             not null,
    voice_key             varchar(255)                    null,
    voice_title           varchar(255)                    null,
    user_id               bigint                          null,
    constraint FK1mqooi8fo7u8xgku3elrb9o58
    foreign key (user_id) references users (user_id)
    );

create table if not exists chat_message
(
    chat_message_id    bigint auto_increment
    primary key,
    created_date       datetime(6)                                                      null,
    last_modified_date datetime(6)                                                      null,
    bot_response       json                                                             null,
    feedback_at        datetime(6)                                                      null,
    feedback_detail    text                                                             null,
    feedback_emotion   enum ('ANGRY', 'ANXIETY', 'HAPPY', 'NEUTRAL', 'SAD', 'SURPRISE') null,
    origin             enum ('MIND_DIARY', 'USER_TEXT', 'USER_VOICE')                   not null,
    user_input         text                                                             null,
    voice_key          varchar(255)                                                     null,
    session_id         char(36)                                                         not null,
    voice_id           bigint                                                           null,
    reply_status       varchar(16) default 'COMPLETED'                                  not null,
    constraint FK23doaiec2fdjnhg0ouhdkn9uh
    foreign key (voice_id) references voice (voice_id),
    constraint fk_chat_message_session
    foreign key (session_id) references chat_session (id)
    on delete cascade
    );

create index idx_chat_message_reply_status
    on chat_message (reply_status, created_date);

create index idx_chat_message_session_created
    on chat_message (session_id asc, created_date desc);

create index idx_chat_message_voice
    on chat_message (voice_id);

create table if not exists chat_session_diary
(
    chat_session_diary_id bigint auto_increment
    primary key,
    created_date          datetime(6) null,
    last_modified_date    datetime(6) null,
    seq                   int         not null,
    session_id            char(36)    not null,
    voice_id              bigint      not null,
    constraint uq_csd_session_voice
    unique (session_id, voice_id),
    constraint fk_csd_session
    foreign key (session_id) references chat_session (id)
    on delete cascade,
    constraint fk_csd_voice
    foreign key (voice_id) references voice (voice_id)
    on delete cascade
    );

create index idx_csd_voice
    on chat_session_diary (voice_id);

create table if not exists mind_diary_trigger
(
    mind_diary_trigger_id bigint auto_increment
    primary key,
    created_date          datetime(6)                              null,
    last_modified_date    datetime(6)                              null,
    reason                varchar(64)                              not null,
    status                enum ('ACCEPTED', 'DECLINED', 'OFFERED') not null,
    session_id            char(36)                                 null,
    voice_id              bigint                                   not null,
    constraint uq_mdt_voice
    unique (voice_id),
    constraint fk_mdt_session
    foreign key (session_id) references chat_session (id)
    on delete set null,
    constraint fk_mdt_voice
    foreign key (voice_id) references voice (voice_id)
    on delete cascade
    );

create index idx_voice_status_completed
    on voice (analysis_status, analysis_completed_at);

create index idx_voice_user_created
    on voice (user_id, created_date);

create table if not exists voice_composite
(
    voice_composite_id         bigint auto_increment
    primary key,
    created_date               datetime(6)                                                      null,
    last_modified_date         datetime(6)                                                      null,
    alpha_bps                  int                                                              null,
    angry_bps                  int                                                              not null,
    anxiety_bps                int                                                              not null,
    arousal_x1000              int                                                              not null,
    beta_bps                   int                                                              null,
    happy_bps                  int                                                              not null,
    intensity_x1000            int                                                              not null,
    neutral_bps                int                                                              not null,
    sad_bps                    int                                                              not null,
    summary                    text                                                             null,
    surprise_bps               int                                                              not null,
    text_magnitude_x1000       int                                                              null,
    text_score_bps             int                                                              null,
    title                      varchar(100)                                                     null,
    top_emotion                enum ('ANGRY', 'ANXIETY', 'HAPPY', 'NEUTRAL', 'SAD', 'SURPRISE') null,
    top_emotion_confidence_bps int                                                              null,
    valence_x1000              int                                                              not null,
    voice_id                   bigint                                                           not null,
    constraint uq_vcp_voice
    unique (voice_id),
    constraint fk_vc_voice2
    foreign key (voice_id) references voice (voice_id)
    on delete cascade
    );

create table if not exists voice_content
(
    voice_content_id   bigint auto_increment
    primary key,
    created_date       datetime(6) null,
    last_modified_date datetime(6) null,
    confidence_bps     smallint    null,
    content            mediumtext  not null,
    locale             varchar(10) null,
    magnitude_x1000    int         null,
    model_version      varchar(32) null,
    provider           varchar(32) null,
    score_bps          smallint    null,
    voice_id           bigint      not null,
    constraint uq_vc_voice
    unique (voice_id),
    constraint fk_vc_voice
    foreign key (voice_id) references voice (voice_id)
    on delete cascade
    );

create table if not exists voice_emotion_label
(
    voice_emotion_label_id bigint auto_increment
    primary key,
    created_date           datetime(6) null,
    last_modified_date     datetime(6) null,
    category               varchar(16) not null,
    intensity_x1000        int         not null,
    label                  varchar(32) not null,
    voice_id               bigint      not null,
    constraint uq_vel_voice_label
    unique (voice_id, label),
    constraint fk_vel_voice
    foreign key (voice_id) references voice (voice_id)
    on delete cascade
    );

create index idx_vel_label
    on voice_emotion_label (label);

create index idx_vel_voice
    on voice_emotion_label (voice_id);

create table if not exists voice_emotion_report
(
    voice_emotion_report_id bigint auto_increment
    primary key,
    created_date            datetime(6)                                                      null,
    last_modified_date      datetime(6)                                                      null,
    message                 text                                                             null,
    reported_emotion        enum ('ANGRY', 'ANXIETY', 'HAPPY', 'NEUTRAL', 'SAD', 'SURPRISE') not null,
    user_id                 bigint                                                           not null,
    voice_id                bigint                                                           not null,
    constraint uq_ver_voice_user
    unique (voice_id, user_id),
    constraint fk_ver_user
    foreign key (user_id) references users (user_id),
    constraint fk_ver_voice
    foreign key (voice_id) references voice (voice_id)
    on delete cascade
    );

create table if not exists voice_question
(
    voice_question_id bigint auto_increment
    primary key,
    question_category enum ('EMOTION', 'PHYSICAL', 'SELF_REFLECTION', 'SOCIAL', 'STRESS') not null,
    question_index    int                                                                 not null,
    voice_id          bigint                                                              not null,
    constraint fk_vq_voice
    foreign key (voice_id) references voice (voice_id)
    on delete cascade
    );

create table if not exists weekly_emotion_report
(
    weekly_emotion_report_id  bigint auto_increment
    primary key,
    created_date              datetime(6) null,
    last_modified_date        datetime(6) null,
    latest_voice_composite_id bigint      null,
    report_message            mediumtext  not null,
    report_month              varchar(7)  not null,
    report_week               int         not null,
    user_id                   bigint      not null,
    constraint uq_weekly_report_user_month_week
    unique (user_id, report_month, report_week),
    constraint fk_weekly_report_user
    foreign key (user_id) references users (user_id)
    );


-- -----------------------------------------------------------------------------
-- emotion_analysis_request : 소분류 감정 분석 요청 원장.
--   Gemini 1차 분석 후 요청 큐로 보내기 전에 PENDING 행을 먼저 커밋하고,
--   응답 큐에서 결과를 받으면 COMPLETED/FAILED 로 마감한다.
--   Standard SQS 는 같은 응답을 두 번 이상 전달하므로 request_id UNIQUE 가 멱등의 기준점.
-- -----------------------------------------------------------------------------
create table if not exists emotion_analysis_request
(
    emotion_analysis_request_id bigint auto_increment
    primary key,
    created_date                datetime(6)  null,
    last_modified_date          datetime(6)  null,
    request_id                  varchar(64)  not null,
    voice_id                    bigint       not null,
    status                      varchar(16)  not null default 'PENDING',
    analysis_result             json         null,
    request_key                 varchar(512) null,
    response_key                varchar(512) null,
    completed_at                datetime(6)  null,
    constraint uq_ear_request_id
    unique (request_id),
    constraint fk_ear_voice
    foreign key (voice_id) references voice (voice_id)
    on delete cascade
    );

create index idx_ear_status_created
    on emotion_analysis_request (status, created_date);

create index idx_ear_voice
    on emotion_analysis_request (voice_id);


-- =============================================================================
-- 백오피스 권한 (기관 관리자·담당자·보호자) — docs/AUTHORIZATION-ERD-COMPARISON.md 설계 A
--   최종 권한 = 소속 그룹에 연결된 역할의 권한 ∪ 개인에게 직접 부여한 역할의 권한.
--   권한 코드·기본 역할 구성의 원본은 코드(PermissionCode, RoleTemplateCode)이고,
--   access_permission / access_role_template 행은 기관 생성 시 코드 기준으로 멱등 동기화된다(seed 없음).
--   상태·범위 컬럼은 값을 추가할 때 ALTER가 필요 없도록 enum 대신 varchar로 둔다.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- backoffice_account : 백오피스 로그인 계정. 어르신 앱 계정(users)과 관계가 없는 별도 정체성.
--   auth_version 이 토큰에 실린 값과 다르면 서명이 유효해도 인증하지 않는다(정지·강제 로그아웃).
-- -----------------------------------------------------------------------------
create table if not exists backoffice_account
(
    account_id         bigint auto_increment
    primary key,
    created_date       datetime(6)  null,
    last_modified_date datetime(6)  null,
    account_uuid       char(36)     not null,
    login_id           varchar(64)  not null,
    password_hash      varchar(255) not null,
    name               varchar(50)  not null,
    phone              varchar(11)  null,
    status             varchar(16)  not null,
    auth_version       bigint       not null,
    constraint uq_boa_account_uuid
    unique (account_uuid),
    constraint uq_boa_login_id
    unique (login_id)
    );

-- -----------------------------------------------------------------------------
-- organization : 어르신을 돌보는 기관. 역할·그룹·구성원·어르신·배정은 모두 기관 안에 갇힌다.
-- -----------------------------------------------------------------------------
create table if not exists organization
(
    organization_id    bigint auto_increment
    primary key,
    created_date       datetime(6)  null,
    last_modified_date datetime(6)  null,
    public_id          varchar(36)  not null,
    name               varchar(100) not null,
    status             varchar(16)  not null,
    constraint uq_org_public_id
    unique (public_id)
    );

-- -----------------------------------------------------------------------------
-- organization_member : 계정의 기관별 소속. PENDING(초대) → ACTIVE(승인) → SUSPENDED / REVOKED.
--   그룹 소속과 개인 역할은 계정이 아니라 이 행에 붙어, 기관 A의 역할이 기관 B 판정에 섞이지 않는다.
-- -----------------------------------------------------------------------------
create table if not exists organization_member
(
    organization_member_id bigint auto_increment
    primary key,
    created_date           datetime(6) null,
    last_modified_date     datetime(6) null,
    organization_id        bigint      not null,
    account_id             bigint      not null,
    status                 varchar(16) not null,
    invited_by             bigint      null,
    approved_by            bigint      null,
    approved_at            datetime(6) null,
    revoked_at             datetime(6) null,
    job_title              varchar(50) null,
    constraint uq_om_org_account
    unique (organization_id, account_id),
    constraint fk_om_organization
    foreign key (organization_id) references organization (organization_id),
    constraint fk_om_account
    foreign key (account_id) references backoffice_account (account_id),
    constraint fk_om_invited_by
    foreign key (invited_by) references organization_member (organization_member_id),
    constraint fk_om_approved_by
    foreign key (approved_by) references organization_member (organization_member_id)
    );

create index idx_om_account
    on organization_member (account_id);

-- -----------------------------------------------------------------------------
-- access_permission : API가 검사하는 최소 행동 권한.
--   organization_assignable = 0 이면 기관이 자기 역할에 넣을 수 없다(RAW_CONTENT_READ 원문 열람).
-- -----------------------------------------------------------------------------
create table if not exists access_permission
(
    permission_id           bigint auto_increment
    primary key,
    created_date            datetime(6)  null,
    last_modified_date      datetime(6)  null,
    code                    varchar(64)  not null,
    description             varchar(255) not null,
    organization_assignable bit          not null,
    constraint uq_ap_code
    unique (code)
    );

-- -----------------------------------------------------------------------------
-- access_role_template : SAFORI 기본 역할(ORG_ADMIN, CARE_WORKER, GUARDIAN)의 버전별 기록.
--   version 은 기본 권한 구성 버전이며 낙관적 락이 아니다.
-- -----------------------------------------------------------------------------
create table if not exists access_role_template
(
    role_template_id   bigint auto_increment
    primary key,
    created_date       datetime(6)  null,
    last_modified_date datetime(6)  null,
    code               varchar(64)  not null,
    name               varchar(100) not null,
    version            bigint       not null,
    data_scope         varchar(32)  not null,
    status             varchar(16)  not null,
    constraint uq_art_code_version
    unique (code, version)
    );

-- -----------------------------------------------------------------------------
-- access_role : 기관 소유 역할. data_scope 는 이 역할로 받은 권한이 미치는 어르신 범위다.
--   ORGANIZATION 소속 기관 전체 / ASSIGNED_RECIPIENT 현재 본인 배정 / LINKED_RECIPIENT 현재 본인 연결
-- -----------------------------------------------------------------------------
create table if not exists access_role
(
    role_id            bigint auto_increment
    primary key,
    created_date       datetime(6)  null,
    last_modified_date datetime(6)  null,
    organization_id    bigint       not null,
    source_template_id bigint       null,
    code               varchar(64)  not null,
    name               varchar(100) not null,
    data_scope         varchar(32)  not null,
    status             varchar(16)  not null,
    constraint uq_ar_org_code
    unique (organization_id, code),
    constraint fk_ar_organization
    foreign key (organization_id) references organization (organization_id),
    constraint fk_ar_source_template
    foreign key (source_template_id) references access_role_template (role_template_id)
    );

-- -----------------------------------------------------------------------------
-- access_role_permission : 역할에 부여된 권한.
--   PK 컬럼 순서는 Hibernate 생성 순서(속성 이름순)에 맞췄고, 역할 기준 조회는 idx_arp_role 이 받는다.
-- -----------------------------------------------------------------------------
create table if not exists access_role_permission
(
    permission_id      bigint      not null,
    role_id            bigint      not null,
    created_date       datetime(6) null,
    last_modified_date datetime(6) null,
    primary key (permission_id, role_id),
    constraint fk_arp_permission
    foreign key (permission_id) references access_permission (permission_id),
    constraint fk_arp_role
    foreign key (role_id) references access_role (role_id)
    );

create index idx_arp_role
    on access_role_permission (role_id);

-- -----------------------------------------------------------------------------
-- access_group : 구성원 묶음. 연결된 역할을 소속 구성원이 상속한다.
--   system_code 는 기본 그룹(기본 역할 템플릿 코드)에만 있고 기관이 만든 그룹은 NULL. 판정에는 쓰지 않는다.
-- -----------------------------------------------------------------------------
create table if not exists access_group
(
    group_id           bigint auto_increment
    primary key,
    created_date       datetime(6)  null,
    last_modified_date datetime(6)  null,
    group_uuid         char(36)     not null,
    organization_id    bigint       not null,
    system_code        varchar(64)  null,
    name               varchar(100) not null,
    group_type         varchar(16)  not null,
    status             varchar(16)  not null,
    constraint uq_ag_group_uuid
    unique (group_uuid),
    constraint uq_ag_org_system_code
    unique (organization_id, system_code),
    constraint fk_ag_organization
    foreign key (organization_id) references organization (organization_id)
    );

create table if not exists access_group_member
(
    group_id               bigint      not null,
    organization_member_id bigint      not null,
    created_date           datetime(6) null,
    last_modified_date     datetime(6) null,
    primary key (group_id, organization_member_id),
    constraint fk_agm_group
    foreign key (group_id) references access_group (group_id),
    constraint fk_agm_member
    foreign key (organization_member_id) references organization_member (organization_member_id)
    );

create index idx_agm_member
    on access_group_member (organization_member_id);

create table if not exists access_group_role
(
    group_id           bigint      not null,
    role_id            bigint      not null,
    created_date       datetime(6) null,
    last_modified_date datetime(6) null,
    primary key (group_id, role_id),
    constraint fk_agr_group
    foreign key (group_id) references access_group (group_id),
    constraint fk_agr_role
    foreign key (role_id) references access_role (role_id)
    );

create index idx_agr_role
    on access_group_role (role_id);

-- -----------------------------------------------------------------------------
-- access_member_role : 개인 예외 역할. 회수·만료돼도 행을 지우지 않고, 같은 역할을 다시 주면 이 행을 재부여한다.
-- -----------------------------------------------------------------------------
create table if not exists access_member_role
(
    organization_member_id bigint       not null,
    role_id                bigint       not null,
    created_date           datetime(6)  null,
    last_modified_date     datetime(6)  null,
    granted_by             bigint       null,
    granted_at             datetime(6)  not null,
    expires_at             datetime(6)  null,
    revoked_at             datetime(6)  null,
    reason                 varchar(255) null,
    primary key (organization_member_id, role_id),
    constraint fk_amr_member
    foreign key (organization_member_id) references organization_member (organization_member_id),
    constraint fk_amr_role
    foreign key (role_id) references access_role (role_id),
    constraint fk_amr_granted_by
    foreign key (granted_by) references organization_member (organization_member_id)
    );

create index idx_amr_role
    on access_member_role (role_id);

-- -----------------------------------------------------------------------------
-- care_recipient : 기관이 관리하는 어르신. user_id 는 어르신 앱 계정이며 앱 가입 전이면 NULL.
--   엔티티는 users 를 연관관계 없이 ID로만 참조하므로(백오피스 모듈 분리 대비) fk_cr_user 는 JPA가 만들지 않는다.
-- -----------------------------------------------------------------------------
create table if not exists care_recipient
(
    recipient_id       bigint auto_increment
    primary key,
    created_date       datetime(6) null,
    last_modified_date datetime(6) null,
    public_id          varchar(36) not null,
    organization_id    bigint      not null,
    user_id            bigint      null,
    status             varchar(16) not null,
    constraint uq_cr_public_id
    unique (public_id),
    constraint uq_cr_user
    unique (user_id),
    constraint fk_cr_organization
    foreign key (organization_id) references organization (organization_id),
    constraint fk_cr_user
    foreign key (user_id) references users (user_id)
    );

-- -----------------------------------------------------------------------------
-- care_record : 대상자 기록. 시스템이 확인 사유를 감지할 때마다 쌓인다(지우지 않음).
--   care_recipient.current_record_id 가 현재 기록이다. 같거나 높은 등급은 현재 기록을 덮어쓰고(기존 ABSORBED),
--   낮은 등급은 ABSORBED 로 쌓인다. 현재 기록을 DONE 처리하면 current_record_id 가 NULL(상태 코드 X)이 된다.
-- -----------------------------------------------------------------------------
create table if not exists care_record
(
    record_id          bigint auto_increment
    primary key,
    created_date       datetime(6)  null,
    last_modified_date datetime(6)  null,
    public_id          varchar(36)  not null,
    recipient_id       bigint       not null,
    status_code        varchar(16)  not null,
    reason_type        varchar(32)  null,
    reason_message     varchar(255) not null,
    detected_at        datetime(6)  not null,
    processing_status  varchar(16)  not null,
    processed_by       bigint       null,
    processed_at       datetime(6)  null,
    constraint uq_crd_public_id
    unique (public_id),
    constraint fk_crd_recipient
    foreign key (recipient_id) references care_recipient (recipient_id),
    constraint fk_crd_processed_by
    foreign key (processed_by) references organization_member (organization_member_id)
    );

create index idx_crd_recipient_detected
    on care_record (recipient_id, detected_at);

alter table care_recipient
    add column current_record_id bigint null,
    add constraint fk_cr_current_record foreign key (current_record_id) references care_record (record_id);

-- -----------------------------------------------------------------------------
-- care_assignment : 담당자 배정 이력. ended_at IS NULL 이 현재 배정이다.
--   재배정은 기존 행을 종료하고 새 행을 만든다. 어르신 1명당 현재 담당자 1명은 어르신 행 잠금 후 서비스가 보장한다.
-- -----------------------------------------------------------------------------
create table if not exists care_assignment
(
    assignment_id      bigint auto_increment
    primary key,
    created_date       datetime(6)  null,
    last_modified_date datetime(6)  null,
    organization_id    bigint       not null,
    recipient_id       bigint       not null,
    worker_member_id   bigint       not null,
    started_at         datetime(6)  not null,
    ended_at           datetime(6)  null,
    assigned_by        bigint       null,
    ended_by           bigint       null,
    reason             varchar(255) null,
    constraint fk_ca_organization
    foreign key (organization_id) references organization (organization_id),
    constraint fk_ca_recipient
    foreign key (recipient_id) references care_recipient (recipient_id),
    constraint fk_ca_worker
    foreign key (worker_member_id) references organization_member (organization_member_id),
    constraint fk_ca_assigned_by
    foreign key (assigned_by) references organization_member (organization_member_id),
    constraint fk_ca_ended_by
    foreign key (ended_by) references organization_member (organization_member_id)
    );

create index idx_ca_worker_active
    on care_assignment (organization_id, worker_member_id, ended_at, recipient_id);

create index idx_ca_recipient_active
    on care_assignment (recipient_id, ended_at);

-- -----------------------------------------------------------------------------
-- guardian_recipient_link : 보호자 연결 이력. ended_at IS NULL 이 현재 연결이며, 어르신 한 명에 보호자 여럿이 연결될 수 있고
--   보호자는 어르신 한 명에만 연결된다(서비스가 검사). relation 은 어르신과의 관계, 기타면 relation_text.
-- -----------------------------------------------------------------------------
create table if not exists guardian_recipient_link
(
    link_id            bigint auto_increment
    primary key,
    created_date       datetime(6) null,
    last_modified_date datetime(6) null,
    organization_id    bigint      not null,
    recipient_id       bigint      not null,
    guardian_member_id bigint      not null,
    started_at         datetime(6) not null,
    ended_at           datetime(6) null,
    relation           varchar(16) null,
    relation_text      varchar(50) null,
    linked_by          bigint      null,
    ended_by           bigint      null,
    constraint fk_grl_organization
    foreign key (organization_id) references organization (organization_id),
    constraint fk_grl_recipient
    foreign key (recipient_id) references care_recipient (recipient_id),
    constraint fk_grl_guardian
    foreign key (guardian_member_id) references organization_member (organization_member_id),
    constraint fk_grl_linked_by
    foreign key (linked_by) references organization_member (organization_member_id),
    constraint fk_grl_ended_by
    foreign key (ended_by) references organization_member (organization_member_id)
    );

create index idx_grl_guardian_active
    on guardian_recipient_link (organization_id, guardian_member_id, ended_at, recipient_id);

create index idx_grl_recipient_active
    on guardian_recipient_link (recipient_id, ended_at);

-- -----------------------------------------------------------------------------
-- 일지 폼: journal_option_group(섹션) / journal_option(항목, parent_code 로 하위 항목 여러 단계).
--   기본 항목은 앱 기동 시 JournalFormSeeder 가 없는 코드만 넣는다. 항목은 지우지 않고 active=false.
-- 일지: care_journal(확인 일시·작성 당시 상태 스냅샷·보호자 공개) / care_journal_selection(고른 항목, 당시 문구).
-- -----------------------------------------------------------------------------
create table if not exists journal_option_group
(
    code       varchar(32) not null
    primary key,
    label      varchar(50) not null,
    selection  varchar(8)  not null,
    required   bit         not null,
    sort_order int         not null
    );

create table if not exists journal_option
(
    code             varchar(40) not null
    primary key,
    group_code       varchar(32) not null,
    parent_code      varchar(40) null,
    label            varchar(50) not null,
    exclusive_choice bit         not null,
    text_input       bit         not null,
    sort_order       int         not null,
    active           bit         not null,
    constraint fk_jo_group
    foreign key (group_code) references journal_option_group (code),
    constraint fk_jo_parent
    foreign key (parent_code) references journal_option (code)
    );

create table if not exists care_journal
(
    journal_id                 bigint auto_increment
    primary key,
    created_date               datetime(6) null,
    last_modified_date         datetime(6) null,
    public_id                  varchar(36) not null,
    recipient_id               bigint      not null,
    writer_member_id           bigint      not null,
    confirmed_at               datetime(6) not null,
    record_id                  bigint      null,
    status_code_snapshot       varchar(16) null,
    processing_status_snapshot varchar(16) null,
    memo                       text        null,
    guardian_visible           bit         not null,
    constraint uq_cj_public_id
    unique (public_id),
    constraint fk_cj_recipient
    foreign key (recipient_id) references care_recipient (recipient_id),
    constraint fk_cj_writer
    foreign key (writer_member_id) references organization_member (organization_member_id),
    constraint fk_cj_record
    foreign key (record_id) references care_record (record_id)
    );

create index idx_cj_recipient_confirmed
    on care_journal (recipient_id, confirmed_at);

create table if not exists care_journal_selection
(
    selection_id   bigint auto_increment
    primary key,
    journal_id     bigint       not null,
    option_code    varchar(40)  not null,
    label_snapshot varchar(50)  not null,
    text_value     varchar(200) null,
    constraint fk_cjs_journal
    foreign key (journal_id) references care_journal (journal_id),
    constraint fk_cjs_option
    foreign key (option_code) references journal_option (code)
    );

-- -----------------------------------------------------------------------------
-- shedlock : 스케줄러 중복 실행 방지 잠금(ShedLock). 앱에서는 JPA 엔티티(SchedulerLock)로 ddl-auto 가 만든다.
-- -----------------------------------------------------------------------------
create table if not exists shedlock
(
    name       varchar(64)  not null
    primary key,
    lock_until datetime(6)  not null,
    locked_at  datetime(6)  not null,
    locked_by  varchar(255) not null
    );
