"""Grafana 대시보드 생성기. 대시보드를 바꿀 때는 이 파일을 고친 뒤 실행하세요.
   python monitoring/gen_dashboard.py monitoring/grafana/dashboards/enjoytrip.json
"""
import json, sys

panels, y, pid = [], 0, 0
DS = {"type": "prometheus", "uid": "prometheus"}


def row(title):
    global y, pid
    pid += 1
    panels.append({"type": "row", "title": title, "id": pid, "collapsed": False,
                   "gridPos": {"h": 1, "w": 24, "x": 0, "y": y}, "panels": []})
    y += 1


def ts(title, targets, unit="short", w=8, x=0, h=8, stack=False, desc=""):
    global pid
    pid += 1
    p = {"type": "timeseries", "title": title, "id": pid, "datasource": DS, "description": desc,
         "gridPos": {"h": h, "w": w, "x": x, "y": y},
         "fieldConfig": {"defaults": {"unit": unit, "custom": {"lineWidth": 2, "fillOpacity": 10,
                                                              "showPoints": "never",
                                                              "stacking": {"mode": "normal" if stack else "none"}}},
                         "overrides": []},
         "options": {"legend": {"displayMode": "list", "placement": "bottom"},
                     "tooltip": {"mode": "multi", "sort": "desc"}},
         "targets": [{"datasource": DS, "expr": e, "legendFormat": l, "refId": chr(65 + i)}
                     for i, (e, l) in enumerate(targets)]}
    panels.append(p)


def line(*specs):
    """한 줄에 같은 높이의 패널을 균등 배치한다."""
    global y
    w = 24 // len(specs)
    for i, s in enumerate(specs):
        ts(**s, w=w, x=i * w)
    y += 8


R = "$__rate_interval"
APP = 'application="enjoytrip", uri!~"/actuator.*"'

row("사용자 입장 (Caddy)")
line(
    dict(title="요청 수 (상태 코드별)", unit="reqps", stack=True,
         targets=[(f'sum by (code) (rate(caddy_http_request_duration_seconds_count[{R}]))', "{{code}}")]),
    dict(title="응답 시간 p50 / p95 / p99", unit="s",
         targets=[(f'histogram_quantile({q}, sum by (le) (rate(caddy_http_request_duration_seconds_bucket[{R}])))', f"p{int(q*100)}")
                  for q in (0.5, 0.95, 0.99)]),
    dict(title="오류율 (5xx 비율)", unit="percentunit",
         targets=[(f'sum(rate(caddy_http_request_duration_seconds_count{{code=~"5.."}}[{R}])) / clamp_min(sum(rate(caddy_http_request_duration_seconds_count[{R}])), 1e-9)', "5xx")]),
)

row("백엔드 (블루/그린)")
line(
    dict(title="색별 처리량", unit="reqps", stack=True,
         desc="전환 순간 한 색에서 다른 색으로 트래픽이 넘어가는 모습을 봅니다.",
         targets=[(f'sum by (color) (rate(http_server_requests_seconds_count{{{APP}}}[{R}]))', "{{color}}")]),
    dict(title="엔드포인트별 p95", unit="s",
         targets=[(f'histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_seconds_bucket{{{APP}}}[{R}])))', "{{method}} {{uri}}")]),
    dict(title="엔드포인트별 처리량", unit="reqps",
         targets=[(f'sum by (method, uri) (rate(http_server_requests_seconds_count{{{APP}}}[{R}]))', "{{method}} {{uri}}")]),
)
line(
    dict(title="5xx (색·엔드포인트별)", unit="reqps",
         targets=[(f'sum by (color, uri) (rate(http_server_requests_seconds_count{{{APP}, outcome="SERVER_ERROR"}}[{R}]))', "{{color}} {{uri}}")]),
    dict(title="살아 있는 색 (scrape up)", unit="short",
         targets=[('up{job="backend"}', "{{instance}}")]),
    dict(title="Tomcat 스레드", unit="short",
         targets=[('sum by (color) (tomcat_threads_busy_threads{application="enjoytrip"})', "{{color}} busy"),
                  ('sum by (color) (tomcat_threads_config_max_threads{application="enjoytrip"})', "{{color}} max")]),
)

