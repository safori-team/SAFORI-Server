package com.safori.domain.user.service;

import com.safori.domain.user.entity.Gender;
import com.safori.domain.user.entity.User;

import java.time.LocalDate;

public interface UserDomainService {

    User registerUser(String username, String password, String name, Gender gender, LocalDate birthDate,
                      String phone, String nickname);

}
