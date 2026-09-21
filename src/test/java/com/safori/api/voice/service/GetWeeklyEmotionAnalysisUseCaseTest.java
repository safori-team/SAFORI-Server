package com.safori.api.voice.service;

import com.safori.api.common.dto.WeekDay;
import com.safori.api.emotion.dto.WeekDayEmotion;
import com.safori.api.emotion.dto.WeeklyAnalysisCombinedResponse;
import com.safori.api.emotion.service.GetWeeklyEmotionReportUseCase;
import com.safori.common.exception.GeneralException;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GetWeeklyEmotionAnalysisUseCaseTest {

    @Mock VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock GetWeeklyEmotionReportUseCase getWeeklyEmotionReportUseCase;
    @InjectMocks GetWeeklyEmotionAnalysisUseCase useCase;

    @Test
    @DisplayName("주간 분석 - 일기 있는 날에 emotionType·voiceId 매칭, 없는 날은 null")
    void execute_matchesEmotionAndVoiceIdPerDay() {
        String username = "user01";
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getCreatedDate()).willReturn(LocalDateTime.of(2024, 1, 15, 10, 0)); // MON
        given(vc.getTopEmotion()).willReturn(EmotionType.HAPPY);
        Voice voice = mock(Voice.class);
        given(vc.getVoice()).willReturn(voice);
        given(voice.getId()).willReturn(42L);
        given(voiceCompositeAdaptor.queryByUsernameAndDateRange(eq(username), any(), any()))
                .willReturn(List.of(vc));
        given(getWeeklyEmotionReportUseCase.execute(eq(username), eq("2024-01"), eq(3), any(), any()))
                .willReturn("주간 리포트");

        WeeklyAnalysisCombinedResponse resp = useCase.execute(username, "2024-01", 3);

        assertThat(resp.getReportMessage()).isEqualTo("주간 리포트");
        List<WeekDayEmotion> em = resp.getWeeklyEmotions();
        assertThat(em).hasSize(7); // 2024-01-14(일) ~ 2024-01-20(토)
        assertThat(em.get(0).getDate()).isEqualTo(LocalDate.of(2024, 1, 14));
        assertThat(em.get(0).getWeekDay()).isEqualTo(WeekDay.SUN);

        WeekDayEmotion mon = em.stream()
                .filter(w -> w.getDate().equals(LocalDate.of(2024, 1, 15)))
                .findFirst().orElseThrow();
        assertThat(mon.getWeekDay()).isEqualTo(WeekDay.MON);
        assertThat(mon.getEmotionType()).isEqualTo(EmotionType.HAPPY);
        assertThat(mon.getVoiceId()).isEqualTo(42L);

        em.stream()
                .filter(w -> !w.getDate().equals(LocalDate.of(2024, 1, 15)))
                .forEach(w -> {
                    assertThat(w.getEmotionType()).isNull();
                    assertThat(w.getVoiceId()).isNull();
                });
    }

    @Test
    @DisplayName("주간 분석 - 방어적으로 하루 복수 건이면 최신(createdDate 큰) 건의 감정·voiceId 선택")
    void execute_multiplePerDay_picksLatest() {
        String username = "user01";
        VoiceComposite earlier = mock(VoiceComposite.class);
        given(earlier.getCreatedDate()).willReturn(LocalDateTime.of(2024, 1, 15, 9, 0));
        VoiceComposite later = mock(VoiceComposite.class);
        given(later.getCreatedDate()).willReturn(LocalDateTime.of(2024, 1, 15, 18, 0));
        given(later.getTopEmotion()).willReturn(EmotionType.ANGRY);
        Voice voice = mock(Voice.class);
        given(later.getVoice()).willReturn(voice);
        given(voice.getId()).willReturn(99L);
        given(voiceCompositeAdaptor.queryByUsernameAndDateRange(eq(username), any(), any()))
                .willReturn(List.of(earlier, later));
        given(getWeeklyEmotionReportUseCase.execute(eq(username), eq("2024-01"), eq(3), any(), any()))
                .willReturn("r");

        WeeklyAnalysisCombinedResponse resp = useCase.execute(username, "2024-01", 3);

        WeekDayEmotion mon = resp.getWeeklyEmotions().stream()
                .filter(w -> w.getDate().equals(LocalDate.of(2024, 1, 15)))
                .findFirst().orElseThrow();
        assertThat(mon.getEmotionType()).isEqualTo(EmotionType.ANGRY);
        assertThat(mon.getVoiceId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("주간 분석 - week가 0 이하면 GeneralException(DATE_RANGE_INVALID_WEEK)")
    void execute_invalidWeek_throws() {
        assertThatThrownBy(() -> useCase.execute("user01", "2024-01", 0))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("이번 주 조회(홈) - 오늘 일기의 emotionType·voiceId 매칭, 리포트 호출 없음")
    void executeCurrentWeek_matchesTodayVoice() {
        String username = "user01";
        LocalDate today = LocalDate.now();
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getCreatedDate()).willReturn(today.atTime(12, 0));
        given(vc.getTopEmotion()).willReturn(EmotionType.SAD);
        Voice voice = mock(Voice.class);
        given(vc.getVoice()).willReturn(voice);
        given(voice.getId()).willReturn(7L);
        given(voiceCompositeAdaptor.queryByUsernameAndDateRange(eq(username), any(), any()))
                .willReturn(List.of(vc));

        List<WeekDayEmotion> result = useCase.executeCurrentWeek(username);

        WeekDayEmotion todayEntry = result.stream()
                .filter(w -> w.getDate().equals(today))
                .findFirst().orElseThrow();
        assertThat(todayEntry.getEmotionType()).isEqualTo(EmotionType.SAD);
        assertThat(todayEntry.getVoiceId()).isEqualTo(7L);

        result.stream()
                .filter(w -> !w.getDate().equals(today))
                .forEach(w -> {
                    assertThat(w.getEmotionType()).isNull();
                    assertThat(w.getVoiceId()).isNull();
                });
    }
}
