package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.common.consts.UserServiceQuestionStaticValues;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.emotion.service.EmotionResolver;
import com.safori.domain.question.entity.VoiceQuestion;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.repository.VoiceContentRepository;
import com.safori.domain.voice.repository.VoiceQuestionRepository;
import com.safori.api.voice.dto.VoiceListItem;
import com.safori.api.voice.dto.VoiceListResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UseCase
public class GetUserVoiceListUseCase {

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final VoiceContentRepository voiceContentRepository;
    private final VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;

    public GetUserVoiceListUseCase(VoiceAdaptor voiceAdaptor,
                                   VoiceCompositeAdaptor voiceCompositeAdaptor,
                                   VoiceQuestionRepository voiceQuestionRepository,
                                   VoiceContentRepository voiceContentRepository,
                                   VoiceEmotionReportAdaptor voiceEmotionReportAdaptor) {
        this.voiceAdaptor = voiceAdaptor;
        this.voiceCompositeAdaptor = voiceCompositeAdaptor;
        this.voiceQuestionRepository = voiceQuestionRepository;
        this.voiceContentRepository = voiceContentRepository;
        this.voiceEmotionReportAdaptor = voiceEmotionReportAdaptor;
    }

    public VoiceListResponse execute(String username, String date) {
        List<Voice> voices = voiceAdaptor.queryByUsernameAndCreatedAt(username, LocalDate.parse(date));
        return VoiceListResponse.builder()
                .voices(toVoiceListItems(username, voices))
                .build();
    }

    public VoiceListResponse execute(String username) {
        List<Voice> voices = voiceAdaptor.queryByUsername(username);
        return VoiceListResponse.builder()
                .voices(toVoiceListItems(username, voices))
                .build();
    }

    //TODO optimization
    private List<VoiceListItem> toVoiceListItems(String username, List<Voice> voices) {
        List<Long> voiceIds = voices.stream().map(Voice::getId).toList();

        Map<Long, VoiceComposite> compositeByVoiceId = voiceCompositeAdaptor.queryByVoiceIds(voiceIds).stream()
                .collect(Collectors.toMap(vc -> vc.getVoice().getId(), vc -> vc));
        Map<Long, VoiceQuestion> questionByVoiceId = voiceQuestionRepository.findByVoice_IdIn(voiceIds).stream()
                .collect(Collectors.toMap(vq -> vq.getVoice().getId(), vq -> vq));
        Map<Long, VoiceContent> contentByVoiceId = voiceContentRepository.findByVoice_IdIn(voiceIds).stream()
                .collect(Collectors.toMap(vc -> vc.getVoice().getId(), vc -> vc));
        Map<Long, EmotionType> reportedByVoiceId = voiceEmotionReportAdaptor.findReportedEmotions(voiceIds, username);

        return voices.stream()
                .map(v -> VoiceListItem.builder()
                        .voiceId(v.getId())
                        .createdAt(v.getCreatedDate().toLocalDate())
                        .analysisStatus(v.getAnalysisStatus())
                        .emotion(EmotionResolver.effectiveTopEmotion(
                                compositeByVoiceId.containsKey(v.getId())
                                        ? compositeByVoiceId.get(v.getId()).getTopEmotion()
                                        : null,
                                reportedByVoiceId.get(v.getId())))
                        .questionTitle(resolveQuestionTitle(questionByVoiceId.get(v.getId())))
                        .content(contentByVoiceId.containsKey(v.getId())
                                ? contentByVoiceId.get(v.getId()).getContent()
                                : null)
                        .build())
                .collect(Collectors.toList());
    }

    private String resolveQuestionTitle(VoiceQuestion voiceQuestion) {
        if (voiceQuestion == null) return null;
        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP.get(voiceQuestion.getQuestionCategory().name());
        if (questions == null || voiceQuestion.getQuestionIndex() < 0 || voiceQuestion.getQuestionIndex() >= questions.size()) {
            return null;
        }
        return questions.get(voiceQuestion.getQuestionIndex());
    }
}
