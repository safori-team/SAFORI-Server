package com.safori.infra.sqs;

/**
 * 응답 메시지가 계약을 벗어나 <b>재수신해도 절대 처리할 수 없는</b> 경우.
 *
 * <p>DB 일시 장애 같은 복구 가능한 실패와 구분하기 위해 따로 둔다. 응답 큐에 DLQ가 없어
 * ACK하지 않으면 메시지 보관 기간 내내 같은 메시지가 되돌아오므로, 이 예외는 폴러가 로그를
 * 남기고 폐기(ACK)한다. 원문은 S3 {@code response_key}에 남아 있고 원장은 PENDING으로 남아
 * 타임아웃 스윕이 일기를 마감하므로 사용자 흐름은 끊기지 않는다.
 */
public class EmotionAnalysisContractException extends RuntimeException {

    public EmotionAnalysisContractException(String message) {
        super(message);
    }
}
