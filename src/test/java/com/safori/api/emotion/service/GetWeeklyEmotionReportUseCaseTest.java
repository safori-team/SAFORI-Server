package com.safori.api.emotion.service;

import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.WeeklyEmotionReport;
import com.safori.domain.voice.repository.WeeklyEmotionReportRepository;
import com.safori.infra.openai.OpenAiWeeklyReportClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetWeeklyEmotionReportUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock WeeklyEmotionReportRepository weeklyEmotionReportRepository;
    @Mock OpenAiWeeklyReportClient openAiWeeklyReportClient;
    @Mock User user;
    @InjectMocks GetWeeklyEmotionReportUseCase useCase;

    private static final String NO_DATA_MESSAGE = "해당 주에는 감정분석 데이터가 없었습니다.";

    @Test
    @DisplayName("데이터 없고 캐시도 없음 - AI 호출 없이 NO_DATA 메시지 저장·반환")
    void execute_noDataNoCache_savesNoDataMessage() {
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(weeklyEmotionReportRepository.findByUser_IdAndReportMonthAndReportWeek(1L, "2024-01", 3))
                .willReturn(Optional.empty());
        given(weeklyEmotionReportRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String msg = useCase.execute("user01", "2024-01", 3, List.of(), List.of());

        assertThat(msg).isEqualTo(NO_DATA_MESSAGE);
        verify(openAiWeeklyReportClient, never()).generateWeeklyReport(any(), any(), any());
        verify(weeklyEmotionReportRepository).save(any());
    }

    @Test
    @DisplayName("캐시 최신 composite id 동일 - AI·저장 없이 캐시 메시지 반환")
    void execute_cacheHitSameLatest_returnsCached() {
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getId()).willReturn(100L);
        WeeklyEmotionReport cached = mock(WeeklyEmotionReport.class);
        given(cached.getLatestVoiceCompositeId()).willReturn(100L);
        given(cached.getReportMessage()).willReturn("캐시된 리포트");
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(weeklyEmotionReportRepository.findByUser_IdAndReportMonthAndReportWeek(1L, "2024-01", 3))
                .willReturn(Optional.of(cached));

        String msg = useCase.execute("user01", "2024-01", 3, List.of(), List.of(vc));

        assertThat(msg).isEqualTo("캐시된 리포트");
        verify(openAiWeeklyReportClient, never()).generateWeeklyReport(any(), any(), any());
        verify(weeklyEmotionReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("데이터 있고 캐시 없음 - AI 호출 후 신규 저장, AI 메시지 반환")
    void execute_hasDataNoCache_generatesAndSaves() {
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getId()).willReturn(100L);
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(user.getName()).willReturn("홍길동");
        given(weeklyEmotionReportRepository.findByUser_IdAndReportMonthAndReportWeek(1L, "2024-01", 3))
                .willReturn(Optional.empty());
        given(openAiWeeklyReportClient.generateWeeklyReport(eq("홍길동"), any(), any())).willReturn("AI 리포트");
        given(weeklyEmotionReportRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String msg = useCase.execute("user01", "2024-01", 3, List.of(), List.of(vc));

        assertThat(msg).isEqualTo("AI 리포트");
        verify(openAiWeeklyReportClient).generateWeeklyReport(eq("홍길동"), any(), any());
        verify(weeklyEmotionReportRepository).save(any());
    }

    @Test
    @DisplayName("캐시 있지만 최신 composite id 다름 - AI 재생성 후 update(신규 저장 아님)")
    void execute_cacheStale_regeneratesAndUpdates() {
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getId()).willReturn(200L);
        WeeklyEmotionReport cached = mock(WeeklyEmotionReport.class);
        given(cached.getLatestVoiceCompositeId()).willReturn(100L);
        given(cached.getReportMessage()).willReturn("새 리포트");
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(user.getName()).willReturn("홍길동");
        given(weeklyEmotionReportRepository.findByUser_IdAndReportMonthAndReportWeek(1L, "2024-01", 3))
                .willReturn(Optional.of(cached));
        given(openAiWeeklyReportClient.generateWeeklyReport(eq("홍길동"), any(), any())).willReturn("새 리포트");

        String msg = useCase.execute("user01", "2024-01", 3, List.of(), List.of(vc));

        assertThat(msg).isEqualTo("새 리포트");
        verify(cached).update(200L, "새 리포트");
        verify(weeklyEmotionReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("데이터 있고 AI 실패·캐시 없음 - 500 대신 fallback 메시지, 저장 안 함")
    void execute_aiFailsNoCache_returnsFallbackWithoutSaving() {
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getId()).willReturn(100L);
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(user.getName()).willReturn("홍길동");
        given(weeklyEmotionReportRepository.findByUser_IdAndReportMonthAndReportWeek(1L, "2024-01", 3))
                .willReturn(Optional.empty());
        given(openAiWeeklyReportClient.generateWeeklyReport(any(), any(), any()))
                .willThrow(new IllegalStateException("OPENAI_API_KEY is not configured"));

        String msg = useCase.execute("user01", "2024-01", 3, List.of(), List.of(vc));

        assertThat(msg).isEqualTo("이번 주 감정 리포트를 준비하지 못했습니다. 잠시 후 다시 확인해주세요.");
        verify(weeklyEmotionReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("데이터 있고 AI 실패·캐시 있음 - 실패분 저장 없이 기존 캐시 메시지 반환")
    void execute_aiFailsWithStaleCache_returnsCachedWithoutSaving() {
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getId()).willReturn(200L);
        WeeklyEmotionReport cached = mock(WeeklyEmotionReport.class);
        given(cached.getLatestVoiceCompositeId()).willReturn(100L);
        given(cached.getReportMessage()).willReturn("직전 리포트");
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(user.getName()).willReturn("홍길동");
        given(weeklyEmotionReportRepository.findByUser_IdAndReportMonthAndReportWeek(1L, "2024-01", 3))
                .willReturn(Optional.of(cached));
        given(openAiWeeklyReportClient.generateWeeklyReport(any(), any(), any()))
                .willThrow(new RuntimeException("OpenAI 5xx"));

        String msg = useCase.execute("user01", "2024-01", 3, List.of(), List.of(vc));

        assertThat(msg).isEqualTo("직전 리포트");
        verify(cached, never()).update(any(), any());
        verify(weeklyEmotionReportRepository, never()).save(any());
    }
}
