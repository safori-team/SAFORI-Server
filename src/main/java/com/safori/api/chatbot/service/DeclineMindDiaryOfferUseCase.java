package com.safori.api.chatbot.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import lombok.RequiredArgsConstructor;

/**
 * 사용자가 모달에서 상담 제안을 거절 → 원장을 DECLINED로 전환. 세션은 만들지 않는다.
 */
@UseCase
@RequiredArgsConstructor
public class DeclineMindDiaryOfferUseCase {

    private final UserAdaptor userAdaptor;
    private final ChatbotDomainService chatbotDomainService;

    public void execute(String username, Long offerId) {
        // 소유권 + OFFERED 검증
        chatbotDomainService.getOfferableOffer(offerId, userAdaptor.queryUserByUsername(username));
        chatbotDomainService.declineOffer(offerId);
    }
}
