package com.safori.api.voice.service;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionReport;
import com.safori.domain.voice.exception.VoiceHandler;
import com.safori.infra.ai.gemini.GeminiVoiceAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportVoiceEmotionUseCaseTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock UserAdaptor userAdaptor;
    @Mock VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;
    @Mock GeminiVoiceAnalyzer geminiVoiceAnalyzer;
    @InjectMocks ReportVoiceEmotionUseCase useCase;

    private Voice voiceOf(String username, String voiceKey) {
        User user = User.builder().username(username).build();
        return Voice.builder().id(1L).user(user).voiceKey(voiceKey).build();
    }

    @Test
    @DisplayName("최초 신고 - 저장 후 재분석 비동기 트리거")
    void execute_firstReport_savesAndTriggersReanalysis() {
        Voice voice = voiceOf("owner", "voices/owner/k.m4a");
        given(voiceAdaptor.queryById(1L)).willReturn(voice);
        given(voiceEmotionReportAdaptor.findByVoiceIdAndUsername(1L, "owner")).willReturn(Optional.empty());
        given(userAdaptor.queryUserByUsername("owner")).willReturn(voice.getUser());

        useCase.execute(1L, "owner", EmotionType.SAD, "실제로 슬펐어요");

        verify(voiceEmotionReportAdaptor).save(any(VoiceEmotionReport.class));
        verify(geminiVoiceAnalyzer).reanalyzeAsync(1L, "voices/owner/k.m4a", EmotionType.SAD, "실제로 슬펐어요");
    }

    @Test
    @DisplayName("재신고 - 기존 신고 덮어쓰기 후 재분석 트리거")
    void execute_reReport_updatesAndTriggersReanalysis() {
        Voice voice = voiceOf("owner", "voices/owner/k.m4a");
        VoiceEmotionReport existing = org.mockito.Mockito.mock(VoiceEmotionReport.class);
        given(voiceAdaptor.queryById(1L)).willReturn(voice);
        given(voiceEmotionReportAdaptor.findByVoiceIdAndUsername(1L, "owner")).willReturn(Optional.of(existing));

        useCase.execute(1L, "owner", EmotionType.HAPPY, "다시 보니 기뻤어요");

        verify(existing).update(EmotionType.HAPPY, "다시 보니 기뻤어요");
        verify(voiceEmotionReportAdaptor, never()).save(any(VoiceEmotionReport.class));
        verify(geminiVoiceAnalyzer).reanalyzeAsync(1L, "voices/owner/k.m4a", EmotionType.HAPPY, "다시 보니 기뻤어요");
    }

    @Test
    @DisplayName("타인 소유 신고 - NO_PERMISSION, 재분석 안 함")
    void execute_notOwner_throwsAndNoReanalysis() {
        Voice voice = voiceOf("owner", "voices/owner/k.m4a");
        given(voiceAdaptor.queryById(1L)).willReturn(voice);

        assertThatThrownBy(() -> useCase.execute(1L, "attacker", EmotionType.SAD, "msg"))
                .isInstanceOf(VoiceHandler.class);

        verify(geminiVoiceAnalyzer, never()).reanalyzeAsync(any(), any(), any(), any());
        verify(voiceEmotionReportAdaptor, never()).save(any(VoiceEmotionReport.class));
    }
}
