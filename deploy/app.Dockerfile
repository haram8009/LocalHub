# syntax=docker/dockerfile:1
# 빌드 컨텍스트: 저장소 루트  (docker build -f deploy/app.Dockerfile .)
# 프론트(Vue)를 빌드해 백엔드 jar 의 static/ 에 넣어 하나의 배포 단위로 만듭니다.
# 블루/그린 전환 때 화면과 API 버전이 항상 짝이 맞습니다.

FROM --platform=$BUILDPLATFORM node:22-alpine AS frontend
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# VITE_* 값은 빌드 시점에 번들에 포함됩니다. 카카오 JavaScript 키는 공개 키입니다.
ARG VITE_KAKAO_MAP_JAVASCRIPT_KEY=
ARG VITE_TOUR_SOURCE=database
ARG VITE_API_BASE_URL=/api
ENV VITE_KAKAO_MAP_JAVASCRIPT_KEY=$VITE_KAKAO_MAP_JAVASCRIPT_KEY \
    VITE_TOUR_SOURCE=$VITE_TOUR_SOURCE \
    VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# 빌드 단계는 빌드 머신 네이티브로 실행하고, 실행 이미지만 대상 플랫폼(arm64)으로 만듭니다.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS backend
WORKDIR /src
COPY backend/gradlew backend/settings.gradle backend/build.gradle backend/gradle.properties ./
COPY backend/gradle gradle
# Windows 체크아웃(CRLF)에서도 빌드되도록 줄바꿈을 정규화합니다.
RUN sed -i 's/\r$//' gradlew
RUN sh gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/src src
COPY --from=frontend /src/dist src/main/resources/static
RUN sh gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v plain | head -n 1)" /app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 app
WORKDIR /app
COPY --from=backend /app.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
