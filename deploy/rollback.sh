#!/usr/bin/env bash
# 직전 색(정지된 이전 버전)으로 즉시 되돌립니다.  사용: ./rollback.sh
# 이전 컨테이너/이미지가 남아 있으므로 새로 pull 하지 않고 몇 초 안에 복귀합니다.
set -euo pipefail
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# 서버: 스크립트와 compose 파일이 같은 디렉터리. 로컬 테스트: DEPLOY_DIR=<저장소 루트>
cd "${DEPLOY_DIR:-$SCRIPT_DIR}"
source "$SCRIPT_DIR/lib.sh"

load_state
[ "${ACTIVE:-none}" != "none" ] || { echo "활성 색 정보가 없습니다."; exit 1; }
TARGET=$(other_color "$ACTIVE")
echo "롤백: $ACTIVE → $TARGET ($(tag_of "$TARGET"))"
export BLUE_TAG GREEN_TAG

compose up -d --no-deps --no-recreate "backend-$TARGET"
wait_healthy "backend-$TARGET" "$HEALTH_TIMEOUT" || { echo "::error::$TARGET 가 healthy 가 아닙니다. 활성 색($ACTIVE)은 그대로 둡니다." >&2; compose logs --tail=80 "backend-$TARGET" >&2; compose rm -sf "backend-$TARGET" >&2 || true; exit 1; }
# 정지돼 있던 색은 JVM 이 차갑다. 급하면 WARMUP_SECONDS=0 으로 바로 넘길 수 있다.
warm_up "$TARGET"
switch_to "$TARGET"
OLD="$ACTIVE"
save_state "$TARGET"
echo "롤백 완료: 활성 = $TARGET. 문제가 있던 $OLD 를 정지합니다."
sleep "${DRAIN_SECONDS:-15}"
compose stop "backend-$OLD"
compose ps
