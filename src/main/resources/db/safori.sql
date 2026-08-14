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
    title                      varchar(15)                                                      null,
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
