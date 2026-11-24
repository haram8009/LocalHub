# 배포 가이드 (OCI + Docker Compose, 블루그린 무중단)

OCI Ampere A1(arm64) 서버 한 대에 Docker Compose로 배포합니다. 이미지는 GitHub Actions가 빌드해 GHCR에 올리고, 서버는 이미지를 pull만 합니다. 새 버전은 **블루/그린 중 쉬고 있는 쪽**에 띄워 검증한 뒤 트래픽을 넘기므로 배포 중에도 서비스가 끊기지 않습니다.

```
git push → GitHub Actions: 테스트 → 이미지 2개 빌드(linux/arm64) → GHCR push
                                         └→ SSH → deploy.sh <커밋 SHA>
서버:  인터넷 → [Caddy :80/:443] ──(활성 색)──▶ backend-blue  ┐
                    │                        backend-green ┴─▶ db(MySQL 8)      [Dozzle 127.0.0.1:8888]
                    └ upstream/active.caddy 가 가리키는 쪽으로 전달 (caddy reload 로 무중단 전환)
```

백엔드 이미지(`-app`)에는 **프론트 빌드 결과가 함께 들어 있습니다.** 그래서 화면과 API 버전이 항상 같은 색에서 짝이 맞고, 전환할 때 Caddy를 재시작하지 않아도 됩니다.

| 파일 | 역할 |
| --- | --- |
| `deploy/app.Dockerfile` | 프론트 빌드 → Spring Boot jar에 포함 → JRE 21 이미지(비root 실행) |
| `deploy/db.Dockerfile` | MySQL 8 + 관광지 스키마·덤프 자동 적재(데이터 디렉터리가 빌 때 1회) |
| `docker-compose.yml` | db, backend-blue, backend-green, proxy(Caddy), dozzle |
| `deploy/Caddyfile` | HTTPS 자동 발급, `/actuator` 차단, 활성 색 upstream import |
| `deploy/deploy.sh`, `deploy/rollback.sh`, `deploy/lib.sh` | 블루그린 배포·롤백 스크립트 (서버 `~/enjoytrip/`에 복사됨) |
| `docker-compose.local.yml`, `deploy/local.env` | 로컬 검증용 오버라이드 |
| `deploy/.env.example` | 서버 `.env` 예시 |
| `.github/workflows/deploy.yml` | 테스트 → 빌드 → 배포 |
| `backend/src/main/resources/db/migration/` | Flyway 마이그레이션(`mysql/`, `h2/`) |

## 1. 서버 준비 (최초 1회)

Ubuntu 기준입니다.

```bash
# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # 다시 로그인해야 적용됩니다
```

**포트 열기는 두 곳 모두 필요합니다.**

1. OCI 콘솔 → 네트워킹 → VCN → 보안 목록(또는 NSG) → 수신 규칙에 `TCP 80`, `TCP 443`, `UDP 443`을 추가합니다. SSH(22)는 이미 열려 있습니다. **3306, 8080, 8888은 열지 마세요.**
2. OCI의 Ubuntu 이미지는 호스트 iptables가 80/443을 막고 있는 경우가 많습니다.

```bash
sudo iptables -I INPUT 6 -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT 6 -p udp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 2. 도메인(DuckDNS) 준비

HTTPS 인증서와 카카오 지도 도메인 등록에 도메인이 필요합니다.

1. [duckdns.org](https://www.duckdns.org)에서 서브도메인을 만들고(예: `localhub`) 서버의 공인 IP를 입력합니다.
2. 카카오 개발자 콘솔 → 앱 → 플랫폼(Web)에 `https://localhub.duckdns.org`를 등록합니다.

## 3. 서버 `.env` 작성

```bash
mkdir -p ~/enjoytrip && cd ~/enjoytrip
# 저장소의 deploy/.env.example 내용을 .env 로 복사해 채웁니다.
nano .env
chmod 600 .env
```

반드시 채울 값: `IMAGE_PREFIX`(`ghcr.io/<소문자 계정>/<저장소>`), `SITE_ADDRESS`(도메인), `JWT_SECRET`(`openssl rand -base64 48`), `DB_ROOT_PASSWORD`, `DB_PASSWORD`, `KAKAO_REST_API_KEY`.
**`JWT_SECRET`은 고정해야 합니다.** 블루와 그린이 같은 키를 써야 전환해도 로그인이 유지되고, 바뀌면 모든 사용자의 로그인이 풀립니다.

