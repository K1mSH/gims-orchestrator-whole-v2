# Invariant: Agent = Target = 관리 DB 일체화

> 상위 규약. 헤더 라우팅(`header-routing.md`) / 추적(`trace-source-branches.md`) / SyncLog per-mapping 등 여러 하위 invariant 의 전제.

---

## 1. 규약 정의

파이프라인 Agent 의 다음 세 개념은 **동일한 DB** 를 가리킨다:

1. **Target DB** — Agent 가 적재하는 목적지 DB (비즈니스 데이터가 쓰이는 DB)
2. **Agent 의 JPA 기본 datasource** — Agent 의 `application.yml` 의 `spring.datasource` 로 연결되는 DB
3. **관리 DB** — Agent 가 `executions` / `sync_logs` / 기타 메타 테이블을 저장하는 DB

즉, 하나의 Agent 는 자기 Target DB 안에 자기 관리 메타 테이블을 같이 둔다. 별도의 "관리 전용 DB" 를 두지 않는다.

### 등가 관계 (필수)
```
Target DB == JPA 기본 datasource == 관리 DB
(물리적으로 동일한 DB 인스턴스 + 동일한 스키마)
```

### Orchestrator 등록 연결
Orchestrator DB 의 Agent 엔티티에는 `targetDatasourceId` 컬럼이 있으며, 그 값은 Orchestrator DB 의 DataSource 등록 테이블에 있는 DB ID 를 가리킨다. 이 등록 정보가 Agent 의 `application.yml` 실제 연결과 **같은 DB** 를 가리켜야 한다.

---

## 2. 적용 범위

### 적용 대상 (필수)
모든 파이프라인 Agent — RCV / Loader / SND / Preprocess 성격 모두 동일:
- `infolink-agent-bojo-dmz` (DMZ 통합 Agent: 10 RCV + Loader + SND)
- `infolink-agent-bojo-internal` (Internal Agent: RCV + Loader)
- `infolink-agent-others-dmz` (DMZ 기타 Agent)
- `infolink-agent-provide-dmz` (Internal Provide Agent)
- 향후 추가되는 모든 파이프라인 Agent 모듈

### 적용 제외
- **API Provider** (`infolink-api-provider`) — 읽기 전용 제공 서비스, Execution/SyncLog 작성 주체 아님
- **API Collector** (`infolink-api-collector`) — 별도 실행 이력 모델(`ApiExecutionHistory`) 보유. 본 invariant 범위 밖
- **Proxy** (`infolink-proxy-dmz` / `infolink-proxy-internal`) — Agent 가 아닌 중계 서비스. 단 관리 DB 라우팅 규약(`header-routing.md`) 의 중요 주체

---

## 3. 준수 확인 절차

대상 Agent 모듈마다 아래 체크 전부 PASS 여야 함.

- [ ] Agent 의 `application.yml` 의 `spring.datasource.url` 이 **운영상 Target DB** 를 가리킴
- [ ] Agent 엔티티의 `targetDatasourceId` 값이 Orchestrator DB DataSource 등록에 존재
- [ ] 해당 등록 레코드의 DB 연결 정보가 Agent 의 `application.yml` 연결과 **같은 DB** (host / port / dbname 일치)
- [ ] 해당 DB 에 `executions` / `sync_logs` 테이블 존재 (JPA ddl-auto 또는 DDL 선배포)
- [ ] Agent 실행 시 `executions` / `sync_logs` 레코드가 해당 DB 에 기록됨 (샘플 실행 후 SQL 확인)
- [ ] Proxy 경유 조회 시 동일 DB 에서 레코드 읽어짐 (`X-Manage-Datasource-Id` 헤더 규약과 합치 — `header-routing.md` 참조)

---

## 4. 적용 Agent 현황표 (감시)

| Agent | application.yml datasource | Orchestrator 등록 ID (targetDatasourceId) | 등록/yml 일치 | executions/sync_logs 쓰기 확인 | 준수 | 최종 확인 | 확인자 |
|-------|----------------------------|-------------------------------------------|:---:|:---:|:---:|----------|-------|
| infolink-agent-bojo-dmz (DMZ) | ENC 처리 (미디코드) | ? | ? | ? | ⚠️ 미확인 | - | - |
| infolink-agent-bojo-internal (Internal) | ENC 처리 | `internal` (추정) | ? | ✅ (4/22 확인) | ⚠️ 표 확인 필요 | - | - |
| infolink-agent-others-dmz (DMZ) | ENC 처리 | ? | ? | ? | ⚠️ 미확인 | - | - |
| infolink-agent-provide-dmz (Internal) | ENC 처리 (PG 29006 api_provider) | `api-provider` | ? | ✅ (4/22 헤더 라우팅 도입으로 확인) | ⚠️ 표 확인 필요 | - | - |

### 현황 파악을 위한 후속 검증 필요
- [ ] 각 Agent `application.yml` ENC 복호화 값 확인 (또는 환경별 주입값)
- [ ] Orchestrator DB `datasource` 테이블 덤프
- [ ] Agent 엔티티의 `targetDatasourceId` 값 덤프
- [ ] 3 자 매칭 검증 쿼리 실행

---

## 5. 위반 이력

### 2026-04-22 이전 — provide Agent 관리 DB 분리 문제
- **증상**: provide Agent 가 PG 29006(api-provider)에 Execution/SyncLog 저장 → Proxy Internal(기존 Oracle 29004 전제)에서 못 읽음
- **근본 원인**: "망당 한 관리 DB" 전제가 provide Agent 추가로 깨짐. Proxy 라우팅이 `Proxy → 고정 관리 DB` 가정
- **해결**: `X-Manage-Datasource-Id` 헤더 라우팅 + `Agent = target 쪽` 규약 확립 (`project_header_manage_routing`, `feedback_agent_at_target` MEMORY 신설)
- **관련**: `dev_plan/2026_04/22/manage-datasource-header-routing.md`

---

## 6. 관련 문서 / 메모리

### 메모리
- `feedback_agent_at_target` — 파이프라인 Agent 의 target DB = 자기 JPA 기본 datasource = 관리 DB 규약
- `project_header_manage_routing` — Proxy 관리 DB 조회 라우팅 = `X-Manage-Datasource-Id` 헤더 + `Agent.targetDatasourceId`
- `feedback_config_vs_registration` — yml DB 등록 ID 하드코딩 금지, Orchestrator DB 등록이 단일 진실원

### 파생 invariant (본 규약에 의존)
- `header-routing.md` — 본 규약을 Proxy 라우팅 레벨에서 구현한 것 (⚠️ 아직 미작성)
- `trace-source-branches.md` — source_refs 역추적도 target DB 가 관리 DB 라는 전제 (⚠️ 아직 미작성)

### 계획 / 검증 기록
- `dev_plan/2026_04/22/manage-datasource-header-routing.md` — 규약 확정 맥락
- `dev_logs/2026_04/2026-04-22.md` — 헤더 라우팅 구현 일지

### 작업 공간
- `verify/_invariants/00-overview.md § 3` — 카테고리 3 (설정 / 단일 진실원)
- `verify/runs/` — 각 검증 실행마다 § 4 현황표 업데이트 반영
