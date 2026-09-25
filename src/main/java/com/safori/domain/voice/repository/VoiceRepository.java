package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.Voice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VoiceRepository extends JpaRepository<Voice, Long> {
    List<Voice> findByUser_Username(String username);

    /**
     * 작성일을 강제로 덮어쓴다. 개발용 시딩 API 전용 — @CreatedDate 감사가 persist 시
     * createdDate를 현재 시각으로 채우므로, 과거 날짜 일기를 만들려면 저장 후 이 쿼리로 갱신한다.
     */
    @Modifying
    @Query("UPDATE Voice v SET v.createdDate = :createdDate WHERE v.id = :voiceId")
    void updateCreatedDate(@Param("voiceId") Long voiceId,
                           @Param("createdDate") LocalDateTime createdDate);

    List<Voice> findByUser_UsernameAndCreatedDateBetween(String username,
                                                         LocalDateTime start,
                                                         LocalDateTime end);

    /** 최근 N건 (홈화면 미리보기용) */
    List<Voice> findByUser_UsernameOrderByCreatedDateDesc(String username, Pageable pageable);

    /** 대상자 판정(작성 주기 감소): 이 시각 이후 일기 작성 시각, 최신순. */
    @Query("SELECT v.createdDate FROM Voice v WHERE v.user.id = :userId AND v.createdDate >= :since "
            + "ORDER BY v.createdDate DESC")
    List<LocalDateTime> findCreatedDates(@Param("userId") Long userId, @Param("since") LocalDateTime since,
                                         Pageable pageable);
}
