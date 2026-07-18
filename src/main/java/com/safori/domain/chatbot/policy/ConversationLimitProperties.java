package com.safori.domain.chatbot.policy;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * CBT 상담 대화 길이 제한 설정.
 *
 * <p>env override 예: {@code SAFORI_CHATBOT_CONVERSATION_MAX_USER_TURNS=6}
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "safori.chatbot.conversation")
public class ConversationLimitProperties {

    /**
     * 세션당 허용하는 사용자 발화 횟수. 마지막 발화에 대한 도란이 응답이 마무리 멘트가 되고,
     * 그 이후 발화는 거부된다.
     *
     * <p>마음일기 세션 기준으로 (도란이 질문 → 사용자 응답)이 이 횟수만큼 오간 뒤 종료된다.
     */
    @Min(1)
    private int maxUserTurns = 4;
}
