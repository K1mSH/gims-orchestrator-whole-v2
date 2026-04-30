# Config Replacement — 바꿔 끼워질 고정값 전수표

실배포 시 **반드시 치환되어야 할 값** 목록. 이 표에 누락된 항목이 있으면 배포 후 장애로 직결.

## 원칙
- "임시값(tmp)" 과 **구분**. 이 표는 **환경에 따라 정당하게 달라지는 고정값**만.
  - tmp 제거는 `production-checklist.md § 1.2` 에서 grep 검증으로 따로 처리.
- 값이 `localhost` / dev 전용 경로 / dev 계정으로 시작하는 모든 위치는 반드시 표에 있어야 함.
- verifier 세션이 주기적으로 grep 스캔 돌려 **표 누락 탐지 + 갱신**.

## 갱신 규칙
- 새 서비스 / 새 DB / 새 외부 의존 추가 시 **즉시** 표에 행 추가
- 배포 때 실제 치환된 값을 "실배포 값" 컬럼에 기록 → 다음 배포 때 근거가 됨
- 항목 삭제 금지 — 더 이상 쓰지 않으면 "폐기" 로 마킹 (히스토리 보존)

---

## A. 관리 DB (Orchestrator / Agent 관리 영역)

| # | 항목 | 현재 dev 값 | 위치 (파일) | 치환 방법 | 실배포 값 | 확인자 | 확인일 |
|:-:|------|-----------|-------------|---------|----------|-------|-------|
| A1 | Orchestrator DB URL | `jdbc:postgresql://localhost:29001/orchestrator` | `sync-orchestrator/backend/src/main/resources/application.yml` | 환경변수 `ORCHESTRATOR_DB_URL` | | | |
| A2 | Orchestrator DB 계정 | `k1m` / `1111` | 동 | 환경변수 / secret | | | |
| A3 | Agent IF DB URL (bojo) | `jdbc:postgresql://localhost:29001/dev` | `sync-agent-bojo/.../application.yml` | 환경변수 | | | |
| A4 | Agent IF DB URL (bojo-int) | `jdbc:postgresql://localhost:29001/dev` | `sync-agent-bojo-int/.../application.yml` | 환경변수 | | | |
| A5 | Agent IF DB URL (others) | `jdbc:postgresql://localhost:29001/dev` | `sync-agent-others/.../application.yml` | 환경변수 | | | |
| A6 | API Collector DB URL | `jdbc:postgresql://localhost:29001/api_collector` | `infolink-api-collector/.../application.yml` | 환경변수 | | | |
| A7 | API Provider DB URL | `jdbc:postgresql://localhost:29006/api_provider` | `gims-api-provider/.../application.yml` / `sync-agent-provide/.../application.yml` | `SPRING_DATASOURCE_URL` 환경변수 (PoC 검증) | (PoC) `host.docker.internal:29006` 으로 override 작동 확인 | docker-poc | 2026-04-29 |

## B. 외부 원본 DB (Orchestrator DB 등록으로 관리)

> ★ 이 섹션은 **Orchestrator DB 의 DataSource 등록 테이블** 에 저장된 값. yml 하드코딩 없음.
> 실배포 시 UI / SQL 로 **DataSource 등록 행을 갱신** 하는 방식.

| # | 항목 | 현재 dev 값 | 위치 | 치환 방법 | 실배포 값 | 확인자 | 확인일 |
|:-:|------|-----------|------|---------|----------|-------|-------|
| B1 | 외부 PG — 대전 | `localhost:29000/daejeon` | Orchestrator DB DataSource 등록 | UI 갱신 | | | |
| B2 | 외부 PG — 바이텍 | `localhost:29000/bytek` | 동 | UI 갱신 | | | |
| B3 | 외부 PG — 충남 | `localhost:29000/chungnam` | 동 | UI 갱신 | | | |
| B4 | 외부 PG — 근산 | `localhost:29000/keunsan` | 동 | UI 갱신 | | | |
| B5 | 외부 MySQL 6 개 (infoworld / hydronet) | `localhost:29010/...` | 동 | UI 갱신 | | | |
| B6 | 내부 Oracle (GIMS) | `localhost:29004/XEPDB1` | 동 | UI 갱신 | | | |
| B7 | 새올 Tibero | `localhost:29005/XEPDB1` (Oracle XE 대체) | 동 | UI 갱신 | | | |

## C. 서비스 간 URL (망 내부 통신)

