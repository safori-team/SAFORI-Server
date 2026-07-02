package com.safori.api.emotion.service;

import com.safori.api.emotion.dto.EmotionLabelDiaryListResponse;
import com.safori.api.voice.dto.VoiceListItem;
import com.safori.common.annotation.UseCase;
import com.safori.common.consts.UserServiceQuestionStaticValues;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.emotion.service.EmotionResolver;
import com.safori.domain.question.entity.VoiceQuestion;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.repository.VoiceContentRepository;
import com.safori.domain.voice.repository.VoiceQuestionRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 특정 월에 특정 세부 감정을 느꼈던 일기 목록 조회.
 * GET /v1/api/users/voices/analyzing/bubble/diaries?yearMonth=2024-01&label=joy
 */
@UseCase
public class GetEmotionLabelDiaryListUseCase {

    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final VoiceContentRepository voiceContentRepository;
    private final VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;

    public GetEmotionLabelDiaryListUseCase(
            VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor,
            VoiceCompositeAdaptor voiceCompositeAdaptor,
            VoiceQuestionRepository voiceQuestionRepository,
            VoiceContentRepository voiceContentRepository,
            VoiceEmotionReportAdaptor voiceEmotionReportAdaptor) {
        this.voiceEmotionLabelAdaptor = voiceEmotionLabelAdaptor;
        this.voiceCompositeAdaptor = voiceCompositeAdaptor;
        this.voiceQuestionRepository = voiceQuestionRepository;
        this.voiceContentRepository = voiceContentRepository;
        this.voiceEmotionReportAdaptor = voiceEmotionReportAdaptor;
    }

    public EmotionLabelDiaryListResponse execute(String username, String yearMonth, String label) {
        YearMonth ym = YearMonth.parse(yearMonth);
        List<Voice> voices = voiceEmotionLabelAdaptor.findVoicesByLabel(
                username, label, ym.getYear(), ym.getMonthValue());

        // category 조회 — 첫 번째 Voice의 레이블에서 추출
        String category = voices.isEmpty() ? null :
                voiceEmotionLabelAdaptor.findByVoiceId(voices.get(0).getId()).stream()
                        .filter(vel -> label.equals(vel.getLabel()))
                        .map(vel -> vel.getCategory())
                        .findFirst()
                        .orElse(null);

        return EmotionLabelDiaryListResponse.builder()
                .yearMonth(yearMonth)
                .label(label)
                .category(category)
                .diaries(toVoiceListItems(username, voices))
                .build();
    }

    private List<VoiceListItem> toVoiceListItems(String username, List<Voice> voices) {
        if (voices.isEmpty()) return List.of();

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
