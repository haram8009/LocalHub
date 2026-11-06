# LocalHub

국내 관광지를 검색하고 여행 코스를 공유하는 웹 애플리케이션입니다. Vue 프론트엔드와 Spring Boot REST API로 구성되어 있습니다.

## 주요 기능

- 지역·관광 유형·키워드별 관광지 검색 및 상세 정보 조회
- 지도와 현재 위치를 활용한 주변 관광지·게시글 조회
- 여행 코스 작성, 방문 순서 저장 및 경로 조회
- 여행 후기·팁·추천 게시글 등록, 수정, 삭제 및 검색
- 장소 구성과 방문 순서를 바탕으로 비슷한 여행 코스 추천
- 회원가입, 로그인·로그아웃, 회원정보 수정 및 탈퇴
- 아이디 찾기와 로컬 개발용 비밀번호 재설정

## 실행 화면

2026년 11월 6일 현재 소스를 실행해 새로 캡처한 화면입니다. Spring Boot를 `local,mysql` 프로필로 실행하고 실제 MySQL 데이터와 Kakao 지도를 사용했습니다. 로그인 전용 화면은 임시 계정으로 실제 로그인해 촬영했으며, 해당 계정과 게시글은 촬영 후 삭제했습니다.

Chrome의 1440 × 1000 뷰포트에서 촬영했습니다. 위치 기반 화면의 테스트 위치는 서울 역삼동 부근(37.5013, 127.0396)이며, 지도는 서울 지역과 관광지 필터를 사용했습니다.

### 메인페이지

![LocalHub 메인페이지](docs/screenshots/home.png)

### 여행지 지도

![여행지 지도와 검색 필터](docs/screenshots/map.png)

### 게시판 목록

![여행 게시판 목록](docs/screenshots/posts.png)

<details>
<summary>지도 필터 및 여행지 상세 화면</summary>

#### 지도 분류 필터 선택

![지도 분류 필터 선택](docs/screenshots/map-filter-category.png)

#### 지도 여행지 상세

![지도에서 선택한 여행지 상세 정보](docs/screenshots/map-location-detail.png)

</details>

<details>
<summary>게시글 상세·작성·수정 및 마이페이지 화면</summary>

#### 게시글 상세

![게시글 상세 화면](docs/screenshots/post-detail.png)

#### 게시글 작성

![게시글 작성 화면](docs/screenshots/post-create.png)

#### 여행지를 첨부한 게시글 작성

![여행지를 첨부한 게시글 작성 화면](docs/screenshots/post-create-with-place.png)

#### 게시글 수정

![게시글 수정 화면](docs/screenshots/post-edit.png)

#### 마이페이지 및 내 게시글 관리

![마이페이지 및 내 게시글 관리 화면](docs/screenshots/mypage.png)

</details>

<details>
<summary>회원가입·로그인 및 계정 찾기 화면</summary>

#### 회원가입

![회원가입 화면](docs/screenshots/signup.png)

#### 로그인

![로그인 화면](docs/screenshots/login.png)

#### 아이디 찾기

![아이디 찾기 화면](docs/screenshots/find-id.png)

#### 비밀번호 찾기

![비밀번호 찾기 화면](docs/screenshots/find-password.png)

#### 비밀번호 재설정

![비밀번호 재설정 화면](docs/screenshots/reset-password.png)

</details>

<details>
<summary>없는 페이지 안내 화면</summary>

![없는 페이지 안내 화면](docs/screenshots/not-found.png)

</details>

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 프론트엔드 | Vue 3, Vue Router, Pinia, Vite |
| 백엔드 | Java 17, Spring Boot 3.5, Spring Data JPA, Spring JDBC, Spring Security |
| 데이터베이스 | MySQL 8, H2 |
| 외부 서비스 | Kakao Maps, Kakao Local, Kakao Mobility, 한국관광공사 TourAPI |
| 테스트 | JUnit 5, Spring Boot Test, Vitest, Vue Test Utils |
| 빌드 | Gradle Wrapper, npm |

## 프로젝트 구조

```text
.
├── backend/             # Spring Boot API 및 테스트
├── frontend/            # Vue 애플리케이션 및 테스트
├── database/            # SQL 스키마, 초기 데이터, ERD
├── scripts/             # DB 초기화 및 샘플 데이터 도구
├── docs/                # API, 설계, 알고리즘 관련 문서
└── README.md
```

