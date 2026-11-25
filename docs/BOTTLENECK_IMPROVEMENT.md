# 병목 개선 기록: 과정, 판단 근거, 적용 방법, 결과

부하 테스트에서 찾은 병목을 어떻게 진단했고, 왜 그 방법을 골랐고, 어떻게 적용했고, 얼마나 나아졌는지 기록합니다.
측정 환경, 시나리오, 실행 방법은 [LOAD_TEST.md](LOAD_TEST.md)를 참고하세요.

## 진행 원칙

1. **측정 먼저**: 추측으로 고치지 않습니다. 증상(k6), 서버 자원(Prometheus), DB 쿼리(performance_schema)를 같은 시간대로 맞춰 봅니다.
2. **한 번에 하나**: 한 가지만 고치고 **같은 조건(초당 25요청, 2분)**으로 다시 잽니다. 그래야 각 수정의 효과를 구분할 수 있습니다.
3. **의미는 그대로**: 성능 때문에 결과(검색 순위, 추천 순서, 일관성 보장)가 바뀌면 안 됩니다. 기존 통합 테스트(실제 MySQL, 56개)를 매번 통과시킵니다.
4. **배포까지 검증**: 운영은 블루그린이라 새 버전과 옛 버전이 같은 DB를 함께 씁니다. 스키마 변경과 기동 작업이 무중단 배포를 깨지 않는지 부하 중에 확인합니다.

### 사용한 근거 자료

| 질문 | 도구 | 지표 |
| --- | --- | --- |
| 사용자가 느끼는 지연과 실패 | k6 요약(JSON), Grafana k6 패널 | 요청별 p95, 실패율, 처리 못 한 요청 |
| 서버의 어디가 막혔나 | Prometheus(Micrometer) | Hikari 활성/대기, Tomcat busy 스레드, 프로세스 CPU |
| DB의 어디에 시간을 쓰나 | `performance_schema.events_statements_summary_by_digest` | 쿼리별 총 시간, 호출 수, 평균, 검사한 행 수 |
| 쿼리가 인덱스를 쓰나 | `EXPLAIN` | type, key, rows, Extra |
| 실패의 원인 | 백엔드 로그, mysqld-exporter | 예외 메시지, 행 잠금 대기 |

---

## 0. 기준선 진단

### 증상

- 스트레스 테스트(20 → 300 req/s 계단)에서 **약 30 req/s**를 넘지 못했습니다. 응답은 20초까지 늘었고 글쓰기 94%가 실패했습니다.
- 초당 25요청(load)에서도 전체 p95 1.29초, 검색 p95 1.99초, 실패율 1.32%(글쓰기 성공률 21%)였습니다.

### 근거: 서버 자원 (스트레스 테스트가 무너지는 순간)

| 지표 | 값 | 해석 |
| --- | --- | --- |
| 백엔드 CPU | **33%** | 계산이 부족한 게 아님 |
| Hikari 활성 / 대기 | **10 / 188** | 커넥션 풀이 전부 사용 중이고 188개 요청이 커넥션을 기다림 |
| Tomcat busy 스레드 | **200 / 200** | 모든 스레드가 DB를 기다리며 멈춤 (Prometheus 수집 요청까지 처리 못 함) |
| 행 잠금 대기 | 초당 3~5회 | 잠금 경합이 있음 |
| 백엔드 로그 | `Deadlock found when trying to get lock` **222건** | 글쓰기 실패의 원인 |

→ **판단**: CPU가 놀고 있는데 스레드와 커넥션이 모두 묶여 있습니다. 앱 계산이 아니라 **DB에서 오래 걸리는 쿼리와 잠금**이 커넥션을 붙잡고 있습니다. 풀이나 스레드를 늘려도 DB에 같은 일을 더 동시에 시킬 뿐이라 해결이 안 됩니다.

### 근거: DB 시간 분석 (performance_schema, 쿼리 시간순)

