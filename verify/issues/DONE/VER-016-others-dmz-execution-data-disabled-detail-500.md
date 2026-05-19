---
id: VER-016
title: agent-others-dmz `execution-data.enabled: false` — detail/source/target/trace API 전체 500
status: CLOSED
created: 2026-05-19
updated: 2026-05-19 (closed)
parts: [agent-others-dmz, tracing, execution-data-controller, frontend-detail]
parallel_safe: true
assignee: forward
related: [sync-log-target-rule-fix, sync-log-rollback-to-local]
---

## Closure (2026-05-19 오후)

`sync-log-rollback-to-local` 사이클 Phase A~F 후 회귀 검증 통과:
- others-dmz `execution-data.enabled: true` 변경 (Phase C) — controller bean 활성화
- agent.history_datasource_id 컬럼 + 자동 등록 흐름 (Phase F)
- backend `buildHeaders` 가 history_datasource_id 헤더 송신
- proxy ProxyDataSourceService.null guard + 동적 datasource 라우팅

검증 결과:
- 새올 detail/source/target/trace 모두 정상 ✅
- provide-tm-gd000203 detail 정상 ✅
- dmz-bojo-loader detail 정상 ✅

부수 발견 (별 트랙):
- 4 agent 모듈 (others-dmz/bojo-dmz/bojo-internal/provide) 의 yml 변경 후 재기동 — 새 agent 등록 시 health 응답에 historyDatasourceId 노출 필요. 본 사이클 통과 후속 작업.
- frontend 등록 폼에 historyDatasourceId readonly 표시 — 별 트랙.

---

## 발견 경로

5/19 sync-log-target-rule-fix 회귀 검증 시나리오 3 (`dmz-others-snd-saeol` 수동 실행) 후 프론트 detail 화면 진입 시 모든 데이터 패널 500.

본 fix (sync_log 위치 = agent.target 룰 일관 통용) 효과 자체는 통과 — saeol-Oracle SYNC_LOG 16 row 박힘, 처리현황 화면 표시 OK. detail 500 은 본 fix 와 무관한 **별 트랙 기존 이슈**.

## 증상

- 화면: `/agents/dmz-others-snd-saeol` → 실행이력 row 클릭 → detail/source/target/trace 패널 모두 500
- backend 로그:
  ```
  HttpServerErrorException$InternalServerError: 500 :
    /api/execution-data/dmz-others-snd-saeol_<uuid>
  ```
- backend → agent 호출 endpoint:
  - `/api/execution-data/{execId}`
  - `/api/execution-data/{execId}/source`
  - `/api/execution-data/{execId}/target`
  - `/api/execution-data/{execId}/trace`
  - `/api/execution-data/{execId}/trace-source`

## 원인

`infolink-agent-others-dmz/src/main/resources/application.yml:29`:

```yaml
common:
  controller:
    execution-data.enabled: false   # ❌ ExecutionDataController bean 자체 비활성
```

→ `@ConditionalOnProperty(name = "common.controller.execution-data.enabled", havingValue = "true")` 조건 미충족 → bean 안 뜸 → 위 모든 endpoint 부재 → 500.

비교 (5 모듈 모두 `true`):
| 모듈 | `execution-data.enabled` |
|------|:--:|
| infolink-agent-bojo-dmz | true |
| infolink-agent-bojo-internal | true |
| infolink-agent-provide | true |
| infolink-proxy-dmz | true |
| infolink-proxy-internal | true |
| **infolink-agent-others-dmz** | **false** ← 단독 |

→ others-dmz 의 5개 agent (yaksoter / jeju / use / api-collect / saeol) 전부 detail/source/target/trace API 영향. 5/14 이전부터 동일 상태였을 가능성 높음 (회귀 아님).

## 부수 발견 — execution 행 미박힘

dev PG `execution` 테이블에 **others-dmz 의 어떤 agent 도 행 없음**:

```
SELECT agent_id, COUNT(*) FROM execution GROUP BY agent_id;
 internal-bojo-loader |    30
 internal-bojo-rcv    |    24
(2 rows)
```

agent 로그상 ExecutionService 가 JPA `insert into execution` 했다고 INFO 박혀있지만 dev PG / saeol-Oracle 어디에도 행 없음. **트랜잭션 commit 실패** 또는 **JPA 가 잘못된 datasource 로 라우팅** 의심. ExecutionDataController 가 살아있어도 execution 행 없으면 detail 빈 응답.

agent-others-dmz JPA datasource 가 dev PG (ENC 풀면 jdbc:postgresql://localhost:29001/dev) 인데 행이 없음 → 다른 데이터소스로 가거나 rollback. 별도 조사 필요.

## 처리 방향 후보

**A. execution-data.enabled: true 로 단순 변경 + 부수 발견 조사**
- 1줄 yml 수정. but false 박은 이유 모름 (보안? 작업 누락? 의도된 비활성?) — git blame 으로 추적 후 결정.
- + execution 행 미박힘 근본 원인 조사 (트랜잭션? routing?). controller 가 살아도 execution 0건이면 여전히 동작 안 함.

**B. 의도된 비활성으로 유지하고 backend 가 우회 처리**
- backend `ExecutionService.fetchAgentExecutionData` 가 다른 endpoint 로 라우팅. but tracing 자체가 ExecutionDataController 책임이라 우회 어려움.

**C. saeol/yaksoter 등 others 모듈 분리 (각자 별도 agent 앱)**
- 큰 리팩토링. 본 fix 와 무관.

## 추적

- 시작: 2026-05-19 회귀 검증 중 발견
- 본 fix (sync-log-target-rule-fix) 와 무관 — 별 트랙
- 5/19 dev_log §메모 박음 + 본 issue 등재
- 후속 결정 보류 (사용자 결정 대기)
