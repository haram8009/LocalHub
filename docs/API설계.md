# LocalHub 데이터 연동 명세

별도 Backend 없이 브라우저에서 한국관광공사 공공데이터 API를 직접 호출하고(CORS 허용 확인),
게시글은 `localStorage`에 저장한다.

## 1. 한국관광공사 국문 관광정보 서비스 (KorService2)

- Base URL: `https://apis.data.go.kr/B551011/KorService2`
- 공통 파라미터: `serviceKey`(`VITE_TOUR_API_KEY`, Decoding 키를 `URLSearchParams`로 인코딩), `MobileOS=ETC`, `MobileApp=EnjoyTrip`, `_type=json`
- 구현: `src/services/tourApi.js`(`tourRequest`) → `src/services/locationService.js`

### 응답 공통 형식

```json
{
  "response": {
    "header": { "resultCode": "0000", "resultMsg": "OK" },
    "body": { "items": { "item": [] }, "numOfRows": 20, "pageNo": 1, "totalCount": 45 }
  }
}
```

- `resultCode !== "0000"`이면 오류로 처리한다.
- `items`는 결과가 없으면 `""`, 1건이면 배열이 아닌 객체로 올 수 있으므로 항상 배열로 정규화한다.
- 인증 오류 등은 XML 문자열로 올 수 있다 → "인증키를 확인해 주세요" 오류로 처리한다.

### 사용 오퍼레이션

| 용도 | 오퍼레이션 | 주요 파라미터 |
| --- | --- | --- |
| 지도 첫 진입 전국 | `areaBasedList2` | 지역 코드 생략(전국), 선택한 `contentTypeId`·분류 코드, `arrange=Q`, `pageNo`, `numOfRows=20`; 페이지 결과를 목록·지도에 표시 |
| 지도 주변 목록 | `locationBasedList2` | `mapX`, `mapY`, `radius`(≤ 20000m), `arrange=E`(거리순), `contentTypeId`, `pageNo`, `numOfRows=20` |
| 근처 여행지 | `locationBasedList2` | 선택 여행지 좌표, `radius=5000`, `arrange=E` |
| 키워드 검색 | `searchKeyword2` | `keyword`, `contentTypeId`, `arrange=A`, `pageNo`, `numOfRows=20` |
| 단건 조회·개요 | `detailCommon2` | `contentId` |
| 소개 정보 | `detailIntro2` | `contentId`, `contentTypeId`(필수) — 쉬는 날·이용 시간·주차·화장실 등 |
| 이미지 | `detailImage2` | `contentId`, `imageYN=Y` |
| 반복 정보 | `detailInfo2` | `contentId`, `contentTypeId` |

### 카테고리 ↔ contentTypeId

| 관광지 | 문화시설 | 축제공연행사 | 여행코스 | 레포츠 | 숙박 | 쇼핑 | 음식점 |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| 12 | 14 | 15 | 25 | 28 | 32 | 38 | 39 |

`전체`는 `contentTypeId`를 생략한다.

### 서비스 함수

| 함수 | 설명 |
| --- | --- |
| `getLocationPage({ mode, category, keyword, center, areaCode, page, size })` | `mode`: `viewport` / `keyword` / `area`. `{ items, totalCount, page, size, totalPages }` 반환 |
| `getLocations({ category, query, bounds, limit })` | 첫 페이지 항목만 필요한 화면(코스 작성, 홈)용 |
| `getLocation(contentId)` | `detailCommon2` 단건 |
| `getNearbyLocations(locationOrId, { limit })` | 거리순 근처 여행지(자기 자신 제외) |
| `getLocationDetail(location)` | `{ overview, intro[], images[], infos[] }`, 일부 실패해도 나머지 반환 |

### 캐시 (`src/services/locationMapCache.js`)

- `snapViewportQuery(bounds)`: 반경을 `[500, 1000, 2000, 5000, 10000, 20000]` 버킷으로 올리고, 중심을 반경/4 격자로 반올림한다. 조금 움직인 지도는 같은 요청이 된다.
- 요청(오퍼레이션 + 파라미터, 페이지 포함) 단위로 메모리 LRU(50개, TTL 10분)에 저장하고 `sessionStorage`에 미러링한다.
- 같은 요청이 진행 중이면 Promise를 공유한다. 호출자가 취소해도 응답은 끝까지 받아 캐시에 저장한다.

## 2. 게시글 (localStorage)

- 구현: `src/services/posts.js`, 키 `enjoytrip:posts`(배열), `enjoytrip:postSeq`(id 시퀀스)
- 저장 레코드: `{ id, category, title, content, authorId, authorName, viewCount, createdAt, updatedAt, locations[] }`
- 작성·수정·삭제는 로그인한 유저(`sessionStorage`) 기준으로 검사한다. `authorId`가 없는 예전 게시글은 누구도 수정·삭제할 수 없다.
- `locations[]`에는 조인할 서버가 없으므로 화면에 필요한 여행지 스냅샷(이름, 좌표, 이미지 등)과 `visitOrder`를 그대로 저장한다.

| 함수 | 설명 |
| --- | --- |
| `getPosts({ query, category, placeId, page, size })` | 제목·내용 검색, 카테고리, 특정 여행지 포함 여부로 필터. 최신순. `{ items, page, size, total, totalPages }` |
| `getPost(id)` | 조회수 +1. 없으면 `ApiError(status 404)` |
| `createPost(post)` | 필수: `title`, `content`. 비로그인 시 `ApiError(status 401)` |
| `updatePost(id, post)` | 작성자가 아니면 `ApiError(status 403)` |
| `deletePost(id)` | 작성자가 아니면 `ApiError(status 403)` |

## 2-1. 회원 (목업, localStorage + sessionStorage)

- 구현: `src/services/auth.js`, 상태: `src/stores/auth.js`. 서버 통신 없음.
- 회원 목록: `localStorage` 키 `enjoytrip:users`, 레코드 `{ id, passwordHash(SHA-256), name, email, createdAt }`
- 로그인 유저: `sessionStorage` 키 `enjoytrip:session`, `{ id, name, email }` (탭을 닫으면 로그아웃)
- 비밀번호 규칙(`src/utils/validators.js`): 8~20자, 영문·숫자·특수문자 각 1개 이상, 공백 불가

| 함수 | 설명 |
| --- | --- |
| `signUp({ userId, password, name, email })` | 검증 실패 422, 아이디·이메일 중복 409(`data.field`) |
| `login({ userId, password })` | 불일치 401. 성공 시 세션 저장 |
| `logout()` / `getSessionUser()` | 세션 삭제 / 조회 |
| `findUserId({ name, email })` | 마스킹된 아이디 반환, 없으면 404 |
| `verifyResetTarget({ userId, email })` → `resetPassword({ userId, email, password })` | 본인 확인 후 비밀번호 재설정 |
| `updateProfile({ name, email })` | 로그인 필요(401), 세션도 함께 갱신 |
| `changePassword({ currentPassword, newPassword })` | 현재 비밀번호 불일치 403 |
| `withdraw(password)` | 비밀번호 불일치 403. 회원 삭제 후 로그아웃(게시글은 유지) |

## 3. Kakao Mobility 길찾기

`src/services/directions.js`에서 `VITE_KAKAO_REST_API_KEY`로 다중 경유지 경로를 요청한다(변경 없음).
