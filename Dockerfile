# Java4Shell(CVE-2026-114938) 대응: 수정이 포함된 Corretto 17.0.21.1.2 로 고정한다.
# 태그(17.0.21)만으로는 패치 번호가 구분되지 않아 digest 로 고정한다.
FROM amazoncorretto:17.0.21-al2023@sha256:9f2fde515fa060e6218b680d0f5045940d915af43af266145586344bddb30320 AS builder

WORKDIR /workspace

# AL2023 이미지엔 gradlew 가 쓰는 xargs(findutils)가 없다
RUN dnf install -y findutils && dnf clean all

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew

COPY src src
RUN ./gradlew clean bootJar otelAgentJar -x test --no-build-cache

FROM amazoncorretto:17.0.21-al2023-headless@sha256:20c64d400ce911a0b2aa8d4a0c577ebcfaeae7d8e1b59667e4f2a29d08ed3920

WORKDIR /app

# 앱 전역 타임존 KST 고정 (createdDate 등 LocalDateTime 저장/조회 tz 일치)
ENV TZ=Asia/Seoul

COPY --from=builder /workspace/build/libs/*.jar app.jar
COPY --from=builder /workspace/build/otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
COPY docker/entrypoint.sh /app/entrypoint.sh

RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

ENV JAVA_OPTS=""
# DB 호출당 스팬이 3~4개씩 겹친다. SQL 이 보이는 JDBC 스팬만 남긴다.
ENV OTEL_INSTRUMENTATION_HIBERNATE_ENABLED=false \
    OTEL_INSTRUMENTATION_SPRING_DATA_ENABLED=false

EXPOSE 8080 9082

ENTRYPOINT ["/app/entrypoint.sh"]
