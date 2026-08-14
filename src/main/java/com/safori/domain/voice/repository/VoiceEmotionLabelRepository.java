package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VoiceEmotionLabelRepository extends JpaRepository<VoiceEmotionLabel, Long> {

    List<VoiceEmotionLabel> findByVoice_Id(Long voiceId);

    /** 여러 voice의 레이블 일괄 조회 — 세션 트리거 배치 처리 시 N+1 방지. */
    List<VoiceEmotionLabel> findByVoice_IdIn(List<Long> voiceIds);

    /**
     * 특정 월의 세부 감정 집계 (버블차트용).
     * label별 등장 횟수(다이어리 수)와 평균 intensity 반환.
     * Object[] = { label(String), category(String), diaryCount(Long), avgIntensity(Double) }
     */
    @Query("""
            SELECT vel.label, vel.category,
                   COUNT(vel.id)           AS diaryCount,
                   AVG(vel.intensityX1000) AS avgIntensity
            FROM VoiceEmotionLabel vel
            JOIN vel.voice v
            WHERE v.user.username = :username
              AND YEAR(v.createdDate)  = :year
              AND MONTH(v.createdDate) = :month
            GROUP BY vel.label, vel.category
            ORDER BY diaryCount DESC
            """)
    List<Object[]> findMonthlyLabelStats(
            @Param("username") String username,
            @Param("year") int year,
            @Param("month") int month
    );

    /**
     * 특정 월 + 특정 label을 가진 Voice 목록 조회 (일기 리스트용).
     * 최신순 정렬.
     */
    @Query("""
            SELECT vel.voice
            FROM VoiceEmotionLabel vel
            JOIN vel.voice v
            WHERE v.user.username = :username
              AND vel.label        = :label
              AND YEAR(v.createdDate)  = :year
              AND MONTH(v.createdDate) = :month
            ORDER BY v.createdDate DESC
            """)
    List<Voice> findVoicesByLabel(
            @Param("username") String username,
            @Param("label") String label,
            @Param("year") int year,
            @Param("month") int month
    );

    /**
     * 특정 voice의 모든 세부 감정 레이블 삭제 (재분석 시 덮어쓰기용).
     */
    void deleteByVoice_Id(Long voiceId);

    /**
     * 같은 트랜잭션 안에서 삭제 → 재삽입할 때 쓰는 벌크 삭제.
     *
     * <p>{@link #deleteByVoice_Id}는 엔티티를 지연 삭제하므로 Hibernate가 INSERT를 먼저
     * 플러시해 {@code uq_vel_voice_label} 을 위반할 수 있다. 이 쿼리는 호출 시점에 DELETE를
     * 실행하고 영속성 컨텍스트를 비워 그 순서 문제를 없앤다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VoiceEmotionLabel vel where vel.voice.id = :voiceId")
    void deleteAllByVoiceIdInBulk(@Param("voiceId") Long voiceId);
}