| # | 항목 | 현재 dev 값 | 위치 | 치환 방법 | 실배포 값 | 확인자 | 확인일 |
|:-:|------|-----------|------|---------|----------|-------|-------|
| C1 | Orchestrator URL | `http://localhost:8080` | Frontend `.env` / Agent yml | 환경변수 | | | |
| C2 | Proxy DMZ URL | `http://localhost:8083` | Orchestrator 설정 / Agent DMZ 호출 | 환경변수 | | | |
| C3 | Proxy Internal URL | `http://localhost:8093` | Orchestrator 설정 / Agent Internal 호출 | `APP_PROXY_URL` 환경변수 (api-provider 측, PoC 검증) | (PoC) `http://sync-proxy-internal:8093` 컨테이너 service name 으로 override 작동 확인 | docker-poc | 2026-04-29 |
| C4 | Agent DMZ (bojo) URL | `http://localhost:8082` | Proxy DMZ 설정 | 환경변수 | | | |
| C5 | Agent Others URL | `http://localhost:8085` | Proxy DMZ 설정 | 환경변수 | | | |
| C6 | Agent Internal (bojo-int) URL | `http://localhost:8092` | Proxy Internal 설정 | 환경변수 | | | |
| C7 | Agent Provide URL | `http://localhost:8096` | Proxy Internal 설정 | 환경변수 | | | |
| C8 | API Collector DMZ URL | `http://localhost:8084` | Orchestrator / Proxy 설정 | 환경변수 | | | |
| C9 | API Collector Internal URL | `http://localhost:8094` | Orchestrator / Proxy 설정 | 환경변수 | | | |
| C10 | API Provider URL | `http://localhost:8095` | 외부 공개 / Frontend | 환경변수 + **외부 공개 도메인** | | | |
| C11 | Frontend URL | `http://localhost:3000` | — | 환경변수 / 외부 도메인 | | | |

## D. 외부 API URL / Key

| # | 항목 | 현재 dev 값 | 위치 | 치환 방법 | 실배포 값 | 확인자 | 확인일 |
|:-:|------|-----------|------|---------|----------|-------|-------|
| D1 | Lookup 공통코드 URL | `application.yml lookup.common-code-url` | `infolink-api-collector/.../application.yml` | 환경변수 | | | |
| D2 | Vworld API key | ? (globals.properties 추정) | — | 환경변수 | | | |
| D3 | data.go.kr API key | ? | — | 환경변수 | | | |
| D4 | Mock API URL (MockApiController) | localhost | api-collector 내부 | **실배포 제거** | N/A | | |

## E. 파일 경로 / 스토리지

| # | 항목 | 현재 dev 값 | 위치 | 치환 방법 | 실배포 값 | 확인자 | 확인일 |
|:-:|------|-----------|------|---------|----------|-------|-------|
| E1 | image_root | `/image_root/obsvr_front/` (Windows) | Globals / Agent | 환경변수 / 실경로 (Linux) | | | |
| E2 | fileStorePath | Windows 경로 | Globals | 환경변수 | | | |
| E3 | 로그 경로 | `./logs/` 등 | 각 모듈 `logback-spring.xml` | 환경변수 / 절대경로 | | | |

## F. 시스템 환경

| # | 항목 | 현재 dev 값 | 위치 | 치환 방법 | 실배포 값 | 확인자 | 확인일 |
|:-:|------|-----------|------|---------|----------|-------|-------|
| F1 | Spring profile | `dev` / 기본 | 각 모듈 application.yml | 환경변수 `SPRING_PROFILES_ACTIVE=prod` | `prod` | | |
| F2 | JVM 타임존 | OS 기본 | 기동 스크립트 | `-Duser.timezone=Asia/Seoul` | | | |
| F3 | JVM 인코딩 | OS 기본 | 기동 스크립트 | `-Dfile.encoding=UTF-8` | | | |
| F4 | `ddl-auto` | `update` (provide/일부) | JPA 설정 | `validate` | `validate` | | |

## G. CORS / Frontend 빌드 설정

> 2026-04-23 grep 스캔에서 추가 발견된 치환 대상. 코드 내 평문 하드코딩 / 환경변수 치환 지점 미적용.

| # | 항목 | 현재 dev 값 | 위치 | 치환 방법 | 실배포 값 | 확인자 | 확인일 |
|:-:|------|-----------|------|---------|----------|-------|-------|
| G1 | Collector CORS allowedOrigins | `http://localhost:3000` | `infolink-api-collector/.../config/WebConfig.java:13` | 환경변수 신설 + `@Value` 치환 | | | |
| G2 | Orchestrator CORS allowedOrigins | `http://localhost:3000`, `http://localhost:5173` | `sync-orchestrator/backend/.../config/WebConfig.java:24` | 환경변수 신설 + `@Value` 치환 | | | |
| G3 | Next.js proxy → collector-api | `http://localhost:8084` | `sync-orchestrator/frontend/next.config.js:7` | `process.env.*` 치환 지점 신설 | | | |
| G4 | Next.js proxy → provider-api | `http://localhost:8095` | `sync-orchestrator/frontend/next.config.js:11` | `process.env.*` 치환 지점 신설 | | | |
| G5 | Next.js proxy → orchestrator | `http://localhost:8080` | `sync-orchestrator/frontend/next.config.js:15` | `process.env.*` 치환 지점 신설 | | | |
| G6 | Frontend SpecTab API host | `http://localhost:8095` (코드 내 평문) | `sync-orchestrator/frontend/components/api-provide/SpecTab.tsx:48` | `process.env.NEXT_PUBLIC_*` 치환 지점 신설 (**VER-003 후보**) | | | |

