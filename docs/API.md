# REST API

기본 경로는 `/api`입니다. JSON을 사용하고, 인증은 **JWT**로 처리합니다. 서버는 세션을 만들지 않습니다(stateless).

- **액세스 토큰**: 로그인 응답 본문의 `accessToken`(HS256, 기본 30분). 보호된 요청에 `Authorization: Bearer <accessToken>` 헤더로 보냅니다. 클라이언트는 메모리에만 보관합니다.
- **리프레시 토큰**: HttpOnly·SameSite=Strict 쿠키 `refreshToken`(경로 `/api/auth`, 기본 14일). 서버에는 SHA-256 해시만 저장하며 1회용입니다(`/auth/refresh` 호출마다 회전).
- 비밀번호 변경·재설정 시 `credential_version`이 올라가 기존 액세스 토큰이 즉시 무효화되고 리프레시 토큰도 모두 폐기됩니다.
- Bearer 헤더가 인증 수단이므로 CSRF 토큰은 사용하지 않습니다(`/auth/csrf` 제거).

## 인증과 회원

| Method | 경로 | 인증 | 요청/응답 요약 |
|---|---|---|---|
| POST | `/auth/signup` | - | `{ userId, password, name, email }` → `{ id, name, email }` (201) |
| POST | `/auth/login` | - | `{ userId, password }` → `{ accessToken, tokenType, expiresIn, user }` + 리프레시 쿠키 |
| POST | `/auth/refresh` | 쿠키 | 리프레시 쿠키 → 새 `{ accessToken, ... }` + 새 쿠키, 실패 시 401 |
| POST | `/auth/logout` | 쿠키 | 리프레시 토큰 폐기, 쿠키 삭제 |
| GET | `/auth/me` | Bearer | 현재 회원 조회, 비로그인 시 401 |
| PUT | `/auth/profile` | Bearer | `{ name, email }` → 수정된 회원 |
| PUT | `/auth/password` | Bearer | `{ currentPassword, newPassword }` → 새 `{ accessToken, ... }`, 다른 기기 토큰 무효화 |
| DELETE | `/auth/me` | Bearer | `{ password }`, 본인 게시글과 회원 삭제 |
| POST | `/auth/find-id` | - | `{ name, email }` → `{ maskedUserId, createdAt }` |
| POST | `/auth/reset-target` | - | `{ userId, email }` → `{ userId, email, devResetCode }`, local 전용, 1분 간격 |
| POST | `/auth/reset-password` | - | `{ userId, code, password }`, 10분/5회 제한, 사용 후 코드 폐기 |

게시글 `POST/PUT/DELETE`는 Bearer 토큰이 필요하며, 없거나 만료·변조된 토큰은 401입니다. 조회(GET)는 공개입니다. 프론트엔드 `backendRequest`가 헤더 부착과 401 시 1회 자동 재발급·재시도를 처리합니다. 서버 설정: `JWT_SECRET`(32바이트 이상, 미설정 시 기동마다 임의 키), `JWT_ACCESS_EXPIRY_SECONDS`, `JWT_REFRESH_EXPIRY_SECONDS`, `JWT_COOKIE_SECURE`(HTTPS면 true).

회원 아이디는 영문 소문자/숫자 4~20자, 비밀번호는 영문/숫자/특수문자를 포함한 공백 없는 8~20자입니다. 이메일은 소문자로 저장하며 중복을 DB에서도 제한합니다. 새 비밀번호는 서버에서 Argon2id로 해시합니다. 기존 BCrypt 회원은 로그인 성공 시 자동으로 업그레이드하며 응답에는 해시를 포함하지 않습니다.

## 게시글·여행 코스

| Method | 경로 | 설명 |
|---|---|---|
| GET | `/posts` | 검색·필터·페이지 목록 |
| GET | `/posts/nearby` | 현재 위치 반경 5km 안의 장소가 첨부된 게시글 추천 |
| GET | `/posts/{id}` | 상세 조회 및 조회수 1 증가 |
| POST | `/posts` | 로그인 필요, 생성 (201) |
| PUT | `/posts/{id}` | 작성자만 수정 |
| DELETE | `/posts/{id}` | 작성자만 삭제 |

목록 쿼리: `query`, `category`, `authorId`, `placeId`, `placeName`, `placeAddress`, `latitude`, `longitude`, `page`(1부터), `size`(1~100, 기본 10).

응답: `{ items, page, size, total, totalPages }`. 검색어가 없으면 생성일 내림차순, 같은 경우 ID 내림차순입니다. 검색어가 있으면 아래의 다중 검색을 사용합니다. 위치 필터는 콘텐츠 ID, 50m 이내 좌표, 정규화한 장소명/주소와 거리 조건으로 기존 동작을 유지합니다. 일반 검색과 페이지 처리는 DB에서 수행합니다. 위치 유사도 필터는 이 소규모 프로젝트에 맞춰 DB에서 1차 검색한 결과를 Java에서 필터링합니다.

주변 게시글: `GET /posts/nearby?latitude=35.16&longitude=129.16&limit=3`. 좌표 필수, `limit`은 기본 3, 범위 1~10입니다. 게시글에 첨부된 유효한 국내 좌표 중 가장 가까운 장소가 5km 이내인 글을 거리 오름차순으로 반환하며, 같은 거리에서는 생성일·ID 내림차순입니다. 한 게시글은 한 번만 표시하고 일치하는 글이 없으면 `{ items: [] }`입니다. 응답 항목은 게시글 요약, `locationCount`, `distanceKm`(가장 가까운 장소까지의 직선거리), `nearestLocation`(해당 장소 정보)을 포함합니다. 조회수는 증가하지 않으며 잘못된 좌표·개수는 400입니다.

