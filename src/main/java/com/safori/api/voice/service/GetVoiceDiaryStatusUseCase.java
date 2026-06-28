package com.safori.api.voice.service;

import com.safori.api.voice.dto.VoiceDiaryStatusResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.exception.VoiceHandler;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class GetVoiceDiaryStatusUseCase {

    private final VoiceAdaptor voiceAdaptor;

    public VoiceDiaryStatusResponse execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if (!voice.getUser().getUsername().equals(username)) {
            throw VoiceHandler.NO_PERMISSION;
        }

        return VoiceDiaryStatusResponse.builder()
                .voiceId(voiceId)
                .diaryStatus(voice.getAnalysisStatus())
                .chatStatus(resolveChatStatus(voice))
                .sessionId(null)
                .build();
    }

    /**
     * chatStatus 도출 규칙:
     * - 일기 분석 FAILED → "failed"
     * - 일기 분석 미완료 → "pending"
     * - 그 외(분석 완료) → "pending" (챗봇 세션 연동은 후속)
     */
    private String resolveChatStatus(Voice voice) {
        if (voice.getAnalysisStatus() == Voice.AnalysisStatus.FAILED) {
            return "failed";
        }
        if (voice.getAnalysisStatus() != Voice.AnalysisStatus.COMPLETED) {
            return "pending";
        }
        return "pending";
    }
}
