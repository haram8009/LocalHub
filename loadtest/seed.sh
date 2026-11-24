#!/usr/bin/env bash
# 부하 테스트 DB 준비: 시드 계정 가입 → 합성 데이터 적재 → 백엔드 재시작(검색 인덱스 재생성) → 준비 완료 대기
#   BASE_URL=http://localhost:8081 POSTS=50000 MEMBERS=1000 bash loadtest/seed.sh
# 저장소 루트에서, 로컬 compose 환경변수(COMPOSE_FILE 등)를 설정한 뒤 실행합니다. 운영 서버에서 실행하지 마세요.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8081}"
POSTS="${POSTS:-50000}"
MEMBERS="${MEMBERS:-1000}"
PASSWORD="${LOADTEST_PASSWORD:-Load1234!}"

case "$BASE_URL" in
  http://localhost*|http://127.0.0.1*) ;;
  *) [ "${I_KNOW_THIS_IS_NOT_PRODUCTION:-}" = "yes" ] || { echo "BASE_URL 이 로컬이 아닙니다: $BASE_URL" >&2; exit 1; } ;;
esac

# shellcheck source=../deploy/lib.sh
source deploy/lib.sh
load_state
[ "$ACTIVE" != "none" ] || { echo "활성 색이 없습니다. 먼저 deploy.sh 로 배포하세요." >&2; exit 1; }

echo "1) 시드 계정(loadseed) 가입 — 이미 있으면 409, 그대로 진행"
# -o /dev/null 은 Windows(Git Bash + MSYS_NO_PATHCONV)에서 실패하므로 본문을 셸에서 버립니다.
code=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/api/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"loadseed\",\"password\":\"$PASSWORD\",\"name\":\"loadseed\",\"email\":\"loadseed@example.com\"}" | tail -n 1)
echo "   HTTP $code"
case "$code" in 201|409) ;; *) echo "시드 계정 가입 실패" >&2; exit 1 ;; esac

echo "2) 합성 데이터 적재 (게시글 $POSTS, 회원 $MEMBERS)"
start=$(date +%s)
{ echo "SET @posts = $POSTS, @members = $MEMBERS;"; cat loadtest/seed.sql; } |
  compose exec -T db sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" ssafy_trip' 2>&1 | grep -v 'Using a password'
echo "   $(( $(date +%s) - start ))초"

echo "3) backend-$ACTIVE 재시작 → 검색 인덱스 재생성"
start=$(date +%s)
compose restart "backend-$ACTIVE" >/dev/null
until compose exec -T "backend-$ACTIVE" curl -fsS -o /dev/null http://localhost:8080/actuator/health/readiness 2>/dev/null; do sleep 5; done
echo "   준비 완료: $(( $(date +%s) - start ))초"

curl -fsS "$BASE_URL/api/posts?size=1" | head -c 200; echo
