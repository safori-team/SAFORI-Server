package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.emotion.service.EmotionResolver;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.domain.voice.entity.VoiceEmotionReport;
import com.safori.domain.voice.exception.VoiceHandler;
import com.safori.api.voice.dto.DiaryAnalysisResponse;
import com.safori.api.voice.dto.MajorEmotionItem;
import com.safori.api.voice.dto.SubEmotionItem;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * GET /v1/api/users/voices/{voiceId}/analysis UseCase.
 *
 * <p>분석 탭에서 필요한 데이터를 반환합니다:
 * <ul>
 *   <li>대표 감정 (topEmotion)</li>
 *   <li>도란이 말풍선 요약 (summary)</li>
 *   <li>세부 감정 breakdown — {@code VoiceEmotionLabel} 기반, intensity 내림차순</li>
 * </ul>
 *
 * <p>분석이 COMPLETED 상태가 아닌 경우 {@link VoiceHandler#ANALYSIS_NOT_COMPLETED} 예외를 던집니다.
 */
@UseCase
@RequiredArgsConstructor
public class GetVoiceAnalysisUseCase {

    /**
     * 세부 감정 레이블(영문) → 한글 표시명.
     * 분석 프롬프트의 감정 name 목록과 1:1 대응.
     * TODO: 기획 확정 후 표시명 조정 가능
     */
    private static final Map<String, String> LABEL_KO = Map.ofEntries(
            // neutral
            Map.entry("calmness",               "평온"),
            Map.entry("contemplation",          "사색"),
            Map.entry("concentration",          "집중"),
            Map.entry("interest",               "흥미"),
            Map.entry("realization",            "깨달음"),
            Map.entry("boredom",                "무료함"),
            Map.entry("tiredness",              "피로"),
            Map.entry("confusion",              "혼란"),
            Map.entry("doubt",                  "의심"),
            Map.entry("nostalgia",              "향수"),
            // happy
            Map.entry("joy",                    "기쁨"),
            Map.entry("ecstasy",                "황홀"),
            Map.entry("contentment",            "만족"),
            Map.entry("satisfaction",           "만족감"),
            Map.entry("amusement",              "즐거움"),
            Map.entry("excitement",             "설렘"),
            Map.entry("pride",                  "자부심"),
            Map.entry("triumph",                "성취감"),
            Map.entry("relief",                 "안도"),
            Map.entry("admiration",             "감탄"),
            Map.entry("adoration",              "경애"),
            Map.entry("love",                   "사랑"),
            Map.entry("romance",                "낭만"),
            Map.entry("entrancement",           "매혹"),
            Map.entry("aesthetic_appreciation", "심미적 감동"),
            Map.entry("determination",          "의지"),
            // sad
            Map.entry("sadness",                "슬픔"),
            Map.entry("distress",               "고통"),
            Map.entry("disappointment",         "실망"),
            Map.entry("guilt",                  "죄책감"),
            Map.entry("shame",                  "수치심"),
            Map.entry("embarrassment",          "당혹"),
            Map.entry("empathic_pain",          "공감적 아픔"),
            Map.entry("sympathy",               "연민"),
            Map.entry("loneliness",             "외로움"),
            // angry
            Map.entry("anger",                  "분노"),
            Map.entry("contempt",               "경멸"),
            Map.entry("disgust",                "혐오"),
            Map.entry("frustration",            "짜증"),
            Map.entry("envy",                   "질투"),
            Map.entry("craving",                "갈망"),
            // fear
            Map.entry("fear",                   "두려움"),
            Map.entry("anxiety",                "불안"),
            Map.entry("horror",                 "공포"),
            // surprise
            Map.entry("surprise_positive",      "긍정적 놀람"),
            Map.entry("surprise_negative",      "부정적 놀람"),
            Map.entry("awe",                    "경외"),
            Map.entry("awkwardness",            "어색함")
    );

    /**
     * 대 감정 카테고리별 탐색 질문.
     * TODO: 기획 의도에 맞게 문구 조정 필요
     */
    private static final Map<String, String> CATEGORY_QUESTION = Map.of(
            "neutral",  "오늘 하루 어떤 순간이 가장 기억에 남나요?",
            "happy",    "오늘 행복한 감정의 원천은 무엇이었나요?",
            "sad",      "슬픔 속에 어떤 그리움이 담겨있지는 않았나요?",
            "angry",    "화가 난 마음 뒤에 서운함이 숨어있지는 않았나요?",
            "anxiety",  "두려움 속에서 나를 지탱해준 것은 무엇인가요?",
            "fear",     "두려움 속에서 나를 지탱해준 것은 무엇인가요?",
            "surprise", "예상치 못한 상황에서 어떤 감정이 가장 먼저 떠올랐나요?"
    );

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;

    public DiaryAnalysisResponse execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if (!voice.getUser().getUsername().equals(username)) {
            throw VoiceHandler.NO_PERMISSION;
        }
        if (voice.getAnalysisStatus() != Voice.AnalysisStatus.COMPLETED) {
            throw VoiceHandler.ANALYSIS_NOT_COMPLETED;
        }

        VoiceComposite composite = voiceCompositeAdaptor.findByVoiceId(voiceId)
                .orElseThrow(() -> VoiceHandler.ANALYSIS_RESULT_NOT_FOUND);

        List<VoiceEmotionLabel> labels = voiceEmotionLabelAdaptor.findByVoiceId(voiceId);

        EmotionType reportedEmotion = voiceEmotionReportAdaptor.findByVoiceIdAndUsername(voiceId, username)
                .map(VoiceEmotionReport::getReportedEmotion)
                .orElse(null);

        return DiaryAnalysisResponse.builder()
                .voiceId(voiceId)
                .topEmotion(EmotionResolver.effectiveTopEmotion(composite.getTopEmotion(), reportedEmotion))
                .summary(composite.getSummary())
                .majorEmotions(buildMajorEmotions(composite))
                .subEmotions(buildSubEmotions(labels))
                .build();
    }

    private static final int SUB_EMOTION_LIMIT = 6;

    /**
     * 6대 감정 분포(VoiceComposite의 *_bps) → MajorEmotionItem 리스트.
     *
     * <ul>
     *   <li>항상 6개 (happy/sad/neutral/angry/anxiety/surprise)</li>
     *   <li>비율 = bps / 100 (0~100, 소수점 1자리). 6개 합 ≈ 100</li>
     *   <li>정렬: 비율 내림차순</li>
     *   <li>질문: {@link #CATEGORY_QUESTION} 매핑 (감정 카테고리 기준)</li>
     * </ul>
     */
    private List<MajorEmotionItem> buildMajorEmotions(VoiceComposite composite) {
        Map<EmotionType, Integer> bpsByEmotion = new java.util.EnumMap<>(EmotionType.class);
        bpsByEmotion.put(EmotionType.HAPPY, composite.getHappyBps());
        bpsByEmotion.put(EmotionType.SAD, composite.getSadBps());
        bpsByEmotion.put(EmotionType.NEUTRAL, composite.getNeutralBps());
        bpsByEmotion.put(EmotionType.ANGRY, composite.getAngryBps());
        bpsByEmotion.put(EmotionType.ANXIETY, composite.getAnxietyBps());
        bpsByEmotion.put(EmotionType.SURPRISE, composite.getSurpriseBps());

        return bpsByEmotion.entrySet().stream()
                .sorted(Comparator.<Map.Entry<EmotionType, Integer>>comparingInt(e ->
                        e.getValue() == null ? 0 : e.getValue()).reversed())
                .map(e -> {
                    int bps = e.getValue() == null ? 0 : e.getValue();
                    return MajorEmotionItem.builder()
                            .emotion(e.getKey())
                            .percentage(Math.round(bps / 10.0) / 10.0)
                            .build();
                })
                .toList();
    }

    /**
     * 세부 감정 레이블 → SubEmotionItem 리스트.
     *
     * <ul>
     *   <li>intensity 내림차순 상위 {@value #SUB_EMOTION_LIMIT}개 (label 수 적으면 그만큼), 동점은 label 사전순</li>
     *   <li>비율 = 해당 label intensityX1000 / 상위 {@value #SUB_EMOTION_LIMIT}개 합계 × 100 (소수점 1자리). 합 ≈ 100</li>
     *   <li>레이블 표시명: {@link #LABEL_KO} 매핑, 미등록 시 원문 그대로</li>
     * </ul>
     */
    private List<SubEmotionItem> buildSubEmotions(List<VoiceEmotionLabel> labels) {
        if (labels.isEmpty()) {
            return List.of();
        }

        // 잘린 label의 몫이 분모에 남지 않도록 상위 N개를 먼저 확정하고 그 안에서 정규화한다.
        List<VoiceEmotionLabel> top = labels.stream()
                .sorted(Comparator.comparingInt(VoiceEmotionLabel::getIntensityX1000).reversed()
                        .thenComparing(VoiceEmotionLabel::getLabel))
                .limit(SUB_EMOTION_LIMIT)
                .toList();

        double total = top.stream()
                .mapToLong(VoiceEmotionLabel::getIntensityX1000)
                .sum();

        return top.stream()
                .map(l -> SubEmotionItem.builder()
                        .label(LABEL_KO.getOrDefault(l.getLabel(), l.getLabel()))
                        .percentage(total > 0
                                ? Math.round(l.getIntensityX1000() / total * 1000.0) / 10.0
                                : 0.0)
                        .question(CATEGORY_QUESTION.getOrDefault(l.getCategory(), ""))
                        .build())
                .toList();
    }

}
