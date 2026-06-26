package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.common.consts.UserServiceQuestionStaticValues;
import com.safori.domain.question.entity.QuestionCategory;
import com.safori.domain.question.exception.QuestionHandler;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.service.VoiceDomainService;
import com.safori.infra.ai.gemini.GeminiVoiceAnalyzer;
import lombok.RequiredArgsConstructor;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class UploadVoiceFileUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final GeminiVoiceAnalyzer geminiVoiceAnalyzer;

    public Long execute(String username, QuestionCategory questionCategory, int questionIndex,
                        String voiceKey) {
        validateQuestion(questionCategory, questionIndex);
        User user = userAdaptor.queryUserByUsername(username);
        Voice voice = voiceDomainService.uploadVoiceFile(user, voiceKey);
        voiceDomainService.linkVoiceQuestion(voice, questionCategory, questionIndex);

        geminiVoiceAnalyzer.analyzeAsync(voice.getId(), voiceKey);

        return voice.getId();
    }

    private void validateQuestion(QuestionCategory questionCategory, int questionIndex) {
        if (questionCategory == null) {
            throw QuestionHandler.NOT_FOUND;
        }
        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP.get(questionCategory.name());
        if (questions == null || questionIndex < 0 || questionIndex >= questions.size()) {
            throw QuestionHandler.NOT_FOUND;
        }
    }
}
