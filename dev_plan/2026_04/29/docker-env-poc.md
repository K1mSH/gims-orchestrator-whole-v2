# Docker 환경변수화 PoC — gims-api-provider + sync-proxy-internal

> 작성일: 2026-04-29 (1차)
> 수정: 2026-04-29 — yml 임시 수정/복원 패턴으로 호스트 영향 0 보장
> 범위: **2개 모듈** Dockerfile/compose 신규 + 환경변수 주입만으로 컨테이너 기동/모듈간 API key 통과 검증
> 목적: 개발 편의용 default 제거 후 **Docker 환경에서 환경변수 주입만으로 ENC(Jasypt) 복호화 체인이 동작하는지** 첫 검증
> 관련:
>  - `verify/tasks/OPEN/TASK-002-env-var-default-policy.md` (옵션 B 채택, dev default 금지)
>  - `verify/deployment/config-replacement.md` (A7/C3/H8 검증 기록 대상)
>  - `verify/issues/OPEN/VER-001-api-provider-externalization-missing.md`

---

## 1. 배경

### 1.1 사용자 의도
- 사업상 배포는 우리 관할이 아니지만, **어떻게 흘러갈지 그림은 잡아두고 싶음**
- 8개 모듈 중 **API key 환경변수화가 가장 필수적**으로 보임 → 이쪽이 깨지면 모듈 간 호출 전부 401
- **기존 작업/호스트 dev 환경에 영향 없는 테스트** 가 본 PoC 의 전제

### 1.2 현재 검증 안된 가정
- 9개 yml 모두 `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` — dev default 박혀있음
- `agent.api-key` / `app.proxy.api-key` / `proxy.api-key` 등 8개 모듈에 같은 ENC 값:
  ```
  ENC(YzTJ0UGnUBS+pOEw5EEu7jEp/rRjAANV95og3onvTTuV4bsTdh7jWP/RUx/jv2vTLojWfJ8QltB9oknwS63RXA==)
  ```
- **운영 배포 시 환경변수 `JASYPT_PASSWORD`만 주입해서 ENC 평문화가 정상 작동하는지** — 한번도 컨테이너 환경에서 검증된 적 없음
- Docker 자산은 `infolink-api-collector` 1개뿐 (그것도 자기 DB 복호화용으로만 JASYPT 사용 — API key 체인은 안 거침)

### 1.3 PoC 대상 선정 사유 — gims-api-provider ↔ sync-proxy-internal
- **api-provider → proxy-internal HTTP 호출에 `X-API-Key` 헤더가 필수**
- 두 모듈 모두 같은 ENC 값을 갖고 있어, 같은 `JASYPT_PASSWORD` 주입 시 같은 평문 떨어져야 통과
- 호출 endpoint: `GET /api/datasources/{id}/connection-info` (`ProviderDataSourceService.createDataSource()`)
- ApiKeyFilter 작동 확인 가능 (sync-agent-common 의 공통 필터)
- **운영 배포 시 가장 먼저 깨질 가능성이 높은 지점** → PoC 의의 큼

---

## 2. 검증 대상 흐름

```
┌─────────────────────────────┐         ┌──────────────────────────────┐
│ gims-api-provider (8095)    │         │ sync-proxy-internal (8093)   │
│                             │         │                              │
│ JASYPT_PASSWORD env ──┐     │         │   ┌── env JASYPT_PASSWORD    │
│                       ↓     │         │   ↓                          │
│ app.proxy.api-key=ENC(...) ─┴─평문─→  X-API-Key ──→ ApiKeyFilter      │
│                             │         │           ↓                  │
│                             │  GET    │       agent.api-key=ENC(...) │
│ ProviderDataSourceService ──┴────────→│       (같은 평문이어야 통과) │
│   /api/datasources/{id}/    │         │           ↓                  │
│   connection-info           │ ←──200──│       Orchestrator 조회 응답 │
└─────────────────────────────┘         └──────────────────────────────┘
```