등록/수정 요청 예시:

```json
{
  "category": "COURSE",
  "title": "부산 하루 여행",
  "content": "바다를 따라 걷는 일정입니다.",
  "locations": [
    {
      "contentId": "123",
      "contentTypeId": "12",
      "name": "해운대해수욕장",
      "address": "부산광역시 해운대구",
      "latitude": 35.1587,
      "longitude": 129.1583
    }
  ]
}
```

카테고리: `COURSE`, `REVIEW`, `TIP`, `RECOMMENDATION`. 제목 최대 200자, 본문 최대 20,000자, 장소 최대 32개. 서버가 작성자·생성일·조회수·방문 순서를 결정합니다. `locations`는 필수 배열이며 장소가 없으면 `[]`을 보냅니다. 여행지는 관광공사 데이터의 스냅샷을 별도 `post_locations` 테이블에 게시글과 함께 저장합니다.

## 관광정보 및 외부 서비스

| Method | 경로 | 설명 |
|---|---|---|
| GET | `/tour/{operation}` | MySQL 모드에서는 DB 조회, API 모드에서는 KorService2 요청 |
| GET | `/regions?x=경도&y=위도` | 카카오 좌표→행정구역 |
| POST | `/directions` | 카카오 다중 경유지 길찾기 |

관광 작업: `ldongCode2`, `areaBasedList2`, `locationBasedList2`, `searchKeyword2`, `detailCommon2`, `detailIntro2`, `detailImage2`, `detailInfo2`.

`local,mysql` 프로필과 프론트엔드 `VITE_TOUR_SOURCE=database`를 함께 사용하면 관광정보는 MySQL에서 조회합니다. API 모드는 `local`, `TOUR_SOURCE=api`, `VITE_TOUR_SOURCE=api` 및 서버의 `TOUR_API_KEY` 설정을 사용합니다.

DB 모드 목록 조건은 `lDongRegnCd`(시도), `lDongSignguCd`(구군), `contentTypeId`(관광 유형), `pageNo`(1부터), `numOfRows`(1~1,000)입니다. 파라미터명은 기존 API와 같지만 지역 값은 제공 DB의 코드(서울 1, 부산 6 등)를 사용합니다. 구군 선택에는 시도가 필요합니다.

- `searchKeyword2`: `keyword`로 관광지 이름을 부분 검색합니다.
- `locationBasedList2`: `mapX`, `mapY`, `radius`(1~20,000m)로 주변 관광지를 조회하며 `arrange=E`는 거리순입니다.
- `detailCommon2`, `detailImage2`: `contentId`로 상세 정보 또는 대표 사진을 조회합니다.
- 제공 DB에는 세부 분류와 별도 소개·반복 정보가 없어 분류 코드는 지원하지 않으며 `detailIntro2`, `detailInfo2`는 빈 목록을 반환합니다.

관광 응답은 `{ response: { header: { resultCode, resultMsg }, body: { items: { item: [...] }, totalCount, pageNo, numOfRows } } }` 형식입니다. 정상 코드 값은 `0000`입니다.

길찾기 요청은 `{ origin: { name, x, y }, destination: { name, x, y }, waypoints: [...] }`입니다. 경유지는 최대 30곳이고 좌표 범위를 서버에서도 검증합니다. 응답은 기존 Vue 정규화 함수를 사용하도록 제공자 응답 형식을 유지합니다. 외부 요청은 연결 5초, 읽기 15초 제한을 둡니다.

오류 응답: `{ "message": "안내 문구", "field": "email" }` 등. 주요 상태 코드는 401(로그인 필요), 403(권한/비밀번호), 404(없음), 409(중복), 422(검증), 429(재설정 재요청 제한), 502(외부 서버 오류), 503(키 미설정 또는 비활성 기능)입니다.

## 알고리즘 API

- `GET /posts/{id}/similar?limit=3`: Jaccard 60% + LCS 순서 40%. limit 1~10, 404/400 처리. 조회수는 변경하지 않으며 점수·공통 장소 수·LCS 길이와 표시 필드를 반환합니다.
- `GET /posts/search?query=부산%20바다&page=1&size=10`: 사전 단어 부분 포함 + Java 집합 연산. AND로 고정합니다. 이전 URL의 mode 값은 무시합니다. 기존 `GET /posts?query=...`도 같은 동작입니다.
- 검색어 최대 100자/고유 토큰 10개, 검색 크기 1~50. 잘못된 조건은 400. 검색 결과에 `relevanceScore` 정수 필드가 추가됩니다.
- 관련도는 검색어별 제목 2점+본문 1점이며 같은 검색어/필드의 중복 매칭은 한 번만 셉니다. 관련도→생성일→ID 내림차순으로 정렬 후 페이지를 나눕니다.
- 빈 검색어/기호만 있는 검색어는 최신순 목록입니다. 카테고리·작성자·장소 필터는 순위와 전체 건수 계산 전에 적용합니다.
- 첫 실행 또는 인덱스 버전 변경 시 기존 게시글의 검색 인덱스를 현재 연결된 DB(MySQL 또는 H2)에 저장합니다. 등록·수정·삭제·탈퇴 시 같은 트랜잭션에서 인덱스도 갱신합니다.

상세 구현·측정 한계는 `ALGORITHM_2_COURSE_RECOMMENDATION.md`, `ALGORITHM_3_SUBSTRING_SEARCH.md`, `SEARCH_PERFORMANCE.md`, `PASSWORD_STORAGE.md`를 참고하세요.
