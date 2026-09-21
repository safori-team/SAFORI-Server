package com.safori.api.voice.service;

import com.safori.api.emotion.dto.MonthlyAnalysisCombinedResponse;
import com.safori.api.emotion.service.GetMonthlyEmotionReportUseCase;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.entity.VoiceComposite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetMonthlyEmotionAnalysisUseCaseTest {

    @Mock VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock GetMonthlyEmotionReportUseCase getMonthlyEmotionReportUseCase;
    @InjectMocks GetMonthlyEmotionAnalysisUseCase useCase;

    @Test
    @DisplayName("월간 분석 - 감정별 count·대표감정·총계 집계 후 리포트 위임")
    void execute_aggregatesCountsAndTopEmotion() {
        String username = "user01";
        VoiceComposite h1 = mock(VoiceComposite.class);
        VoiceComposite h2 = mock(VoiceComposite.class);
        VoiceComposite s1 = mock(VoiceComposite.class);
        given(h1.getTopEmotion()).willReturn(EmotionType.HAPPY);
        given(h2.getTopEmotion()).willReturn(EmotionType.HAPPY);
        given(s1.getTopEmotion()).willReturn(EmotionType.SAD);
        given(voiceCompositeAdaptor.queryByUsernameAndDateRange(eq(username), any(), any()))
                .willReturn(List.of(h1, h2, s1));
        given(getMonthlyEmotionReportUseCase.execute(eq(username), eq("2024-01"), any(), any()))
                .willReturn("월간 리포트");

        MonthlyAnalysisCombinedResponse resp = useCase.execute(username, "2024-01");

        assertThat(resp.getMonthlyEmotionCounts().get(EmotionType.HAPPY)).isEqualTo(2L);
        assertThat(resp.getMonthlyEmotionCounts().get(EmotionType.SAD)).isEqualTo(1L);
        assertThat(resp.getMonthlyEmotionCounts().get(EmotionType.NEUTRAL)).isEqualTo(0L);
        assertThat(resp.getTopEmotion()).isEqualTo(EmotionType.HAPPY);
        assertThat(resp.getTotalCount()).isEqualTo(3);
        assertThat(resp.getReportMessage()).isEqualTo("월간 리포트");
        verify(getMonthlyEmotionReportUseCase).execute(eq(username), eq("2024-01"), any(), any());
    }

    @Test
    @DisplayName("월간 분석 - 데이터 없으면 총계 0·전 감정 count 0, 리포트는 위임")
    void execute_noData_zeroCounts() {
        String username = "user01";
        given(voiceCompositeAdaptor.queryByUsernameAndDateRange(eq(username), any(), any()))
                .willReturn(List.of());
        given(getMonthlyEmotionReportUseCase.execute(eq(username), eq("2024-01"), any(), any()))
                .willReturn("데이터 없음");

        MonthlyAnalysisCombinedResponse resp = useCase.execute(username, "2024-01");

        assertThat(resp.getTotalCount()).isEqualTo(0);
        assertThat(resp.getMonthlyEmotionCounts().values()).allMatch(c -> c == 0L);
        assertThat(resp.getReportMessage()).isEqualTo("데이터 없음");
    }
}