**검증 포인트**
1. JASYPT_PASSWORD 환경변수 주입 → ENC(...) 복호화 OK → 컨테이너 정상 기동
2. api-provider 가 proxy-internal 호출 시 X-API-Key 헤더 매칭 → 200 응답
3. 두 컨테이너에 다른 JASYPT_PASSWORD 주입 시 → 기동은 되지만 모듈간 호출 401 (또는 ENC 복호화 실패로 기동 실패)
4. JASYPT_PASSWORD 미주입 시 → **default 제거 후엔 기동 실패** (fail fast 확인)

---

## 3. 핵심 패턴 — yml 임시 수정/복원 (호스트 영향 0 보장)

> **사용자 제안 (2026-04-29)**: "JAR 생성 전에 잠깐 바꾸고 복원하는 식으로 테스트"

### 3.1 격리 원리

```
[원본 yml]  ─┬─ git tracked, 호스트 bootRun 시 그대로 사용 (영향 0)
             │
[빌드 시점]──┴─ 1. yml 패치 적용 (default 제거 + 환경변수 패턴)
                2. ./gradlew clean build  ← jar/BOOT-INF/classes/ 안에 패치된 yml 박힘
                3. git checkout -- application.yml  ← 즉시 복원
                4. build/libs/*.jar  ← 패치 상태로 생성됨
                5. JAR 을 PoC 디렉토리로 복사 → Dockerfile 이 COPY

[호스트 환경] git tracked yml 원본 → bootRun 영향 0
[Docker 환경] jar 안 yml = default 제거된 상태 → JASYPT_PASSWORD 미주입 시 fail fast
```

### 3.2 yml 패치 범위 (모듈별)

**gims-api-provider/src/main/resources/application.yml**
| 라인 | 변경 전 | 변경 후 | 호스트 영향 |
|:-:|---|---|---|
| 10 | `url: jdbc:postgresql://localhost:29006/api_provider` | `url: ${API_PROVIDER_DB_URL:jdbc:postgresql://localhost:29006/api_provider}` | 없음 (default 유지) |
| 11 | `username: k1m` | `username: ${API_PROVIDER_DB_USER:k1m}` | 없음 |
| 12 | `password: 1111` | `password: ${API_PROVIDER_DB_PASSWORD:1111}` | 없음 |
| 37 | `url: http://localhost:8093` | `url: ${PROXY_INTERNAL_URL:http://localhost:8093}` | 없음 |
| 43 | `password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` | `password: ${JASYPT_PASSWORD}` | **있음 — 빌드 후 즉시 복원으로 회피** |

**sync-proxy-internal/src/main/resources/application.yml**
| 라인 | 변경 전 | 변경 후 | 호스트 영향 |
|:-:|---|---|---|
| 42 | `password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` | `password: ${JASYPT_PASSWORD}` | **있음 — 빌드 후 즉시 복원으로 회피** |

> 핵심: **JASYPT_PASSWORD default 제거만 호스트에 영향이 있으나, build 후 즉시 git checkout 으로 복원** 하여 호스트 영향 0 보장.

### 3.3 자동화 스크립트로 사고 방지

수동으로 patch/복원 시 yml dirty 상태로 다른 작업과 충돌 가능 → 빌드 스크립트에서 단계별 가드 보장.

**`dev_plan/2026_04/29/docker-poc/build-jars.sh`** (산출물)

핵심 가드:
1. 빌드 시작 전 yml dirty 검사 (이전 빌드 실패 잔재 확인) — dirty 시 abort + 사용자에 명시적 복원 요구
2. trap 으로 스크립트 abort 시에도 yml 복원 보장 (`trap 'restore_ymls' EXIT INT TERM`)
3. patch 적용 → build → 즉시 복원 → JAR 복사 (5단계 순서 강제)
4. 빌드 종료 후 `git diff --quiet` 재검사로 복원 확인

> 본 스크립트가 **호스트 영향 0** 의 기술적 보장 수단. 수동 실행 금지 권장.

---

