# ==========================================
# 1단계: 빌드 스테이지 (JDK 21)
# ==========================================
FROM eclipse-temurin:21-jdk-jammy@sha256:c7d5863b5dd8f26b90c64f1d80cc2b0e5a5e4642f8db9955a370d348edd8f438 AS builder
WORKDIR /app

# Gradle 래퍼 및 빌드 스크립트 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew

# 소스코드 복사 및 bootJar 빌드 (테스트 제외하여 빠른 배포 빌드)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ==========================================
# 2단계: 실행 스테이지 (초경량 JRE 21)
# ==========================================
FROM eclipse-temurin:21-jre-jammy@sha256:61d6c7b34d36aee3f45d043101259f97f3c6d428dc2a6f75513789983c5e254f AS runner
WORKDIR /app

# 보안을 위한 비root 시스템 유저 생성
RUN addgroup --system --gid 1001 spring && adduser --system --uid 1001 --ingroup spring spring

COPY --chown=spring:spring docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 0555 /app/docker-entrypoint.sh

# 빌더에서 생성된 실행 가능한 jar 파일만 추출
COPY --from=builder --chown=spring:spring /app/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080
ENV TZ=Asia/Seoul

ENTRYPOINT ["/app/docker-entrypoint.sh"]
