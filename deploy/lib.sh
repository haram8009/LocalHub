#!/usr/bin/env bash
# deploy.sh / rollback.sh 공용 함수. 상태 파일(.deploy-state)에 활성 색과 색별 이미지 태그를 기록합니다.

STATE_FILE=".deploy-state"

compose() { docker compose "$@"; }

load_state() {
  ACTIVE=none; BLUE_TAG=latest; GREEN_TAG=latest
  # shellcheck disable=SC1090
  [ -f "$STATE_FILE" ] && source "$STATE_FILE"
  return 0
}
save_state() {  # $1 = 새 활성 색
  printf 'ACTIVE=%s\nBLUE_TAG=%s\nGREEN_TAG=%s\n' "$1" "$BLUE_TAG" "$GREEN_TAG" > "$STATE_FILE.tmp"
  mv "$STATE_FILE.tmp" "$STATE_FILE"
}
other_color() { [ "$1" = "blue" ] && echo green || echo blue; }
tag_of() { [ "$1" = "blue" ] && echo "$BLUE_TAG" || echo "$GREEN_TAG"; }
set_tag() {  # $1 = 색, $2 = 태그
  if [ "$1" = "blue" ]; then BLUE_TAG="$2"; else GREEN_TAG="$2"; fi
}

# 활성 upstream 파일을 원자적으로 교체한다. 새 색이 잠깐 안 보여도 최대 10초 재시도한다.
write_upstream() {  # $1 = 색
  mkdir -p upstream
  cat > upstream/active.caddy.tmp <<EOF
reverse_proxy backend-$1:8080 {
	lb_try_duration 10s
	lb_try_interval 250ms
}
EOF
  mv upstream/active.caddy.tmp upstream/active.caddy
}

# 컨테이너를 재시작하지 않는 설정 재적재. 진행 중인 연결은 유지된다.
reload_proxy() {
  compose exec -T proxy caddy reload --config /etc/caddy/Caddyfile
}

switch_to() {  # $1 = 색
  write_upstream "$1"
  echo "$(date +%T.%3N) upstream → $1 (caddy reload)"
  reload_proxy
  echo "$(date +%T.%3N) reload 완료"
}

# 전환 전에 새 색의 JVM 을 데운다(JIT 컴파일·클래스 로딩·커넥션 풀).
# 부하 테스트에서 차가운 색으로 바로 넘기면 첫 20초 동안 p95 가 4~9초로 튀었다.
# 데이터를 바꾸지 않는 읽기 요청만 컨테이너 안에서 4개 동시 루프로 보낸다. WARMUP_SECONDS=0 이면 생략.
warm_up() {  # $1 = 색
  local secs="${WARMUP_SECONDS:-20}"
  [ "$secs" -gt 0 ] || return 0
  echo "$(date +%T) backend-$1 워밍업 ${secs}초 (읽기 요청만)"
  compose exec -T "backend-$1" sh -c '
    B=http://localhost:8080/api/posts
    ID=$(curl -s "$B?size=1" | grep -o "\"id\":[0-9]*" | head -n 1 | cut -d: -f2)
    end=$(( $(date +%s) + '"$secs"' ))
    for w in 1 2 3 4; do
      while [ "$(date +%s)" -lt "$end" ]; do
        curl -s -o /dev/null "$B?size=12&page=$w"
        curl -s -o /dev/null "$B/search?query=%EC%84%9C%EC%9A%B8"
        curl -s -o /dev/null "$B/nearby?latitude=37.5665&longitude=126.978&limit=5"
        if [ -n "$ID" ]; then curl -s -o /dev/null "$B/$ID/similar"; fi
      done &
    done
    wait' || echo "워밍업 중 오류(무시하고 진행)" >&2
}

# 배포마다 새 앱 이미지가 쌓여 디스크가 차지 않도록, 두 색이 쓰는 태그만 남기고 같은 저장소의 옛 이미지를 지운다.
# (롤백은 한 단계 전 색까지만 가능하므로 그 이전 이미지는 필요 없다.) KEEP_OLD_IMAGES=1 이면 생략.
prune_old_images() {  # $1 = 방금 띄운 서비스
  [ "${KEEP_OLD_IMAGES:-0}" = "1" ] && return 0
  local repo ref
  repo=$(docker inspect --format '{{.Config.Image}}' "$(compose ps -q "$1")" 2>/dev/null) || return 0
  repo="${repo%:*}"
  docker images --format '{{.Repository}}:{{.Tag}}' "$repo" | while read -r ref; do
    case "${ref##*:}" in "$BLUE_TAG"|"$GREEN_TAG") ;; *) docker rmi "$ref" >/dev/null 2>&1 && echo "옛 이미지 삭제: $ref" ;; esac
  done
  docker image prune -f >/dev/null 2>&1 || true   # 태그 없는(dangling) 층
}

wait_healthy() {  # $1 = 서비스, $2 = 제한 시간(초)
  local cid status waited=0
  cid=$(compose ps -q "$1")
  while [ "$waited" -lt "$2" ]; do
    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || echo none)
    [ "$status" = "healthy" ] && { echo "$1: healthy"; return 0; }
    sleep 3; waited=$((waited + 3))
  done
  echo "$1: $status (timeout ${2}s)" >&2
  return 1
}