| 총 시간 | 호출 | 평균 | 검사 행/호출 | 쿼리 |
| ---: | ---: | ---: | ---: | --- |
| 1,998초 | 1,350 | **1,480ms** | **998,845** | 검색: `post_search_postings WHERE term LIKE ?` (검색어 1개) |
| 696초 | 433 | 1,606ms | 999,582 | 검색: 같은 쿼리 (검색어 여러 개) |
| 194초 | 1,178 | 165ms | 30,156 | 주변 추천: `post_locations JOIN travel_posts` 상자 범위 |
| 89초 | 1,783 | 50ms | 13,648 | 검색 결과 메타데이터 `WHERE id IN (...)` |
| 70초 | 1,005,992 | 0.1ms | 0 | `SELECT @@SESSION.transaction_read_only` (드라이버가 트랜잭션마다 실행) |
| 40초 | 3,658 | 11ms | 0 | 목록 `COUNT(*)` |

(색인 재구축용 INSERT 100만 건은 데이터 준비 단계라 제외)

→ **판단**: 읽기 쿼리 시간의 약 **77%가 검색**입니다. 검색어 하나에 postings **100만 행을 전부 훑습니다**(평균 1.5초). 이 쿼리가 커넥션을 붙잡는 시간이 풀 고갈의 주원인이고, 그다음이 주변 추천입니다.

### 우선순위를 정한 근거

| 순서 | 대상 | 이유 |
| --- | --- | --- |
| A | 교착 상태 | **실패**를 만드는 문제라 지연보다 먼저. 원인이 설정 한 줄(격리 수준)이라 범위가 작음 |
| B | 검색 | DB 시간의 77%. 가장 큰 효과 |
| C | 주변 추천 | DB 시간 2위. 필요 없는 행을 대량 전송 |

---

## A. 읽기 격리 수준: SERIALIZABLE → REPEATABLE READ 스냅샷 읽기

### 문제

검색, 비슷한 코스, 주변 추천의 읽기 트랜잭션이 `SERIALIZABLE`이었습니다. InnoDB에서 SERIALIZABLE은 모든 일반 SELECT를 `SELECT ... FOR SHARE`(공유 잠금)로 바꿉니다. 그래서 다음 일이 겹칩니다.

- 검색은 postings 100만 행을 읽으며 **읽은 행 전체에 공유 잠금**을 겁니다.
- 글쓰기는 같은 postings에 행을 넣거나 지우려고 **배타 잠금**을 기다립니다.
- 두 쪽이 서로의 잠금을 기다리면 InnoDB가 교착 상태로 판단해 한쪽(주로 글쓰기)을 롤백합니다 → **222건 실패**.

### 왜 원래 SERIALIZABLE이었나

H2 시절, 게시글 본문과 검색 색인을 각각 읽는 사이에 다른 트랜잭션이 글을 수정하면 **옛 색인 + 새 본문**이 섞여 보이는 문제가 테스트로 재현됐습니다. 이를 막으려고 SERIALIZABLE로 올렸습니다. 이 보장은 지켜야 합니다.

### 검토한 대안

| 대안 | 섞임 방지 | 쓰기 차단 | 판단 |
| --- | --- | --- | --- |
| SERIALIZABLE 유지 + 교착 시 재시도 | O | **막음** | 증상만 숨김. 부하가 오르면 재시도가 경합을 더 키움 |
| READ COMMITTED | **X** | 안 막음 | SELECT마다 새 스냅샷이라 본문과 색인 사이에서 섞일 수 있음 |
| **REPEATABLE READ (읽기 전용)** | **O** | **안 막음** | InnoDB는 첫 읽기 시점의 스냅샷(MVCC)을 트랜잭션 끝까지 **잠금 없이** 읽음 |

