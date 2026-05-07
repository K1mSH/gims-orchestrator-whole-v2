# 서비스/모듈 이름 정리

> 작성일: 2026-05-07
> 사용자 의도: `infolink-` prefix 활용 방향으로 통일
> 사용자가 이 파일을 직접 편집해 새 이름 결정

---

## 1. 현재 모듈 목록 (실 디렉토리 기준 12개 — frontend 분리 반영)

> `sync-orchestrator/backend` 와 `sync-orchestrator/frontend` 는 이미 완전히 독립된 두 프로젝트가
> 한 우산 디렉토리에 묶여있을 뿐 (각자 build 시스템 / 의존성 / 실행). 분리 비용 매우 낮음.

| # | 현 디렉토리 | 포트 | 역할 | settings.gradle `rootProject.name` |
|---|---------|:----:|------|--------|
| 1 | `sync-orchestrator/backend/` | 8080 | 중앙 관리 API (Spring Boot, Gradle) | `sync-orchestrator-backend` |
| 2 | `sync-orchestrator/frontend/` | 3000 | 운영자 UI (Next.js, npm) | (Gradle 아님 — `package.json` `name`) |
| 3 | `sync-orchestrator-auth/` | 8096 | JWT 발급 / 사용자 관리 | `sync-orchestrator-auth` |
| 4 | `sync-agent-common/` | — | 공통 JAR 라이브러리 (ApiKeyFilter / JwksClient / JwtCookieAuthFilter) | `sync-agent-common` |
| 5 | `sync-agent-bojo/` | 8082 | DMZ 보조관측망 Agent (10업체 RCV + Loader + SND) | `sync-agent-bojo` |
| 6 | `sync-agent-bojo-int/` | 8092 | Internal 보조관측망 Agent (RCV + Loader) | `sync-agent-bojo-int` |
| 7 | `sync-agent-others/` | 8085 | DMZ Others Agent (제주/이용량/약수터/나라/뉴스 SND) | (확인 필요) |
| 8 | `sync-agent-provide/` | ? | Provide Agent (별 트랙) | (확인 필요) |
| 9 | `sync-proxy-dmz/` | 8083 | DMZ Proxy (datasource 패스스루 + 추적 API) | `sync-proxy-dmz` |
| 10 | `sync-proxy-internal/` | 8093 | Internal Proxy | `sync-proxy-internal` |
| 11 | `infolink-api-collector/` | 8084 / 8094 | 외부 공공 API 수집 (DMZ + Internal 단일 모듈) | `infolink-api-collector` |
| 12 | `gims-api-provider/` | 8095 | GIMS 데이터 외부 제공 (B4 등 핸들러) | `gims-api-provider` |

---

## 2. 새 이름 결정 표 (사용자 채움)

> 사용자 직접 편집 영역. 비워둔 칸 채우면서 결정.
> `infolink-` 통일 후보를 1차안으로 제안 — 변경하고 싶은 칸은 자유롭게 수정.

| # | 현재 디렉토리 | **새 디렉토리** | **새 `rootProject.name`** | **새 `spring.application.name`** | 비고 |
|---|---|---|---|---|---|
| 1 | `sync-orchestrator/backend/` | `infolink-orchestrator-backend/` | `infolink-orchestrator-backend` | `infolink-orchestrator-backend` | 우산 디렉토리(`sync-orchestrator/`)에서 끌어올림 |
| 2 | `sync-orchestrator/frontend/` | `infolink-orchestrator-frontend/` | (Gradle 아님) | (Next.js — `package.json` `name` 갱신) | 우산 디렉토리에서 끌어올림. 별 모듈 분리 ✅ |
| 3 | `sync-orchestrator-auth/` | `infolink-auth/` | `infolink-auth` | `infolink-auth` | -orchestrator- 떼고 단순화 후보 |
| 4 | `sync-agent-common/` | `infolink-agent-common/` | `infolink-agent-common` | (라이브러리 — 해당 X) | |
| 5 | `sync-agent-bojo/` | `infolink-agent-bojo-dmz/` | `infolink-agent-bojo-dmz` | `infolink-agent-bojo-dmz` | -dmz suffix 명시 (대칭) |
| 6 | `sync-agent-bojo-int/` | `infolink-agent-bojo-internal/` | `infolink-agent-bojo-internal` | `infolink-agent-bojo-internal` | -int → -internal 통일 |
| 7 | `sync-agent-others/` | `infolink-agent-others-dmz/` | `infolink-agent-others-dmz` | `infolink-agent-others-dmz` | DMZ 전용 — suffix 명시 |
| 8 | `sync-agent-provide/` | `infolink-agent-provide-dmz/` | `infolink-agent-provide-dmz` | `infolink-agent-provide-dmz` | DMZ 전용 |
| 9 | `sync-proxy-dmz/` | `infolink-proxy-dmz/` | `infolink-proxy-dmz` | `infolink-proxy-dmz` | |
| 10 | `sync-proxy-internal/` | `infolink-proxy-internal/` | `infolink-proxy-internal` | `infolink-proxy-internal` | |
| 11 | `infolink-api-collector/` | `infolink-api-collector/` | `infolink-api-collector` | `infolink-api-collector` | 그대로 (이미 정합) |
| 12 | `gims-api-provider/` | `infolink-api-provider/` | `infolink-api-provider` | `infolink-api-provider` | prefix `infolink-` 통일 (사용자 결정 5/7) |

