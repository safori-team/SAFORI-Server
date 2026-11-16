package com.safori.api.user.service;

import com.safori.api.user.dto.UserInfoResponse;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.security.dto.AccountRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetUserInfoUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @InjectMocks GetUserInfoUseCase getUserInfoUseCase;

    @Test
    @DisplayName("내 정보 조회 - 어르신 앱 토큰이면 username으로 조회하고 role=ELDER, 권한·기관 없음")
    void execute_returnsUserInfo() {
        User user = User.builder().username("user01").name("홍길동").birthDate(LocalDate.of(1960, 3, 12)).build();
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);

        UserInfoResponse response = getUserInfoUseCase.execute(
                new UsernamePasswordAuthenticationToken("user01", "", List.of()));

        assertThat(response.getRole()).isEqualTo(AccountRole.ELDER);
        assertThat(response.getPermissions()).isEmpty();
        assertThat(response.getOrganization()).isNull();
        assertThat(response.getUsername()).isEqualTo("user01");
        assertThat(response.getBirthDate()).isEqualTo(LocalDate.of(1960, 3, 12));
        assertThat(response.getName()).isEqualTo("홍길동");
    }
}
