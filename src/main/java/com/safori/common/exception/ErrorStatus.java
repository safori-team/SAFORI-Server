package com.safori.common.exception;

import com.safori.common.annotation.ExplainError;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Objects;

import static org.springframework.http.HttpStatus.*;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

    // 서버 오류 (5000번대)
    @ExplainError("500번대 알수없는 오류입니다. 서버 관리자에게 문의 주세요")
    _INTERNAL_SERVER_ERROR(INTERNAL_SERVER_ERROR, 5000, "서버 에러, 관리자에게 문의 바랍니다."),
    @ExplainError("인증이 필요없는 api입니다.")
    _UNAUTHORIZED_LOGIN_DATA_RETRIEVAL_ERROR(INTERNAL_SERVER_ERROR, 5001, "서버 에러, 로그인이 필요없는 요청입니다."),
    _ASSIGNABLE_PARAMETER(BAD_REQUEST, 5002, "인증타입이 잘못되어 할당이 불가능합니다."),

    // 일반 요청 오류 (4000번대)
    _BAD_REQUEST(BAD_REQUEST, 4000, "잘못된 요청입니다."),
    _UNAUTHORIZED(UNAUTHORIZED, 4001, "로그인이 필요합니다."),
    _FORBIDDEN(FORBIDDEN, 4002, "금지된 요청입니다."),
    DATE_RANGE_INVALID_WEEK(BAD_REQUEST, 4003, "유효하지 않은 주차 값입니다."),
    DATE_RANGE_INVALID_CALENDAR_WEEK_RANGE(BAD_REQUEST, 4004, "유효하지 않은 주간 날짜 범위입니다."),

    // 유저 오류 (4050번대)
    USER_USERNAME_ALREADY_EXISTS(BAD_REQUEST, 4050, "이미 존재하는 username입니다."),
    USER_NOT_FOUND(BAD_REQUEST, 4052, "존재하지 않는 유저입니다."),
    USER_PASSWORD_NOT_MATCH(BAD_REQUEST, 4053, "비밀번호가 일치하지 않습니다."),

    // 인증/토큰 오류 (4060번대)
    AUTH_INVALID_REFRESH_TOKEN(UNAUTHORIZED, 4060, "유효하지 않은 리프레시 토큰입니다."),

    // 질문 오류 (4100번대)
    QUESTION_NOT_FOUND(BAD_REQUEST, 4100, "존재하지 않는 질문입니다."),

    // 음성 오류 (4150번대)
    VOICE_NOT_FOUND(BAD_REQUEST, 4150, "존재하지 않는 음성파일입니다."),
    VOICE_NO_PERMISSION(BAD_REQUEST, 4151, "음성파일의 접근권한이 없습니다."),
    VOICE_ANALYSIS_NOT_COMPLETED(BAD_REQUEST, 4152, "분석이 완료되지 않은 음성파일입니다."),
    VOICE_ANALYSIS_RESULT_NOT_FOUND(BAD_REQUEST, 4153, "분석 결과가 존재하지 않습니다."),
    VOICE_ALREADY_EXISTS_TODAY(BAD_REQUEST, 4154, "오늘은 이미 마음일기를 작성했습니다."),

    // 챗봇 오류 (4200번대)
    CHAT_SESSION_NOT_FOUND(BAD_REQUEST, 4200, "존재하지 않는 챗봇 세션입니다."),
    CHAT_SESSION_NO_PERMISSION(BAD_REQUEST, 4201, "챗봇 세션의 접근권한이 없습니다."),
    CHAT_MESSAGE_NOT_FOUND(BAD_REQUEST, 4202, "존재하지 않는 채팅 메시지입니다."),
    CHAT_MESSAGE_NO_PERMISSION(BAD_REQUEST, 4203, "채팅 메시지의 접근권한이 없습니다."),
    CHAT_FEEDBACK_INVALID_EMOTION(BAD_REQUEST, 4204, "유효하지 않은 감정 피드백 값입니다."),
    CHAT_VOICE_STT_FAILED(BAD_REQUEST, 4205, "음성 분석(STT)에 실패했습니다."),
    CHAT_SESSION_CLOSED(BAD_REQUEST, 4206, "이미 마무리된 상담입니다. 새로운 상담을 시작해주세요."),
    CHAT_OFFER_NOT_FOUND(BAD_REQUEST, 4207, "존재하지 않는 상담 제안입니다."),
    CHAT_OFFER_NO_PERMISSION(BAD_REQUEST, 4208, "상담 제안의 접근권한이 없습니다."),
    CHAT_OFFER_NOT_OFFERABLE(BAD_REQUEST, 4209, "이미 응답한 상담 제안입니다."),
    CHAT_OFFER_EXPIRED(BAD_REQUEST, 4210, "상담 제안 조건이 더 이상 유효하지 않습니다."),
    CHAT_REPLY_IN_PROGRESS(BAD_REQUEST, 4211, "도란이가 아직 답변을 만들고 있어요."),
    CHAT_SESSION_CRISIS_CLOSED(BAD_REQUEST, 4212,
            "안전을 위해 종료된 상담입니다. 자살예방 상담전화 109로 전문가의 도움을 받아보세요."),
    @ExplainError("턴이 남아 있는 세션에 연장(POST /sessions/{sessionId}/extend)을 호출한 경우. 연장은 턴 소진 후에만 가능하다.")
    CHAT_SESSION_NOT_EXTENDABLE(BAD_REQUEST, 4213, "아직 대화 횟수가 남아 있어 연장할 수 없습니다."),

    // TTS 오류 (4250번대)
    TTS_TEXT_EMPTY(BAD_REQUEST, 4250, "읽을 문장이 비어 있습니다."),
    TTS_TEXT_TOO_LONG(BAD_REQUEST, 4251, "읽을 문장이 너무 깁니다."),
    @ExplainError("GCP Cloud TTS 호출 실패·타임아웃 또는 TTS/S3 미구성. 클라이언트는 기기 내장 TTS로 폴백한다.")
    TTS_SYNTHESIS_FAILED(BAD_REQUEST, 4252, "음성 합성에 실패했습니다."),

    // 기관·구성원 오류 (4300번대)
    ORGANIZATION_INACTIVE(BAD_REQUEST, 4300, "비활성화된 기관입니다."),
    @ExplainError("기관 A의 구성원·역할·그룹·어르신을 기관 B의 것과 엮으려 한 경우. 기관 관리자도 소속 기관 밖은 다룰 수 없다.")
    ORGANIZATION_MISMATCH(BAD_REQUEST, 4301, "다른 기관의 대상은 처리할 수 없습니다."),
    ORGANIZATION_MEMBER_ALREADY_EXISTS(BAD_REQUEST, 4302, "이미 기관에 소속되었거나 초대된 계정입니다."),
    @ExplainError("승인 대기(PENDING)가 아닌 구성원을 승인하거나, 소속 종료(REVOKED)된 구성원을 정지·재활성화하는 등 상태 전이가 맞지 않는 경우.")
    ORGANIZATION_MEMBER_INVALID_STATUS(BAD_REQUEST, 4303, "현재 구성원 상태에서는 처리할 수 없는 요청입니다."),
    ORGANIZATION_MEMBER_SELF_APPROVAL(BAD_REQUEST, 4304, "본인의 가입은 직접 승인할 수 없습니다."),
    @ExplainError("기관 관리자는 기관당 1명이다. 소속 종료(REVOKED)되지 않은 관리자가 있는데 관리자를 또 초대한 경우.")
    ORGANIZATION_ADMIN_ALREADY_EXISTS(BAD_REQUEST, 4305, "이미 기관 관리자가 있는 기관입니다."),
    @ExplainError("백오피스 계정(관리자·담당자·보호자)은 한 기관에만 소속된다. 소속 종료되지 않은 다른 기관 멤버십이 있는 경우.")
    ORGANIZATION_MEMBER_OF_OTHER_ORGANIZATION(BAD_REQUEST, 4306, "이미 다른 기관에 소속된 계정입니다."),
    @ExplainError("경로의 구성원 식별자가 없거나, 요청한 구성원의 기관 소속이 아니거나, 기대한 역할(예: 담당자)이 아닌 경우.")
    ORGANIZATION_MEMBER_NOT_FOUND(BAD_REQUEST, 4307, "존재하지 않는 구성원입니다."),

    // 백오피스 계정 오류 (4350번대)
    ACCOUNT_LOGIN_ID_ALREADY_EXISTS(BAD_REQUEST, 4350, "이미 사용 중인 로그인 아이디입니다."),
    ACCOUNT_INACTIVE(BAD_REQUEST, 4351, "사용할 수 없는 백오피스 계정입니다."),
    @ExplainError("담당자·보호자가 내 정보 수정에서 이름·연락처·아이디를 바꾸려 한 경우. 비밀번호만 바꿀 수 있다.")
    ACCOUNT_PROFILE_NOT_EDITABLE(BAD_REQUEST, 4352, "기관 관리자만 수정할 수 있는 항목입니다."),

    // 권한·역할 오류 (4400번대)
    ACCESS_ROLE_CODE_ALREADY_EXISTS(BAD_REQUEST, 4400, "기관에 이미 존재하는 역할 코드입니다."),
    @ExplainError("organization_assignable=false 권한(예: RAW_CONTENT_READ 원문 열람)을 기관 역할에 넣으려 한 경우.")
    ACCESS_PERMISSION_NOT_ASSIGNABLE(BAD_REQUEST, 4401, "기관에서 부여할 수 없는 권한입니다."),
    ACCESS_INVALID_ROLE_EXPIRY(BAD_REQUEST, 4402, "역할 만료 시각은 현재 이후여야 합니다."),

    // 돌봄 대상·배정 오류 (4450번대)
    @ExplainError("어르신은 한 기관에만 등록된다. 같은 앱 계정(user_id)이 이미 어느 기관에든 등록된 경우.")
    CARE_RECIPIENT_ALREADY_REGISTERED(BAD_REQUEST, 4450, "이미 기관에 등록된 어르신입니다."),
    CARE_RECIPIENT_INACTIVE(BAD_REQUEST, 4451, "비활성화된 어르신입니다."),
    @ExplainError("활성 상태가 아니거나 배정 범위(ASSIGNED_RECIPIENT) 권한이 없는 구성원을 담당자로 배정하려 한 경우.")
    CARE_WORKER_NOT_ASSIGNABLE(BAD_REQUEST, 4452, "배정할 수 없는 담당자입니다."),
    @ExplainError("활성 상태가 아니거나 연결 범위(LINKED_RECIPIENT) 권한이 없는 구성원을 보호자로 연결하려 한 경우.")
    CARE_GUARDIAN_NOT_LINKABLE(BAD_REQUEST, 4453, "연결할 수 없는 보호자입니다."),
    @ExplainError("경로의 대상자 식별자(public_id)가 없거나 요청한 구성원의 기관 대상자가 아닌 경우.")
    CARE_RECIPIENT_NOT_FOUND(BAD_REQUEST, 4454, "존재하지 않는 대상자입니다."),
    CARE_RECORD_NOT_FOUND(BAD_REQUEST, 4455, "존재하지 않는 기록입니다."),
    @ExplainError("현재 기록이 아닌 기록(흡수·완료됨)의 처리 상태를 바꾸려 했거나, 흡수됨(ABSORBED)으로 바꾸려 한 경우.")
    CARE_RECORD_NOT_PROCESSABLE(BAD_REQUEST, 4456, "처리 상태를 바꿀 수 없는 기록입니다."),
    @ExplainError("일지 항목 선택이 폼 규칙에 맞지 않는 경우: 없는·비활성 항목, 중복, 단일 선택 섹션에 2개 이상, 필수 섹션 미선택, "
            + "'특이사항 없음'과 다른 항목 동시 선택, 부모 없이 하위 항목 선택, 기타 입력값 누락(또는 입력 항목이 아닌데 입력값).")
    CARE_JOURNAL_INVALID_SELECTION(BAD_REQUEST, 4457, "일지 항목 선택이 올바르지 않습니다."),
    CARE_JOURNAL_NOT_FOUND(BAD_REQUEST, 4458, "존재하지 않는 일지입니다."),
    @ExplainError("보호자는 대상자 한 명에만 연결된다. 다른 대상자와 연결된 보호자는 먼저 연결을 해제해야 한다.")
    CARE_GUARDIAN_ALREADY_LINKED(BAD_REQUEST, 4459, "이미 다른 대상자와 연결된 보호자입니다."),
    CARE_JOURNAL_INVALID_PERIOD(BAD_REQUEST, 4460, "조회 기간이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final Integer code;
    private final String message;

    @Override
    public Reason getReason() {
        return Reason.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .build();
    }

    @Override
    public Reason getReasonHttpStatus() {
        return Reason.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }

    @Override
    public String getExplainError() throws NoSuchFieldException {
        Field field = this.getClass().getField(this.name());
        ExplainError annotation = field.getAnnotation(ExplainError.class);
        return Objects.nonNull(annotation) ? annotation.value() : this.getMessage();
    }
}
