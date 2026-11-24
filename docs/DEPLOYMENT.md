# 배포 가이드 (OCI + Docker Compose)

OCI Ampere A1(arm64) 서버 한 대에 Docker Compose로 배포합니다. 이미지는 GitHub Actions가 빌드해 GHCR에 올리고, 서버는 이미지를 pull만 합니다.

```
git push → GitHub Actions: 테스트 → 이미지 3개 빌드(linux/arm64) → GHCR push
                                         └→ SSH로 서버 접속 → docker compose pull/up → 헬스체크 대기
서버: [Caddy :80/:443] ─ /     정적 파일(Vue)
                         └ /api → backend:8080 → db(MySQL 8)      [Dozzle 127.0.0.1:8888]
```

| 파일 | 역할 |
| --- | --- |
| `backend/Dockerfile` | Spring Boot JAR 빌드 후 JRE 21 이미지(비root 실행) |
| `deploy/web.Dockerfile`, `deploy/Caddyfile` | 프론트 빌드 + Caddy(HTTPS 자동 발급, `/api` 프록시, SPA fallback) |
| `deploy/db.Dockerfile` | MySQL 8 + 관광지 스키마·덤프 자동 적재(첫 기동 1회) |
| `docker-compose.yml` | 운영 구성(db, backend, web, dozzle) |
| `docker-compose.local.yml` | 로컬에서 소스를 직접 빌드해 검증하는 오버라이드 |
| `deploy/.env.example` | 서버 `.env` 예시 |
| `.github/workflows/deploy.yml` | 테스트 → 빌드 → 배포 |

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
`JWT_SECRET`은 고정해야 합니다. 바뀌면 모든 사용자의 로그인이 풀립니다.

## 4. GitHub 설정

저장소 **Settings → Secrets and variables → Actions**

| 종류 | 이름 | 값 |
| --- | --- | --- |
| Secret | `OCI_HOST` | 서버 공인 IP |
| Secret | `OCI_USER` | SSH 사용자(예: `ubuntu`) |
| Secret | `OCI_SSH_KEY` | 배포 전용 SSH **개인키** 전체 |
| Variable | `VITE_KAKAO_MAP_JAVASCRIPT_KEY` | 카카오 JavaScript 키(공개 키) |

- 배포 전용 키는 `ssh-keygen -t ed25519 -f deploy_key`로 만들고 `deploy_key.pub`을 서버의 `~/.ssh/authorized_keys`에 추가합니다.
- **Settings → Environments → New environment**에서 `production`을 만들면 승인 규칙을 걸 수 있습니다.
- 이미지는 기본적으로 private으로 올라갑니다. 배포 작업이 `GITHUB_TOKEN`으로 서버에서 로그인해 pull하므로 별도 토큰이 필요 없습니다.
- GitHub Actions는 임의의 IP에서 SSH로 접속합니다. 서버의 22번 포트를 특정 IP로 제한했다면 배포가 실패하니, 키 인증만 허용(비밀번호 로그인 끔)하는 방식으로 운영하세요.

## 5. 배포

`main`(또는 `master`)에 push하면 자동으로 실행됩니다. 수동 실행은 Actions 탭 → **Build and deploy** → Run workflow.

**첫 기동은 MySQL이 관광지 덤프(약 5만 건)를 적재하느라 1~3분 더 걸립니다.** 배포 단계는 db → backend → web이 `healthy`가 될 때까지 최대 5분 기다리고, 실패하면 해당 서비스의 로그를 출력하며 워크플로를 실패 처리합니다. 자동 롤백은 아직 없습니다.

## 6. 상태와 로그 확인

```bash
cd ~/enjoytrip
docker compose ps                    # STATUS 에 healthy / unhealthy 표시
docker compose logs -f backend       # 실시간 로그 (Ctrl+C 로 종료)
docker compose logs --tail=100 db web
docker stats --no-stream             # CPU·메모리
```

**웹 로그 화면(Dozzle)**: 외부에 열지 않고 SSH 터널로만 접속합니다.

```powershell
ssh -L 8888:127.0.0.1:8888 ubuntu@<서버IP>
```

접속 후 브라우저에서 `http://localhost:8888`을 엽니다. 컨테이너별 로그를 실시간으로 보고 검색할 수 있습니다.

## 7. 롤백

이미지는 커밋 SHA 태그로 남아 있습니다. 이전 커밋의 SHA로 되돌립니다.

```bash
cd ~/enjoytrip
TAG=<이전 커밋 SHA 40자리> docker compose up -d
```

DB 스키마가 바뀐 배포라면 코드만 되돌려서는 안 될 수 있습니다. 스키마 변경은 다음 단계의 Flyway 도입 때 함께 다룹니다.

## 8. 백업

회원·게시글은 MySQL 볼륨에만 있습니다. 하루 한 번 덤프를 받아 두세요.

```bash
# crontab -e
0 4 * * * cd ~/enjoytrip && docker compose exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction ssafy_trip members travel_posts post_locations post_search_postings search_index_state refresh_tokens password_resets' | gzip > ~/backup/ssafy_trip_$(date +\%F).sql.gz
```

`~/backup` 디렉터리를 먼저 만들고, 가능하면 OCI Object Storage 등 다른 곳에도 복사하세요.

## 로컬에서 먼저 검증

Docker Desktop(또는 Docker Engine)을 실행한 뒤 저장소 루트에서 실행합니다. 소스를 직접 빌드하므로 처음에는 수 분이 걸립니다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml --env-file deploy/local.env up --build -d
docker compose -f docker-compose.yml -f docker-compose.local.yml --env-file deploy/local.env ps
```

`http://localhost:8081`로 접속합니다. 정리는 `down -v`(DB 볼륨까지 삭제)입니다.

## 알려진 제한

- **비밀번호 찾기**: 이메일 발송이 없어서 운영(`mysql` 프로필)에서는 비밀번호 재설정이 막혀 있습니다(`local` 프로필에서만 동작).
- **무중단 배포 아님**: `docker compose up -d`가 backend를 교체하는 동안 수 초간 `/api`가 끊깁니다. 다음 단계에서 블루그린으로 개선합니다.
- **스키마 마이그레이션 도구 없음**: 백엔드가 기동 때 `CREATE TABLE IF NOT EXISTS`만 실행합니다. 컬럼 변경은 수동으로 해야 합니다.
