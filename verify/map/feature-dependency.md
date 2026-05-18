# 파트 의존 맵

> 목적: 수정 범위 → 영향 받는 모듈/기능 파악 → 병렬 가능 여부 판단.
> 갱신 시점: 파트 추가/제거, 의존 관계 변화, 새 공유 규약 등장.

---

## 1. 파트 목록

| ID | 파트 | 위치 | 수정 시 영향 |
|----|------|------|--------------|
| P1 | **infolink-agent-common** | `infolink-agent-common/` | 모든 Agent JAR 재배포 + **전 파이프 회귀** |
| P2 | infolink-proxy-dmz (8083) | `infolink-proxy-dmz/` | DMZ 파이프 전체 (bojo, others) |
| P3 | infolink-proxy-internal (8093) | `infolink-proxy-internal/` | Internal 파이프 전체 (bojo-internal, provide) + **관리 DB 헤더 라우팅** |
| P4 | infolink-agent-bojo-dmz (DMZ, 8082) | `infolink-agent-bojo-dmz/` | DMZ 10 RCV + Loader + SND |
| P5 | infolink-agent-others-dmz (DMZ, 8085) | `infolink-agent-others-dmz/` | 기타 Agent 해당 모듈만 |
| P6 | infolink-agent-bojo-internal (Internal, 8092) | `infolink-agent-bojo-internal/` | Internal RCV + Loader |
| P7 | infolink-agent-provide (Internal, 8096) | `infolink-agent-provide/` | provide 파이프 (Oracle 29004 → PG 29006) |
| P8 | infolink-api-collector (8084/8094) | `infolink-api-collector/` | 독립 모듈 (api_collector DB) |
| P9 | infolink-api-provider (8095) | `infolink-api-provider/` | 독립 읽기 모듈 (PG 29006) |
| P10 | **infolink-orchestrator-backend** (8080) | `infolink-orchestrator-backend/` | 전 Agent 통신 → **광범위 회귀** |
| P11 | infolink-orchestrator-frontend (3000) | `infolink-orchestrator-frontend/` | UI 만 (백엔드 영향 없음) |
| P12 | DDL 스크립트 | `scripts/ddl/` | 해당 DB 사용 Agent |
| P13 | Agent YAML 설정 | `*/src/main/resources/config/agents/` | 해당 Agent 만 (병렬 안전) |

---

## 2. 공유 규약 (수정 시 영향 광범위)

| 규약 | 정의 위치 | 영향 파트 |
|------|----------|----------|
| 관리 DB 헤더 `X-Manage-Datasource-Id` | P1 (ExecutionDataController), P3 (Proxy 라우팅), P10 (ExecutionService) | P1 + P3 + P10 동시 수정 필요 |
| Source 추적 3단계 분기 (buildSourceFilter) | P1 (ExecutionDataController) | P1 변경 → 모든 Agent 재배포 |
| ExecutionId 인덱스 (IF/Target 6개 엔티티) | P1 entity + P12 DDL | P1 + P12 |
| SyncLog per-mapping 규약 (mappingName/sourceTables/targetTables/readCount/writeCount) | P1 | P1 변경 → 모든 Agent 재배포 |
| Loader 모드별 Step 교체 (PipelineRegistry 복합키) | P1 + 각 Agent 의 LoaderPipelineConfig | P1 + (P4 or P6 or P7) |
| Source 추적 patterns (source_pk_column/source_refs/merge-key) | P1 + 각 Step 구현체 | P1 + 해당 Step 보유 Agent |

---

## 3. 겹침 매트릭스 (Rule of thumb)

`X` = 겹침 (병렬 위험, 동시 수정 시 충돌/회귀 위험) / `·` = 병렬 안전

