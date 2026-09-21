package com.safori.api.emotion.service;

import com.safori.api.emotion.dto.EmotionLabelDiaryListResponse;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.domain.voice.repository.VoiceContentRepository;
import com.safori.domain.voice.repository.VoiceQuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GetEmotionLabelDiaryListUseCaseTest {

    @Mock VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    @Mock VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock VoiceQuestionRepository voiceQuestionRepository;
    @Mock VoiceContentRepository voiceContentRepository;
    @Mock VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;
    @InjectMocks GetEmotionLabelDiaryListUseCase useCase;

    @Test
    @DisplayName("label 일기목록 - category 추출 + 일기항목(감정·내용) 구성")
    void execute_buildsDiaryList() {
        String username = "user01";
        Voice voice = mock(Voice.class);
        given(voice.getId()).willReturn(10L);
        given(voice.getCreatedDate()).willReturn(LocalDateTime.of(2024, 1, 15, 10, 0));
        given(voice.getAnalysisStatus()).willReturn(Voice.AnalysisStatus.COMPLETED);

        VoiceEmotionLabel labelEntity = mock(VoiceEmotionLabel.class);
        given(labelEntity.getLabel()).willReturn("joy");
        given(labelEntity.getCategory()).willReturn("happy");

        VoiceComposite composite = mock(VoiceComposite.class);
        given(composite.getVoice()).willReturn(voice);
        given(composite.getTopEmotion()).willReturn(EmotionType.HAPPY);

        VoiceContent content = mock(VoiceContent.class);
        given(content.getVoice()).willReturn(voice);
        given(content.getContent()).willReturn("오늘 즐거웠어요");

        given(voiceEmotionLabelAdaptor.findVoicesByLabel(eq(username), eq("joy"), eq(2024), eq(1)))
                .willReturn(List.of(voice));
        given(voiceEmotionLabelAdaptor.findByVoiceId(10L)).willReturn(List.of(labelEntity));
        given(voiceCompositeAdaptor.queryByVoiceIds(List.of(10L))).willReturn(List.of(composite));
        given(voiceQuestionRepository.findByVoice_IdIn(List.of(10L))).willReturn(List.of());
        given(voiceContentRepository.findByVoice_IdIn(List.of(10L))).willReturn(List.of(content));
        given(voiceEmotionReportAdaptor.findReportedEmotions(List.of(10L), username)).willReturn(Map.of());

        EmotionLabelDiaryListResponse resp = useCase.execute(username, "2024-01", "joy");

        assertThat(resp.getYearMonth()).isEqualTo("2024-01");
        assertThat(resp.getLabel()).isEqualTo("joy");
        assertThat(resp.getCategory()).isEqualTo("happy");
        assertThat(resp.getDiaries()).hasSize(1);
        assertThat(resp.getDiaries().get(0).getVoiceId()).isEqualTo(10L);
        assertThat(resp.getDiaries().get(0).getEmotion()).isEqualTo(EmotionType.HAPPY);
        assertThat(resp.getDiaries().get(0).getContent()).isEqualTo("오늘 즐거웠어요");
        assertThat(resp.getDiaries().get(0).getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.COMPLETED);
        assertThat(resp.getDiaries().get(0).getQuestionTitle()).isNull();
    }

    @Test
    @DisplayName("label 일기목록 - 해당 label 일기 없으면 category null·빈 목록")
    void execute_noVoices_emptyList() {
        given(voiceEmotionLabelAdaptor.findVoicesByLabel(eq("user01"), eq("joy"), eq(2024), eq(1)))
                .willReturn(List.of());

        EmotionLabelDiaryListResponse resp = useCase.execute("user01", "2024-01", "joy");

        assertThat(resp.getCategory()).isNull();
        assertThat(resp.getDiaries()).isEmpty();
    }
}
