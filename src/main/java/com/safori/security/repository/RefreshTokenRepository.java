package com.safori.security.repository;

import com.safori.security.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /** 영속성 컨텍스트에 올라 있던 토큰도 새 주인으로 읽히도록 반영 후 비운다. 호출자는 이 뒤에 엔티티를 바꾸지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.username = :to WHERE t.username = :from")
    int renameOwner(@Param("from") String from, @Param("to") String to);
}
