// k6 부하 시나리오. loadtest/run.sh 로 실행합니다. (PROFILE=smoke|load|stress|spike|soak)
// 실제 사용 비율을 흉내 낸 혼합 트래픽을 '도착률(arrival-rate)' 방식으로 보냅니다.
// 서버가 느려져도 요청 속도가 줄지 않으므로 지연이 그대로 드러납니다(closed model 의 착시 방지).
import http from 'k6/http';
import { check, fail } from 'k6';

const BASE = __ENV.BASE_URL || 'http://proxy:80';
const PROFILE = __ENV.PROFILE || 'smoke';
const RATE = Number(__ENV.RATE || 50);          // load/soak: 초당 요청 수
const DURATION = __ENV.DURATION || '5m';
const PASSWORD = __ENV.LOADTEST_PASSWORD || 'Load1234!';
const MEMBERS = Number(__ENV.MEMBERS || 1000);
const POST_MIN = 1000001, POST_MAX = 1000000 + Number(__ENV.POSTS || 50000);

const profiles = {
  smoke: { executor: 'constant-vus', vus: 1, duration: '30s' },
  load: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
    preAllocatedVUs: 50, maxVUs: 400 },
  // 한계점 찾기: 1분마다 초당 요청을 올립니다.
  stress: { executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 800,
    stages: [20, 50, 100, 150, 200, 300].flatMap((r) => [{ target: r, duration: '20s' }, { target: r, duration: '40s' }]) },
  spike: { executor: 'ramping-arrival-rate', startRate: 20, timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 800,
    stages: [{ target: 20, duration: '1m' }, { target: RATE * 4, duration: '10s' }, { target: RATE * 4, duration: '30s' },
      { target: 20, duration: '10s' }, { target: 20, duration: '1m' }] },
  soak: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: __ENV.DURATION || '30m',
    preAllocatedVUs: 50, maxVUs: 400 },
};
if (!profiles[PROFILE]) fail(`알 수 없는 PROFILE: ${PROFILE}`);

// 합격 기준. 넘으면 k6 종료 코드가 0이 아니게 됩니다. (smoke 는 동작 확인용이라 기준을 느슨하게)
const p95 = Number(__ENV.P95_MS || 300);
export const options = {
  scenarios: { [PROFILE]: profiles[PROFILE] },
  // url 태그(게시글 id 마다 다름)는 Prometheus 라벨 폭증을 막으려고 뺍니다. 엔드포인트는 name 태그로 구분합니다.
  systemTags: ['status', 'method', 'name', 'scenario', 'check', 'error_code', 'expected_response'],
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${p95}`],
    ...Object.fromEntries(['list', 'detail', 'search', 'nearby', 'similar', 'tour', 'login', 'write']
      .map((n) => [`http_req_duration{name:${n}}`, [`p(95)<${n === 'login' ? p95 * 3 : p95 * 2}`]])),
  },
};

const pick = (a) => a[Math.floor(Math.random() * a.length)];
const int = (lo, hi) => lo + Math.floor(Math.random() * (hi - lo + 1));
const user = (i) => `load${String(i).padStart(4, '0')}`;
const json = { headers: { 'Content-Type': 'application/json' } };

const CITIES = [[37.5665, 126.978], [35.1796, 129.0756], [33.4996, 126.5312], [37.7519, 128.8761],
  [35.8562, 129.2247], [35.8242, 127.148], [35.8714, 128.6014], [34.7604, 127.6622]];
const TERMS = ['해수욕장', '시장', '공원', '사찰', '카페', '박물관', '가족 여행', '부산', '제주', '맛집 투어', '노을', '산책 코스'];

// 글쓰기용 토큰: 시작 전에 20명 로그인 (액세스 토큰 30분이면 soak 이외에는 충분)
export function setup() {
  const tokens = [];
  for (let i = 1; i <= 20; i++) {
    const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ userId: user(i), password: PASSWORD }),
      { ...json, tags: { name: 'setup' } });
    if (r.status !== 200) fail(`setup 로그인 실패 ${user(i)}: ${r.status} ${r.body}`);
    tokens.push(r.json('accessToken'));
  }
  return { tokens };
}

// 실제 사용 비율 (합 100)
const MIX = [
  ['list', 30], ['detail', 25], ['search', 15], ['nearby', 10], ['similar', 10], ['tour', 5], ['login', 3], ['write', 2],
];
const ACTIONS = {
  list: () => http.get(`${BASE}/api/posts?page=${Math.random() < 0.9 ? int(1, 5) : int(6, 500)}&size=10`, { tags: { name: 'list' } }),
  detail: () => http.get(`${BASE}/api/posts/${int(POST_MIN, POST_MAX)}`, { tags: { name: 'detail' } }),
  search: () => http.get(`${BASE}/api/posts?query=${encodeURIComponent(pick(TERMS))}&size=10`, { tags: { name: 'search' } }),
  nearby: () => {
    const [lat, lng] = pick(CITIES);
    const j = () => (Math.random() - 0.5) * 0.1;
    return http.get(`${BASE}/api/posts/nearby?latitude=${(lat + j()).toFixed(5)}&longitude=${(lng + j()).toFixed(5)}&limit=5`,
      { tags: { name: 'nearby' } });
  },
  similar: () => http.get(`${BASE}/api/posts/${int(POST_MIN, POST_MAX)}/similar?limit=5`, { tags: { name: 'similar' } }),
  tour: () => http.get(`${BASE}/api/tour/areaBasedList2?areaCode=${int(1, 8)}&contentTypeId=12&numOfRows=10&pageNo=${int(1, 5)}`,
    { tags: { name: 'tour' } }),
  login: () => http.post(`${BASE}/api/auth/login`, JSON.stringify({ userId: user(int(1, MEMBERS)), password: PASSWORD }),
    { ...json, tags: { name: 'login' } }),
  write: (data) => http.post(`${BASE}/api/posts`, JSON.stringify({
    category: 'REVIEW', title: `부하 테스트 글 ${Date.now()}`, content: '부하 테스트 중 작성한 글입니다. 시장 산책 코스',
    locations: [{ contentId: '126508', contentTypeId: '12', name: '경복궁', latitude: 37.5796, longitude: 126.977 },
      { contentId: '126535', contentTypeId: '12', name: '창덕궁', latitude: 37.5794, longitude: 126.991 }],
  }), { headers: { ...json.headers, Authorization: `Bearer ${pick(data.tokens)}` }, tags: { name: 'write' } }),
};
const TOTAL = MIX.reduce((s, [, w]) => s + w, 0);

export default function (data) {
  let r = Math.random() * TOTAL, name = MIX[0][0];
  for (const [n, w] of MIX) { if ((r -= w) < 0) { name = n; break; } }
  const res = ACTIONS[name](data);
  check(res, { [`${name} ok`]: (x) => x.status >= 200 && x.status < 300 });
}
