package com.safori.domain.chatbot.policy;

import com.safori.domain.emotion.entity.EmotionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * 마음일기 → 챗봇 세션 생성 정책 설정.
 *
 * <p>PM 정책 변경 시 코드 배포 없이 yaml/env만 바꿔 대응하기 위한 값들이다.
 * 규칙의 "형태" 자체가 바뀌면(예: 감정 강도 기반 트리거) {@link SessionTriggerPolicy}
 * 구현체를 추가하고 {@link #policy} 값을 바꾼다.
 *
 * <p>env override 예: {@code SAFORI_CHATBOT_SESSION_TRIGGER_CONSECUTIVE_DAYS=5}
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "safori.chatbot.session-trigger")
public class SessionTriggerProperties {

    /** 적용할 트리거 정책. */
    @NotNull
    private PolicyType policy = PolicyType.CONSECUTIVE_NEGATIVE;

    /** 스케줄러 실행 간격. {@code @Scheduled}가 문자열로 직접 읽으므로 여기선 문서화 용도. */
    @NotNull
    private Duration interval = Duration.ofMinutes(10);

    /**
     * 후보 스캔 시 거슬러 올라갈 범위. 이 시간 안에 분석 완료된 일기만 후보가 된다.
     * 넓히면 장애 복구/백필 범위가 늘지만, 정책 변경 직후 과거 세션이 한꺼번에 생성될 수 있다.
     */
    @NotNull
    private Duration scanLookback = Duration.ofHours(48);

    /** 한 번의 스케줄 실행에서 처리할 최대 후보 수. LLM 호출 폭주 방지. */
    @Min(1)
    private int scanBatchSize = 50;

    // -- CONSECUTIVE_NEGATIVE 정책 파라미터 --------------------------------

    /** 세션 생성에 필요한 연속 부정 감정 일기 일수. */
    @Min(1)
    private int consecutiveDays = 3;

    /** "부정" 대분류로 취급할 감정. 대분류 정의가 바뀌면 이 값만 수정한다. */
    @NotEmpty
    private Set<EmotionType> negativeEmotions =
            EnumSet.of(EmotionType.SAD, EmotionType.ANGRY, EmotionType.ANXIETY);

    // -- 세션 컨텍스트 윈도우 ----------------------------------------------

    /** 세션 프롬프트에 함께 넣을 일기 범위. */
    @NotNull
    private ContextWindow contextWindow = ContextWindow.STREAK;

    /** {@link ContextWindow#FIXED_DAYS} 전용 — 최근 며칠치를 넣을지. */
    @Min(1)
    private int contextWindowDays = 3;

    /** 컨텍스트 일기 최대 개수. 프롬프트 길이/토큰 비용 상한. */
    @Min(1)
    private int maxContextDiaries = 5;

    public enum PolicyType {
        /** 분석 완료된 모든 마음일기마다 세션 생성 (구 정책). */
        ALWAYS,
        /** N일 연속 부정 감정일 때만 세션 생성. */
        CONSECUTIVE_NEGATIVE
    }

    public enum ContextWindow {
        /** 트리거된 일기 1개만. */
        TRIGGER_ONLY,
        /** 최근 {@code contextWindowDays}일치. */
        FIXED_DAYS,
        /** 부정 감정 연속 스트릭 전체 ({@code maxContextDiaries}로 상한). */
        STREAK
    }
}
