package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.common.consts.UserServiceQuestionStaticValues;
import com.safori.domain.question.entity.QuestionCategory;
import com.safori.domain.question.exception.QuestionHandler;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.exception.VoiceHandler;
import com.safori.domain.voice.service.VoiceDomainService;
import com.safori.infra.ai.gemini.GeminiVoiceAnalyzer;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@UseCase
@RequiredArgsConstructor
public class UploadVoiceFileUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final GeminiVoiceAnalyzer geminiVoiceAnalyzer;

    public Long execute(String username, QuestionCategory questionCategory, int questionIndex,
                        String voiceKey) {
        validateQuestion(questionCategory, questionIndex);
        // 1일 1일기 제약 — 오늘 작성한 일기가 이미 있으면 거부 (삭제 후 재작성은 허용)
        if (!voiceAdaptor.queryByUsernameAndCreatedAt(username, LocalDate.now()).isEmpty()) {
            throw VoiceHandler.ALREADY_EXISTS_TODAY;
        }
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
