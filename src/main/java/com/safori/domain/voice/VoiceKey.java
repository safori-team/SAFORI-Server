package com.safori.domain.voice;

import com.safori.domain.voice.exception.VoiceHandler;

/**
 * 음성 S3 키({@code voices/{username}/{uuid}.{ext}})의 소유권 검증.
 *
 * <p>키는 클라이언트가 presigned URL 발급 응답에서 받아 그대로 되돌려 보내는 값이라 그대로 믿으면 안 된다.
 * 서버가 이 키로 S3를 읽거나 presigned GET을 발급하는 모든 진입점(일기 등록·채팅 음성·재생 URL)이 함께 쓴다.
 */
public final class VoiceKey {

    private VoiceKey() {
    }

    /** 본인 네임스페이스의 키만 허용 (IDOR 방지). 경로 traversal('..') 차단. */
    public static void verifyOwnedBy(String username, String voiceKey) {
        String ownerPrefix = "voices/" + username + "/";
        if (voiceKey == null || !voiceKey.startsWith(ownerPrefix) || voiceKey.contains("..")) {
            throw VoiceHandler.NO_PERMISSION;
        }
    }
}