### 결정 포인트 (전 항목 사용자 결정 완료 5/7)

- [x] **frontend 별 모듈로 분리** — `sync-orchestrator/` 우산 디렉토리 해체. backend/frontend 형제로
- [x] **`gims-api-provider` → `infolink-api-provider`** — `infolink-` prefix 통일. 우리 시스템 모듈 단일화
- [x] **`sync-orchestrator-auth` → `infolink-auth`** — `-orchestrator-` 떼고 단순화
- [x] **`-int` → `-internal`** 통일 — bojo-int → bojo-internal. proxy-internal 과 대칭
- [x] **DMZ suffix 명시** — bojo/others/provide 모두 `-dmz` 명시. proxy-dmz 와 대칭

---

## 3. 영향 범위 체크리스트 (리네이밍 시 필요 작업)

### 3-0. Frontend 분리 (선행)
- [ ] `sync-orchestrator/frontend/` → 루트로 끌어올림 (`mv` or `git mv`)
- [ ] `sync-orchestrator/backend/` → 루트로 끌어올림
- [ ] 빈 `sync-orchestrator/` 디렉토리 삭제
- [ ] `frontend/package.json` `name` 갱신 (현 `sync-orchestrator-frontend` → `infolink-orchestrator-frontend`)
- [ ] `frontend/next.config.js` 의 backend/auth/collector/provider rewrite 호스트는 그대로 (port 기반이라 영향 X)
- [ ] backend/frontend 분리 빌드 검증 (각자 독립 디렉토리에서 빌드/실행)

### 3-1. 디렉토리 / 빌드 설정 (전체 모듈)
- [ ] 12개 디렉토리 rename (frontend 분리 포함)
- [ ] 10개 `settings.gradle` `rootProject.name` 갱신 (frontend 제외 — Gradle 아님)
- [ ] 10개 `build.gradle` archive 이름 (있는 경우)
- [ ] 10개 모듈 `application.yml` `spring.application.name` 갱신
  - infolink-orchestrator-backend, infolink-auth, infolink-agent-bojo-dmz, infolink-agent-bojo-internal, infolink-agent-others-dmz, infolink-agent-provide-dmz, infolink-proxy-dmz, infolink-proxy-internal, infolink-api-collector, infolink-api-provider

### 3-2. JAR 의존성
- [ ] `sync-agent-common-1.0.0-SNAPSHOT.jar` 파일명 → `infolink-agent-common-1.0.0-SNAPSHOT.jar`
- [ ] 9개 모듈 `libs/` 안의 JAR 교체
- [ ] 9개 모듈 `build.gradle` `implementation files('libs/sync-agent-common-...')` 경로 갱신

### 3-3. 도커 / 외부 인프라
- [ ] 도커 컨테이너 이름 (해당 시)
- [ ] (DB 컨테이너 `gims_dmz_saeol_oracle` 등은 무관 — DB 서비스라 분리)

### 3-4. 문서 (참조 다수)
- [ ] `D:/dev/claude/CLAUDE.md` (프로젝트 instructions)
- [ ] `docs/ARCHITECTURE.md`, `docs/UI_GUIDE.md`, `docs/AUTH_DESIGN.md`, `docs/AUTH_FLOW.md`
- [ ] `docs/claude-memory/*.md`
- [ ] `verify/_invariants/`, `verify/deployment/`, `verify/checklists/`
- [ ] `todo/system/*.md`
- [ ] `dev_plan/` 과거 문서 (참조용 — 업데이트 권장 but 옵션)
- [ ] `dev_logs/2026_*/` (과거 기록 — 손대지 않음)
- [ ] `MEMORY.md` + `memory/*.md` (사용자 영역 — 별도 동기화)
- [ ] `test_plan/bojo-test.md`, `test_plan/others-test.md`