## 시작하기

### 사전 요구사항

- JDK 21 권장 (`JAVA_HOME` 설정, 소스 호환 버전 Java 17)
- Node.js 22.18 이상인 22.x 또는 24.12 이상
- MySQL 8.0 이상 및 MySQL 클라이언트
- 데이터베이스와 테이블을 생성할 수 있는 MySQL 계정

Gradle은 저장소의 Wrapper를 사용하므로 별도로 설치하지 않아도 됩니다. 아래 명령은 프로젝트 루트 기준 PowerShell 예시입니다.

### 1. 환경 설정

최초 설정 시 예제 파일을 복사합니다. 이미 `.env`가 있다면 필요한 항목만 수정합니다.

```powershell
Copy-Item backend/.env.mysql.example backend/.env
Copy-Item frontend/.env.example frontend/.env
```

`backend/.env`에 MySQL 접속 정보와 사용할 외부 API 키를 입력합니다.

```dotenv
SPRING_PROFILES_ACTIVE=local,mysql
DB_URL=jdbc:mysql://localhost:3306/ssafy_trip?connectionTimeZone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=ssafy
DB_PASSWORD=your_database_password
SEED_DEMO=true
KAKAO_REST_API_KEY=your_kakao_rest_api_key
```

`frontend/.env`에는 프론트엔드 설정을 입력합니다.

```dotenv
VITE_API_BASE_URL=/api
VITE_TOUR_SOURCE=database
VITE_KAKAO_MAP_JAVASCRIPT_KEY=your_kakao_javascript_key
```

MySQL 모드에서는 관광정보 API 키 없이 관광지 데이터를 조회합니다. 지도 표시에는 카카오 JavaScript 키가, 위치 기반 지역명 조회와 길찾기에는 카카오 REST API 키가 필요합니다. 카카오 애플리케이션에 사용할 웹 도메인을 등록하세요.

`.env` 파일은 Git에서 제외됩니다. 백엔드 설정은 `KEY=value` 형식으로 작성하며, 운영체제 환경변수가 파일 설정보다 우선합니다.

### 2. 데이터베이스 초기화

MySQL 서버를 실행한 뒤 초기 데이터를 적재합니다.

```powershell
node scripts/Import-TripDatabase.cjs
```

초기화 스크립트는 `ssafy_trip` 데이터베이스와 관광지 테이블을 만들고 제공된 덤프를 적재합니다. 기존 테이블이 있으면 중단하므로 최초 설치 시에만 실행합니다. 회원·게시글 테이블은 백엔드 실행 시 생성됩니다.

MySQL 클라이언트의 기본 경로와 설치 경로가 다르면 실행 전에 지정합니다.

```powershell
$env:MYSQL_EXE = 'C:\path\to\mysql.exe'
```

초기화 스크립트는 `backend/.env`, 루트 `.env`, 운영체제 환경변수 순으로 설정을 읽으며 뒤의 값이 우선합니다. 같은 접속 정보를 여러 파일에 중복 설정하지 않는 것을 권장합니다.

### 3. 백엔드 실행

```powershell
cd backend
.\gradlew.bat bootRun
```

기본 API 주소는 `http://localhost:8080`입니다. macOS/Linux에서는 `./gradlew bootRun`을 사용합니다.

### 4. 프론트엔드 실행

별도 터미널에서 실행합니다.

```powershell
cd frontend
npm ci
npm run dev
```

브라우저에서 터미널에 표시된 주소(기본 `http://localhost:5173`)를 엽니다. 개발 서버는 `/api` 요청을 백엔드의 `8080` 포트로 전달합니다.

### 개발용 예제 게시글 추가 (선택)

백엔드를 실행한 상태에서 프로젝트 루트에서 다음 명령으로 역삼역 주변 예제 게시글을 추가할 수 있습니다.

```powershell
.\scripts\Seed-Yeoksam.ps1
```

`scripts/yeoksam-posts.json`의 장소 정보와 창작한 코스·후기를 사용합니다. 실제 사용자 후기가 아닌 로컬 개발용 데이터입니다. 같은 작성자의 동일 제목은 건너뛰며 기존 게시글은 삭제하지 않습니다. 동시에 여러 번 실행하지 마세요. 서버와 개발 계정은 `-BaseUrl`, `-UserId`, `-Password`로 지정할 수 있습니다.

