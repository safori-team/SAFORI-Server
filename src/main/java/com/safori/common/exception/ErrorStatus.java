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
    CHAT_REPLY_IN_PROGRESS(BAD_REQUEST, 4211, "도란이가 아직 답변을 만들고 있어요.");

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
