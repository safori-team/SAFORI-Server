package com.safori.domain.user.repository;

import com.safori.domain.user.entity.Role;
import com.safori.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.save(User.builder()
                .username("user01")
                .password("ENCODED")
                .name("홍길동")
                .role(Role.USER)
                .userUuid(UUID.randomUUID().toString())
                .build());
    }

    @Test
    @DisplayName("existsByUsername - 존재하면 true, 없으면 false")
    void existsByUsername() {
        assertThat(userRepository.existsByUsername("user01")).isTrue();
        assertThat(userRepository.existsByUsername("nobody")).isFalse();
    }

    @Test
    @DisplayName("findByUsername - 저장된 유저를 username으로 조회")
    void findByUsername() {
        assertThat(userRepository.findByUsername("user01"))
                .isPresent()
                .get()
                .satisfies(u -> {
                    assertThat(u.getName()).isEqualTo("홍길동");
                    assertThat(u.getRole()).isEqualTo(Role.USER);
                });
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }
}