→ **선택 근거**: InnoDB의 REPEATABLE READ에서 일반 SELECT는 잠금 없는 **일관된 읽기**(consistent read)입니다. 트랜잭션 안의 모든 SELECT가 같은 시점을 보므로 "본문과 색인이 섞이지 않음"이 그대로 보장되고, 쓰기를 전혀 막지 않습니다. H2에서 섞임이 재현된 것은 H2의 구현 차이였고, 운영 DB는 MySQL입니다.

### 적용 방법

```java
// PostSearchService, CourseRecommendationService, NearbyPostService
// InnoDB REPEATABLE READ: 첫 읽기 시점의 스냅샷을 잠금 없이 끝까지 읽습니다(쓰기를 막지 않음).
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
```

**검증**: `PostSearchIntegrationTest.snapshotReadDoesNotMixOldPostingsWithNewPostContentAndDoesNotBlockWriters`(실제 MySQL)
1. 읽기 트랜잭션에서 본문과 색인을 한 번 읽어 스냅샷을 고정합니다.
2. 다른 스레드가 같은 글을 수정하고 커밋합니다. **5초 안에 커밋되어야 통과**합니다(쓰기가 막히지 않음).
3. 같은 읽기 트랜잭션에서 다시 읽으면 본문과 색인이 **둘 다 옛 버전**입니다(섞이지 않음).
4. 트랜잭션이 끝난 뒤의 새 검색은 새 버전을 봅니다.

### 결과 (초당 25요청)

| | 전 | 후 |
| --- | ---: | ---: |
| 실패율 | 1.32% | **0%** |
| 교착 상태 | 222건 | **0건** |
| 글쓰기 p95 | 807ms (성공 21%) | **82ms** (성공 100%) |
| 전체 p95 | 1,285ms | 772ms |
| 검색 p95 | 1,990ms | 1,069ms |

검색은 잠금 대기가 사라진 만큼만 빨라졌고 여전히 1초대입니다. 100만 행을 훑는 비용 자체는 그대로이기 때문입니다 → B로 넘어갑니다.

---

## B. 부분 일치 검색: 단어 사전 테이블 (Flyway V2)

### 문제

검색은 "부산"으로 "부산여행", "부산에서"도 찾는 **부분 일치**입니다. 그래서 `term LIKE '%부산%'`처럼 앞에 `%`가 붙고, B-tree 인덱스를 쓸 수 없어 테이블 전체를 훑습니다.

그런데 훑는 대상이 `post_search_postings`(단어, 글) 쌍 **100만 행**이었습니다. 같은 단어("서울")가 글마다 한 행씩 중복되어 있어서, 서로 다른 단어는 약 **5.8만 개**뿐인데도 100만 행을 비교합니다.

### 검토한 대안

| 대안 | 판단 |
| --- | --- |
| 커넥션 풀·스레드 늘리기 | 쿼리 하나의 비용(1.5초)은 그대로. 동시에 더 많이 훑게 되어 DB CPU만 더 씀 |
| 결과 캐시 | 검색어 조합이 다양해 적중률이 낮고, 글이 바뀌면 무효화가 복잡함 |
| MySQL FULLTEXT(ngram) | 토큰 분리 방식과 점수가 바뀌어 **검색 의미가 달라짐**. 직접 구현한 색인 알고리즘(과제 요구)을 대체함. H2와 호환되지 않음 |
| **서로 다른 단어만 모은 사전을 훑기** | 의미가 같음(같은 단어 집합에 같은 LIKE). 훑는 행이 100만 → 5.8만(약 17분의 1) |

→ **선택 근거**: 부분 일치를 유지하는 한 "훑기"는 피할 수 없으니 **훑는 양을 줄입니다**. 사전에서 맞는 단어를 찾은 뒤, 그 단어의 postings는 PK(term_hash, post_id)로 바로 찾습니다. 결과는 기존과 같습니다.

### 적용 방법

**1) 스키마 (`db/migration/mysql/V2__search_terms.sql`, H2도 같은 내용)**

