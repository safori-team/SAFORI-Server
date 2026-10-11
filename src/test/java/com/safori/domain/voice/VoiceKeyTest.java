package com.safori.domain.voice;

import com.safori.domain.voice.exception.VoiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoiceKeyTest {

    @Test
    @DisplayName("본인 네임스페이스의 키는 통과")
    void ownKey_passes() {
        assertThatCode(() -> VoiceKey.verifyOwnedBy("tester", "voices/tester/uuid.m4a"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "voices/other/uuid.m4a",           // 남의 키
            "voices/tester2/uuid.m4a",         // 아이디가 접두사만 같은 다른 사용자
            "voices/tester/../other/uuid.m4a", // 경로 traversal
            "tts/shared/voice/abc.mp3",        // 음성 네임스페이스 밖
            ""
    })
    @DisplayName("그 밖의 키는 NO_PERMISSION")
    void foreignKey_throws(String voiceKey) {
        assertThatThrownBy(() -> VoiceKey.verifyOwnedBy("tester", voiceKey))
                .isSameAs(VoiceHandler.NO_PERMISSION);
    }
}