### 3-5. Backend DB 데이터
- [ ] `agent_code` (논리적 식별자) — 디렉토리명과 별개. 변경 의무 X. 단 `dmz-bojo-rcv-daejeon` 같은 패턴이 디렉토리 정합 원하면 `agent` 테이블 데이터 같이 갱신 (선택)

### 3-6. Java 패키지명 통일 (사용자 결정 5/7 — 같이 진행)

> 모든 패키지 root 를 `com.infolink.*` 로 통일. "깔끔하게" 원칙.

#### 현재 패키지 root 6종
| 현재 root | 사용 모듈 | → | 새 root |
|---|---|:-:|---|
| `com.gims.auth` | sync-orchestrator-auth | → | `com.infolink.auth` |
| `com.gims.provider` | gims-api-provider | → | `com.infolink.provider` |
| `com.infolink.collector` | infolink-api-collector | → | `com.infolink.collector` (유지) |
| `com.sync.agent` | bojo / bojo-int / others / provide / common | → | `com.infolink.agent` |
| `com.sync.orchestrator` | sync-orchestrator/backend | → | `com.infolink.orchestrator` |
| `com.sync.proxy` | sync-proxy-dmz / proxy-internal | → | `com.infolink.proxy` |

#### Agent 하위 패키지 (현재 분리되어 있음 — 충돌 없음)
| 모듈 | 현재 sub-package | → | 새 sub-package |
|---|---|:-:|---|
| sync-agent-common | `com.sync.agent.common` | → | `com.infolink.agent.common` |
| sync-agent-bojo | `com.sync.agent.bojo` | → | `com.infolink.agent.bojo` |
| sync-agent-bojo-int | `com.sync.agent.bojoint` | → | `com.infolink.agent.bojo` (DMZ와 통합 — 사용자 결정 5/7 옵션 A) |
| sync-agent-others | `com.sync.agent.others` | → | `com.infolink.agent.others` |
| sync-agent-provide | `com.sync.agent.provide` | → | `com.infolink.agent.provide` |

> **bojo / bojo-internal 패키지 = `com.infolink.agent.bojo` 통합** (사용자 결정 5/7) — JAR 별 분리라 클래스 충돌 없음. 디렉토리에서만 zone 구분, 패키지는 도메인 단일

#### 영향 범위
- [ ] 모든 `.java` 파일의 `package com.sync.*` 또는 `package com.gims.*` 선언 수정
- [ ] 모든 `import com.sync.*` 또는 `import com.gims.*` 수정
- [ ] 디렉토리 구조 이동 (`src/main/java/com/sync/agent/...` → `src/main/java/com/infolink/agent/...`)
- [ ] `application.yml` 의 `@ComponentScan(basePackages=...)` / `@EntityScan` / `@EnableJpaRepositories` 갱신
  - sync-orchestrator/backend `@ComponentScan({"com.sync.orchestrator", "com.sync.agent.common.config"})` (5/6 추가분) — `{"com.infolink.orchestrator", "com.infolink.agent.common.config"}`
  - 검증자 모듈 3개 (backend / api-provider / api-collector) 의 AutoConfiguration import 경로
- [ ] `META-INF/spring/AutoConfiguration.imports` 의 FQCN — `com.sync.agent.common.auth.AuthVerifierAutoConfiguration` → `com.infolink.agent.common.auth.AuthVerifierAutoConfiguration`
- [ ] Jasypt / Spring 설정의 클래스 FQCN 참조 (있으면)
- [ ] IDE refactor 후 빌드 검증 11개 모듈 + frontend

---

## 4. 실행 계획 (Phase 단위)

### 사전 조건
- [ ] 7개 가동 서비스 정지 (auth/backend/proxy-dmz/proxy-internal/collector/provider/agent-bojo + frontend)
- [ ] 작업 트리 정리 (5/6 untracked dev_plan 2개 커밋 또는 별 처리 결정)
- [ ] 브랜치 결정 — main 직접 vs `refactor/rename-modules` 분리 (사용자 결정 필요)

---

### Phase 1 — 디렉토리 + Gradle/yml 이름 (1h, 낮은 위험)

자바 패키지는 건드리지 않음. 단순 외피 변경.

