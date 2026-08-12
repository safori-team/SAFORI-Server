package com.safori.domain.voice.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.question.entity.QuestionCategory;
import com.safori.domain.question.entity.VoiceQuestion;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.repository.VoiceQuestionRepository;
import com.safori.domain.voice.repository.VoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@DomainService
@Transactional
@RequiredArgsConstructor
public class VoiceDomainServiceImpl implements VoiceDomainService {

    private final VoiceRepository voiceRepository;
    private final VoiceQuestionRepository voiceQuestionRepository;

    @Override
    public Voice uploadVoiceFile(User user, String voiceKey) {
        Voice voice = Voice.builder()
                .user(user)
                .voiceKey(voiceKey)
                .voiceTitle("voiceTitle")       // TODO voice Title
                .analysisStatus(Voice.AnalysisStatus.PROCESSING)
                .build();
        return voiceRepository.save(voice);
    }

    @Override
    public VoiceQuestion linkVoiceQuestion(Voice voice, QuestionCategory questionCategory, int questionIndex) {
        VoiceQuestion voiceQuestion = VoiceQuestion.builder()
                .voice(voice)
                .questionCategory(questionCategory)
                .questionIndex(questionIndex)
                .build();
        return voiceQuestionRepository.save(voiceQuestion);
    }
}
