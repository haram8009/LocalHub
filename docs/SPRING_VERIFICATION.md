# Spring 전환 검증 결과

검증일: 2026-11-13 (Asia/Seoul). 프론트엔드 파일 변경 없음.

## 자동 테스트와 빌드

JDK 21, Gradle 8.14.3으로 `test`와 `bootJar`를 실행했다. DB를 사용하는 통합 테스트는 실제 MySQL 8.0.46을 사용했다. `MySqlIntegrationTest`가 실행별 임시 DB를 생성하고 MySQL 스키마 SQL을 적용하며, 종료 시 테스트 DB를 삭제한다. 단위 테스트를 포함한 전체 45개가 통과했다.

| 테스트 | 통과 |
| --- | ---: |
| 회원·인증·게시글 API | 9 |
| 관광지 조회·입력 검증·필수 관광 유형 7종 | 10 |
| 코스 추천 API·쿼리 수 | 4 |
| 코스 유사도 계산 | 3 |
| 비밀번호 해시 마이그레이션 | 3 |
| 주변 게시글 API·쿼리 수 | 5 |
| 검색·수정·삭제·롤백·동시 읽기 일관성 | 6 |
| 검색 알고리즘 | 2 |
| 생성·수정·탈퇴의 강제 실패 롤백 | 3 |
| **합계** | **45 / 45** |

`bootJar` 생성 성공. 일반적인 재실행 명령:

```powershell
cd backend
.\gradlew.bat test bootJar
```

MySQL 서버가 실행되어 있어야 한다. `backend/.env`의 접속 정보를 기본으로 사용하며 `TEST_DB_HOST`, `TEST_DB_PORT`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`로 테스트 전용 계정을 지정할 수 있다. 계정에는 테스트 DB 생성·삭제 권한이 필요하다. 서비스 DB 이름은 연결 대상으로 사용하지 않는다.

동시성 테스트는 InnoDB `REPEATABLE_READ` 읽기 전용 트랜잭션 도중 쓰기가 기다리지 않고 커밋되며, 그 뒤에도 같은 읽기 트랜잭션 안에서는 게시글 본문과 검색 인덱스가 모두 이전 버전으로 보이는 것(버전 혼합 없음)을 확인한다. 이 보장은 MySQL(InnoDB) 기준이며, 로컬 개발용 H2에서는 확인하지 않는다.

Gradle 테스트 리포트: `backend/build/reports/tests/test/index.html`.

## 실제 MySQL HTTP 확인

기존 개발 DB와 분리된 임시 MySQL 8 데이터베이스에 관광 유형 7종의 합성 fixture를 적재했다. 패키징한 JAR을 `local,mysql` 프로필, 18080 포트로 실행하고 Postman 컬렉션을 공식 CLI 러너 Newman으로 수행했다. Postman 데스크톱 UI에서 클릭 실행한 것은 아니다.

- 컬렉션 요청 38개, CSRF 선행 요청 15개: HTTP 요청 총 **53건**, 실패 **0건**.
- HTTP 상태·응답 계약·유형 필터·방문 순서·검색 반영·삭제·세션 무효화 검증 **53개**, 실패 **0개**.
- 회원가입 → 중복 가입 차단 → 로그인 → 프로필 수정 → 관광지 조회 → 코스 CRUD → 검색·추천 → 로그아웃 → 비밀번호 찾기·재설정·변경 → 탈퇴와 연관 데이터 삭제 순서를 확인했다.
- 실험용 계정은 컬렉션 마지막 요청에서 삭제했다. 검증 후 임시 서버 종료와 검증 DB 삭제를 확인했다.
- 외부 TourAPI/Kakao 호출과 실제 이메일 발송은 이번 검증 대상이 아니다. 관광 DB의 실제 공공데이터 전체를 대상으로 실행한 테스트도 아니다.

요청별 상태 코드와 assertion 결과는 [검증 요약 JSON](postman/verification-summary.json)에 기록했다. 비밀번호·CSRF 토큰·세션 쿠키를 담은 원본 실행 리포트는 저장소 산출물에서 제외했다.

## Postman에서 재실행

1. 기존 README대로 관광 DB를 준비하고 `local,mysql` 백엔드를 실행한다.
2. [컬렉션](postman/EnjoyTrip-Spring.postman_collection.json)을 Postman에 Import한다.
3. 컬렉션 변수 `baseUrl`을 서버 주소로 지정한다. 기본값은 `http://localhost:8080`이다.
4. 쿠키 저장을 활성화한 상태로 Collection Runner에서 전체 요청을 순서대로 실행한다.

각 쓰기 요청의 pre-request script가 CSRF 토큰을 요청하고 헤더에 넣는다. 쿠키 jar가 동일 세션을 유지한다. 테스트 계정은 실행 시각으로 생성되며 마지막에 탈퇴한다. 중간에 실행을 중단하면 생성된 테스트 계정·게시글이 남을 수 있다. 비밀번호 재설정 검증에는 `local` 프로필이 필요하다.

관광 테스트는 서울 지역 관광 데이터가 준비되어 있어야 한다. 유형별 조회에서 받은 콘텐츠 ID를 상세 조회에 사용한다.

Newman을 사용하는 경우:

```powershell
npx newman run docs/postman/EnjoyTrip-Spring.postman_collection.json --env-var baseUrl=http://localhost:8080
```