```sql
CREATE TABLE IF NOT EXISTS post_search_terms (
 term_hash VARCHAR(64) PRIMARY KEY, term LONGTEXT NOT NULL
);
```

**2) 검색 쿼리 (`SearchIndexMapper.xml`)**

```sql
-- 전: postings 100만 행을 훑음
SELECT term, post_id, title_hit, body_hit FROM post_search_postings WHERE term LIKE ? ...
-- 후: 사전 5.8만 행을 훑고, postings 는 PK 로 찾음
SELECT p.term, p.post_id, p.title_hit, p.body_hit
FROM post_search_terms t JOIN post_search_postings p ON p.term_hash = t.term_hash
WHERE (t.term LIKE ? ESCAPE '!' OR ...)
```

**3) 쓰기**: 색인에 단어를 넣을 때 사전에도 넣습니다(`addTerm` 후 `add`). 이미 있으면 무시합니다(MySQL `INSERT IGNORE`, H2 `MERGE ... KEY`). 같은 매퍼 ID를 DB마다 다르게 쓰려고 MyBatis `databaseId`(`MyBatisConfig`의 `VendorDatabaseIdProvider`)를 추가했습니다. 글이 지워져도 사전의 단어는 남지만, postings와 조인하므로 결과에 영향이 없습니다.

**4) 기존 데이터와 블루그린 호환**
- 색인 버전(`SearchIndexState.VERSION`)을 올리면 기동할 때 색인을 처음부터 다시 만듭니다. 5만 글 기준 **약 7분**이 걸려 배포가 그만큼 막힙니다.
- 그래서 버전을 올리지 않고, 기동할 때 **postings에는 있는데 사전에 없는 단어만 채웁니다**(`SearchIndexInitializer` → `syncTerms`).
- 이 방식은 블루그린 전환 중 **사전을 모르는 옛 색이 쓴 글**도 다음 기동 때 사전에 반영합니다.
- 검증: `PostSearchIntegrationTest.startupSyncRestoresDictionaryForPostsWrittenWithoutIt`. 사전을 비운 뒤(옛 버전이 쓴 글을 흉내) 기동 동기화 후 다시 검색되는지, 두 번째 실행에서는 0개를 추가하는지 확인합니다.

### 배포 중 발견한 문제와 보완: 기동 동기화가 9분 이상

처음 만든 동기화 쿼리는 다음과 같았습니다.

```sql
INSERT IGNORE INTO post_search_terms(term_hash, term)
SELECT p.term_hash, p.term FROM post_search_postings p
WHERE p.post_id = (SELECT MIN(q.post_id) FROM post_search_postings q WHERE q.term_hash = p.term_hash)
  AND NOT EXISTS (SELECT 1 FROM post_search_terms t WHERE t.term_hash = p.term_hash)
```

부하 데이터로 배포하자 새 색이 **240초 안에 healthy가 되지 못했고**, 배포 스크립트가 전환을 거부했습니다. 기존 색은 그대로 서비스했으니 안전장치는 의도대로 동작했습니다.

**근거 (performance_schema)**: 이 쿼리는 호출당 평균 **420초**에 **4억 3천만 행**을 검사했습니다. 100만 행 각각에 대해 `MIN` 서브쿼리를 다시 실행했기 때문입니다. 실패한 배포의 컨테이너가 지워진 뒤에도 DB에서 계속 돌고 있었습니다. 게다가 InnoDB의 `INSERT ... SELECT`는 **읽은 행에 공유 잠금**을 걸어 그동안 글쓰기까지 막습니다.

**보완**:
1. 단어별 대표 행을 `GROUP BY term_hash` + `MIN(post_id)`로 구합니다. 이 조합은 PK(term_hash, post_id)를 **단어 수만큼만 건너뛰며** 읽습니다(loose index scan, EXPLAIN의 `Using index for group-by`).
2. 잠그지 않는 일반 SELECT로 빠진 단어를 읽어 온 뒤, 500개씩 나눠 넣습니다.