## 환경변수

| 위치 | 변수 | 설명 |
| --- | --- | --- |
| 백엔드 | `SPRING_PROFILES_ACTIVE` | MySQL 개발 환경은 `local,mysql`, 기본값은 `local` |
| 백엔드 | `DB_URL` | JDBC 접속 URL |
| 백엔드 | `DB_USERNAME`, `DB_PASSWORD` | DB 계정 및 비밀번호 |
| 백엔드 | `SEED_DEMO` | 게시글이 비어 있을 때 샘플 생성 여부, 기본값 `true` |
| 백엔드 | `TOUR_SOURCE` | 기본 프로필의 관광정보 소스, 기본값 `api`. `mysql` 프로필은 `database` 사용 |
| 백엔드 | `TOUR_API_KEY` | 외부 TourAPI 모드에서 사용하는 Decoding 키 |
| 백엔드 | `KAKAO_REST_API_KEY` | 지역명 조회·길찾기용 키 |
| 프론트엔드 | `VITE_API_BASE_URL` | API 기본 경로, 예제값 `/api` |
| 프론트엔드 | `VITE_TOUR_SOURCE` | `database` 또는 `api`, 백엔드 방식과 일치하도록 설정 |
| 프론트엔드 | `VITE_KAKAO_MAP_JAVASCRIPT_KEY` | 지도 표시용 키 |

MySQL 대신 H2와 외부 TourAPI를 사용하려면 백엔드 프로필을 `local`, 관광정보 소스를 `api`로 설정합니다. DB URL은 `jdbc:h2:file:./data/enjoytrip;DB_CLOSE_ON_EXIT=FALSE`, 사용자 이름은 `sa`, 비밀번호는 빈 값으로 설정하고 `TOUR_API_KEY`를 입력합니다. 프론트엔드의 `VITE_TOUR_SOURCE`도 `api`로 변경한 뒤 두 서버를 재시작합니다.

## 테스트

백엔드 테스트는 메모리 H2를 사용합니다. 실행 전에 `local` 프로필을 명시해 개발용 MySQL 설정과 분리합니다.

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = 'local'
.\gradlew.bat test
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

프론트엔드 테스트:

```powershell
cd frontend
npm run test:unit -- --run
```

## 빌드

각 디렉터리에서 실행합니다.

```powershell
# backend/
.\gradlew.bat bootJar

# frontend/
npm run build
```

- 백엔드 결과물: `backend/build/libs/enjoytrip-backend-0.0.1-SNAPSHOT.jar`
- 프론트엔드 결과물: `frontend/dist/`

백엔드 실행 파일은 `backend/`에서 다음과 같이 실행합니다.

```powershell
java -jar build/libs/enjoytrip-backend-0.0.1-SNAPSHOT.jar
```

배포 시 프론트엔드 정적 파일을 제공하고 `/api` 요청을 백엔드로 전달하도록 웹 서버를 설정해야 합니다. 프론트엔드 라우팅에는 `index.html` fallback이 필요하며, Vite 개발 서버의 프록시 설정은 빌드 결과에 포함되지 않습니다.

## 참고 사항

- 로그인은 서버 세션을 사용하므로 백엔드를 재시작하면 다시 로그인해야 합니다.
- 비밀번호 재설정은 `local` 프로필에서 개발용 인증 코드를 반환합니다. 이메일 발송은 구현되어 있지 않습니다.
- DB 모드는 제공된 관광 유형과 지역 코드를 사용하며, 외부 TourAPI의 세부 분류는 지원하지 않습니다.
- `SEED_DEMO=false`로 샘플 게시글 생성을 끌 수 있습니다. 샘플 작성자 계정으로는 로그인할 수 없습니다.

## 관련 문서

- [API 문서](docs/API.md)
- [데이터베이스 스키마](database/01-schema.sql)
- [ERD](database/ERD.md)
- [여행 코스 추천](docs/ALGORITHM_2_COURSE_RECOMMENDATION.md)
- [게시글 검색](docs/ALGORITHM_3_SUBSTRING_SEARCH.md)
- [비밀번호 저장 방식](docs/PASSWORD_STORAGE.md)

## 제작자

박시온, 이하람
