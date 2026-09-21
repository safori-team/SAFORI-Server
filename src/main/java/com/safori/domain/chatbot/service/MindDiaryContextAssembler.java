package com.safori.domain.chatbot.service;

import com.safori.common.consts.UserServiceQuestionStaticValues;
import com.safori.domain.chatbot.model.MindDiaryEntry;
import com.safori.domain.question.entity.VoiceQuestion;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.domain.voice.repository.VoiceContentRepository;
import com.safori.domain.voice.repository.VoiceQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 컨텍스트 일기들을 상담 프롬프트 엔트리로 변환한다.
 * composite/label/content/question을 일기 수와 무관하게 각 1회의 IN 쿼리로 모아 N+1을 피한다.
 */
@Component
@RequiredArgsConstructor
public class MindDiaryContextAssembler {

    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final VoiceContentRepository voiceContentRepository;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final ChatbotMessageMapper mapper;

    /** @param voices 시간순(오래된 → 최신). 마지막이 트리거 일기. */
    public List<MindDiaryEntry> assemble(List<Voice> voices) {
        List<Long> ids = voices.stream().map(Voice::getId).toList();

        Map<Long, VoiceComposite> compositeById = voiceCompositeAdaptor.queryByVoiceIds(ids).stream()
                .collect(Collectors.toMap(c -> c.getVoice().getId(), c -> c, (a, b) -> a));
        Map<Long, List<VoiceEmotionLabel>> labelsById = voiceEmotionLabelAdaptor.findByVoiceIds(ids)
                .stream().collect(Collectors.groupingBy(l -> l.getVoice().getId()));
        Map<Long, String> contentById = voiceContentRepository.findByVoice_IdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getVoice().getId(), VoiceContent::getContent, (a, b) -> a));
        Map<Long, VoiceQuestion> questionById = voiceQuestionRepository.findByVoice_IdIn(ids).stream()
                .collect(Collectors.toMap(q -> q.getVoice().getId(), q -> q, (a, b) -> a));

        List<MindDiaryEntry> entries = new ArrayList<>(voices.size());
        for (Voice v : voices) {
            VoiceComposite composite = compositeById.get(v.getId());
            List<VoiceEmotionLabel> labels = labelsById.getOrDefault(v.getId(), List.of());
            entries.add(new MindDiaryEntry(
                    v.getCreatedDate() == null ? null : v.getCreatedDate().toString(),
                    resolveQuestionText(questionById.get(v.getId())),
                    contentById.get(v.getId()),
                    mapper.summarizeVoiceEmotion(composite, labels),
                    mapper.emotionHint(composite)));
        }
        return entries;
    }

    /**
     * VoiceQuestion → QUESTION_MAP에서 질문 텍스트. 매핑이 없거나 인덱스가 잘못되면 "(자유 일기)".
     */
    private String resolveQuestionText(VoiceQuestion q) {
        if (q == null) return "(자유 일기)";
        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP
                .get(q.getQuestionCategory().name());
        if (questions == null
                || q.getQuestionIndex() < 0
                || q.getQuestionIndex() >= questions.size()) {
            return "(자유 일기)";
        }
        return questions.get(q.getQuestionIndex());
    }
}