1. 디렉토리 12개 `git mv`
   - `sync-orchestrator/backend` → `infolink-orchestrator-backend`
   - `sync-orchestrator/frontend` → `infolink-orchestrator-frontend`
   - `sync-orchestrator/` (빈 우산 디렉토리) 삭제
   - `sync-orchestrator-auth` → `infolink-auth`
   - `sync-agent-common` → `infolink-agent-common`
   - `sync-agent-bojo` → `infolink-agent-bojo-dmz`
   - `sync-agent-bojo-int` → `infolink-agent-bojo-internal`
   - `sync-agent-others` → `infolink-agent-others-dmz`
   - `sync-agent-provide` → `infolink-agent-provide-dmz`
   - `sync-proxy-dmz` → `infolink-proxy-dmz`
   - `sync-proxy-internal` → `infolink-proxy-internal`
   - `gims-api-provider` → `infolink-api-provider`
   - (`infolink-api-collector` 그대로)
2. 10개 `settings.gradle` `rootProject.name` 갱신
3. 10개 모듈 `application.yml` `spring.application.name` 갱신
4. frontend `package.json` `name` 갱신 (`sync-orchestrator-frontend` → `infolink-orchestrator-frontend`)
5. 10개 모듈 `libs/sync-agent-common-*.jar` 삭제 (Phase 2에서 새 이름으로 재빌드)
6. 10개 모듈 `build.gradle` `implementation files('libs/sync-agent-common-*.jar')` → `infolink-agent-common-*.jar` 경로 갱신
7. **검증**: 각 모듈 (Java 패키지 변경 전이라) 빌드 시도 — common JAR 미존재로 실패 예상. 디렉토리/이름 변경만 정합성 확인 (git status / git diff)
8. **결정 게이트**: 디렉토리·이름만 OK 인지 사용자 확인 후 Phase 2 진입

---

### Phase 2 — Java 패키지 root rename (1.5~2h, 중간 위험)

가장 큰 작업. 모든 `.java` 의 `package`/`import` 수정 + 디렉토리 이동.

**패키지 root 6종 → `com.infolink.*`**

| 모듈 | 작업 |
|---|---|
| infolink-auth | `src/main/java/com/gims/auth/...` → `com/infolink/auth/...` (디렉토리 이동 + 모든 .java 의 package 선언 + import 갱신) |
| infolink-api-provider | `com/gims/provider/...` → `com/infolink/provider/...` |
| infolink-api-collector | `com/infolink/collector/` (그대로 — 이미 정합) |
| infolink-agent-common | `com/sync/agent/common/...` → `com/infolink/agent/common/...` |
| infolink-agent-bojo-dmz | `com/sync/agent/bojo/...` → `com/infolink/agent/bojo/...` |
| infolink-agent-bojo-internal | `com/sync/agent/bojoint/...` → `com/infolink/agent/bojo/...` (DMZ와 통합) |
| infolink-agent-others-dmz | `com/sync/agent/others/...` → `com/infolink/agent/others/...` |
| infolink-agent-provide-dmz | `com/sync/agent/provide/...` → `com/infolink/agent/provide/...` |
| infolink-orchestrator-backend | `com/sync/orchestrator/...` → `com/infolink/orchestrator/...` |
| infolink-proxy-dmz | `com/sync/proxy/...` → `com/infolink/proxy/...` |
| infolink-proxy-internal | `com/sync/proxy/...` → `com/infolink/proxy/...` |

**부속 갱신**
- `application.yml` 의 base-package 참조 (있으면)
- `@ComponentScan(basePackages=...)` — 특히 backend `{"com.sync.orchestrator", "com.sync.agent.common.config"}` (5/6 추가) → `{"com.infolink.orchestrator", "com.infolink.agent.common.config"}`
- `@EntityScan` / `@EnableJpaRepositories` 갱신
- `META-INF/spring/AutoConfiguration.imports` (infolink-agent-common) — `com.sync.agent.common.auth.AuthVerifierAutoConfiguration` → `com.infolink.agent.common.auth.AuthVerifierAutoConfiguration`
- common JAR 재빌드 (`infolink-agent-common-1.0.0-SNAPSHOT.jar` 새 이름) → 9개 검증자 모듈 libs/ 복사

**검증**
- 11개 Gradle 모듈 `clean build -x test` 모두 SUCCESS
- frontend `npm run build` SUCCESS
- 단위 테스트 17/17 회귀 (infolink-auth)

**결정 게이트**: 사용자 확인 후 Phase 3 진입

---

### Phase 3 — 통합 기동 + smoke test (45분)

1. 7개 서비스 순서대로 기동
   - infolink-auth (8096) → infolink-orchestrator-backend (8080) → infolink-proxy-dmz (8083) → infolink-proxy-internal (8093) → infolink-api-collector (8084) → infolink-api-provider (8095) → infolink-agent-bojo-dmz (8082) → infolink-orchestrator-frontend (3000)
