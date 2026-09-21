package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.api.voice.dto.RecentVoiceItem;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.emotion.service.EmotionResolver;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.repository.VoiceContentRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 홈화면 — 최근 마음일기 3건 (topEmotion, STT 내용, 날짜만 포함).
 */
@UseCase
@RequiredArgsConstructor
public class GetRecentVoicesUseCase {

    private static final int HOME_RECENT_LIMIT = 3;

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceContentRepository voiceContentRepository;
    private final VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;

    public List<RecentVoiceItem> execute(String username) {
        List<Voice> voices = voiceAdaptor.queryLatestByUsername(username, HOME_RECENT_LIMIT);
        if (voices.isEmpty()) {
            return List.of();
        }

        List<Long> voiceIds = voices.stream().map(Voice::getId).toList();

        Map<Long, VoiceComposite> compositeByVoiceId =
                voiceCompositeAdaptor.queryByVoiceIds(voiceIds).stream()
                        .collect(Collectors.toMap(vc -> vc.getVoice().getId(), vc -> vc));

        Map<Long, VoiceContent> contentByVoiceId =
                voiceContentRepository.findByVoice_IdIn(voiceIds).stream()
                        .collect(Collectors.toMap(vc -> vc.getVoice().getId(), vc -> vc));

        Map<Long, EmotionType> reportedByVoiceId =
                voiceEmotionReportAdaptor.findReportedEmotions(voiceIds, username);

        return voices.stream()
                .map(v -> {
                    VoiceComposite composite = compositeByVoiceId.get(v.getId());
                    VoiceContent content = contentByVoiceId.get(v.getId());
                    EmotionType aiTopEmotion = composite != null ? composite.getTopEmotion() : null;
                    return RecentVoiceItem.builder()
                            .voiceId(v.getId())
                            .date(v.getCreatedDate().toLocalDate())
                            .topEmotion(EmotionResolver.effectiveTopEmotion(aiTopEmotion, reportedByVoiceId.get(v.getId())))
                            .content(content != null ? content.getContent() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