```sql
SELECT p.term_hash, p.term
FROM (SELECT term_hash, MIN(post_id) AS post_id FROM post_search_postings GROUP BY term_hash) m
JOIN post_search_postings p ON p.term_hash = m.term_hash AND p.post_id = m.post_id
WHERE NOT EXISTS (SELECT 1 FROM post_search_terms t WHERE t.term_hash = m.term_hash)
```

→ 100만 postings에서 **2.6초**(5.8만 단어). 배포 전체가 22초에 끝났습니다.

**교훈**: 기동 시 실행되는 데이터 작업은 **운영 규모 데이터로** 재야 합니다. 테스트 데이터(글 몇 개)에서는 드러나지 않았습니다.

### 결과

| | 전(A 적용 후) | 후 |
| --- | ---: | ---: |
| 검색 p95 (k6) | 1,069ms | **139ms** |
| 검색 쿼리 평균 (DB) | 1,480ms | **43.5ms** |
| 검색 쿼리 검사 행 | 약 100만 | **약 6.2만** |

---

## C. 주변 추천: 거리 계산과 상위 N 선택을 DB로 (Flyway V3)

### 문제

주변 추천은 "현재 위치에서 5km 안에 장소가 있는 글을, 가까운 순으로 N개" 보여 줍니다. 기존 방식은 다음과 같았습니다.

1. 위도·경도 상자(bounding box) 안의 장소를 **전부** 본문 열(`l.*`: 설명, 이미지 URL 등)과 함께 가져옵니다.
2. 자바에서 하버사인 거리를 계산하고, 글마다 가장 가까운 장소를 고르고, 정렬해서 N개를 자릅니다.

**근거 (EXPLAIN, 서울 시청 기준)**

```
type: ALL   possible_keys: PRIMARY,idx_post_location_coordinates   key: NULL
rows: 196160   Extra: Using where; Using filesort
```

- 상자 안에 **23,265행**이 있습니다(전체 20만 행의 약 12%). 위도 범위만으로는 41,945행입니다.
- 비율이 커서 옵티마이저가 `(latitude, longitude)` 인덱스를 버리고 **테이블 전체를 읽고 정렬**합니다. 인덱스로 찾은 뒤 행마다 본문을 다시 읽는 비용이 더 크다고 판단하기 때문입니다.
- 결과적으로 **N=3개를 보여 주려고 2만 3천 행을 본문 열까지 전송**했습니다(쿼리 평균 165ms, 검사 3만 행).

### 검토한 대안

| 대안 | 판단 |
| --- | --- |
| MySQL 공간 인덱스(`SPATIAL`, `ST_Distance_Sphere`) | 좌표 열을 `POINT`로 바꿔야 하고 H2와 호환되지 않음. 이 규모에서는 과함 |
| 지오해시 열 추가 | 쓰기 로직과 기존 데이터 변환이 필요 |
| 인덱스 힌트로 강제 | 행마다 테이블을 다시 읽는 비용은 그대로 |
| **SQL에서 계산하고 덮개 인덱스 사용** | MySQL과 H2 모두 지원하는 표준 SQL(윈도 함수). 스키마는 인덱스 하나만 바뀜 |

→ **선택 근거**: 비용의 대부분은 거리 계산이 아니라 **필요 없는 행과 열을 읽고 보내는 것**입니다.
- 거리 계산에 필요한 열(위도, 경도, visit_order, 그리고 PK로 따라오는 post_id, visit_index)을 **모두 담은 덮개 인덱스**를 만들면 상자 범위를 테이블에 접근하지 않고 읽을 수 있습니다.
- 본문 열은 최종 N개만 읽습니다.

### 적용 방법

**1) 인덱스 (`db/migration/mysql/V3__nearby_covering_index.sql`)**

