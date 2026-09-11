# ==========================================
# 1단계: 빌드 스테이지 (JDK 21)
# ==========================================
FROM eclipse-temurin:21-jdk-jammy AS builder
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
FROM eclipse-temurin:21-jre-jammy AS runner
WORKDIR /app

# 보안을 위한 비root 시스템 유저 생성
RUN addgroup --system --gid 1001 spring && adduser --system --uid 1001 --ingroup spring spring
USER spring:spring

# 빌더에서 생성된 실행 가능한 jar 파일만 추출
COPY --from=builder --chown=spring:spring /app/build/libs/*.jar app.jar

EXPOSE 8080
ENV TZ=Asia/Seoul

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
