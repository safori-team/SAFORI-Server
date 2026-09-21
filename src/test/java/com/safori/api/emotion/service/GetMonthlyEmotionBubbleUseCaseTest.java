package com.safori.api.emotion.service;

import com.safori.api.emotion.dto.MonthlyEmotionBubbleResponse;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetMonthlyEmotionBubbleUseCaseTest {

    @Mock VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    @InjectMocks GetMonthlyEmotionBubbleUseCase useCase;

    @Test
    @DisplayName("버블차트 - label 집계 Object[]를 EmotionLabelItem으로 매핑(avgIntensity Double→int)")
    void execute_mapsLabelStats() {
        String username = "user01";
        List<Object[]> rows = List.of(
                new Object[]{"joy", "happy", 5L, 7523.0},
                new Object[]{"anxiety", "anxiety", 3L, 4000.0}
        );
        given(voiceEmotionLabelAdaptor.findMonthlyLabelStats(eq(username), eq(2024), eq(1)))
                .willReturn(rows);

        MonthlyEmotionBubbleResponse resp = useCase.execute(username, "2024-01");

        assertThat(resp.getYearMonth()).isEqualTo("2024-01");
        assertThat(resp.getLabels()).hasSize(2);
        assertThat(resp.getLabels().get(0).getLabel()).isEqualTo("joy");
        assertThat(resp.getLabels().get(0).getLabelKr()).isEqualTo("기쁨");
        assertThat(resp.getLabels().get(0).getCategory()).isEqualTo("happy");
        assertThat(resp.getLabels().get(0).getDiaryCount()).isEqualTo(5L);
        assertThat(resp.getLabels().get(0).getAvgIntensityX1000()).isEqualTo(7523);
        assertThat(resp.getLabels().get(1).getLabel()).isEqualTo("anxiety");
        assertThat(resp.getLabels().get(1).getLabelKr()).isEqualTo("불안");
        assertThat(resp.getLabels().get(1).getAvgIntensityX1000()).isEqualTo(4000);
    }

    @Test
    @DisplayName("버블차트 - 집계 결과 없으면 빈 labels")
    void execute_noStats_emptyLabels() {
        given(voiceEmotionLabelAdaptor.findMonthlyLabelStats(eq("user01"), eq(2024), eq(1)))
                .willReturn(List.of());

        MonthlyEmotionBubbleResponse resp = useCase.execute("user01", "2024-01");

        assertThat(resp.getLabels()).isEmpty();
    }
}
