package com.safori.api.tts.service;

import com.safori.api.tts.dto.TtsSynthesisResponse;
import com.safori.common.annotation.UseCase;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;
import com.safori.common.service.S3PresignService;
import com.safori.infra.tts.CloudTextToSpeechClient;
import com.safori.infra.tts.TextToSpeechException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.core.exception.SdkException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 화면 낭독용 TTS 합성 UseCase.
 *
 * <p>검증 → S3 캐시 조회 → (MISS 시) Cloud TTS 합성 → S3 저장 → presigned GET 발급 순으로 처리한다.
 * 캐시는 전 사용자 공유이므로 캐시 키에 username을 넣지 않는다.
 * 질문 목록·CBT 왜곡 설명 같은 고정 문구는 최초 1회만 GCP를 호출한다.
 *
 * <p>고정 문구인지 일회성 문구인지는 서버가 판별할 수 없으므로 호출자(cacheable)가 알려준다.
 * 두 종류는 서로 겹치지 않는 prefix에 저장되어 버킷 수명주기 규칙을 따로 걸 수 있다.
 * 일회성 문구도 조회는 그대로 수행한다. headObject는 저렴하고, 같은 화면을 다시 낭독할 때 히트한다.
 */
@Slf4j
@UseCase
public class SynthesizeSpeechUseCase {

    private static final String SHARED_PREFIX = "tts/shared/";
    private static final String EPHEMERAL_PREFIX = "tts/ephemeral/";
    private static final String KEY_SUFFIX = ".mp3";
    private static final String CONTENT_TYPE = "audio/mpeg";

    private final Optional<CloudTextToSpeechClient> textToSpeechClient;
    private final Optional<S3PresignService> s3PresignService;
    private final int maxTextLength;

    public SynthesizeSpeechUseCase(
            Optional<CloudTextToSpeechClient> textToSpeechClient,
            Optional<S3PresignService> s3PresignService,
            @Value("${tts.max-text-length:500}") int maxTextLength
    ) {
        this.textToSpeechClient = textToSpeechClient;
        this.s3PresignService = s3PresignService;
        this.maxTextLength = maxTextLength;
    }

    /**
     * @param username  인증된 사용자명. 캐시 키에는 넣지 않고 로깅에만 사용한다.
     * @param text      합성할 평문
     * @param cacheable 전 사용자 공유 고정 문구면 true, 일회성 문구면 false
     */
    public TtsSynthesisResponse execute(String username, String text, boolean cacheable) {
        validate(text);

        CloudTextToSpeechClient client = textToSpeechClient
                .orElseThrow(() -> {
                    log.warn("TTS가 구성되지 않았습니다. tts.api-key를 확인해주세요. (username={})", username);
                    return new GeneralException(ErrorStatus.TTS_SYNTHESIS_FAILED);
                });
        S3PresignService s3 = s3PresignService
                .orElseThrow(() -> {
                    log.warn("S3가 구성되지 않아 TTS 캐시를 사용할 수 없습니다. (username={})", username);
                    return new GeneralException(ErrorStatus.TTS_SYNTHESIS_FAILED);
                });

        String objectKey = buildObjectKey(text, client.getVoice(), client.getSpeakingRate(), cacheable);

        if (isCached(s3, objectKey)) {
            return toResponse(s3, objectKey, true);
        }

        byte[] audio;
        try {
            audio = client.synthesize(text);
        } catch (TextToSpeechException e) {
            log.warn("TTS 합성 실패 (username={}, textLength={})", username, text.length(), e);
            throw new GeneralException(ErrorStatus.TTS_SYNTHESIS_FAILED);
        }

        try {
            s3.putObject(objectKey, audio, CONTENT_TYPE);
        } catch (SdkException e) {
            log.warn("TTS 캐시 저장 실패 (key={})", objectKey, e);
            throw new GeneralException(ErrorStatus.TTS_SYNTHESIS_FAILED);
        }
        return toResponse(s3, objectKey, false);
    }

    /**
     * 캐시 조회 실패는 MISS로 간주하고 합성을 진행한다.
     * ListBucket 권한이 없는 버킷은 없는 키에 404 대신 403을 반환하므로,
     * 여기서 예외를 그대로 올리면 캐시 MISS마다 엔드포인트가 5xx로 떨어진다.
     */
    private boolean isCached(S3PresignService s3, String objectKey) {
        try {
            return s3.existsObject(objectKey);
        } catch (SdkException e) {
            log.warn("TTS 캐시 조회 실패, MISS로 처리한다 (key={})", objectKey, e);
            return false;
        }
    }

    private void validate(String text) {
        if (text == null || text.isBlank()) {
            throw new GeneralException(ErrorStatus.TTS_TEXT_EMPTY);
        }
        if (text.length() > maxTextLength) {
            throw new GeneralException(ErrorStatus.TTS_TEXT_TOO_LONG);
        }
    }

    /**
     * 캐시 키: {@code tts/{shared|ephemeral}/{voice}/{sha256(text|voice|speakingRate)}.mp3}
     *
     * <p>음성·속도를 해시에 포함해 설정을 바꿔도 옛 캐시가 재사용되지 않도록 한다.
     * cacheable은 해시가 아니라 prefix로만 반영한다. 같은 문장이면 어느 쪽이든 오디오는 동일하고,
     * 달라지는 것은 보관 기간뿐이기 때문이다.
     */
    private String buildObjectKey(String text, String voice, double speakingRate, boolean cacheable) {
        String source = text + "|" + voice + "|" + speakingRate;
        String prefix = cacheable ? SHARED_PREFIX : EPHEMERAL_PREFIX;
        return prefix + voice + "/" + sha256(source) + KEY_SUFFIX;
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private TtsSynthesisResponse toResponse(S3PresignService s3, String objectKey, boolean cached) {
        return new TtsSynthesisResponse(
                s3.generateGetUrl(objectKey),
                cached,
                s3.getGetUrlExpirySeconds()
        );
    }
}