## 4. GitHub 설정

저장소 **Settings → Secrets and variables → Actions**

| 종류 | 이름 | 값 |
| --- | --- | --- |
| Secret | `OCI_HOST` | 서버 공인 IP |
| Secret | `OCI_USER` | SSH 사용자(예: `ubuntu`) |
| Secret | `OCI_SSH_KEY` | 배포 전용 SSH **개인키** 전체(암호 없는 키) |
| Variable | `VITE_KAKAO_MAP_JAVASCRIPT_KEY` | 카카오 JavaScript 키(공개 키) |

- 배포 전용 키는 `ssh-keygen -t ed25519 -f deploy_key`로 만들고 `deploy_key.pub`을 서버의 `~/.ssh/authorized_keys`에 추가합니다.
- **Settings → Environments → New environment**에서 `production`을 만들면 승인 규칙을 걸 수 있습니다.
- 이미지는 기본적으로 private으로 올라갑니다. 배포 작업이 `GITHUB_TOKEN`으로 서버에서 로그인해 pull하므로 별도 토큰이 필요 없습니다.
- 워크플로는 SSH 접속을 먼저 점검합니다. 실패하면 키 형식 문제인지(`OCI_SSH_KEY`), 사용자명/공개키 등록 문제인지 메시지로 구분해 줍니다.

## 5. 배포와 블루그린 전환

`main`에 push하면 자동으로 실행됩니다. 수동 실행은 Actions 탭 → **Build and deploy** → Run workflow.

서버에서는 `deploy.sh <커밋 SHA>`가 다음 순서로 동작합니다.

1. 지금 서비스 중이 아닌 색(예: green)에 새 이미지를 띄웁니다. 기존 색(blue)은 계속 서비스합니다.
2. 새 색이 `healthy`(readiness)가 될 때까지 기다립니다.
3. 전환 전 **스모크 테스트**: 새 색의 화면(`/`)과 API(`/api/posts`)가 응답하는지 확인합니다.
4. 통과하면 `upstream/active.caddy`를 새 색으로 바꾸고 `caddy reload`합니다. 진행 중인 연결은 끊기지 않습니다.
5. 이전 색은 30초(`DRAIN_SECONDS`) 뒤 **정지**합니다(삭제하지 않음 → 즉시 롤백 가능).

2~3단계에서 실패하면 새 색을 제거하고 종료합니다. **기존 색은 그대로 서비스되고 워크플로만 실패로 표시됩니다.**

첫 배포는 MySQL이 관광지 덤프(약 5만 건)를 적재하느라 1~3분 더 걸립니다. 이후 배포 때 db, proxy는 재생성되지 않습니다(`--no-recreate`). Compose 파일의 db/proxy 설정을 바꿨다면 서버에서 `docker compose up -d <서비스>`로 직접 적용하세요(그 서비스는 잠깐 끊깁니다).

## 6. 상태와 로그 확인

```bash
cd ~/enjoytrip
cat .deploy-state                    # 활성 색과 색별 이미지 태그
docker compose ps                    # STATUS 에 healthy / unhealthy 표시
docker compose logs -f backend-blue  # 실시간 로그 (활성 색의 서비스 이름 사용)
docker compose logs --tail=100 db proxy
docker stats --no-stream             # CPU·메모리
```

**웹 로그 화면(Dozzle)**: 외부에 열지 않고 SSH 터널로만 접속합니다.

```powershell
ssh -L 8888:127.0.0.1:8888 ubuntu@<서버IP>
```

접속 후 브라우저에서 `http://localhost:8888`을 엽니다. 컨테이너별 로그를 실시간으로 보고 검색할 수 있습니다.

## 7. 롤백

직전 버전의 컨테이너가 정지 상태로 남아 있으므로, 새로 받지 않고 몇 초 안에 되돌립니다.

```bash
cd ~/enjoytrip
bash rollback.sh
```

직전 색을 다시 띄워 healthy를 확인한 뒤 트래픽을 넘기고, 문제가 있던 색을 정지합니다. 롤백은 한 단계 전 버전까지만 가능합니다. 더 이전 버전이 필요하면 `bash deploy.sh <그 커밋 SHA>`로 해당 이미지를 다시 배포하세요.

