# syntax=docker/dockerfile:1
# 빌드 컨텍스트: 저장소 루트  (docker build -f deploy/db.Dockerfile .)
# 데이터 디렉터리가 비어 있는 첫 기동 때만 관광지 스키마와 덤프(약 5만 건)를 자동 적재합니다.
# 회원·게시글 테이블은 백엔드가 기동하면서 만듭니다.
FROM mysql:8.0
COPY database/01-schema.sql /docker-entrypoint-initdb.d/01-schema.sql
COPY database/original/SSAFY_TRIP_Dump.sql /docker-entrypoint-initdb.d/02-dump.sql