row("자원 (JVM · 커넥션 풀)")
line(
    dict(title="CPU 사용률 (프로세스)", unit="percentunit",
         targets=[('process_cpu_usage{application="enjoytrip"}', "{{color}}")]),
    dict(title="힙 사용량", unit="bytes",
         targets=[('sum by (color) (jvm_memory_used_bytes{application="enjoytrip", area="heap"})', "{{color}} used"),
                  ('sum by (color) (jvm_memory_max_bytes{application="enjoytrip", area="heap"})', "{{color}} max")]),
    dict(title="GC 정지 시간 (초/초)", unit="percentunit",
         targets=[(f'sum by (color) (rate(jvm_gc_pause_seconds_sum{{application="enjoytrip"}}[{R}]))', "{{color}}")]),
)
line(
    dict(title="Hikari 커넥션", unit="short",
         desc="pending 이 0보다 크면 요청이 DB 커넥션을 기다리고 있습니다.",
         targets=[('sum by (color) (hikaricp_connections_active{application="enjoytrip"})', "{{color}} active"),
                  ('sum by (color) (hikaricp_connections_pending{application="enjoytrip"})', "{{color}} pending"),
                  ('sum by (color) (hikaricp_connections_max{application="enjoytrip"})', "{{color}} max")]),
    dict(title="커넥션 획득 대기 (평균)", unit="s",
         targets=[(f'sum by (color) (rate(hikaricp_connections_acquire_seconds_sum{{application="enjoytrip"}}[{R}])) / clamp_min(sum by (color) (rate(hikaricp_connections_acquire_seconds_count{{application="enjoytrip"}}[{R}])), 1e-9)', "{{color}}")]),
    dict(title="커넥션 사용 시간 (평균)", unit="s",
         targets=[(f'sum by (color) (rate(hikaricp_connections_usage_seconds_sum{{application="enjoytrip"}}[{R}])) / clamp_min(sum by (color) (rate(hikaricp_connections_usage_seconds_count{{application="enjoytrip"}}[{R}])), 1e-9)', "{{color}}")]),
)

row("MySQL")
line(
    dict(title="쿼리 수", unit="ops",
         targets=[(f'rate(mysql_global_status_queries[{R}])', "queries"),
                  (f'rate(mysql_global_status_slow_queries[{R}])', "slow")]),
    dict(title="실행 중 스레드 / 연결", unit="short",
         targets=[('mysql_global_status_threads_running', "running"),
                  ('mysql_global_status_threads_connected', "connected")]),
    dict(title="행 잠금 대기", unit="short",
         desc="SERIALIZABLE 읽기처럼 잠금을 거는 쿼리가 쓰기와 부딪히면 올라갑니다.",
         targets=[(f'rate(mysql_global_status_innodb_row_lock_waits[{R}])', "waits/s"),
                  ('mysql_global_status_innodb_row_lock_current_waits', "current"),
                  (f'rate(mysql_global_status_innodb_deadlocks[{R}])', "deadlocks/s")]),
)

row("부하 발생기 (k6)")
line(
    dict(title="가상 사용자 (VU)", unit="short",
         targets=[('sum(k6_vus)', "VU")]),
    dict(title="k6 요청 수 (엔드포인트별)", unit="reqps", stack=True,
         targets=[(f'sum by (name) (rate(k6_http_reqs_total{{name!="setup"}}[{R}]))', "{{name}}")]),
    dict(title="k6 응답 시간 p95 (엔드포인트별)", unit="s",
         desc="k6 가 측정한 사용자 체감 시간. 백엔드 p95 와 차이가 크면 프록시·네트워크 대기입니다.",
         targets=[('max by (name) (k6_http_req_duration_p95{name!="setup"})', "{{name}}")]),
)

dash = {
    "uid": "enjoytrip-overview", "title": "EnjoyTrip 개요", "tags": ["enjoytrip"],
    "timezone": "browser", "schemaVersion": 41, "version": 1, "editable": False,
    "refresh": "5s", "time": {"from": "now-15m", "to": "now"},
    "panels": panels,
}
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    json.dump(dash, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(len(panels), "panels")
