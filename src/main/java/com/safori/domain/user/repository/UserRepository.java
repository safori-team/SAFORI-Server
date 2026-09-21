package com.safori.domain.user.repository;

import com.safori.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUserUuid(String userUuid);

    boolean existsByUsername(String username);
}
