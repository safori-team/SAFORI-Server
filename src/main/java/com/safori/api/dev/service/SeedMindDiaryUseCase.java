package com.safori.api.dev.service;

import com.safori.api.dev.dto.SeedMindDiaryResponse;
import com.safori.common.annotation.UseCase;
import com.safori.common.service.S3PresignService;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.question.entity.QuestionCategory;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.service.VoiceDomainService;
import com.safori.infra.ai.gemini.GeminiVoiceAnalyzer;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Optional;

/**
 * [개발/테스트 전용] 마음일기를 원하는 날짜로 심는다.
 *
 * <p>목적: 스케줄러의 "3일 연속 부정 감정" 조건을 결정적으로 재현한다. 프론트 없이 서버가
 * 오디오를 S3에 올리고(기존 presigned PUT 경로 그대로), 실제 분석을 돌려 도란이가 쓸 실제
 * 내용(전사/라벨/요약)을 만든 뒤, 필요하면 대표 감정만 지정값으로 덮어쓴다.
 *
 * <p>흐름:
 * <ol>
 *   <li>서버가 presigned URL 발급받아 오디오 바이트를 S3에 PUT</li>
 *   <li>Voice 등록 + 질문 링크 (1일 1일기 제약은 시딩 편의를 위해 건너뜀)</li>
 *   <li>동기 분석 — composite/label/content 저장, analysisCompletedAt=now (스캔창 진입)</li>
 *   <li>emotion이 주어지면 composite.topEmotion만 덮어씀 (내용은 실제 분석값 유지)</li>
 *   <li>createdDate를 지정 날짜로 백데이트 (스트릭 판정 기준)</li>
 * </ol>
 */
@UseCase
@RequiredArgsConstructor
public class SeedMindDiaryUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final GeminiVoiceAnalyzer geminiVoiceAnalyzer;
    private final Optional<S3PresignService> s3PresignService;

    public SeedMindDiaryResponse execute(String username, LocalDate diaryDate,
                                         QuestionCategory questionCategory, Integer questionIndex,
                                         EmotionType emotionOverride,
                                         byte[] audioBytes, String extension, String contentType) {
        S3PresignService s3 = s3PresignService.orElseThrow(
                () -> new IllegalStateException("S3가 구성되지 않았습니다. AWS 설정을 확인해주세요."));

        // 1) 서버가 presigned 발급 + PUT (프론트 업로드 경로 재현)
        String voiceKey = s3.uploadBytesViaPresignedPut(username, extension, audioBytes, contentType);

        // 2) 등록 (1일 1일기 제약은 시딩 편의상 생략)
        User user = userAdaptor.queryUserByUsername(username);
        Voice voice = voiceDomainService.uploadVoiceFile(user, voiceKey);
        if (questionCategory != null && questionIndex != null) {
            voiceDomainService.linkVoiceQuestion(voice, questionCategory, questionIndex);
        }

        // 3) 실제 분석 동기 실행 (내용은 진짜) — 실패 시 명확히 알림
        boolean analyzed = geminiVoiceAnalyzer.analyzeSync(voice.getId(), voiceKey);
        if (!analyzed) {
            throw new IllegalStateException(
                    "분석 실패 또는 Gemini 미구성 — 시딩하려면 GEMINI_API_KEY/S3 설정이 필요합니다. voiceId="
                            + voice.getId());
        }

        // 4) 감정 덮어쓰기 (선택) — 대표 감정만, 내용은 유지
        String finalEmotion;
        boolean overridden = emotionOverride != null;
        VoiceComposite composite = voiceCompositeAdaptor.findByVoiceId(voice.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "분석 결과(composite)가 없습니다. voiceId=" + voice.getId()));
        if (overridden) {
            composite.overrideTopEmotion(emotionOverride);
            voiceCompositeAdaptor.save(composite);
            finalEmotion = emotionOverride.name();
        } else {
            finalEmotion = composite.getTopEmotion() == null ? null : composite.getTopEmotion().name();
        }

        // 5) 작성일 백데이트 (스트릭 기준). analysisCompletedAt은 now 그대로 → 스캔창 유지
        voiceAdaptor.backdateCreatedDate(voice.getId(), diaryDate.atTime(9, 0));

        return new SeedMindDiaryResponse(voice.getId(), diaryDate, finalEmotion, overridden);
    }
}
