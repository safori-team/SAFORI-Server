package com.safori.api.voice.service;

import com.safori.api.voice.dto.VoiceDetailResponse;
import com.safori.common.service.S3PresignService;
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
import com.safori.domain.voice.exception.VoiceHandler;
import com.safori.domain.voice.repository.VoiceContentRepository;
import com.safori.domain.voice.repository.VoiceQuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetUserVoiceDetailUseCaseTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock VoiceQuestionRepository voiceQuestionRepository;
    @Mock VoiceContentRepository voiceContentRepository;
    @Mock VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;

    private GetUserVoiceDetailUseCase useCase(Optional<S3PresignService> s3) {
        return new GetUserVoiceDetailUseCase(voiceAdaptor, voiceCompositeAdaptor,
                voiceQuestionRepository, voiceContentRepository, s3, voiceEmotionReportAdaptor);
    }

    @Test
    @DisplayName("소유자 - 음성/감정/전사/질문 결합 상세 반환")
    void execute_owner_returnsDetail() {
        User user = User.builder().username("u").build();
        Voice voice = Voice.builder()
                .id(1L).user(user).voiceKey("voices/u/k.m4a")
                .createdDate(LocalDateTime.of(2026, 1, 15, 10, 0))
                .analysisStatus(Voice.AnalysisStatus.COMPLETED)
                .build();
        VoiceComposite composite = VoiceComposite.builder().voice(voice).topEmotion(EmotionType.SAD).build();
        VoiceContent content = VoiceContent.builder().voice(voice).content("힘든 하루였어요").build();
        VoiceQuestion vq = VoiceQuestion.builder().voice(voice)
                .questionCategory(QuestionCategory.STRESS).questionIndex(0).build();

        given(voiceAdaptor.queryById(1L)).willReturn(voice);
        given(voiceCompositeAdaptor.queryByVoiceIds(List.of(1L))).willReturn(List.of(composite));
        given(voiceQuestionRepository.findByVoice_Id(1L)).willReturn(Optional.of(vq));
        given(voiceContentRepository.findByVoice_Id(1L)).willReturn(Optional.of(content));
        given(voiceEmotionReportAdaptor.findByVoiceIdAndUsername(1L, "u")).willReturn(Optional.empty());

        VoiceDetailResponse res = useCase(Optional.empty()).execute(1L, "u");

        assertThat(res.getVoiceId()).isEqualTo(1L);
        assertThat(res.getTopEmotion()).isEqualTo(EmotionType.SAD);
        assertThat(res.getContent()).isEqualTo("힘든 하루였어요");
        assertThat(res.getQuestionTitle()).isNotNull();
        assertThat(res.getS3Url()).isNull();          // S3 미구성
        assertThat(res.getReportedEmotion()).isNull();
    }

    @Test
    @DisplayName("타인 소유 - NO_PERMISSION")
    void execute_notOwner_throws() {
        User owner = User.builder().username("owner").build();
        Voice voice = Voice.builder().id(1L).user(owner).voiceKey("k").build();
        given(voiceAdaptor.queryById(1L)).willReturn(voice);

        assertThatThrownBy(() -> useCase(Optional.empty()).execute(1L, "attacker"))
                .isInstanceOf(VoiceHandler.class);
    }
}
