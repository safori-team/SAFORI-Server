package com.safori.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@ConditionalOnBean(S3Presigner.class)
@RequiredArgsConstructor
public class S3PresignService {

    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    private static final Duration PUT_EXPIRY = Duration.ofMinutes(10);
    private static final Duration GET_EXPIRY = Duration.ofHours(1);

    /**
     * 음성 파일 업로드용 Presigned PUT URL을 발급한다.
     * 경로: voices/{username}/{UUID}.{extension}
     *
     * @param username  인증된 사용자명 (S3 경로 scoping용)
     * @param extension 파일 확장자 (예: m4a, mp3)
     * @return presignedUrl + voiceKey
     */
    public PresignedUploadResult generatePutUrl(String username, String extension) {
        String voiceKey = "voices/" + username + "/" + UUID.randomUUID() + "." + extension;

        String presignedUrl = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(PUT_EXPIRY)
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(voiceKey)
                                .build())
                        .build()
        ).url().toString();

        return new PresignedUploadResult(presignedUrl, voiceKey);
    }

    /**
     * 음성 파일 조회용 Presigned GET URL을 발급한다. (유효기간 1시간)
     *
     * @param voiceKey S3 오브젝트 키 (예: voices/user1/uuid.m4a)
     * @return presigned GET URL
     */
    public String generateGetUrl(String voiceKey) {
        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(GET_EXPIRY)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(voiceKey)
                                .build())
                        .build()
        ).url().toString();
    }

    /**
     * 서버가 직접 Presigned PUT URL을 발급받아 그 URL로 바이트를 업로드한다.
     * 프론트 없이(테스트/시딩 목적) 프론트와 동일한 업로드 경로를 서버에서 재현한다.
     *
     * @return 업로드된 오브젝트의 voiceKey (이후 기존 등록/분석 흐름에 그대로 사용)
     */
    public String uploadBytesViaPresignedPut(String username, String extension,
                                             byte[] bytes, String contentType) {
        PresignedUploadResult presigned = generatePutUrl(username, extension);
        try {
            java.net.http.HttpRequest.Builder req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(presigned.presignedUrl()))
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes));
            if (contentType != null && !contentType.isBlank()) {
                req.header("Content-Type", contentType);
            }
            java.net.http.HttpResponse<Void> res = java.net.http.HttpClient.newHttpClient()
                    .send(req.build(), java.net.http.HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("S3 PUT 실패 status=" + res.statusCode());
            }
            return presigned.voiceKey();
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("S3 업로드 중 오류", e);
        }
    }

    public record PresignedUploadResult(String presignedUrl, String voiceKey) {}
}
