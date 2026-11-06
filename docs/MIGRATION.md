# Spring Boot 이전 분석 및 검증

## 기존 코드에서 서버 기능에 해당한 부분

| 기존 파일 | 기존 역할 | 변경 후 |
|---|---|---|
| `src/services/auth.js` | localStorage 회원 DB, 클라이언트 비밀번호 해시, sessionStorage 로그인, 개인정보/비밀번호 변경 및 탈퇴 | `frontend/src/services/auth.js`는 HTTP 어댑터, `backend`의 AuthController/AuthService/MemberRepository가 처리 |
| `src/services/posts.js` | localStorage 게시글·순번·샘플 생성, 검색/페이징, 작성자 검사, 조회수, 위치 유사도 검색 | 서버 JPA 저장, 트랜잭션과 작성자 검사, DB ID, DB 검색/페이징 및 Java 위치 필터 |
| `src/services/tourApi.js` | 브라우저에서 관광공사 키를 넣어 외부 호출 | `/api/tour/{operation}`을 통해 서버 키로 호출, Vue 응답 정규화 유지 |
| `src/services/directions.js` | 브라우저에서 카카오 REST 키로 길찾기 호출 | `/api/directions`에서 인증키 추가 및 경유지 검증 |
| `src/services/currentLocation.js` | 좌표→행정구역 REST 호출 | 브라우저 위치 권한/좌표 취득은 유지, REST 호출은 `/api/regions` |
| `src/stores/auth.js`, `src/main.js`, `src/router/index.js` | 브라우저 저장값으로 로그인 복원 | 시작할 때 `/api/auth/me`에서 서버 세션을 복원하고 기존 라우트 가드와 연결 |

보존한 기능: Vue 화면/지도 SDK/브라우저 위치 권한, 관광정보 응답 정규화, 분류 체계와 화면 필터, 취소 요청, 관광정보 캐시. `storage.js`와 sessionStorage는 여행지 조회 캐시에서만 사용하며 회원·게시글 영속 저장에는 사용하지 않습니다. 기존 localStorage 데이터를 삭제하거나 자동 전송하지 않습니다.

## 데이터 및 인증

- H2 파일 DB + JPA. `members`, `travel_posts`, `post_locations` 테이블을 자동 생성/갱신합니다.
- 게시글 작성자 ID와 이름은 로그인 세션의 DB 회원을 기준으로 설정합니다. 클라이언트가 보낸 작성자·조회수는 채택하지 않습니다.
- 장소 스냅샷은 별도 테이블에 게시글과 함께 저장합니다. 방문 순서는 배열 순서로 서버에서 부여합니다.
- 조회수 증가와 게시글 변경은 비관적 잠금으로 동시 요청의 갱신 손실을 방지합니다.
- BCrypt 비밀번호, HttpOnly 세션 쿠키, 로그인 시 세션 ID 교체, Spring Security CSRF 검증을 사용합니다.
- 비밀번호 변경 시 계정 credentialVersion을 증가시켜 다른 로그인 세션을 무효화합니다. 현재 세션은 유지합니다.
- 탈퇴는 비밀번호 확인 후 본인 게시글과 회원을 같은 트랜잭션에서 삭제합니다.
- 외부 서비스 주소/작업은 서버에서 고정하고 키는 환경변수로 관리합니다. 외부 키나 제공자의 오류 본문은 서버 오류 메시지로 노출하지 않습니다.
- 로컬 비밀번호 재설정은 개발용 코드 표시 방식입니다. 실제 이메일 본인 확인은 제공하지 않으며 `local` 이외 프로필에서는 코드 발급을 차단합니다.

## 검증

2026-10-15, Windows / JDK 21 / Gradle 8.14.3 / Node.js 24에서 확인했습니다.

- Vue 단위 테스트: **21개 파일, 140개 테스트 통과**.
- Vue 프로덕션 빌드: 성공.
- 수정된 서비스/스토어/main.js ESLint: 통과.
- Spring Boot 통합 테스트: **8개 테스트 통과**. H2 메모리 DB로 테스트 데이터를 격리합니다.
- Gradle `bootJar`: 성공.
- 실제 HTTP: Vite 프록시 → Spring Boot → H2로 샘플 10개 조회, 가입, 로그인, 현재 회원, 글 등록/수정/조회/검색, 로그아웃/401 확인.
- 서버 종료 및 재시작 후 직접 만든 게시글과 회원이 H2 파일에 유지되고 재로그인할 수 있음을 확인. 탈퇴 시 작성글 삭제 확인.

관광공사/카카오의 실제 응답은 인증키가 제공되지 않아 실서비스 호출을 검증하지 않았습니다. 기존 정규화/지도/지역 필터 단위 테스트와 키 미설정 오류 응답을 검증했습니다.

Windows의 한글 프로젝트 경로에서는 Gradle 테스트 JVM의 클래스 로딩 문제가 발생하여 백엔드 통합 테스트를 동일 소스의 임시 영문 경로에서 수행했습니다. 원본 프로젝트는 영문 경로이므로 해당 위치에서 Gradle Wrapper로 실행하는 구성을 제공합니다.

## 2026-10-15 알고리즘 추가 적용

유사 코스(Jaccard+LCS), 부분 포함 역색인 검색(Java AND/OR), Argon2id와 기존 BCrypt 회원 호환을 적용했다. 최신 검증은 [ALGORITHM_VALIDATION.md](ALGORITHM_VALIDATION.md), 상세 기획서는 [제안 2](ALGORITHM_2_COURSE_RECOMMENDATION.md)와 [제안 3](ALGORITHM_3_SUBSTRING_SEARCH.md), 검색 성능은 [SEARCH_PERFORMANCE.md](SEARCH_PERFORMANCE.md)에 기록한다. 위의 초기 전환 검증 수치와 별도로 최종 백엔드 27개/프론트엔드 144개 테스트를 통과했다.
