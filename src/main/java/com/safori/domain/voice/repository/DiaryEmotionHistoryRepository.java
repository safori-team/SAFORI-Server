package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.Voice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세션 트리거 스케줄러용 조회. 일반 Voice CRUD와 목적이 달라 리포지토리를 분리한다.
 */
public interface DiaryEmotionHistoryRepository extends JpaRepository<Voice, Long> {

    /**
     * 분석이 끝났고 아직 트리거된 적 없는 일기 ID.
     * {@code mind_diary_trigger} 원장에 행이 있으면 제외한다 — 세션이 삭제돼도 원장 행은
     * 남으므로, 한 번 트리거된 일기는 다시 후보가 되지 않는다(세션 부활 방지).
     */
    @Query("""
            SELECT v.id
            FROM Voice v
            JOIN VoiceComposite vc ON vc.voice.id = v.id
            WHERE v.analysisStatus = com.safori.domain.voice.entity.Voice.AnalysisStatus.COMPLETED
              AND v.analysisCompletedAt >= :since
              AND NOT EXISTS (
                  SELECT 1 FROM MindDiaryTrigger t WHERE t.voice.id = v.id
              )
            ORDER BY v.analysisCompletedAt ASC
            """)
    List<Long> findUntriggeredVoiceIds(@Param("since") LocalDateTime since,
                                       org.springframework.data.domain.Pageable pageable);

    /**
     * 기간 내 일기의 대표 감정 원본 데이터.
     * 반환 컬럼: [0] voiceId(Long), [1] createdDate(LocalDateTime),
     * [2] aiTopEmotion(EmotionType), [3] reportedEmotion(EmotionType)
     *
     * <p>대표 감정 결정은 {@code EmotionResolver}가 하므로 두 감정을 그대로 실어 보낸다.
     */
    @Query("""
            SELECT v.id, v.createdDate, vc.topEmotion, ver.reportedEmotion
            FROM Voice v
            JOIN VoiceComposite vc ON vc.voice.id = v.id
            LEFT JOIN VoiceEmotionReport ver ON ver.voice.id = v.id AND ver.user.id = v.user.id
            WHERE v.user.id = :userId
              AND v.createdDate >= :start
              AND v.createdDate < :end
            ORDER BY v.createdDate DESC
            """)
    List<Object[]> findEmotionRows(@Param("userId") Long userId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}
