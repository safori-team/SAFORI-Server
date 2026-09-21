package com.safori.domain.notification.repository;

import com.safori.domain.notification.entity.DeviceToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findAllByUser_Id(Long userId);

    void deleteByToken(String token);

    void deleteAllByTokenIn(List<String> tokens);
}