## 4. 사전 결정 필요 항목 (사용자 확인 후 진행)

> ⚠️ 아래 결정에 따라 작업 범위가 달라짐. 진행 전 검토.

### 4.1 외부 의존 처리 방식

api-provider 와 proxy-internal 이 외부에 의존하는 것:
- api-provider: PG `localhost:29006` (자기 관리 DB)
- proxy-internal: Internal Oracle `localhost:29004` (ENC 박힘) + Orchestrator `localhost:8080` (ENC 박힘)
- proxy-internal 의 orchestrator 호출은 본 PoC 시나리오에서 **선택적** (datasource 등록 정보 조회 시점에만 필요)

| 옵션 | 설명 | 작업량 | 단점 |
|---|---|:---:|---|
| **a. 호스트 의존 그대로** | 컨테이너에서 `host.docker.internal:29006` 등으로 접근. yml/ENC 값 변경 X (Docker Desktop 의 자동 매핑 활용) | 가장 적음 | ENC 안의 `localhost` 가 컨테이너 내에서 어떻게 풀릴지 — **호스트 매핑 처리 + ENC 재암호화 또는 환경변수 override 필요** |
| b. compose 안에 모두 포함 | PG/Oracle/orchestrator 컨테이너도 같이 띄움 | 가장 큼 (orchestrator Dockerfile 신규 필요) | 본 PoC 의 범위 폭증, 별도 dev_plan 필요 |
| c. `network_mode: host` | 컨테이너가 호스트 네트워크 직접 사용. Windows Docker Desktop 4.34+ 베타 지원 | 적음 | Windows 에서 안정성 미확인. 환경 의존적 |

**채택**: **(a)** — yml 환경변수 패턴(§3.2)으로 컨테이너에서는 `host.docker.internal` 주입, 호스트에서는 default(`localhost`) 사용.

### 4.2 JASYPT_PASSWORD 회전 여부

| 옵션 | 설명 | 비고 |
|---|---|---|
| **a. 기존 dev 비밀 그대로** | `sync-pipeline-secret-key-2024` 를 환경변수로만 주입 | ENC 재암호화 불필요. 검증은 "환경변수 주입 흐름" + "default 제거 fail fast" |
| b. 새 비밀로 회전 | 새 secret 생성 → 두 모듈 ENC 8건 재암호화 → 새 비밀 환경변수 주입 | 진짜 운영 시뮬레이션. 작업량 ↑ |

**채택**: **(a)** — 8개 모듈 동시 회전 시뮬레이션이라 본 PoC 범위 초과. (b) 회전은 별도 사이클.

### 4.3 default 제거 범위

| 옵션 | 설명 |
|---|---|
| **a. JASYPT 만 default 제거 (jar 안 한정)** | 본 PoC 2개 모듈의 yml 에 임시 패치 → 빌드 → 즉시 복원. 호스트 영향 0 |
| b. 9개 yml 일괄 제거 (영구) | TASK-002 본 처리. 본 PoC 와 별개 task |

**채택**: **(a)** — 본 PoC 의 핵심 패턴.

---

## 5. 작업 단계

### 5.1 PoC 디렉토리 구조

```
dev_plan/2026_04/29/docker-poc/
├── docker-compose.yaml
├── .env.example
├── .gitignore             # .env, jars/ 제외
├── build-jars.sh          # yml 임시 패치 → build → 복원 → jar 복사
├── api-provider-yml.patch
├── proxy-internal-yml.patch
├── api-provider/
│   └── Dockerfile         # 단순 JAR COPY 패턴
├── proxy-internal/
│   └── Dockerfile
└── jars/                  # gitignore (build-jars.sh 산출물)
    ├── api-provider.jar
    └── proxy-internal.jar
```

### 5.2 yml 패치 파일 작성

`api-provider-yml.patch` — §3.2 표 5 라인 변경
`proxy-internal-yml.patch` — §3.2 표 1 라인 변경

> patch 파일은 `diff -u` 로 생성. `patch -p0` 로 적용/복원. 또는 `git apply` 사용.

