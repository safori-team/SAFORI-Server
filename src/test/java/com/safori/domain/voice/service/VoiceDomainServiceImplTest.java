package com.safori.domain.voice.service;

import com.safori.domain.question.entity.QuestionCategory;
import com.safori.domain.question.entity.VoiceQuestion;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.repository.VoiceQuestionRepository;
import com.safori.domain.voice.repository.VoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VoiceDomainServiceImplTest {

    @Mock VoiceRepository voiceRepository;
    @Mock VoiceQuestionRepository voiceQuestionRepository;
    @Mock User user;
    @Mock Voice voice;

    @Test
    @DisplayName("uploadVoiceFile - voiceKey/소유자 매핑 + 분석상태 PENDING으로 저장")
    void uploadVoiceFile_savesPendingVoice() {
        VoiceDomainServiceImpl service = new VoiceDomainServiceImpl(voiceRepository, voiceQuestionRepository);
        given(voiceRepository.save(any(Voice.class))).willAnswer(inv -> inv.getArgument(0));

        Voice result = service.uploadVoiceFile(user, "voices/u/uuid.m4a");

        ArgumentCaptor<Voice> captor = ArgumentCaptor.forClass(Voice.class);
        verify(voiceRepository).save(captor.capture());
        Voice saved = captor.getValue();
        assertThat(saved.getVoiceKey()).isEqualTo("voices/u/uuid.m4a");
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.PROCESSING);
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("linkVoiceQuestion - voice/카테고리/인덱스로 VoiceQuestion 저장")
    void linkVoiceQuestion_savesMappedVoiceQuestion() {
        VoiceDomainServiceImpl service = new VoiceDomainServiceImpl(voiceRepository, voiceQuestionRepository);
        given(voiceQuestionRepository.save(any(VoiceQuestion.class))).willAnswer(inv -> inv.getArgument(0));

        VoiceQuestion result = service.linkVoiceQuestion(voice, QuestionCategory.EMOTION, 3);

        ArgumentCaptor<VoiceQuestion> captor = ArgumentCaptor.forClass(VoiceQuestion.class);
        verify(voiceQuestionRepository).save(captor.capture());
        VoiceQuestion saved = captor.getValue();
        assertThat(saved.getVoice()).isSameAs(voice);
        assertThat(saved.getQuestionCategory()).isEqualTo(QuestionCategory.EMOTION);
        assertThat(saved.getQuestionIndex()).isEqualTo(3);
        assertThat(result).isSameAs(saved);
    }
}
