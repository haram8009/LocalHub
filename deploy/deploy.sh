#!/usr/bin/env bash
# 블루그린 무중단 배포.  사용: ./deploy.sh <이미지 태그>
#
#   1) 비활성 색(예: green)에 새 이미지를 띄운다 (활성 색은 계속 서비스 중)
#   2) readiness 헬스체크 + 스모크 테스트 통과를 기다린다. 실패하면 활성 색은 그대로 두고 종료
#   3) Caddy 의 upstream 을 새 색으로 바꿔 `caddy reload` (연결 유지)
#   4) 이전 색은 DRAIN_SECONDS 뒤에 정지(삭제하지 않음 → rollback.sh 로 즉시 복귀 가능)
#
# 환경변수: DRAIN_SECONDS(기본 30), HEALTH_TIMEOUT(기본 240초), SKIP_PULL=1(로컬 빌드 이미지 사용)
set -euo pipefail

NEW_TAG="${1:?usage: deploy.sh <image-tag>}"
DRAIN_SECONDS="${DRAIN_SECONDS:-30}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# 서버: 스크립트와 compose 파일이 같은 디렉터리. 로컬 테스트: DEPLOY_DIR=<저장소 루트>
cd "${DEPLOY_DIR:-$SCRIPT_DIR}"
source "$SCRIPT_DIR/lib.sh"

load_state
TARGET=$(other_color "${ACTIVE:-none}")
echo "활성: ${ACTIVE:-없음} → 새 버전 대상: $TARGET (태그 $NEW_TAG)"

set_tag "$TARGET" "$NEW_TAG"
export BLUE_TAG GREEN_TAG

if [ "${SKIP_PULL:-0}" != "1" ]; then
  compose pull "backend-$TARGET"
fi

# 첫 배포: 인프라(db, proxy, dozzle)를 먼저 올린다. 이미 있으면 재생성하지 않는다(무중단 보장).
mkdir -p upstream
if [ "${ACTIVE:-none}" = "none" ] && [ ! -f upstream/active.caddy ]; then
  write_upstream "$TARGET"
fi
compose up -d --no-recreate db proxy dozzle

compose up -d --no-deps --force-recreate "backend-$TARGET"
if ! wait_healthy "backend-$TARGET" "$HEALTH_TIMEOUT"; then
  echo "::error::backend-$TARGET 가 healthy 가 되지 못했습니다. 활성 색(${ACTIVE:-없음})은 그대로 유지합니다." >&2
  compose logs --tail=80 "backend-$TARGET" >&2 || true
  compose rm -sf "backend-$TARGET" >&2 || true
  exit 1
fi

# 스모크 테스트: 전환 전에 새 색이 실제 요청을 처리하는지 확인 (화면 + API)
smoke_ok() {
  compose exec -T "backend-$TARGET" curl -fsS -o /dev/null "http://localhost:8080/api/posts?size=1" &&
  { [ "$(compose exec -T "backend-$TARGET" curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/)" = "200" ]; }
}
if ! smoke_ok; then
  echo "::error::스모크 테스트 실패. 전환하지 않고 backend-$TARGET 를 제거합니다." >&2
  compose logs --tail=80 "backend-$TARGET" >&2 || true
  compose rm -sf "backend-$TARGET" >&2 || true
  exit 1
fi

switch_to "$TARGET"
OLD="${ACTIVE:-none}"
save_state "$TARGET"
echo "전환 완료: 활성 = $TARGET ($NEW_TAG)"

if [ "$OLD" != "none" ]; then
  echo "이전 색($OLD)을 ${DRAIN_SECONDS}초 뒤 정지합니다 (처리 중인 요청 정리)."
  sleep "$DRAIN_SECONDS"
  compose stop "backend-$OLD"
fi
compose ps