### 5.3 build-jars.sh 작성

```bash
#!/bin/bash
set -e

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
POC_DIR="$REPO_ROOT/dev_plan/2026_04/29/docker-poc"

cleanup() {
    echo "[cleanup] yml 복원..."
    git -C "$REPO_ROOT" checkout -- gims-api-provider/src/main/resources/application.yml 2>/dev/null || true
    git -C "$REPO_ROOT" checkout -- sync-proxy-internal/src/main/resources/application.yml 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# 1. dirty 검사
git -C "$REPO_ROOT" diff --quiet gims-api-provider/src/main/resources/application.yml \
    || { echo "ERROR: api-provider yml dirty. 수동 복원 후 재실행"; exit 1; }
git -C "$REPO_ROOT" diff --quiet sync-proxy-internal/src/main/resources/application.yml \
    || { echo "ERROR: proxy-internal yml dirty. 수동 복원 후 재실행"; exit 1; }

# 2. patch 적용
cd "$REPO_ROOT"
git apply "$POC_DIR/api-provider-yml.patch"
git apply "$POC_DIR/proxy-internal-yml.patch"

# 3. build
cd "$REPO_ROOT/gims-api-provider" && ./gradlew clean build -x test
cd "$REPO_ROOT/sync-proxy-internal" && ./gradlew clean build -x test

# 4. 복원 (trap 도 발동하지만 명시적으로)
cleanup

# 5. JAR 복사
mkdir -p "$POC_DIR/jars"
cp "$REPO_ROOT/gims-api-provider/build/libs"/*.jar "$POC_DIR/jars/api-provider.jar"
cp "$REPO_ROOT/sync-proxy-internal/build/libs"/*.jar "$POC_DIR/jars/proxy-internal.jar"

# 6. 복원 검증
git -C "$REPO_ROOT" diff --quiet gims-api-provider/src/main/resources/application.yml || \
    { echo "ERROR: api-provider yml 복원 실패"; exit 1; }
git -C "$REPO_ROOT" diff --quiet sync-proxy-internal/src/main/resources/application.yml || \
    { echo "ERROR: proxy-internal yml 복원 실패"; exit 1; }

echo "[done] JAR 생성 완료, yml 원상 복원 확인"
```

### 5.4 Dockerfile 작성 (단순 JAR COPY 패턴)

**`api-provider/Dockerfile`**
```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app
COPY ../jars/api-provider.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

> 멀티스테이지 빌드(collector 패턴) 안 씀 — JAR 은 build-jars.sh 가 사전 생성. Dockerfile 은 그냥 띄우기만.
> 단, Docker context 가 PoC 디렉토리라 `COPY jars/api-provider.jar` 형태가 더 자연스러움 → §5.5 참조.

**`proxy-internal/Dockerfile`** — 동일 패턴, JAR 만 다름

### 5.5 docker-compose.yaml

```yaml
services:
  gims-api-provider:
    build:
      context: .
      dockerfile: api-provider/Dockerfile
    container_name: gims-api-provider
    ports:
      - "8095:8095"
    environment:
      JASYPT_PASSWORD: ${JASYPT_PASSWORD}
      API_PROVIDER_DB_URL: jdbc:postgresql://host.docker.internal:29006/api_provider
      API_PROVIDER_DB_USER: k1m
      API_PROVIDER_DB_PASSWORD: 1111
      PROXY_INTERNAL_URL: http://sync-proxy-internal:8093
    extra_hosts:
      - "host.docker.internal:host-gateway"
    depends_on:
      - sync-proxy-internal

  sync-proxy-internal:
    build:
      context: .
      dockerfile: proxy-internal/Dockerfile
    container_name: sync-proxy-internal
    ports:
      - "8093:8093"
    environment:
      JASYPT_PASSWORD: ${JASYPT_PASSWORD}
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

### 5.6 .env.example

```
JASYPT_PASSWORD=sync-pipeline-secret-key-2024
```

