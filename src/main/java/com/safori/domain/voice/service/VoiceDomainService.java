package com.safori.domain.voice.service;

import com.safori.domain.question.entity.QuestionCategory;
import com.safori.domain.question.entity.VoiceQuestion;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;

public interface VoiceDomainService {

    Voice uploadVoiceFile(User user, String voiceKey);

    VoiceQuestion linkVoiceQuestion(Voice voice, QuestionCategory questionCategory, int questionIndex);
}
