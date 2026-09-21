package com.safori.api.user.service;

import com.safori.api.user.dto.UserInfoResponse;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetUserInfoUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @InjectMocks GetUserInfoUseCase getUserInfoUseCase;

    @Test
    @DisplayName("내 정보 조회 - username으로 조회하여 username·name 응답")
    void execute_returnsUserInfo() {
        User user = User.builder().username("user01").name("홍길동").build();
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);

        UserInfoResponse response = getUserInfoUseCase.execute("user01");

        assertThat(response.getUsername()).isEqualTo("user01");
        assertThat(response.getName()).isEqualTo("홍길동");
    }
}
