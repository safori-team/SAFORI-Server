package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.MindDiaryOfferResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.domain.chatbot.entity.MindDiaryTriggerStatus;
import com.safori.domain.chatbot.repository.MindDiaryTriggerRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자의 대기 중(OFFERED) 상담 제안을 조회한다. 앱 진입/홈에서 모달 표시 여부 판단용.
 * 여러 개면 가장 최근 제안 하나를 반환한다.
 */
@UseCase
@RequiredArgsConstructor
public class GetMindDiaryOfferUseCase {

    private final UserAdaptor userAdaptor;
    private final MindDiaryTriggerRepository mindDiaryTriggerRepository;

    @Transactional(readOnly = true)
    public MindDiaryOfferResponse execute(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        List<MindDiaryTrigger> offers = mindDiaryTriggerRepository
                .findByVoice_User_IdAndStatusOrderByCreatedDateDesc(
                        user.getId(), MindDiaryTriggerStatus.OFFERED);
        if (offers.isEmpty()) {
            return MindDiaryOfferResponse.none();
        }
        MindDiaryTrigger latest = offers.get(0);
        return MindDiaryOfferResponse.of(
                latest.getId(), latest.getReason(), latest.getCreatedDate());
    }
}
