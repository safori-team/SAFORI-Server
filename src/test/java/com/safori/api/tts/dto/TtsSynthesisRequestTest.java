package com.safori.api.tts.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TtsSynthesisRequestTest {

    @Test
    @DisplayName("cacheable 미지정 시 공용 캐시(true)로 간주한다")
    void isCacheable_defaultsToTrue() {
        assertThat(new TtsSynthesisRequest("문장", null).isCacheable()).isTrue();
    }

    @Test
    @DisplayName("cacheable 명시 값은 그대로 사용한다")
    void isCacheable_respectsExplicitValue() {
        assertThat(new TtsSynthesisRequest("문장", true).isCacheable()).isTrue();
        assertThat(new TtsSynthesisRequest("문장", false).isCacheable()).isFalse();
    }
}
