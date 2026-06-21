package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.common.consts.UserServiceQuestionStaticValues;
import com.safori.common.service.S3PresignService;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceEmotionReport;
import com.safori.domain.voice.exception.VoiceHandler;
import com.safori.domain.question.entity.VoiceQuestion;
import com.safori.domain.voice.repository.VoiceContentRepository;
import com.safori.domain.voice.repository.VoiceQuestionRepository;
import com.safori.api.voice.dto.VoiceDetailResponse;

import java.util.List;
import java.util.Optional;

@UseCase
public class GetUserVoiceDetailUseCase {

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final VoiceContentRepository voiceContentRepository;
    private final Optional<S3PresignService> s3PresignService;
    private final VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;

    public GetUserVoiceDetailUseCase(VoiceAdaptor voiceAdaptor,
                                     VoiceCompositeAdaptor voiceCompositeAdaptor,
                                     VoiceQuestionRepository voiceQuestionRepository,
                                     VoiceContentRepository voiceContentRepository,
                                     Optional<S3PresignService> s3PresignService,
                                     VoiceEmotionReportAdaptor voiceEmotionReportAdaptor) {
        this.voiceAdaptor = voiceAdaptor;
        this.voiceCompositeAdaptor = voiceCompositeAdaptor;
        this.voiceQuestionRepository = voiceQuestionRepository;
        this.voiceContentRepository = voiceContentRepository;
        this.s3PresignService = s3PresignService;
        this.voiceEmotionReportAdaptor = voiceEmotionReportAdaptor;
    }

    public VoiceDetailResponse execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if (!voice.getUser().getUsername().equals(username)) throw VoiceHandler.NO_PERMISSION;

        VoiceComposite composite = voiceCompositeAdaptor.queryByVoiceIds(List.of(voiceId)).stream()
                .findFirst()
                .orElse(null);
        VoiceQuestion voiceQuestion = voiceQuestionRepository.findByVoice_Id(voiceId).orElse(null);
        VoiceContent voiceContent = voiceContentRepository.findByVoice_Id(voiceId).orElse(null);

        Optional<VoiceEmotionReport> report = voiceEmotionReportAdaptor.findByVoiceIdAndUsername(voiceId, username);

        return VoiceDetailResponse.builder()
                .voiceId(voiceId)
                .createdAt(voice.getCreatedDate().toLocalDate())
                .analysisStatus(voice.getAnalysisStatus())
                .topEmotion(composite != null ? composite.getTopEmotion() : null)
                .questionTitle(resolveQuestionTitle(voiceQuestion))
                .content(voiceContent != null ? voiceContent.getContent() : null)
                .s3Url(s3PresignService.map(svc -> svc.generateGetUrl(voice.getVoiceKey())).orElse(null))
                .reportedEmotion(report.map(VoiceEmotionReport::getReportedEmotion).orElse(null))
                .reportMessage(report.map(VoiceEmotionReport::getMessage).orElse(null))
                .build();
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
