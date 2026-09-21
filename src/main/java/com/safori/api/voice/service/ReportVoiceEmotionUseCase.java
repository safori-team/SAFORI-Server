package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionReport;
import com.safori.domain.voice.exception.VoiceHandler;
import com.safori.infra.ai.gemini.GeminiVoiceAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /v1/api/users/voices/{voiceId}/report UseCase.
 *
 * <p>AI 분석 결과가 실제 감정과 다를 때 사용자가 올바른 감정을 신고합니다.
 * voice 1개당 user 1개의 신고만 유지 (재신고 시 기존 내용 덮어쓰기).
 */
@UseCase
@RequiredArgsConstructor
public class ReportVoiceEmotionUseCase {

    private final VoiceAdaptor voiceAdaptor;
    private final UserAdaptor userAdaptor;
    private final VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;
    private final GeminiVoiceAnalyzer geminiVoiceAnalyzer;

    @Transactional
    public void execute(Long voiceId, String username, EmotionType reportedEmotion, String message) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if (!voice.getUser().getUsername().equals(username)) {
            throw VoiceHandler.NO_PERMISSION;
        }

        voiceEmotionReportAdaptor.findByVoiceIdAndUsername(voiceId, username)
                .ifPresentOrElse(
                        // 기존 신고 덮어쓰기
                        existing -> existing.update(reportedEmotion, message),
                        // 최초 신고 생성
                        () -> {
                            User user = userAdaptor.queryUserByUsername(username);
                            voiceEmotionReportAdaptor.save(VoiceEmotionReport.builder()
                                    .voice(voice)
                                    .user(user)
                                    .reportedEmotion(reportedEmotion)
                                    .message(message)
                                    .build());
                        }
                );

        // 신고 반영 재분석 (비동기). 실패해도 기존 분석 결과는 보존된다.
        geminiVoiceAnalyzer.reanalyzeAsync(voiceId, voice.getVoiceKey(), reportedEmotion, message);
    }
}
