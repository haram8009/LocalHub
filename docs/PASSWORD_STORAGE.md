# 서버 비밀번호 해싱 적용

새 비밀번호는 Spring Security `PasswordEncoder`를 통해 Argon2id로 저장한다.

- 등록 키: `argon2id-v1`
- Salt: encode 호출마다 무작위 16바이트
- 해시 길이: 32바이트
- 메모리: 19,456 KiB (19 MiB)
- 반복: 2회
- 병렬도: 1
- 저장값: `{argon2id-v1}$argon2id$...` 전체 문자열, 기존 VARCHAR(255) 컬럼 사용
- 구현: `MigratingPasswordEncoder`, `SecurityConfig`, `AuthController`
- 의존성: Bouncy Castle `bcprov-jdk18on:1.86`

원문 비밀번호는 trim/Unicode 정규화하지 않고 서버 인코더의 encode를 한 번만 호출한다. 반복·메모리 비용은 Argon2id 내부 설정이다. 로그인·변경·재설정·탈퇴 확인은 matches를 사용하며 SHA-256 사전 해싱이나 추가 encode 반복문은 사용하지 않는다.

## 기존 회원 호환

기존 접두사 없는 `$2...` BCrypt와 `{bcrypt}` 해시도 검증한다. 올바른 비밀번호로 로그인했을 때만 Argon2id로 새로 해싱하여 같은 회원 레코드에 저장한다. 잘못된 로그인에는 저장값이 바뀌지 않는다. 같은 비밀번호의 인코딩만 바꾸는 과정이므로 credentialVersion을 올려 다른 세션을 끊지는 않는다. 비밀번호 자체를 바꾸는 기존 흐름은 credentialVersion을 올린다.

로그인 재해싱과 프로필/비밀번호 변경·탈퇴는 회원 행 잠금으로 직렬화한다. 게시글 등록도 회원 확인 잠금을 먼저 얻는다. 게시글 수정·삭제의 작성자 확인은 회원에 추가 쓰기 잠금을 잡지 않아 회원→게시글과 게시글→회원의 상반된 잠금 순서를 만들지 않는다.

손상된 해시와 알 수 없는 접두사는 인증 실패로 처리한다. 원문·저장 해시는 응답 DTO나 브라우저 저장소에 넣지 않는다. 기존 브라우저 SHA-256 값을 Argon2id로 변환하는 방식은 사용하지 않는다.

## 비용 실험

고정된 합성 비밀번호만 사용하여 JDK 21에서 4회 워밍업 뒤 15회 측정의 중앙값을 기록했다. 동시 실험은 스레드 4개에서 matches 작업 16개를 모두 마칠 때까지의 전체 벽시계 시간이다. 개별 동시 요청의 평균/p95 지연을 뜻하지 않는다.

| 메모리 KiB | 반복 | encode 중앙값 | matches 중앙값 | 동시 4개, 16건 전체 |
|---:|---:|---:|---:|---:|
| 19,456 | 2 | 23.15ms | 21.73ms | 155.50ms |
| 19,456 | 3 | 32.91ms | 31.14ms | 217.26ms |
| 32,768 | 2 | 40.48ms | 39.14ms | 285.28ms |

설정된 Argon2 작업 메모리만 계산하면 기본값의 동시 4개는 약 76 MiB이다. JVM 객체·라이브러리·GC 등의 실제 프로세스 최대 메모리를 측정한 값은 아니다. 이번 측정은 과제용 로컬 보정이며 운영 트래픽에 대한 최적화나 로그인 요청 제한 검증을 완료했다는 뜻은 아니다. 비밀번호 해싱을 빠르게 만드는 것이 성능 개선 목표도 아니다.

재현 소스: `docs/benchmarks/PasswordCostBenchmark.java`, 원자료: `docs/benchmarks/password-cost.csv`. 실행에는 Spring Security crypto, spring-jcl, Bouncy Castle JAR가 클래스패스에 필요하다.

검증: 무작위 Salt, 정상/오류 비밀번호, 비용 필드, 기존 BCrypt 로그인 후 업그레이드, 실패 로그인 시 유지, 손상 해시, 가입·변경·재설정·탈퇴 통합 테스트.

기준: [Spring Security 6.5 Password Storage](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html), [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Bouncy Castle 배포](https://www.bouncycastle.org/download/bouncy-castle-java/).
