package com.safori.api.emotion.service;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.MonthlyEmotionReport;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.repository.MonthlyEmotionReportRepository;
import com.safori.infra.openai.OpenAiMonthlyReportClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetMonthlyEmotionReportUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock MonthlyEmotionReportRepository monthlyEmotionReportRepository;
    @Mock OpenAiMonthlyReportClient openAiMonthlyReportClient;
    @Mock User user;
    @InjectMocks GetMonthlyEmotionReportUseCase useCase;

    private static final String NO_DATA_MESSAGE = "해당 달에는 감정 분석 데이터가 없었습니다.";
    private static final Map<EmotionType, Long> COUNTS = Map.of(EmotionType.HAPPY, 2L, EmotionType.SAD, 1L);

    @Test
    @DisplayName("데이터 없고 캐시도 없음 - AI 호출 없이 NO_DATA 메시지 저장·반환")
    void execute_noDataNoCache_savesNoDataMessage() {
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(monthlyEmotionReportRepository.findByUser_IdAndReportMonth(1L, "2024-01"))
                .willReturn(Optional.empty());
        given(monthlyEmotionReportRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String msg = useCase.execute("user01", "2024-01", Map.of(), List.of());

        assertThat(msg).isEqualTo(NO_DATA_MESSAGE);
        verify(openAiMonthlyReportClient, never()).generateMonthlyReport(any(), any(), any());
        verify(monthlyEmotionReportRepository).save(any());
    }

    @Test
    @DisplayName("캐시 최신 composite id 동일 - AI·저장 없이 캐시 메시지 반환")
    void execute_cacheHitSameLatest_returnsCached() {
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getId()).willReturn(100L);
        MonthlyEmotionReport cached = mock(MonthlyEmotionReport.class);
        given(cached.getLatestVoiceCompositeId()).willReturn(100L);
        given(cached.getReportMessage()).willReturn("캐시된 월간 리포트");
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(monthlyEmotionReportRepository.findByUser_IdAndReportMonth(1L, "2024-01"))
                .willReturn(Optional.of(cached));

        String msg = useCase.execute("user01", "2024-01", COUNTS, List.of(vc));

        assertThat(msg).isEqualTo("캐시된 월간 리포트");
        verify(openAiMonthlyReportClient, never()).generateMonthlyReport(any(), any(), any());
        verify(monthlyEmotionReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("데이터 있고 캐시 없음 - AI 호출 후 신규 저장, AI 메시지 반환")
    void execute_hasDataNoCache_generatesAndSaves() {
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getId()).willReturn(100L);
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(user.getName()).willReturn("홍길동");
        given(monthlyEmotionReportRepository.findByUser_IdAndReportMonth(1L, "2024-01"))
                .willReturn(Optional.empty());
        given(openAiMonthlyReportClient.generateMonthlyReport(eq("홍길동"), any(), any())).willReturn("AI 월간 리포트");
        given(monthlyEmotionReportRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String msg = useCase.execute("user01", "2024-01", COUNTS, List.of(vc));

        assertThat(msg).isEqualTo("AI 월간 리포트");
        verify(openAiMonthlyReportClient).generateMonthlyReport(eq("홍길동"), any(), any());
        verify(monthlyEmotionReportRepository).save(any());
    }

    @Test
    @DisplayName("캐시 있지만 최신 composite id 다름 - AI 재생성 후 update(신규 저장 아님)")
    void execute_cacheStale_regeneratesAndUpdates() {
        VoiceComposite vc = mock(VoiceComposite.class);
        given(vc.getId()).willReturn(200L);
        MonthlyEmotionReport cached = mock(MonthlyEmotionReport.class);
        given(cached.getLatestVoiceCompositeId()).willReturn(100L);
        given(cached.getReportMessage()).willReturn("새 월간 리포트");
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(user.getName()).willReturn("홍길동");
        given(monthlyEmotionReportRepository.findByUser_IdAndReportMonth(1L, "2024-01"))
                .willReturn(Optional.of(cached));
        given(openAiMonthlyReportClient.generateMonthlyReport(eq("홍길동"), any(), any())).willReturn("새 월간 리포트");

        String msg = useCase.execute("user01", "2024-01", COUNTS, List.of(vc));

        assertThat(msg).isEqualTo("새 월간 리포트");
        verify(cached).update(200L, "새 월간 리포트");
        verify(monthlyEmotionReportRepository, never()).save(any());
    }
}