## 8. DB 스키마 변경 (Flyway)

스키마는 Flyway가 백엔드 기동 때 적용합니다(`backend/src/main/resources/db/migration/mysql/V*__*.sql`, H2용은 `h2/`).

- 관광지 테이블만 있고 이력이 없는 기존 DB는 baseline(0)으로 인식하고 `V1`을 적용합니다. `V1`은 `IF NOT EXISTS`라서 이미 있는 데이터를 건드리지 않습니다.
- 새 변경은 **새 파일**(`V2__설명.sql`)로 추가하고, 이미 적용된 파일은 수정하지 않습니다.
- **블루와 그린이 같은 DB를 공유**하므로 전환 도중에는 옛 버전과 새 버전이 동시에 DB를 씁니다. 다음 순서로 나눠서 배포하세요.
  1. 컬럼·테이블 **추가**(옛 코드가 무시해도 되는 형태, `NULL` 허용 또는 기본값) → 배포
  2. 새 코드가 새 컬럼을 사용 → 배포
  3. 쓰지 않는 컬럼·테이블 **삭제** → 그다음 배포
- 컬럼 이름 변경이나 삭제를 한 번에 하면 전환 중인 옛 버전이 오류를 냅니다.
- 검색 인덱스 버전(`SearchIndexState.VERSION`)을 바꾸면 새 색이 기동할 때 인덱스를 다시 만듭니다. 그동안 옛 색에서 작성된 글은 인덱스에 빠질 수 있으니, 인덱스 버전 변경은 단독으로, 트래픽이 적을 때 배포하세요.

## 9. 백업

회원·게시글은 MySQL 볼륨에만 있습니다. 하루 한 번 덤프를 받아 두세요.

```bash
# crontab -e
0 4 * * * cd ~/enjoytrip && docker compose exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction ssafy_trip members travel_posts post_locations post_search_postings search_index_state refresh_tokens password_resets' | gzip > ~/backup/ssafy_trip_$(date +\%F).sql.gz
```

`~/backup` 디렉터리를 먼저 만들고, 가능하면 OCI Object Storage 등 다른 곳에도 복사하세요.

## 로컬에서 먼저 검증

Docker Desktop(또는 Docker Engine)을 켠 뒤, 저장소 루트에서 Git Bash로 실행합니다. 아래는 서버와 같은 `deploy.sh`를 로컬 이미지로 돌려 보는 방법입니다.

```bash
# 앱 이미지 빌드(수 분 소요). 두 번째 태그는 "새 버전" 시뮬레이션용입니다.
docker build -t enjoytrip-local-app:v1 -f deploy/app.Dockerfile .
docker tag enjoytrip-local-app:v1 enjoytrip-local-app:v2

export DEPLOY_DIR="$PWD" COMPOSE_PATH_SEPARATOR=: COMPOSE_FILE=docker-compose.yml:docker-compose.local.yml \
       COMPOSE_ENV_FILES=deploy/local.env SKIP_PULL=1 DRAIN_SECONDS=5 MSYS_NO_PATHCONV=1
bash deploy/deploy.sh v1      # 첫 배포 (blue)
bash deploy/deploy.sh v2      # 무중단 전환 (green)
bash deploy/rollback.sh       # blue 로 복귀
```

`http://localhost:8081`로 접속합니다. 정리는 `docker compose down -v`(DB 볼륨까지 삭제)와 `rm -rf upstream .deploy-state`입니다.

## 알려진 제한

- **비밀번호 찾기**: 이메일 발송이 없어서 운영(`mysql` 프로필)에서는 비밀번호 재설정이 막혀 있습니다(`local` 프로필에서만 동작).
- **DB와 프록시는 무중단 대상이 아님**: 앱(백엔드+프론트) 배포만 무중단입니다. MySQL 설정이나 Caddy 컨테이너 설정을 바꿔 재생성하면 그 서비스는 잠깐 끊깁니다.
- **자동 롤백 없음**: 전환 이후에 발견되는 문제(느린 쿼리, 로직 오류)는 사람이 `rollback.sh`로 되돌려야 합니다.
- **서버 한 대**: 호스트나 OCI 장애에는 대비되지 않습니다.
