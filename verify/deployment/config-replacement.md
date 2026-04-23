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
| A7 | API Provider DB URL | `jdbc:postgresql://localhost:29006/api_provider` | `gims-api-provider/.../application.yml` / `sync-agent-provide/.../application.yml` | 환경변수 | | | |

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
| C3 | Proxy Internal URL | `http://localhost:8093` | Orchestrator 설정 / Agent Internal 호출 | 환경변수 | | | |
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

---

## G. 미확인 — 추가 grep 스캔 필요

> verifier 세션이 실제 코드베이스 grep 으로 채울 항목. 현재 내가 기억만으로 나열한 것이라 누락 가능.

- [ ] `grep -rn "localhost" --include="*.yml"` — 전체 yml 하드코딩 전수
- [ ] `grep -rn "localhost" --include="*.java"` — 자바 코드 하드코딩
- [ ] `grep -rn "localhost" --include="*.properties"` — properties 파일
- [ ] `grep -rn "localhost" --include="*.ts" --include="*.tsx"` — 프론트엔드 호출
- [ ] `grep -rn "29001\|29000\|29010\|29004\|29005\|29006" --include="*"` — dev 포트 하드코딩
- [ ] `grep -rn "D:/\|D:\\\\\|C:/" --include="*"` — Windows 경로
- [ ] `grep -rn "k1m\|1111" --include="*"` — dev 계정 하드코딩
- [ ] globals.properties 전수 검토

각 스캔 결과 → 위 A~F 표에 반영 + 정당한 치환 대상인지 판단.

---

## 이력 / 폐기 항목

(배포 반복 중 제거되거나 변경된 항목 기록 — 삭제 금지)

| 폐기 일자 | 항목 | 사유 |
|---------|------|------|
| - | - | - |
