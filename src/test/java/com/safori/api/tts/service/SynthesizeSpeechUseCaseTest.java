package com.safori.api.tts.service;

import com.safori.api.tts.dto.TtsSynthesisResponse;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;
import com.safori.common.service.S3PresignService;
import com.safori.infra.tts.CloudTextToSpeechClient;
import com.safori.infra.tts.TextToSpeechException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SynthesizeSpeechUseCaseTest {

    private static final String USERNAME = "user1";
    private static final String VOICE = "ko-KR-Chirp3-HD-Achernar";
    private static final int MAX_TEXT_LENGTH = 500;

    @Mock
    private CloudTextToSpeechClient textToSpeechClient;

    @Mock
    private S3PresignService s3PresignService;

    private SynthesizeSpeechUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SynthesizeSpeechUseCase(
                Optional.of(textToSpeechClient), Optional.of(s3PresignService), MAX_TEXT_LENGTH);
    }

    private void givenTtsSettings() {
        given(textToSpeechClient.getVoice()).willReturn(VOICE);
        given(textToSpeechClient.getSpeakingRate()).willReturn(1.0);
    }

    @Test
    @DisplayName("캐시 HIT - GCP를 호출하지 않고 cached=true로 반환")
    void execute_cacheHit_skipsSynthesis() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString())).willReturn(true);
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3?X-Amz-Signature=abc");
        given(s3PresignService.getGetUrlExpirySeconds()).willReturn(3600);

        // when
        TtsSynthesisResponse response = useCase.execute(USERNAME, "오늘 하루는 어땠나요?", true);

        // then
        assertThat(response.cached()).isTrue();
        assertThat(response.audioUrl()).contains("X-Amz-Signature");
        assertThat(response.expiresInSeconds()).isEqualTo(3600);
        verify(textToSpeechClient, never()).synthesize(anyString());
        verify(s3PresignService, never()).putObject(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("캐시 MISS - 합성 후 audio/mpeg로 저장하고 cached=false로 반환")
    void execute_cacheMiss_synthesizesAndStores() {
        // given
        givenTtsSettings();
        byte[] audio = {1, 2, 3};
        given(s3PresignService.existsObject(anyString())).willReturn(false);
        given(textToSpeechClient.synthesize("오늘 하루는 어땠나요?")).willReturn(audio);
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3?X-Amz-Signature=abc");
        given(s3PresignService.getGetUrlExpirySeconds()).willReturn(3600);

        // when
        TtsSynthesisResponse response = useCase.execute(USERNAME, "오늘 하루는 어땠나요?", true);

        // then
        assertThat(response.cached()).isFalse();
        verify(s3PresignService).putObject(anyString(), eq(audio), eq("audio/mpeg"));
    }

    @Test
    @DisplayName("캐시 키 - tts/{voice}/ prefix와 .mp3 확장자, 동일 입력은 동일 키")
    void execute_cacheKeyFormatIsStable() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString())).willReturn(true);
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3");

        // when
        useCase.execute(USERNAME, "같은 문장", true);
        useCase.execute("otherUser", "같은 문장", true);

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(s3PresignService, org.mockito.Mockito.times(2)).existsObject(captor.capture());
        assertThat(captor.getAllValues().get(0)).startsWith("tts/shared/" + VOICE + "/").endsWith(".mp3");
        // 캐시는 전 사용자 공유 - username은 키에 포함되지 않는다
        assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
    }

    @Test
    @DisplayName("캐시 키 - 음성이 달라지면 키도 달라진다")
    void execute_cacheKeyDiffersByVoice() {
        // given
        given(s3PresignService.existsObject(anyString())).willReturn(true);
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3");
        given(textToSpeechClient.getSpeakingRate()).willReturn(1.0);
        given(textToSpeechClient.getVoice()).willReturn(VOICE, "ko-KR-Chirp3-HD-Charon");

        // when
        useCase.execute(USERNAME, "같은 문장", true);
        useCase.execute(USERNAME, "같은 문장", true);

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(s3PresignService, org.mockito.Mockito.times(2)).existsObject(captor.capture());
        assertThat(captor.getAllValues().get(0)).isNotEqualTo(captor.getAllValues().get(1));
    }

    @Test
    @DisplayName("빈 문장 - 4250, GCP를 호출하지 않는다")
    void execute_blankText_throwsTextEmpty() {
        assertThatThrownBy(() -> useCase.execute(USERNAME, "   ", true))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.TTS_TEXT_EMPTY);

        verify(textToSpeechClient, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("null 문장 - 4250")
    void execute_nullText_throwsTextEmpty() {
        assertThatThrownBy(() -> useCase.execute(USERNAME, null, true))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.TTS_TEXT_EMPTY);
    }

    @Test
    @DisplayName("최대 길이 초과 - 4251, GCP를 호출하지 않는다")
    void execute_tooLongText_throwsTextTooLong() {
        String text = "가".repeat(MAX_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> useCase.execute(USERNAME, text, true))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.TTS_TEXT_TOO_LONG);

        verify(textToSpeechClient, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("최대 길이 경계값은 허용된다")
    void execute_maxLengthText_isAccepted() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString())).willReturn(true);
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3");

        // when & then
        assertThat(useCase.execute(USERNAME, "가".repeat(MAX_TEXT_LENGTH), true)).isNotNull();
    }

    @Test
    @DisplayName("합성 실패 - 4252로 변환하고 S3에 저장하지 않는다")
    void execute_synthesisFailure_throwsSynthesisFailed() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString())).willReturn(false);
        given(textToSpeechClient.synthesize(anyString())).willThrow(new TextToSpeechException("timeout"));

        // when & then
        assertThatThrownBy(() -> useCase.execute(USERNAME, "오늘 하루는 어땠나요?", true))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.TTS_SYNTHESIS_FAILED);

        verify(s3PresignService, never()).putObject(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("cacheable=false - tts/ephemeral/ prefix에 저장한다")
    void execute_notCacheable_usesEphemeralPrefix() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString())).willReturn(false);
        given(textToSpeechClient.synthesize(anyString())).willReturn(new byte[]{1});
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3");

        // when
        useCase.execute(USERNAME, "오늘 일기 본문입니다", false);

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(s3PresignService).putObject(captor.capture(), any(), anyString());
        assertThat(captor.getValue()).startsWith("tts/ephemeral/" + VOICE + "/").endsWith(".mp3");
    }

    @Test
    @DisplayName("cacheable - 같은 문장이라도 shared와 ephemeral은 서로 다른 키를 쓴다")
    void execute_cacheableSplitsPrefixForSameText() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString())).willReturn(true);
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3");

        // when
        useCase.execute(USERNAME, "같은 문장", true);
        useCase.execute(USERNAME, "같은 문장", false);

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(s3PresignService, org.mockito.Mockito.times(2)).existsObject(captor.capture());
        assertThat(captor.getAllValues().get(0)).startsWith("tts/shared/");
        assertThat(captor.getAllValues().get(1)).startsWith("tts/ephemeral/");
        // prefix만 다르고 해시는 같다 - 같은 문장이면 오디오는 동일하기 때문
        assertThat(captor.getAllValues().get(0).replace("tts/shared/", ""))
                .isEqualTo(captor.getAllValues().get(1).replace("tts/ephemeral/", ""));
    }

    @Test
    @DisplayName("cacheable=false - 캐시 조회는 그대로 수행해 재낭독 시 히트한다")
    void execute_notCacheable_stillChecksCache() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString())).willReturn(true);
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3");

        // when
        TtsSynthesisResponse response = useCase.execute(USERNAME, "오늘 일기 본문입니다", false);

        // then
        assertThat(response.cached()).isTrue();
        verify(textToSpeechClient, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("캐시 조회 실패 - MISS로 간주하고 합성을 진행한다")
    void execute_cacheLookupFailure_fallsBackToSynthesis() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString()))
                .willThrow(SdkException.builder().message("access denied").build());
        given(textToSpeechClient.synthesize(anyString())).willReturn(new byte[]{1});
        given(s3PresignService.generateGetUrl(anyString())).willReturn("https://s3/tts.mp3");

        // when
        TtsSynthesisResponse response = useCase.execute(USERNAME, "오늘 하루는 어땠나요?", true);

        // then
        assertThat(response.cached()).isFalse();
        verify(textToSpeechClient).synthesize("오늘 하루는 어땠나요?");
    }

    @Test
    @DisplayName("캐시 저장 실패 - 4252")
    void execute_cacheStoreFailure_throwsSynthesisFailed() {
        // given
        givenTtsSettings();
        given(s3PresignService.existsObject(anyString())).willReturn(false);
        given(textToSpeechClient.synthesize(anyString())).willReturn(new byte[]{1});
        org.mockito.BDDMockito.willThrow(SdkException.builder().message("put failed").build())
                .given(s3PresignService).putObject(anyString(), any(), anyString());

        // when & then
        assertThatThrownBy(() -> useCase.execute(USERNAME, "오늘 하루는 어땠나요?", true))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.TTS_SYNTHESIS_FAILED);
    }

    @Test
    @DisplayName("TTS 미구성 - 4252 (앱은 기기 내장 TTS로 폴백)")
    void execute_ttsNotConfigured_throwsSynthesisFailed() {
        // given
        SynthesizeSpeechUseCase notConfigured =
                new SynthesizeSpeechUseCase(Optional.empty(), Optional.of(s3PresignService), MAX_TEXT_LENGTH);

        // when & then
        assertThatThrownBy(() -> notConfigured.execute(USERNAME, "오늘 하루는 어땠나요?", true))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.TTS_SYNTHESIS_FAILED);
    }

    @Test
    @DisplayName("S3 미구성 - 4252")
    void execute_s3NotConfigured_throwsSynthesisFailed() {
        // given
        SynthesizeSpeechUseCase notConfigured =
                new SynthesizeSpeechUseCase(Optional.of(textToSpeechClient), Optional.empty(), MAX_TEXT_LENGTH);

        // when & then
        assertThatThrownBy(() -> notConfigured.execute(USERNAME, "오늘 하루는 어땠나요?", true))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.TTS_SYNTHESIS_FAILED);
    }
}
