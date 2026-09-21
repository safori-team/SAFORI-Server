package com.safori.domain.emotion.repository;

import com.safori.domain.emotion.entity.EmotionAnalysisRequest;
import com.safori.domain.emotion.entity.EmotionAnalysisStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmotionAnalysisRequestRepository extends JpaRepository<EmotionAnalysisRequest, Long> {

    /**
     * 응답 반영용 조회. 같은 응답이 두 인스턴스에 동시에 전달돼도 한쪽만 상태를 바꾸도록
     * 행 잠금을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EmotionAnalysisRequest r where r.requestId = :requestId")
    Optional<EmotionAnalysisRequest> findByRequestIdForUpdate(@Param("requestId") String requestId);

    Optional<EmotionAnalysisRequest> findByRequestId(String requestId);

    /** 타임아웃 스윕 후보. 한 번에 처리할 양을 제한해 스케줄러 실행 시간을 묶어둔다. */
    List<EmotionAnalysisRequest> findTop100ByStatusAndCreatedDateBefore(
            EmotionAnalysisStatus status, LocalDateTime threshold);
}
