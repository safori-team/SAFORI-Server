package com.safori.infra.ai.gemini;

import com.safori.common.event.VoiceAnalysisCompletedEvent;
import com.safori.common.event.VoiceReanalyzedEvent;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceContentAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.google.genai.types.UploadFileConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class GeminiVoiceAnalyzer {

    private static final String PROMPT = """
            당신은 감정 분석 AI입니다. 사용자의 한국어 음성을 분석하여 주어진 JSON 스키마 형식으로 응답하세요.

            - transcript: 정확한 한국어 전사
            - summary: 도란이(AI 상담사)가 건네는 공감 말풍선 (2~3문장, 따뜻한 어투, 한국어)
            - title: 전체 발화를 대표하는 제목 (15자 이내, 명사형으로 종결, 한국어)
            - segments: 자연스러운 발화 단위로 분할 (최소 1, 최대 10구간)
              - timestamp: MM:SS 형식 (예: "00:15")
              - text: 해당 구간 전사 텍스트
              - category: 해당 구간의 주요 감정 대분류 (neutral/happy/sad/angry/anxiety/surprise 중 하나)
              - emotions: 상위 최대 5개 감정 배열
                - name: 감정 label (아래 category별 목록에서만 선택)
                - intensity: 감정 강도 (0.0~1.0)
              - prosody_notes: 톤/속도/억양 관찰 (한국어 1문장)
            - stability_score: 0(극도로 불안정) ~ 10(매우 안정적). 화자의 전반적 감정 안정성 기준.

            감정 category와 name의 대응:
            [neutral]  calmness, contemplation, concentration, interest, realization, boredom, tiredness, confusion, doubt, nostalgia
            [happy]    joy, ecstasy, contentment, satisfaction, amusement, excitement, pride, triumph, relief, admiration, adoration, love, romance, entrancement, aesthetic_appreciation, determination
            [sad]      sadness, distress, disappointment, guilt, shame, embarrassment, empathic_pain, sympathy, loneliness
            [angry]    anger, contempt, disgust, frustration, envy, craving
            [anxiety]  fear, anxiety, horror
            [surprise] surprise_positive, surprise_negative, awe, awkwardness

            중요: category와 name은 반드시 위 대응표에 맞게 선택하세요. 불안/공포 관련 감정은 category=anxiety, 놀람 관련 감정은 category=surprise를 사용하세요.
            """;

    private final Optional<Client> geminiClient;
    private final String modelName;
    private final Optional<S3Client> s3Client;
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceContentAdaptor voiceContentAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final GeminiEmotionMapper emotionMapper;
    private final String s3Bucket;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public GeminiVoiceAnalyzer(
            Optional<Client> geminiClient,
            @Value("${gemini.model:gemini-2.5-flash}") String modelName,
            Optional<S3Client> s3Client,
            VoiceAdaptor voiceAdaptor,
            VoiceCompositeAdaptor voiceCompositeAdaptor,
            VoiceContentAdaptor voiceContentAdaptor,
            VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor,
            GeminiEmotionMapper emotionMapper,
            @Value("${spring.cloud.aws.s3.bucket:}") String s3Bucket,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.geminiClient = geminiClient;
        this.modelName = modelName;
        this.s3Client = s3Client;
        this.voiceAdaptor = voiceAdaptor;
        this.voiceCompositeAdaptor = voiceCompositeAdaptor;
        this.voiceContentAdaptor = voiceContentAdaptor;
        this.voiceEmotionLabelAdaptor = voiceEmotionLabelAdaptor;
        this.emotionMapper = emotionMapper;
        this.s3Bucket = s3Bucket;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Async
    public void analyzeAsync(Long voiceId, String voiceKey) {
        if (analyzeSync(voiceId, voiceKey)) {
            // 이벤트 발행은 분석 상태 관리와 분리 — 실패해도 COMPLETED 상태를 덮어쓰지 않음
            try {
                eventPublisher.publishEvent(new VoiceAnalysisCompletedEvent(voiceId));
            } catch (Exception e) {
                log.warn("VoiceAnalysisCompletedEvent 발행 실패 (분석 결과는 저장됨) - voiceId={}", voiceId, e);
            }
        }
    }

    /**
     * 동기 분석 + 저장. 정상 흐름은 {@link #analyzeAsync}가 감싸고, 개발용 시딩 API는 결과를
     * 즉시 확인해 감정을 덮어써야 하므로 이 동기 버전을 직접 호출한다.
     *
     * @return 분석·저장 성공 여부 (Gemini/S3 미구성이거나 실패면 false)
     */
    public boolean analyzeSync(Long voiceId, String voiceKey) {
        if (geminiClient.isEmpty() || s3Client.isEmpty()) {
            log.debug("Gemini or S3 client not configured, skipping analysis for voiceId={}", voiceId);
            return false;
        }
        try {
            Voice voice = voiceAdaptor.queryById(voiceId);
            voice.markAnalysisProcessing();
            voiceAdaptor.save(voice);

            byte[] audioBytes = downloadFromS3(voiceKey);
            String analysisJson = analyzeWithGemini(audioBytes, voiceKey, PROMPT);
            GeminiAnalysisResult result = objectMapper.readValue(analysisJson, GeminiAnalysisResult.class);
            VoiceComposite composite = emotionMapper.toVoiceComposite(result, voice);
            voiceCompositeAdaptor.save(composite);

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
            voice.markAnalysisCompleted();
            voiceAdaptor.save(voice);
            log.info("Gemini analysis saved for voiceId={}, topEmotion={}, labels={}, transcript={}chars",
                    voiceId, composite.getTopEmotion(), labels.size(),
                    result.transcript() != null ? result.transcript().length() : 0);
            return true;
        } catch (Exception e) {
            log.error("Gemini analysis failed for voiceId={}, voiceKey={}", voiceId, voiceKey, e);
            try {
                Voice voice = voiceAdaptor.queryById(voiceId);
                voice.markAnalysisFailed();
                voiceAdaptor.save(voice);
            } catch (Exception ex) {
                log.error("Failed to update analysisStatus to FAILED for voiceId={}", voiceId, ex);
            }
            return false;
        }
    }

    /**
     * 사용자 감정 신고 반영 재분석.
     * <p>정정 신고({@code reportedEmotion} + {@code message})를 프롬프트에 주입해 다시 분석하고,
     * <b>성공 시에만</b> 기존 분석 결과(composite/content/label)를 삭제 후 새 결과로 덮어쓴다.
     * Gemini 호출이 실패하면 삭제 이전이므로 기존 분석 결과가 보존된다.
     * <p>최초 분석과 달리 {@link VoiceAnalysisCompletedEvent}는 발행하지 않는다(이미 발행됨).
     */
    @Async
    public void reanalyzeAsync(Long voiceId, String voiceKey, EmotionType reportedEmotion, String message) {
        if (geminiClient.isEmpty() || s3Client.isEmpty()) {
            log.debug("Gemini or S3 client not configured, skipping reanalysis for voiceId={}", voiceId);
            return;
        }

        try {
            Voice voice = voiceAdaptor.queryById(voiceId);

            // Gemini 호출 먼저 — 실패 시 아래 삭제/저장에 도달하지 않아 기존 결과 보존
            byte[] audioBytes = downloadFromS3(voiceKey);
            String analysisJson = analyzeWithGemini(audioBytes, voiceKey, buildReanalysisPrompt(reportedEmotion, message));
            GeminiAnalysisResult result = objectMapper.readValue(analysisJson, GeminiAnalysisResult.class);

            // 덮어쓰기: 기존 분석 결과 제거 후 재저장
            voiceCompositeAdaptor.deleteByVoiceId(voiceId);
            voiceEmotionLabelAdaptor.deleteByVoiceId(voiceId);
            voiceContentAdaptor.deleteByVoiceId(voiceId);

            voiceCompositeAdaptor.save(emotionMapper.toVoiceComposite(result, voice));
            List<VoiceEmotionLabel> labels = emotionMapper.toEmotionLabels(result, voice);
            if (!labels.isEmpty()) {
                voiceEmotionLabelAdaptor.saveAll(labels);
            }
            if (result.transcript() != null && !result.transcript().isBlank()) {
                voiceContentAdaptor.save(VoiceContent.builder()
                        .voice(voice)
                        .content(result.transcript())
                        .locale("ko-KR")
                        .provider("gemini")
                        .modelVersion(modelName)
                        .build());
            }
            voice.markAnalysisCompleted();
            voiceAdaptor.save(voice);
            log.info("Gemini reanalysis saved for voiceId={}, reportedEmotion={}", voiceId, reportedEmotion);

            // 감정이 바뀌었을 수 있으므로 상담 제안/0턴 세션 재평가를 트리거한다.
            try {
                eventPublisher.publishEvent(new VoiceReanalyzedEvent(voiceId, voice.getUser().getId()));
            } catch (Exception e) {
                log.warn("VoiceReanalyzedEvent 발행 실패 (재분석 결과는 저장됨) - voiceId={}", voiceId, e);
            }
        } catch (Exception e) {
            // 실패 시 기존 분석 결과 보존 (상태/데이터 변경 없음)
            log.error("Gemini reanalysis failed for voiceId={} (기존 분석 결과 유지)", voiceId, e);
        }
    }

    /** 음성을 동기 STT+분석 (챗봇 음성 리프레이밍 전용). 저장/이벤트 없이 결과만 반환. */
    public GeminiAnalysisResult transcribeAndAnalyze(String voiceKey) throws Exception {
        if (geminiClient.isEmpty() || s3Client.isEmpty()) {
            throw new IllegalStateException("Gemini or S3 client not configured");
        }
        byte[] audioBytes = downloadFromS3(voiceKey);
        String analysisJson = analyzeWithGemini(audioBytes, voiceKey, PROMPT);
        return objectMapper.readValue(analysisJson, GeminiAnalysisResult.class);
    }

    private String buildReanalysisPrompt(EmotionType reportedEmotion, String message) {
        StringBuilder sb = new StringBuilder(PROMPT);
        sb.append("\n\n[중요·사용자 정정] 사용자가 이 음성의 실제 대표 감정을 '")
                .append(reportedEmotion.name().toLowerCase())
                .append("'(으)로 정정했습니다.");
        if (message != null && !message.isBlank()) {
            sb.append(" 사용자 설명: ").append(message);
        }
        sb.append(" 이 정정을 우선 반영하여 다시 분석하세요.");
        return sb.toString();
    }

    private byte[] downloadFromS3(String voiceKey) {
        ResponseBytes<GetObjectResponse> responseBytes = s3Client.get().getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(voiceKey)
                        .build()
        );
        return responseBytes.asByteArray();
    }

    private String analyzeWithGemini(byte[] audioBytes, String voiceKey, String promptText) throws Exception {
        Client client = geminiClient.get();
        String mimeType = resolveMimeType(voiceKey);

        Path tempFile = Files.createTempFile("gemini_voice_", extractExtension(voiceKey));
        try {
            Files.write(tempFile, audioBytes);

            com.google.genai.types.File uploaded = client.files.upload(
                    tempFile.toFile(),
                    UploadFileConfig.builder().mimeType(mimeType).build()
            );

            String fileUri = uploaded.uri().orElseThrow(
                    () -> new IllegalStateException("Gemini file URI is empty after upload"));
            log.info("Gemini file uploaded: uri={}, mimeType={}, model={}", fileUri, mimeType, modelName);

            GenerateContentResponse response = client.models.generateContent(
                    modelName,
                    Content.builder()
                            .role("user")
                            .parts(List.of(
                                    Part.builder().fileData(
                                            FileData.builder().fileUri(fileUri).build()
                                    ).build(),
                                    Part.builder().text(promptText).build()
                            ))
                            .build(),
                    GenerateContentConfig.builder()
                            .responseMimeType("application/json")
                            .responseSchema(buildResponseSchema())
                            .build()
            );

            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Empty response from Gemini");
            }
            return text;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Gemini에 반환 JSON 구조를 강제하는 스키마.
     * emotions를 [{ name, intensity }] 배열로 고정하여
     * 호출마다 구조가 달라지는 문제를 방지한다.
     */
    private Schema buildResponseSchema() {
        // { name: string, intensity: number }
        Schema emotionSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "name",      Schema.builder().type(Type.Known.STRING).build(),
                        "intensity", Schema.builder().type(Type.Known.NUMBER).build()
                ))
                .required(List.of("name", "intensity"))
                .build();

        // segment 내 필드 (Map.of는 10개 제한이라 HashMap 사용)
        Map<String, Schema> segmentProps = new HashMap<>();
        segmentProps.put("timestamp",    Schema.builder().type(Type.Known.STRING).build());
        segmentProps.put("text",         Schema.builder().type(Type.Known.STRING).build());
        segmentProps.put("category",     Schema.builder().type(Type.Known.STRING).build());
        segmentProps.put("emotions",     Schema.builder()
                .type(Type.Known.ARRAY)
                .items(emotionSchema)
                .build());
        segmentProps.put("prosody_notes", Schema.builder().type(Type.Known.STRING).build());

        Schema segmentSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(segmentProps)
                .required(List.of("timestamp", "text", "category", "emotions", "prosody_notes"))
                .build();

        // root
        Map<String, Schema> rootProps = new HashMap<>();
        rootProps.put("transcript",      Schema.builder().type(Type.Known.STRING).build());
        rootProps.put("summary",         Schema.builder().type(Type.Known.STRING).build());
        rootProps.put("title",           Schema.builder().type(Type.Known.STRING).build());
        rootProps.put("segments",        Schema.builder()
                .type(Type.Known.ARRAY)
                .items(segmentSchema)
                .build());
        rootProps.put("stability_score", Schema.builder().type(Type.Known.NUMBER).build());

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(rootProps)
                .required(List.of("transcript", "summary", "title", "segments", "stability_score"))
                .build();
    }

    private String resolveMimeType(String voiceKey) {
        String lower = voiceKey.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        return "audio/mpeg";
    }

    private String extractExtension(String voiceKey) {
        int dot = voiceKey.lastIndexOf('.');
        return dot >= 0 ? voiceKey.substring(dot) : ".bin";
    }
}
