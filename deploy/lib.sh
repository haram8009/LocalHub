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
