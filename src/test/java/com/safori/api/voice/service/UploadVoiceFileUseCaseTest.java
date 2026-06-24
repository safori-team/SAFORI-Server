package com.safori.api.voice.service;

import com.safori.domain.question.entity.QuestionCategory;
import com.safori.domain.question.exception.QuestionHandler;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.service.VoiceDomainService;
import com.safori.infra.ai.gemini.GeminiVoiceAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadVoiceFileUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock VoiceDomainService voiceDomainService;
    @Mock GeminiVoiceAnalyzer geminiVoiceAnalyzer;
    @Mock User user;
    @Mock Voice voice;

    @Test
    @DisplayName("업로드 성공 - voice 저장 + 질문 연결 후 voiceId 반환")
    void execute_savesVoiceAndLinksQuestion() {
        String username = "testUser";
        String voiceKey = "voices/testUser/uuid.m4a";
        QuestionCategory category = QuestionCategory.EMOTION;
        int index = 0;

        given(userAdaptor.queryUserByUsername(username)).willReturn(user);
        given(voiceDomainService.uploadVoiceFile(user, voiceKey)).willReturn(voice);
        given(voice.getId()).willReturn(1L);

        UploadVoiceFileUseCase useCase =
                new UploadVoiceFileUseCase(userAdaptor, voiceDomainService, geminiVoiceAnalyzer);

        Long voiceId = useCase.execute(username, category, index, voiceKey);

        assertThat(voiceId).isEqualTo(1L);
        verify(voiceDomainService).uploadVoiceFile(user, voiceKey);
        verify(voiceDomainService).linkVoiceQuestion(voice, category, index);
        verify(geminiVoiceAnalyzer).analyzeAsync(1L, voiceKey);
    }

    @Test
    @DisplayName("유효하지 않은 질문 인덱스 - QUESTION_NOT_FOUND, 저장 안 함")
    void execute_invalidQuestionIndex_throws() {
        UploadVoiceFileUseCase useCase =
                new UploadVoiceFileUseCase(userAdaptor, voiceDomainService, geminiVoiceAnalyzer);

        assertThatThrownBy(() ->
                useCase.execute("testUser", QuestionCategory.EMOTION, 999, "voices/u/uuid.m4a"))
                .isInstanceOf(QuestionHandler.class);

        verify(userAdaptor, never()).queryUserByUsername("testUser");
        verify(voiceDomainService, never()).uploadVoiceFile(user, "voices/u/uuid.m4a");
    }
}
