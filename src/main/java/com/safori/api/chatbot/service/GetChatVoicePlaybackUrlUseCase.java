package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.VoicePlaybackResponse;
import com.safori.common.annotation.UseCase;
import com.safori.common.service.S3PresignService;
import com.safori.domain.voice.exception.VoiceHandler;

import java.util.Optional;

/**
 * 채팅 음성(USER_VOICE) 발화를 다시 듣기 위한 presigned GET URL을 발급한다.
 *
 * <p>히스토리에 통째로 URL을 싣지 않고, 사용자가 재생을 누른 메시지의 voiceKey로만 호출한다.
 * 소유권은 voiceKey 경로 prefix({@code voices/{username}/})로 검증 — 본인이 업로드한 키만 발급 가능.</p>
 */
@UseCase
public class GetChatVoicePlaybackUrlUseCase {

    /** S3PresignService.GET_EXPIRY(1시간)와 동일. 클라이언트 캐싱 안내용. */
    private static final long URL_EXPIRES_IN_SECONDS = 3600;

    private final Optional<S3PresignService> s3PresignService;

    public GetChatVoicePlaybackUrlUseCase(Optional<S3PresignService> s3PresignService) {
        this.s3PresignService = s3PresignService;
    }

    public VoicePlaybackResponse execute(String username, String voiceKey) {
        verifyOwnership(username, voiceKey);
        S3PresignService service = s3PresignService
                .orElseThrow(() -> new IllegalStateException("S3가 구성되지 않았습니다. AWS 설정을 확인해주세요."));

        String url = service.generateGetUrl(voiceKey);
        return new VoicePlaybackResponse(url, URL_EXPIRES_IN_SECONDS);
    }

    /** 본인 네임스페이스의 키만 허용 (IDOR 방지). 경로 travers('..') 차단. */
    private void verifyOwnership(String username, String voiceKey) {
        String ownerPrefix = "voices/" + username + "/";
        if (voiceKey == null || !voiceKey.startsWith(ownerPrefix) || voiceKey.contains("..")) {
            throw VoiceHandler.NO_PERMISSION;
        }
    }
}
