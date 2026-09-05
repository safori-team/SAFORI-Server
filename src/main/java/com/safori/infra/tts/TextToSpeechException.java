package com.safori.infra.tts;

/**
 * Cloud TTS 합성 실패를 나타내는 인프라 예외.
 * UseCase에서 ErrorStatus.TTS_SYNTHESIS_FAILED로 변환한다.
 */
public class TextToSpeechException extends RuntimeException {

    public TextToSpeechException(String message) {
        super(message);
    }

    public TextToSpeechException(String message, Throwable cause) {
        super(message, cause);
    }
}
