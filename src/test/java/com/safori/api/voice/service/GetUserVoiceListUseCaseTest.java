package com.safori.api.voice.service;

import com.safori.api.voice.dto.VoiceListItem;
import com.safori.api.voice.dto.VoiceListResponse;
import com.safori.common.consts.UserServiceQuestionStaticValues;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.question.entity.QuestionCategory;
import com.safori.domain.question.entity.VoiceQuestion;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.repository.VoiceContentRepository;
import com.safori.domain.voice.repository.VoiceQuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetUserVoiceListUseCaseTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock VoiceQuestionRepository voiceQuestionRepository;
    @Mock VoiceContentRepository voiceContentRepository;
    @Mock VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;

    private GetUserVoiceListUseCase useCase() {
        return new GetUserVoiceListUseCase(voiceAdaptor, voiceCompositeAdaptor,
                voiceQuestionRepository, voiceContentRepository, voiceEmotionReportAdaptor);
    }

    @Test
    @DisplayName("전체 목록 - 음성/감정/질문/전사 결합 매핑")
    void execute_all_mapsCombinedItem() {
        User user = User.builder().username("u").build();
        Voice voice = Voice.builder()
                .id(1L).user(user)
                .createdDate(LocalDateTime.of(2026, 1, 15, 10, 0))
                .analysisStatus(Voice.AnalysisStatus.COMPLETED)
                .build();
        VoiceComposite composite = VoiceComposite.builder().voice(voice).topEmotion(EmotionType.HAPPY).build();
        VoiceContent content = VoiceContent.builder().voice(voice).content("오늘 즐거웠어요").build();
        VoiceQuestion vq = VoiceQuestion.builder().voice(voice)
                .questionCategory(QuestionCategory.EMOTION).questionIndex(0).build();

        given(voiceAdaptor.queryByUsername("u")).willReturn(List.of(voice));
        given(voiceCompositeAdaptor.queryByVoiceIds(List.of(1L))).willReturn(List.of(composite));
        given(voiceQuestionRepository.findByVoice_IdIn(List.of(1L))).willReturn(List.of(vq));
        given(voiceContentRepository.findByVoice_IdIn(List.of(1L))).willReturn(List.of(content));
        given(voiceEmotionReportAdaptor.findReportedEmotions(List.of(1L), "u")).willReturn(Map.of());

        VoiceListResponse res = useCase().execute("u");

        assertThat(res.getVoices()).hasSize(1);
        VoiceListItem item = res.getVoices().get(0);
        assertThat(item.getVoiceId()).isEqualTo(1L);
        assertThat(item.getCreatedAt()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(item.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.COMPLETED);
        assertThat(item.getEmotion()).isEqualTo(EmotionType.HAPPY);
        assertThat(item.getQuestionTitle())
                .isEqualTo(UserServiceQuestionStaticValues.QUESTION_MAP.get("EMOTION").get(0));
        assertThat(item.getContent()).isEqualTo("오늘 즐거웠어요");
    }

    @Test
    @DisplayName("분석 전 - 감정/전사/질문 null")
    void execute_beforeAnalysis_nullFields() {
        User user = User.builder().username("u").build();
        Voice voice = Voice.builder()
                .id(2L).user(user)
                .createdDate(LocalDateTime.of(2026, 1, 16, 9, 0))
                .analysisStatus(Voice.AnalysisStatus.PROCESSING)
                .build();

        given(voiceAdaptor.queryByUsername("u")).willReturn(List.of(voice));
        given(voiceCompositeAdaptor.queryByVoiceIds(List.of(2L))).willReturn(List.of());
        given(voiceQuestionRepository.findByVoice_IdIn(List.of(2L))).willReturn(List.of());
        given(voiceContentRepository.findByVoice_IdIn(List.of(2L))).willReturn(List.of());
        given(voiceEmotionReportAdaptor.findReportedEmotions(List.of(2L), "u")).willReturn(Map.of());

        VoiceListItem item = useCase().execute("u").getVoices().get(0);

        assertThat(item.getEmotion()).isNull();
        assertThat(item.getContent()).isNull();
        assertThat(item.getQuestionTitle()).isNull();
        assertThat(item.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.PROCESSING);
    }

    @Test
    @DisplayName("신고 감정 있으면 AI 대표감정보다 우선 노출")
    void execute_reportedEmotion_overridesAiTopEmotion() {
        User user = User.builder().username("u").build();
        Voice voice = Voice.builder()
                .id(1L).user(user)
                .createdDate(LocalDateTime.of(2026, 1, 15, 10, 0))
                .analysisStatus(Voice.AnalysisStatus.COMPLETED)
                .build();
        VoiceComposite composite = VoiceComposite.builder().voice(voice).topEmotion(EmotionType.HAPPY).build();

        given(voiceAdaptor.queryByUsername("u")).willReturn(List.of(voice));
        given(voiceCompositeAdaptor.queryByVoiceIds(List.of(1L))).willReturn(List.of(composite));
        given(voiceQuestionRepository.findByVoice_IdIn(List.of(1L))).willReturn(List.of());
        given(voiceContentRepository.findByVoice_IdIn(List.of(1L))).willReturn(List.of());
        given(voiceEmotionReportAdaptor.findReportedEmotions(List.of(1L), "u"))
                .willReturn(Map.of(1L, EmotionType.SAD));

        VoiceListItem item = useCase().execute("u").getVoices().get(0);

        assertThat(item.getEmotion()).isEqualTo(EmotionType.SAD);  // AI=HAPPY 무시, 신고=SAD 우선
    }
}