2. Health 7/7 UP 확인
3. Smoke test
   - JWT 로그인 → cookie
   - `/api/agents` (backend) → 200
   - `/api/manage/operations` (provider) → 200
   - `/api/endpoints` (collector) → 200
   - frontend `/` → 200 (cookie 있으면 dashboard)
4. **결정 게이트**: 정상 동작 확인 후 Phase 4

---

### Phase 4 — 문서 일괄 갱신 (30분)

`sync-` / `gims-` / `com.sync.` / `com.gims.` 다섯 패턴 grep + sed 또는 수동 갱신:
- `D:/dev/claude/CLAUDE.md`
- `docs/ARCHITECTURE.md`, `docs/UI_GUIDE.md`, `docs/AUTH_DESIGN.md`, `docs/AUTH_FLOW.md`
- `docs/claude-memory/*.md`
- `verify/_invariants/*.md`, `verify/deployment/*.md`, `verify/checklists/*.md`
- `todo/system/*.md`
- `MEMORY.md` + `memory/*.md` (사용자 영역 — 같이 동기화)
- `test_plan/bojo-test.md`, `test_plan/others-test.md`
- `dev_plan/2026_05/07/service-naming.md` 본 문서 (전후 매핑 그대로 보존, 단 "현재" 표기는 보강)

> `dev_logs/*` 과거 기록은 손대지 않음 (그 시점의 기록 보존).

---

### Phase 5 — 커밋 + 태그

1. 변경사항 단일 커밋
   ```
   refactor(rename): 전 모듈 infolink-* prefix + com.infolink.* 패키지로 통일

   - 디렉토리 12개 rename (sync-/gims- → infolink-)
   - frontend 별 모듈 분리 (sync-orchestrator/{backend,frontend} → 형제)
   - Java 패키지 root 6종 → com.infolink.*
   - bojo-int → bojo-internal (디렉토리만, 패키지는 bojo와 통합)
   - 영향: settings.gradle / application.yml / build.gradle libs / @ComponentScan / AutoConfiguration.imports / 문서 다수
   ```
2. **결정 게이트**: 1차 반입 baseline tag 박을지 사용자 확인 (`stable-2026-05-07-rename` 같은)

---

## 5. 최종 확정 매핑 (5/7 사용자 결정 완료)

| # | 현재 | → | 최종 |
|:-:|---|:-:|---|
| 1 | `sync-orchestrator/backend/` | → | `infolink-orchestrator-backend/` |
| 2 | `sync-orchestrator/frontend/` | → | `infolink-orchestrator-frontend/` |
| 3 | `sync-orchestrator-auth/` | → | `infolink-auth/` |
| 4 | `sync-agent-common/` | → | `infolink-agent-common/` |
| 5 | `sync-agent-bojo/` | → | `infolink-agent-bojo-dmz/` |
| 6 | `sync-agent-bojo-int/` | → | `infolink-agent-bojo-internal/` |
| 7 | `sync-agent-others/` | → | `infolink-agent-others-dmz/` |
| 8 | `sync-agent-provide/` | → | `infolink-agent-provide-dmz/` |
| 9 | `sync-proxy-dmz/` | → | `infolink-proxy-dmz/` |
| 10 | `sync-proxy-internal/` | → | `infolink-proxy-internal/` |
| 11 | `infolink-api-collector/` | → | `infolink-api-collector/` (유지) |
| 12 | `gims-api-provider/` | → | `infolink-api-provider/` |

### 결정 원칙
1. **prefix `infolink-` 통일** — 모든 자체 모듈
2. **zone suffix 명시** (`-dmz` / `-internal`) — 모든 zone 분리 모듈
3. **단순화 우선** — `infolink-auth` (orchestrator 산하 X), `infolink-orchestrator-{backend|frontend}` (우산 디렉토리 X)
4. **Java 패키지 root 통일** (`com.infolink.*`) — bojo-internal 은 bojo 와 패키지 통합 (zone 구분은 디렉토리만)

### 모든 결정 ✅ 완료 (5/7)

| 영역 | 상태 |
|---|:--:|
| 디렉토리 12개 매핑 | ✅ |
| `rootProject.name` 10개 (Gradle 모듈) | ✅ |
| `spring.application.name` 10개 (Spring 모듈) | ✅ |
| frontend 별 모듈 분리 | ✅ |
| 패키지 root 6종 → `com.infolink.*` | ✅ |
| Agent 하위 패키지 5종 (bojo-int 통합) | ✅ |

## 6. 사용자 메모 칸

> 자유 메모

- (필요 시 채우기)
