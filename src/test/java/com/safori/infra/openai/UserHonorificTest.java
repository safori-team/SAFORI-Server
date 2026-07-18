package com.safori.infra.openai;

import com.safori.domain.user.entity.Gender;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.UserHonorific;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserHonorificTest {

    private User user(String name, String nickname, Gender gender) {
        return User.builder().name(name).nickname(nickname).gender(gender).build();
    }

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

    @Test
    @DisplayName("별명이 있으면 별명을 우선해 부른다")
    void nickname_takesPriority() {
        assertThat(UserHonorific.of(user("홍길동", "길동이", Gender.MALE))).isEqualTo("길동이 할아버지");
    }

    @Test
    @DisplayName("별명이 없으면(null/공백) 실명을 쓴다")
    void noNickname_usesName() {
        assertThat(UserHonorific.of(user("홍길동", null, Gender.FEMALE))).isEqualTo("홍길동 할머니");
        assertThat(UserHonorific.of(user("홍길동", "  ", Gender.FEMALE))).isEqualTo("홍길동 할머니");
    }

    @Test
    @DisplayName("displayName은 별명 우선, 호칭 접미사는 붙이지 않는다")
    void displayName_nicknameFirstNoSuffix() {
        assertThat(UserHonorific.displayName(user("홍길동", "길동이", Gender.MALE))).isEqualTo("길동이");
        assertThat(UserHonorific.displayName(user("홍길동", null, Gender.MALE))).isEqualTo("홍길동");
    }
}
