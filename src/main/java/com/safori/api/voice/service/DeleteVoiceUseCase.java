package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.exception.VoiceHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 음성(마음일기) 삭제.
 * 소유권 확인 후, 이 일기가 트리거한 상담 세션을 함께 지우고 Voice 레코드를 삭제한다.
 */
@UseCase
@RequiredArgsConstructor
public class DeleteVoiceUseCase {

    private final VoiceAdaptor voiceAdaptor;
    private final ChatbotDomainService chatbotDomainService;

    @Transactional
    public void execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if (!voice.getUser().getUsername().equals(username)) {
            throw VoiceHandler.NO_PERMISSION;
        }
        // 순서 중요: 세션·트리거 원장을 먼저 지워야 voice 삭제가 FK 위반 없이 된다.
        //   - chat_message.voice_id FK에는 cascade가 없어, 세션을 먼저 지워 메시지를 cascade로 정리한다.
        //   - mind_diary_trigger.voice_id FK의 cascade가 실제 DB에 반영돼 있지 않을 수 있어(ddl-auto:update),
        //     원장 행도 명시적으로 지운다. 안 지우면 voice 삭제가 FK 위반으로 500 난다.
        chatbotDomainService.deleteTriggerAndSessionByVoiceId(voiceId);
        voiceAdaptor.deleteById(voiceId);
    }
}
