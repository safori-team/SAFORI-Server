package com.safori.infra.openai;

import com.safori.domain.user.entity.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserHonorificTest {

    @Test
    @DisplayName("남성 - 이름 + 할아버지")
    void male_grandfather() {
        assertThat(UserHonorific.of("홍길동", Gender.MALE)).isEqualTo("홍길동 할아버지");
    }

    @Test
    @DisplayName("여성 - 이름 + 할머니")
    void female_grandmother() {
        assertThat(UserHonorific.of("김영희", Gender.FEMALE)).isEqualTo("김영희 할머니");
    }

    @Test
    @DisplayName("성별 미상(null) - 이름 + 님 폴백")
    void nullGender_fallbackNim() {
        assertThat(UserHonorific.of("홍길동", null)).isEqualTo("홍길동님");
    }
}