### 5.7 .gitignore

```
.env
jars/
```

### 5.8 검증 시나리오 실행

| # | 시나리오 | 기대 결과 |
|:-:|---|---|
| S1 | `docker compose up --build` (.env 정상) | 두 컨테이너 정상 기동 |
| S2 | api-provider 가 proxy-internal 호출 (테스트 endpoint) | X-API-Key 통과 → 200 응답 |
| S3 | `JASYPT_PASSWORD` 비우고 기동 | **두 컨테이너 모두 기동 실패** (default 제거 효과) |
| S4 | 한쪽만 다른 JASYPT_PASSWORD | 평문 X-API-Key 불일치 → 401 (또는 한쪽 기동 실패) |
| S5 | proxy-internal 의 datasource ENC 가 `localhost:29004` 박힘 | host.docker.internal 매핑이 ENC 안에는 없음 → **proxy-internal 의 Internal Oracle 접속은 실패 예상** (PoC 한계 확인) |

S5 의 결과를 명확히 기록 → 다음 사이클에서 ENC 재암호화 또는 yml override 패턴 결정 근거.

### 5.9 회귀 테스트
- 호스트에서 기존 JAR/bootRun 흐름 정상 작동 확인 (yml 복원 후)
- gradle build 통과 (build-jars.sh 종료 시점에 jar 산출 확인)
- ApiKeyFilter 호스트 환경 회귀 (현재 dev 시나리오)

---

## 6. 산출물 (git tracked)

| # | 파일 | 변경 |
|:-:|---|---|
| 1 | `dev_plan/2026_04/29/docker-poc/docker-compose.yaml` | **신규** |
| 2 | `dev_plan/2026_04/29/docker-poc/.env.example` | **신규** |
| 3 | `dev_plan/2026_04/29/docker-poc/.gitignore` | **신규** |
| 4 | `dev_plan/2026_04/29/docker-poc/build-jars.sh` | **신규** |
| 5 | `dev_plan/2026_04/29/docker-poc/api-provider-yml.patch` | **신규** |
| 6 | `dev_plan/2026_04/29/docker-poc/proxy-internal-yml.patch` | **신규** |
| 7 | `dev_plan/2026_04/29/docker-poc/api-provider/Dockerfile` | **신규** |
| 8 | `dev_plan/2026_04/29/docker-poc/proxy-internal/Dockerfile` | **신규** |
| 9 | `verify/deployment/config-replacement.md` | A7/C3/H8 확인자/확인일 기입 |
| 10 | `verify/runs/2026-04-29.md` | **신규** PoC 실행 기록 |
| 11 | `dev_logs/2026_04/2026-04-29.md` | **신규** |

> **본 모듈 디렉토리(`gims-api-provider/`, `sync-proxy-internal/`) 의 git tracked 파일은 0건 변경** — 호스트 영향 0 보장의 가시적 증거.

---

## 7. 영향 범위

### 7.1 본 모듈 디렉토리 영향
- **0건**. yml 은 build-jars.sh 가 임시 수정 → 즉시 복원 → git diff 깨끗하게 종료.

### 7.2 호스트 dev 환경 영향
- **0건**. yml 원본 그대로 유지되므로 bootRun, JAR 직접 실행 모두 기존 동작.

### 7.3 회귀 위험
- **build-jars.sh 의 cleanup trap 미실행 시 yml dirty 잔존** → trap 으로 EXIT/INT/TERM 모두 커버. 빌드 종료 후 `git diff --quiet` 재검사로 안전망.
- 8093/8095 포트 충돌 (호스트 ↔ 컨테이너) — 컨테이너 가동 전 호스트 프로세스 종료 (수동)
- proxy-internal 의 datasource ENC(`localhost:29004`) 한계 — 컨테이너에서 Internal Oracle 접속 깨짐 → S5 로 명시 검증 (PoC 한계로 기록)