```sql
CREATE INDEX idx_post_location_nearby ON post_locations(latitude, longitude, visit_order);
DROP INDEX idx_post_location_coordinates ON post_locations;  -- 새 인덱스의 앞부분과 같아 중복
```

옛 색(옛 쿼리)도 새 인덱스의 앞부분을 그대로 쓸 수 있어 블루그린 전환 중에도 안전합니다.

**2) 쿼리 (`PostMapper.xml` `findNearestPoints`) — 안쪽부터**

1. 상자 안 장소를 덮개 인덱스만으로 읽어 하버사인 거리를 계산하고, 반경 5km 밖을 거릅니다.
2. `ROW_NUMBER() OVER (PARTITION BY post_id ORDER BY distance_km, visit_order)`로 **글마다 가장 가까운 장소 하나**를 고릅니다. 동률이면 앞 순서이며, 기존 자바 로직과 같습니다.
3. 거리 → 최신 → id 순으로 정렬해 `LIMIT N`.
4. 그 N개만 `post_locations`의 본문 열과 조인합니다.

```sql
SELECT l.*, best.created_at FROM (
  SELECT ranked.post_id, ranked.visit_index, ranked.distance_km, p.created_at FROM (
    SELECT d.*, ROW_NUMBER() OVER (PARTITION BY d.post_id ORDER BY d.distance_km, d.visit_order) AS rn
    FROM (SELECT post_id, visit_index, visit_order, /* 하버사인 */ ... AS distance_km
          FROM post_locations
          WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?) d
    WHERE d.distance_km <= 5
  ) ranked JOIN travel_posts p ON p.id = ranked.post_id
  WHERE ranked.rn = 1
  ORDER BY ranked.distance_km, p.created_at DESC, ranked.post_id DESC
  LIMIT ?
) best JOIN post_locations l ON l.post_id = best.post_id AND l.visit_index = best.visit_index
ORDER BY best.distance_km, best.created_at DESC, best.post_id DESC
```

**3) 서비스 (`NearbyPostService`)**: 자바의 필터, 최근접 선택, 정렬 코드를 지우고 DB가 준 N개의 응답만 만듭니다. 응답의 `distanceKm`는 기존과 같은 자바 하버사인으로 다시 계산해 소수점까지 이전과 같게 유지합니다.

**검증**: 기존 `NearbyPostIntegrationTest`를 고치지 않고 그대로 통과했습니다. 다음을 확인하는 테스트입니다.
- 글마다 가장 가까운 장소로 순위를 매기고 같은 글을 중복해서 내보내지 않음
- 5km 안의 장소만 포함하고 결과가 N개보다 적어도 됨
- 거리가 같으면 최신 → id 순, 개수 제한 적용
- 잘못된 좌표와 개수는 400 응답

**EXPLAIN (후)**

```
type: range   key: idx_post_location_nearby   Extra: Using where; Using index
```

`Using index` = 테이블에 접근하지 않고 인덱스만 읽음. 정렬(filesort)도 상자 전체가 아니라 반경 안 후보에 대해서만 일어납니다.

### 결과

| | 전 | 후 |
| --- | ---: | ---: |
| 주변 추천 p95 (k6) | 818ms | **74ms** |
| 쿼리 평균 (DB) | 165ms | **23.8ms** |
| 앱으로 전송하는 행 | 약 2만 3천 (본문 열 포함) | **N개(최대 10)** |

---

## B+C 적용 후 종합 결과

### 같은 부하(초당 25요청, 2분)

| p95 | 기준선 | A 후 | B+C 후 |
| --- | ---: | ---: | ---: |
| 전체 | 1,285ms | 772ms | **112ms** |
| 검색 | 1,990ms | 1,069ms | **139ms** |
| 주변 추천 | 860ms | 818ms | **74ms** |
| 목록 | 332ms | 80ms | **43ms** |
| 상세 | 348ms | 80ms | **58ms** |
| 비슷한 코스 | 359ms | 65ms | **17ms** |
| 글쓰기 | 807ms | 82ms | 125ms |
| 실패율 | 1.32% | 0% | **0%** |

