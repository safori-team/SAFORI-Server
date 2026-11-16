package com.safori.domain.user.service;

import com.safori.domain.user.entity.Gender;
import com.safori.domain.user.entity.User;

import java.time.LocalDate;

public interface UserDomainService {

    User registerUser(String username, String password, String name, Gender gender, LocalDate birthDate,
                      String phone, String nickname);


    /** 기관 관리자가 대상자 정보를 고친다. 휴대폰 번호는 숫자만 저장한다. */
    User changeProfile(User user, String name, String phone, LocalDate birthDate);

    /** 아이디 변경. 어르신·백오피스 계정을 통틀어 유일해야 한다. 같은 아이디면 그대로 둔다. */
    User changeUsername(User user, String username);

    User changePassword(User user, String rawPassword);
}