|        | P1 | P2 | P3 | P4 | P5 | P6 | P7 | P8 | P9 | P10 | P11 |
|--------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:---:|:---:|
| **P1** |  - | X  | X  | X  | X  | X  | X  | ·  | ·  |  X  |  ·  |
| **P2** |  X | -  | ·  | X  | X  | ·  | ·  | ·  | ·  |  X  |  ·  |
| **P3** |  X | ·  | -  | ·  | ·  | X  | X  | ·  | ·  |  X  |  ·  |
| **P4** |  X | X  | ·  | -  | ·  | ·  | ·  | ·  | ·  |  X  |  ·  |
| **P5** |  X | X  | ·  | ·  | -  | ·  | ·  | ·  | ·  |  X  |  ·  |
| **P6** |  X | ·  | X  | ·  | ·  | -  | ·  | ·  | ·  |  X  |  ·  |
| **P7** |  X | ·  | X  | ·  | ·  | ·  | -  | ·  | X  |  X  |  ·  |
| **P8** |  · | ·  | ·  | ·  | ·  | ·  | ·  | -  | ·  |  X  |  ·  |
| **P9** |  · | ·  | ·  | ·  | ·  | ·  | X  | ·  | -  |  X  |  ·  |
| **P10**|  X | X  | X  | X  | X  | X  | X  | X  | X  |  -  |  X  |
| **P11**|  · | ·  | ·  | ·  | ·  | ·  | ·  | ·  | ·  |  X  |  -  |

### P13 (Agent YAML) 규칙
- 서로 다른 Agent YAML 수정은 항상 **독립** (P13끼리 `·`).
- 단, **같은 Agent** 의 YAML 을 두 세션이 동시에 고치면 당연히 겹침.
- YAML 이 가리키는 DB 등록 ID 는 Orchestrator(P10)에서 관리 — 신규 DB 등록 필요 시 P10 합의 필요.

---

## 4. 병렬 안전 판단 예시

| 시나리오 | 판정 | 근거 |
|---------|:----:|------|
| forward: provide 신규 YAML 추가 (P7 config, P13) / parallel: DMZ bojo YAML 수정 (P4 config, P13) | ✅ 안전 | 다른 YAML |
| forward: common ExecutionDataController 수정 (P1) / parallel: bojo-internal 로직 수정 (P6) | ❌ 겹침 | P1 변경 시 JAR 재배포 + 모든 Agent 회귀 필요 |
| forward: api-collector 기능 추가 (P8) / parallel: provide 테이블 추가 (P7 YAML + P12 DDL) | ✅ 안전 | 독립 모듈 + 독립 DB |
| forward: Oracle DDL 추가 (P12) / parallel: provide 엔티티 추가 (P7) | ⚠️ 순서 의존 | DDL 선행 후 엔티티 검증 — 순차 |
| forward: Orchestrator API 변경 (P10) / parallel: frontend 호출부 수정 (P11) | ⚠️ 계약 공유 | 같은 API 시그니처 건드리면 겹침, 다른 API 면 독립 |
| forward: provide Step 로직 수정 (P7) / parallel: provide DDL 수정 (P12) | ❌ 겹침 | 같은 Agent 의 다른 레이어 — 동시 배포 필요 |

---

## 5. 파트 → 체크리스트 매핑

수정된 파트에 따라 **반드시 돌려야 할 체크리스트**.

| 수정 파트 | 필수 체크리스트 |
|----------|---------------|
| P1 (common) | 모든 `checklists/*` — **전 파이프 회귀** |
| P2 (DMZ Proxy) | bojo, others |
| P3 (Internal Proxy) | bojo-internal, provide, trace-lifecycle |
| P4 (bojo) | bojo-rcv, bojo-loader, bojo-snd |
| P5 (others) | others |
| P6 (bojo-internal) | bojo-internal, trace-lifecycle |
| P7 (provide) | provide-agent, trace-lifecycle |
| P8 (api-collector) | api-collector |
| P9 (api-provider) | api-provider |
| P10 (Orchestrator backend) | orchestrator-api, 전 파이프 스모크 테스트 |
| P11 (frontend) | frontend-smoke |
| P12 (DDL) | 해당 DB 를 쓰는 Agent 체크리스트 |
| P13 (YAML) | 해당 Agent 체크리스트 |

> 체크리스트 자체는 `checklists/` 에서 관리. 추가/갱신 시 본 표 갱신.
