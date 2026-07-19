package com.safori.domain.chatbot.repository;

import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.domain.chatbot.entity.MindDiaryTriggerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MindDiaryTriggerRepository extends JpaRepository<MindDiaryTrigger, Long> {

    /** 일기가 트리거한 원장 행 (일기 삭제/재평가 시). */
    Optional<MindDiaryTrigger> findByVoice_Id(Long voiceId);

    /** 사용자의 특정 상태 원장을 최신순으로. 대기 중 제안(OFFERED) 조회 등. */
    List<MindDiaryTrigger> findByVoice_User_IdAndStatusOrderByCreatedDateDesc(
            Long userId, MindDiaryTriggerStatus status);

    /** 사용자의 여러 상태 원장. 재분석 후 재평가 대상(OFFERED, ACCEPTED) 조회. */
    List<MindDiaryTrigger> findByVoice_User_IdAndStatusIn(
            Long userId, List<MindDiaryTriggerStatus> statuses);
}
