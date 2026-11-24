#!/usr/bin/env bash
# k6 를 Docker 로 실행합니다(설치 불필요). 결과는 Prometheus 로 전송되어 Grafana 'EnjoyTrip 개요' 아래쪽에 보입니다.
#   bash loadtest/run.sh smoke
#   RATE=80 DURATION=5m bash loadtest/run.sh load
#   bash loadtest/run.sh stress
# 요약 JSON 은 loadtest/results/ 에 저장됩니다.
set -euo pipefail
cd "$(dirname "$0")"
PROFILE="${1:-smoke}"
NETWORK="${NETWORK:-enjoytrip_default}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p results

# Git Bash 에서 /scripts 경로가 Windows 경로로 바뀌지 않게 합니다.
export MSYS_NO_PATHCONV=1
docker run --rm -i --network "$NETWORK" \
  -v "$(pwd -W 2>/dev/null || pwd):/scripts" \
  -e PROFILE="$PROFILE" -e RATE="${RATE:-50}" -e DURATION="${DURATION:-5m}" -e P95_MS="${P95_MS:-300}" \
  -e BASE_URL="${BASE_URL:-http://proxy:80}" -e POSTS="${POSTS:-50000}" -e MEMBERS="${MEMBERS:-1000}" \
  -e K6_PROMETHEUS_RW_SERVER_URL="${PROM_RW_URL:-http://prometheus:9090/api/v1/write}" \
  -e K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg" \
  -e K6_PROMETHEUS_RW_PUSH_INTERVAL=5s \
  grafana/k6:1.2.3 run \
  -o experimental-prometheus-rw \
  --tag testid="$PROFILE-$STAMP" \
  --summary-export "/scripts/results/$PROFILE-$STAMP.json" \
  /scripts/scenarios.js