B와 C는 한 이미지로 함께 측정했습니다. 다만 검색과 주변 추천은 서로 다른 엔드포인트라 각 수정의 효과가 해당 엔드포인트 수치에 따로 드러납니다. 목록·상세처럼 직접 고치지 않은 요청도 빨라진 것은 **무거운 쿼리가 커넥션을 오래 붙잡지 않게 되어** 커넥션 대기가 줄었기 때문입니다. 글쓰기(82 → 125ms)는 사전 테이블에 단어를 함께 넣는 비용이 더해진 결과이며, 통과 기준(600ms) 안입니다.

### 처리 한계 (스트레스 테스트)

| 도착률 | 실제 처리 | p95 | 백엔드 CPU |
| ---: | ---: | ---: | ---: |
| 50 req/s | 50 | 112~118ms | 26~30% |
| 100 req/s | 100 | 130ms | 55% |
| 120 req/s | 120 | 190ms | 76% |
| 135 req/s 이상 | 120~160 | 1.4~3.0초 | 97~100% |

**약 30 → 약 120 req/s (p95 300ms 이내, 약 4배)**. 한계에서의 양상도 바뀌었습니다.

| | 기준선 | 지금 |
| --- | --- | --- |
| 막히는 곳 | DB 잠금, 느린 쿼리 (CPU 33%) | **백엔드 CPU 100%** |
| 한계를 넘으면 | 교착 상태로 글쓰기 실패 | 실패 없이 느려지기만 함 |

DB가 아니라 계산 자원이 한계라는 것은, 이제 서버 사양을 올리거나 인스턴스를 늘리면 그만큼 처리량이 늘어나는 구조가 되었다는 뜻입니다.

---

## D. 부하 중 블루그린 배포: 전환 전 JVM 워밍업

### 문제

초당 50요청 중에 배포와 롤백을 하자 **2건이 실패**했고, p95 5.3초, 최대 12.5초까지 튀었습니다.

**근거 (Prometheus, 색별 10초 단위)**: 전환 직후 새 색의 CPU가 **100%**, p95가 **4~6초**로 약 20초 지속됐습니다. 롤백할 때 다시 띄운 색에서도 같은 일(6~9초)이 일어났습니다.

**판단**: 새로 뜬 JVM은 JIT 컴파일 전이라 코드를 인터프리터로 실행하고, 클래스 로딩과 커넥션 생성도 첫 요청에서 일어납니다. 헬스체크와 스모크 테스트는 요청 몇 개뿐이라 데워지지 않은 상태로 **전체 트래픽이 한 번에** 넘어왔습니다.

### 검토한 대안

| 대안 | 판단 |
| --- | --- |
| 점진적 전환(트래픽 10% → 100%) | Caddy 가중치 설정과 단계별 판정이 필요해 스크립트가 복잡해짐 |
| CDS/AOT 등 JVM 기동 최적화 | 효과는 있지만 이미지 빌드 변경이 크고, JIT 워밍업 자체는 남음 |
| **전환 전에 직접 요청을 보내 데우기** | 스크립트 함수 하나로 해결. 실제 경로(목록, 검색, 주변, 비슷한 코스)를 그대로 데움 |

### 적용 방법 (`deploy/lib.sh`의 `warm_up`)

- 헬스체크와 스모크 테스트를 통과한 뒤, 전환 **전에** 새 색 컨테이너 안에서 읽기 요청을 4개 동시 루프로 20초 동안 보냅니다.
- **읽기 요청만** 보냅니다. 상세 조회는 조회수를 올리므로 제외해 데이터를 바꾸지 않습니다.
- `deploy.sh`와 `rollback.sh` 둘 다 적용합니다.
- `WARMUP_SECONDS`로 시간을 조절합니다. 급한 롤백이면 `WARMUP_SECONDS=0`으로 건너뜁니다.

