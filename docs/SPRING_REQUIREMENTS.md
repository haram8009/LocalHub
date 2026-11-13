# Spring 관통 프로젝트 요구사항 및 구현

기준: `자바전공_enjoytrip_16_07_스프링.pdf` 3~7쪽, 16~17쪽. 필수 기능과 Spring REST + MyBatis 전환을 적용한다. 프론트엔드 소스와 기존 API URL·JSON 계약은 유지한다. PDF의 추가·심화 기능은 선택 사항으로 구분한다.

## 필수 요구사항

| 번호 | 요구사항 | 구현과 확인 방법 |
| --- | --- | --- |
| F101 | 지역별 관광정보 수집·조회 | 기존 공공 관광 데이터 적재 스크립트와 TourAPI 연동 유지. DB 조회를 `AttractionMapper.xml`로 전환. 시도·시군구, 키워드, 페이지, 좌표 검색 지원 |
| F102 | 관광지·숙박·음식점 조회 | `contentTypeId=12,32,39`, 지역 조건과 함께 필터링. 매개변수화 테스트와 Postman 컬렉션으로 확인 |
| F103 | 문화시설·공연·여행코스·쇼핑 조회 | `contentTypeId=14,15,25,38`, F102와 같은 Mapper 및 REST 응답 계약 사용 |
| F107 | 회원가입·조회·수정·탈퇴 | `AuthController → AccountService → MemberRepository → MemberMapper`. 중복 ID/이메일, 비밀번호 검증, 본인 인증, 탈퇴 시 게시글·장소·검색 인덱스 삭제 |
| F108 | 로그인·로그아웃·비밀번호 찾기 | 서버 세션, CSRF, 로그인 시 세션 ID 교체, Argon2id 및 기존 BCrypt 마이그레이션 유지. 비밀번호 변경·재설정 시 자격 증명 버전 증가 |
| 구조 | Spring + REST + MyBatis | JPA/Hibernate 의존성과 엔티티 매핑 제거. 관광지·회원·게시글·검색 인덱스의 실제 DB 접근을 Mapper 인터페이스와 XML SQL로 전환 |
| 산출물 | 요구사항·클래스 다이어그램·소스·실행 확인 | 이 문서, [클래스·트랜잭션 설계](SPRING_ARCHITECTURE.md), [API](API.md), [검증 결과](SPRING_VERIFICATION.md), README 테스트 결과 |

비밀번호 찾기는 기존 수업용 `local` 프로필 정책을 유지한다. `/reset-target`이 개발용 코드를 반환하고 `/reset-password`가 10분 만료·5회 제한을 검사한다. 실제 이메일 발송은 구현 범위에 포함하지 않는다. `local`이 아닌 환경은 코드 발급을 503으로 거부한다.

## 기존 선택 기능의 범위

| 번호 | 현재 지원 범위 |
| --- | --- |
| F104 | 여행 코스의 장소 목록·방문 순서·설명을 DB에 저장하고 조회·수정·삭제. 별도 날짜별 일정·경비 필드는 미구현 |
| F105 | 기존 여행지 추천·후기에 위치와 이미지 URL을 포함한 장소 스냅샷을 저장. 별도 사진 업로드·방문 날짜 관리 기능은 미구현 |
| F110 | 기존 공유 게시판 CRUD, 작성자 권한 검사, 검색·추천 유지 |
| F106/F109/F111~F114 | 뉴스 크롤링, 관리자 공지, 날씨, 별도 사진 추천, 일출·일몰, 충전소 기능은 선택 사항이며 이번 범위에 포함하지 않음 |

## 변경 전후

- 변경 전: 관광지는 JDBC 문자열 SQL, 회원·게시글·검색은 JPA. 회원·게시글의 업무 처리와 트랜잭션이 컨트롤러에 존재했다.
- 변경 후: SQL은 `backend/src/main/resources/mappers/`에 등록한다. 컨트롤러는 HTTP 입력 검증과 서비스 호출을 담당하고 서비스가 업무 트랜잭션을 소유한다.
- 화면에서 사용하던 응답 필드, 상태 코드, 쿠키·CSRF 처리와 검색·추천 알고리즘을 유지한다.
- SQL의 사용자 값은 모두 `#{...}` 바인딩을 사용한다. 동적 조건은 MyBatis `<if>`, `<choose>`, `<foreach>`로 구성한다.

## 실행

기존 README의 관광 DB 적재와 `local,mysql` 설정을 그대로 사용한다. MySQL 프로필은 `db/schema-mysql.sql`, 기본 H2는 `db/schema.sql`로 애플리케이션 테이블을 생성한다. 관광 테이블·데이터는 기존 import 스크립트가 담당한다.

```powershell
cd backend
.\gradlew.bat test bootJar
.\gradlew.bat bootRun
```

테스트는 Gradle에서 `local,mysql`과 빈 config import를 지정한다. `MySqlIntegrationTest`가 실제 MySQL 서버에 무작위 이름의 `enjoytrip_test_...` DB를 생성하고 동적 datasource 설정으로 주입한다. 서비스 DB는 사용하지 않으며 테스트 JVM 종료 시 생성한 DB만 삭제한다. 접속 정보는 `TEST_DB_*` 환경변수 또는 `backend/.env`에서 읽는다.
