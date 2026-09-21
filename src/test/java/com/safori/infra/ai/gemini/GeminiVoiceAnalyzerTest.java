package com.safori.infra.ai.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceContentAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.infra.sqs.EmotionAnalysisRequestSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeminiVoiceAnalyzerTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock VoiceContentAdaptor voiceContentAdaptor;
    @Mock VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    @Mock GeminiEmotionMapper emotionMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EmotionAnalysisRequestSender minorAnalysisRequestSender;

    private GeminiVoiceAnalyzer analyzerWithoutClient() {
        // Gemini/S3 미설정(Optional.empty) 상태
        return new GeminiVoiceAnalyzer(
                Optional.empty(), "gemini-2.5-flash", Optional.empty(),
                voiceAdaptor, voiceCompositeAdaptor, voiceContentAdaptor, voiceEmotionLabelAdaptor,
                emotionMapper, "test-bucket", new ObjectMapper(), eventPublisher,
                minorAnalysisRequestSender);
    }

    @Test
    @DisplayName("재분석 - Gemini/S3 미설정 시 스킵, 기존 결과 손대지 않음")
    void reanalyze_notConfigured_skipsWithoutTouchingData() {
        analyzerWithoutClient().reanalyzeAsync(1L, "voices/u/k.m4a", EmotionType.SAD, "msg");

        verify(voiceAdaptor, never()).queryById(any());
        verify(voiceCompositeAdaptor, never()).deleteByVoiceId(any());
        verify(voiceEmotionLabelAdaptor, never()).deleteByVoiceId(any());
        verify(voiceContentAdaptor, never()).deleteByVoiceId(any());
    }
}