### 7.4 다른 모듈 영향
- 없음 (api-provider/proxy-internal 의 jar 만 PoC 디렉토리로 복사, 다른 모듈 코드/설정 미접근)
- ApiKeyFilter 는 sync-agent-common 의 공통 필터지만 본 PoC 는 코드 수정 X

---

## 8. 위험 / 회피책

| 위험 | 회피 |
|---|---|
| build-jars.sh 비정상 종료 시 yml dirty 잔존 | trap EXIT/INT/TERM + 종료 후 `git diff --quiet` 재검사 |
| ENC 안에 박힌 `localhost` 가 컨테이너 내부에서 풀릴 때 깨짐 (proxy-internal datasource 등) | S5 로 명시 검증, 깨짐을 정상 결과로 기록 → ENC 재암호화는 별도 사이클 |
| 8093/8095 포트 충돌 | 컨테이너 가동 전 호스트 프로세스 종료 |
| 다른 검증 (Internal Oracle 접속, orchestrator 호출) 이 본 PoC 에서 깨짐 | PoC 의 의도가 **API key 통과 검증** 이므로 다른 깨짐은 "PoC 한계" 로 기록 |
| Docker Desktop 미가동 / Windows host-gateway 미지원 | 사전 확인 (5.1 진행 전) |
| build-jars.sh 가 다른 unstaged 변경(VER-001/005 등) 까지 건드림 | `git checkout -- <특정 파일>` 패턴으로 단일 파일만 복원 (다른 변경 무관) |

---

## 9. 완료 조건 (DoD)

- [ ] PoC 디렉토리 8건 산출물 생성 (§6 1~8)
- [ ] `build-jars.sh` 실행 → JAR 2개 생성 + yml 원상 복원 확인 (`git status` 깨끗)
- [ ] `docker compose up --build` 으로 두 컨테이너 정상 기동
- [ ] S1~S4 시나리오 결과 기록 (S5 는 한계 확인용)
- [ ] api-provider → proxy-internal 호출 테스트 200 확인 (X-API-Key 통과)
- [ ] JASYPT_PASSWORD 미주입 시 fail fast 확인 (S3)
- [ ] verify/runs/2026-04-29.md 작성
- [ ] config-replacement.md A7/C3/H8 확인자/확인일 기입
- [ ] dev_logs/2026_04/2026-04-29.md 작성
- [ ] PoC 산출물 커밋 (사용자 승인 후) — **git tracked 변경은 PoC 디렉토리 + verify/dev_logs 에 한정**

---

## 10. 본 PoC 가 다루지 않는 것 (scope creep 방지)

- 8개 모듈 전체 Dockerfile 작성 — 별도 dev_plan
- TASK-002 의 9개 yml + 11개 Java default **영구 제거** — 별도 task 본 처리
- JASYPT_PASSWORD 회전 + ENC 8건 재암호화 — 별도 사이클
- compose 안에 PG/Oracle/orchestrator 포함 — 별도 dev_plan
- 8085(Agent Others) 등 다른 모듈 검증
- Internal Oracle / Orchestrator 의 ENC 안 host 변경 검증 (S5 한계만 기록)

---

## 11. 진행 전 사용자 결정 사항 요약

1. **§4.1 외부 의존 처리** — (a/b/c) 중 추천: **a** (호스트 + host.docker.internal)
2. **§4.2 JASYPT 회전** — (a/b) 중 추천: **a** (기존 비밀)
3. **§4.3 default 제거 범위** — (a/b) 중 추천: **a** (jar 안 한정 + 즉시 복원)
4. **Docker Desktop 가동 확인** — Windows 환경 가용한지

---

## 12. 다음 단계 (PoC 결과 후)

- PoC 통과 시 → 같은 패턴으로 8개 모듈 전체 Dockerfile 작성 (별도 dev_plan)
- ENC 재암호화 / JASYPT 회전 흐름 별도 사이클 (verify/deployment/credentials-rotation.md 갱신)
- TASK-002 본 처리 (9개 yml + 11개 Java default 영구 제거)
- compose 통합 (PG/Oracle/orchestrator 포함)
