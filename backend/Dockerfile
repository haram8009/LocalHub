# syntax=docker/dockerfile:1
# 빌드 단계는 빌드 머신(amd64) 네이티브로 실행하고, 실행 이미지만 대상 플랫폼(arm64)으로 만듭니다.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
# Windows 체크아웃(CRLF)에서도 빌드되도록 줄바꿈을 정규화합니다.
RUN sed -i 's/\r$//' gradlew
RUN sh gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN sh gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v plain | head -n 1)" /app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
