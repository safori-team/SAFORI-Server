package com.safori.infra.ai.gemini;

import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceContentAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gemini 분석 결과의 DB 반영을 트랜잭션 단위로 묶는다.
 *
 * <p>S3 다운로드·Gemini 호출은 수 초~수십 초 걸리므로 {@link GeminiVoiceAnalyzer}가 트랜잭션 밖에서 하고,
 * 이 클래스는 결과를 받은 뒤의 짧은 쓰기만 맡는다. composite·label·content는 한 트랜잭션으로 저장해
 * 일부만 남는 상태를 만들지 않는다.
 */
@Component
public class VoiceAnalysisResultWriter {

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceContentAdaptor voiceContentAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final GeminiEmotionMapper emotionMapper;
    private final String modelName;

    public VoiceAnalysisResultWriter(
            VoiceAdaptor voiceAdaptor,
            VoiceCompositeAdaptor voiceCompositeAdaptor,
            VoiceContentAdaptor voiceContentAdaptor,
            VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor,
            GeminiEmotionMapper emotionMapper,
            @Value("${gemini.model:gemini-2.5-flash}") String modelName) {
        this.voiceAdaptor = voiceAdaptor;
        this.voiceCompositeAdaptor = voiceCompositeAdaptor;
        this.voiceContentAdaptor = voiceContentAdaptor;
        this.voiceEmotionLabelAdaptor = voiceEmotionLabelAdaptor;
        this.emotionMapper = emotionMapper;
        this.modelName = modelName;
    }

    public record SavedAnalysis(Voice voice, VoiceComposite composite, int labelCount) {}

    @Transactional
    public void markProcessing(Long voiceId) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        voice.markAnalysisProcessing();
        voiceAdaptor.save(voice);
    }

    @Transactional
    public void markCompleted(Long voiceId) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        voice.markAnalysisCompleted();
        voiceAdaptor.save(voice);
    }

    @Transactional
    public void markFailed(Long voiceId) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        voice.markAnalysisFailed();
        voiceAdaptor.save(voice);
    }

    /** 최초 분석 결과 저장. 분석 상태는 바꾸지 않는다(소분류 위임 여부에 따라 호출측이 마감). */
    @Transactional
    public SavedAnalysis save(Long voiceId, GeminiAnalysisResult result) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        return saveAll(voice, result);
    }

    /**
     * 재분석 결과로 기존 composite·label·content를 덮어쓰고 분석을 마감한다.
     * 삭제와 재저장이 한 트랜잭션이라 중간에 실패하면 기존 결과가 그대로 남는다.
     */
    @Transactional
    public SavedAnalysis replace(Long voiceId, GeminiAnalysisResult result) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        voiceCompositeAdaptor.deleteByVoiceId(voiceId);
        voiceEmotionLabelAdaptor.deleteByVoiceId(voiceId);
        voiceContentAdaptor.deleteByVoiceId(voiceId);

        SavedAnalysis saved = saveAll(voice, result);
        voice.markAnalysisCompleted();
        voiceAdaptor.save(voice);
        return saved;
    }

    private SavedAnalysis saveAll(Voice voice, GeminiAnalysisResult result) {
        VoiceComposite composite = voiceCompositeAdaptor.save(emotionMapper.toVoiceComposite(result, voice));

        // 세부 감정 레이블 저장 (버블차트용)
        List<VoiceEmotionLabel> labels = emotionMapper.toEmotionLabels(result, voice);
        if (!labels.isEmpty()) {
            voiceEmotionLabelAdaptor.saveAll(labels);
        }

        // transcript → voice_content 저장 (null-safe)
        if (result.transcript() != null && !result.transcript().isBlank()) {
            voiceContentAdaptor.save(VoiceContent.builder()
                    .voice(voice)
                    .content(result.transcript())
                    .locale("ko-KR")
                    .provider("gemini")
                    .modelVersion(modelName)
                    .build());
        }
        return new SavedAnalysis(voice, composite, labels.size());
    }
}