## H. Java `@Value` 환경변수 키 (이미 외부화됨 — 참고)

> 코드 내에 `@Value("${key:default}")` 로 **이미 외부화된** 키 목록. 실배포 시 환경변수 주입만 하면 전환 가능.
> 이 표는 "새로 치환해야 할 대상" 이 아니라 "주입할 환경변수 키 레퍼런스".

| # | 환경변수 키 | 기본값 (dev) | 사용처 |
|:-:|------------|-----------|--------|
| H1 | `agent.orchestrator-url` | `http://localhost:8080` | 전 Agent (bojo, bojo-int, others, provide) + Proxy DMZ/Internal — `SyncDataSourceService` / `ProxyDataSourceService` / `ConnectionInfoController` |
| H2 | `orchestrator.url` | `http://localhost:8080` | api-collector `OrchestratorClient.java:24` |
| H3 | `lookup.common-code-url` | `http://localhost:8084/mock/common/select/{groupCode}` | api-collector `application.yml:36` — **Mock fallback (VER-002 연관)** |
| H4 | `lookup.api-key-url` | `http://localhost:8084/mock/api-keys` | api-collector `application.yml:37` — **Mock fallback (VER-002 연관)** |
| H5 | `app.api-key-validation.url` | `http://localhost:8095/api/mock/api-key/validate` | api-provider `ApiKeyValidationService.java:22` — **Mock fallback** |
| H6 | `anyang.api.fac-url` | `http://localhost:8084/mock/anyang/fac` | api-collector `AnyangUsageExecutor.java:39` — **Mock fallback** |
| H7 | `anyang.api.data-url` | `http://localhost:8084/mock/anyang/data` | api-collector `AnyangUsageExecutor.java:42` — **Mock fallback** |
| H8 | `JASYPT_PASSWORD` | `sync-pipeline-secret-key-2024` | 전 모듈 (ENC 복호화 마스터 키 — `credentials-rotation.md` 연관) — **PoC(2026-04-29) 검증**: 환경변수 주입 시 ENC 정상 복호화 / 미주입 시 fail fast(`Failed to bind 'spring.datasource.password'`) / 모듈간 불일치 시 startup failure → 8 모듈 동일값 강제됨 |
| H9 | `SPRING_PROFILES_ACTIVE` | (미설정 → default) | 전 모듈 — 실배포 `prod` |

---

## I. grep 스캔 내역 (verifier 주기 재실행용)

> 최초 실행 2026-04-23. 새 커밋 반영 시 주기적으로 재실행 → 신규 하드코딩 탐지 + 이 문서 표 갱신.

- [x] `rg localhost --glob "*.yml"` — 2026-04-23: gims-api-provider(A7) + api-collector 의 lookup URL(H3~H4) 평문, 나머지 yml 은 ENC / 주석
- [x] `rg localhost --glob "*.java"` — 2026-04-23: 13건 중 11건 `@Value` 외부화 완료(H1~H7), CORS 2건 하드코딩(G1~G2)
- [x] `rg localhost --glob "*.{ts,tsx,js}"` — 2026-04-23: Next.js 프록시 3건(G3~G5) + SpecTab 1건(G6) + UI placeholder 1건(무해)
- [x] `rg localhost --glob "*.properties"` — 2026-04-23: 매치 없음
- [x] `rg "29000|29001|29004|29005|29006|29010"` — 2026-04-23: yml/java 내 매치 전부 주석. 외부 원본 DB dev 포트(B1~B7)는 Orchestrator DataSource 등록으로 관리됨
- [x] `rg "\\bk1m\\b|password:\\s*1111"` — 2026-04-23: `gims-api-provider/application.yml:11-12` 단 1곳 (**VER-001**)
- [x] `rg "[DC]:[\\\\/]"` — 2026-04-23: 0건
- [x] globals.properties — 2026-04-23: orchestrator_v2 에는 존재하지 않음 (newgims_v2 전용)

각 스캔 결과 → 위 A~H 표에 반영 완료. 새 항목 발견 시 즉시 해당 표에 행 추가.

---

## 이력 / 폐기 항목

(배포 반복 중 제거되거나 변경된 항목 기록 — 삭제 금지)

| 폐기 일자 | 항목 | 사유 |
|---------|------|------|
| - | - | - |
