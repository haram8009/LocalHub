# syntax=docker/dockerfile:1
# 빌드 컨텍스트: 저장소 루트  (docker build -f deploy/web.Dockerfile .)
# 프론트를 빌드한 정적 파일을 Caddy가 서빙하고 /api 는 backend 로 프록시합니다.
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
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

FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/dist /srv
EXPOSE 80 443 443/udp