### 결과 (초당 50요청 중 배포 → 롤백)

| | 워밍업 없음 | 워밍업 20초 |
| --- | ---: | ---: |
| 실패 | 2 / 11,786 | **0 / 12,021** |
| p95 / p99 | 5,326 / 6,783ms | **131 / 208ms** |
| 최대 | 12.5초 | **415ms** |
| 처리하지 못한 요청 | 234 | **0** |
| 배포 소요 | 48초 | 68초 |

배포가 20초 늘어나는 대신 사용자가 느끼는 끊김이 사라졌습니다.

---

## 운영 중 겪은 문제: 디스크

측정 도중 Docker Desktop의 디스크가 가득 차 쓰기가 막혔습니다. 원인은 이미지를 빌드할 때마다 쌓인 **빌드 캐시(20GB 이상)**였습니다. 그 상태에서 빌드한 이미지는 jar가 깨져 있었는데, 배포 스크립트의 헬스체크가 이를 걸러 기존 색이 계속 서비스했습니다.

**조치**
- 로컬: 빌드 직후 `docker builder prune -af`로 캐시를 비웁니다([DEPLOYMENT.md](DEPLOYMENT.md)).
- 서버: `deploy.sh`가 전환 후 **두 색이 쓰는 태그만 남기고** 옛 앱 이미지를 지웁니다(`prune_old_images`). 롤백은 한 단계 전까지만 가능하므로 그 이전 이미지는 필요 없습니다. `KEEP_OLD_IMAGES=1`이면 생략합니다.

---

## 알려진 한계와 다음 후보

**한계**
- **사전 반영 지연**: 블루그린 전환 중 옛 색(V2 이전 버전)이 쓴 글은, 새 색이 기동 동기화를 마친 뒤부터 옛 색이 정지될 때까지(워밍업과 정리 대기, 약 50초) 사전에 단어가 빠질 수 있습니다. 다음 기동 때 채워지며, V2 이후 버전끼리의 배포에서는 생기지 않습니다.
- **측정 환경**: 로컬 Docker(백엔드 2 CPU) 결과입니다. 서버 사양이 다르면 절대 수치는 달라지므로, 서버가 준비되면 같은 시나리오로 다시 재야 합니다.

**다음 후보** (현재 DB 시간 상위, 측정 전)

| 후보 | 근거 | 방법 |
| --- | --- | --- |
| 목록 `COUNT(*)` + OFFSET | 호출 1.8만 회, 평균 6.5ms. 뒤 페이지일수록 느려짐 | 키셋 페이지네이션, 총 개수 캐시 |
| 검색 결과 메타데이터 `IN (...)` | 평균 18.7ms, 결과가 많은 검색어에서 수천 개 id | 필터·정렬·페이지를 DB에서 처리 |
| 관광지 조회 | 평균 43.8ms, 1.3만 행 검사 | 인덱스 확인 |
| `SELECT @@SESSION.transaction_read_only` | 트랜잭션마다 1회(기준선에서 100만 회) | JDBC `useLocalSessionState=true` |
| 백엔드 CPU | 120 req/s에서 한계 | 로그인(bcrypt)·검색 점수 계산 프로파일링, 서버 사양에 맞춘 풀 크기 |

## 관련 커밋

| 커밋 | 내용 |
| --- | --- |
| `1bf5383`, `c8cc63b` | 지표 노출, Prometheus/Grafana |
| `8eafe07` | k6 시나리오, 부하 데이터 |
| `9e64704` | A. 격리 수준 |
| `6f3507c` | B. 검색 단어 사전 (V2) |
| `c990b4b` | C. 주변 추천 SQL, 덮개 인덱스 (V3) |
| `5ff423a` | B 보완. 기동 동기화 9분 → 2.6초 |
| `9f4a650` | D. 워밍업, 옛 이미지 정리 |
