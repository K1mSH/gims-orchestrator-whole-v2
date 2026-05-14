# Agent 시스템 관리 테이블 Retention 확장 계획

작성일: 2026-05-14
대상 사각지대: `sync_log` (1차) + 향후 동일 패턴의 Agent 관리 테이블

> ⚠️ 본 문서는 **계획서**다. 사용자 검토 전에는 코드/yml 수정 일체 금지.
> ([[feedback_plan_not_approval]])

---

## 1. 배경 — 왜 이 작업이 필요한가

현행 retention 체계 (5/8 `retention-candidates-safety.md` 정착) 는 **Agent 의 비즈니스 target/source 테이블** 만 다룬다:

- yml `retention-candidates` 에 (table, dateColumn) 화이트리스트
- 4 layer 검증 (yml ↔ Frontend ↔ Backend PUT ↔ Agent Controller)
- Orchestrator DB `agents.retention_config` 에 운영자 입력 정책 저장
- DataRetentionScheduler (cron `0 0 2 * * *`) 가 `/api/cleanup/{agentCode}` 호출

**누락 구간**: Agent 가 자기 메타 DB 에 쓰는 **관리 테이블** — 대표적으로 `sync_log`. 이 테이블은:

- 어떤 Agent 의 target 도, source 도 아님 → 현행 retention-candidates 화이트리스트에 등재 불가
- 그러나 Agent 실행마다 row 가 누적 (per-mapping 단위, [[project_verify_system]])
- tracing(`/trace-source`), 처리현황 화면, 회귀 검증의 근거 데이터 → 무한 누적은 운영 부담

같은 패턴의 다른 후보 (현재는 sync_log 만, 단 메커니즘은 일반화):
- 향후 audit / 실행 step error log / soft-delete tombstone 등 추가 시 동일 채널 재사용

---

## 2. 결정된 방향 — 옵션 A (Agent yml 확장) 채택

대화 합의 흐름:
- "DB 관리 메뉴 분리"(완전 분리) → 정책이 흩어지면 운영자 인지 부하 ↑, 분리 실익 적음
- "datasource 별 yml 분리" → Agent 추가/삭제 동기화 부담
- ✅ "Agent yml 에 system-tables 섹션 추가" → 기존 4 layer 검증 / Scheduler / cleanup endpoint 재사용, 가장 가벼움

추가 결정 포인트 (이 문서에서 사용자 확인 필요):
- (Q1) yml 키 명칭 — `system-retention-candidates` vs `meta-retention-candidates` vs 통합 (`retention-candidates` + `type: system`)
- (Q2) sync_log 같은 **모듈 공유 테이블** 의 중복 청소 방지 전략 (4 절 참고)
- (Q3) Orchestrator DB 측 관리 테이블 (execution 등) retention 은 본 작업 범위에 포함? → 본 계획서는 **Agent sync_log 만** 으로 한정 제안

---

## 3. 코드 구조 변경 (요약)

### 3-1. yml 스키마 확장

각 Agent yml 에 신규 섹션 추가 (예시):

```yml
# 기존 (그대로)
retention-candidates:
  - table: PM_GD111021
    dateColumn: OBSRVN_DT
    description: 이용량 시간자료 (GIMS 통합)

# NEW — 관리 테이블 후보
system-retention-candidates:
  - table: sync_log
    dateColumn: created_at
    description: 동기화 처리 로그 (매핑 단위)
    shared: true   # 같은 datasource 공유 — 중복 청소 회피용 마크
```

`shared: true` 는 **같은 `targetDatasourceId`** 를 쓰는 Agent 들 사이에서 **첫 번째 Agent 만 실제 청소** 하도록 Scheduler 가 dedup 처리.

대안 (Q1 통합형):
```yml
retention-candidates:
  - table: PM_GD111021
    dateColumn: OBSRVN_DT
    type: business        # default — 생략 가능
  - table: sync_log
    dateColumn: created_at
    type: system
    shared: true
```

→ **추천**: 분리형 (`system-retention-candidates`). 운영 화면에서 분리 표시 / Frontend dropdown 그룹핑 / 4 layer 검증 시 system-만 별도 set 으로 처리 → 비즈니스 후보 검증 로직과 결합도 ↓.

### 3-2. 코드 변경 파일

| 파일 | 변경 내용 |
|------|----------|
| `infolink-agent-common/.../config/RetentionConfig.java` | `private List<TableRetention> systemTargets = new ArrayList<>();` 필드 추가 |
| `infolink-agent-common/.../model/RetentionCandidate.java` | `private boolean shared;` 필드 추가 (기본 false). business 후보엔 의미 없으나 단일 모델 유지 |
| `infolink-agent-common/.../service/RetentionCandidatesProvider.java` | `List<RetentionCandidate> getSystemCandidates(String agentCode);` 추가 |
| 각 Agent 모듈의 `RetentionCandidatesProvider` 구현체 | yml `system-retention-candidates` 파싱 + 노출 |
| `infolink-agent-common/.../controller/DataRetentionController.java` | `systemTargets` 도 4 layer 검증 (system candidates 화이트리스트). 검증 후 `executeCleanup` 두 번 호출 또는 통합 처리 |
| `infolink-agent-common/.../service/DataRetentionService.java` | 변경 없음 권장 (테이블 단위 DELETE 동일). 호출처에서 두 list 를 합쳐 전달하거나 두 번 호출 |
| `infolink-orchestrator-backend/.../scheduler/DataRetentionScheduler.java` | (a) `systemTargets` 도 retention_config JSON 읽어 inject, (b) **shared dedup** — 같은 (`endpointUrl` × `targetDatasourceId` × `table`) 조합은 첫 Agent 1건만 systemTargets 포함하여 호출, 나머지 Agent 호출 시 systemTargets 제거 |
| Orchestrator API (`/api/agents/{id}/retention`) | PUT body 검증에 `systemTargets` 항목 추가, system candidates 외 거부 |
| Frontend retention 등록 화면 | dropdown 에 "관리 테이블" 그룹 추가, system row 표시 분리, shared 마크 시 "이 datasource 의 다른 Agent 와 공유" 안내 노출 |

### 3-3. yml 적용 범위 (1차)

`sync_log` 는 모든 Agent 가 자기 메타 DB 에 쓰므로 **전 Agent yml 추가 대상**. 다만:

- DMZ Agent (bojo-dmz / others-dmz / provide-dmz) → DMZ PG (`dmz` datasource) 공유 → **하나의 Agent 에서만 cleanup 실행** (shared dedup)
- Internal Agent (bojo-internal — 다중 논리 Agent) → Internal PG (`internal` datasource) 공유 → 동일

운영 단순화: `sync_log` 에 한해 **common 에 default system candidate 를 제공** 하여 yml 누락을 방지하는 것도 검토 (단, 일관성 위해 명시 등재 권장 — 모든 yml 에 같은 6줄 반복).

---

## 4. 운영 모델 — shared 테이블 중복 청소 방지

문제: DMZ 모듈 1개에 12 Agent. 12개가 각자 같은 `sync_log` 를 청소하면 첫 Agent 가 정리 → 나머지 11개는 0건 (idempotent 안전, 단 IO 낭비 + lock 충돌 가능).

해결 옵션:

- **(A) Scheduler 측 dedup (추천)**: `targetDatasourceId × table × dateColumn` 키로 그룹화, 첫 Agent 의 호출에만 systemTargets 포함. 나머지 호출 시 system 부분 제거하고 비즈니스 target 만 전송.
  - 장점: Agent 측 코드 변화 없음, 단일 소스에서 dedup
  - 단점: Scheduler 로직 복잡도 ↑
- **(B) Agent 측 advisory lock**: PG `pg_try_advisory_xact_lock` 으로 같은 datasource 의 sync_log cleanup 직렬화
  - 장점: Scheduler 변경 최소
  - 단점: PG 전용 (Oracle 호환 별도 처리 필요), 디버깅 난이도 ↑
- **(C) 그대로 두기 (idempotent 신뢰)**: 11번의 중복 cleanup 모두 0건 → 무해
  - 장점: 코드 변화 없음
  - 단점: 새벽 2시 대량 동시 DELETE 로 lock contention 가능성, 로그 노이즈

→ **추천 (A)**. (Q2) 는 사용자 결정.

---

## 5. 리스크 + 완화책

| # | 리스크 | 완화 |
|---|--------|------|
| R1 | sync_log retention 으로 인한 **trace 추적 가능 기간 제한** | 운영자 retention 일수 결정 시 UI 에 "이 일수 이전 execution 은 trace-source 불가" 안내. 보존일 권장 default 제안 — **90일** (현행 대수성/장기 검증 사이클 기준, 사용자 확인 필요) |
| R2 | sync_log 청소 중 새 sync_log INSERT 충돌 | DELETE 는 row-level lock — 신규 INSERT 와 충돌 X. 새벽 2시 cron 도 의도. 추가 조치 불필요 |
| R3 | shared 테이블 중복 청소 | 4 절 dedup 적용 |
| R4 | 4 layer 검증 확장 시 기존 비즈니스 검증 회귀 | system 후보는 **별도 Set** 으로 분리 검증 — 비즈니스 후보 검증 로직은 그대로 유지. ([[feedback_no_regression_organic]]) — 모든 Agent 케이스 (RCV / Loader / SND / 비대상 빈 배열) 테스트 |
| R5 | Orchestrator DB execution / audit 도 무한 누적 | **본 작업 scope 외**. 후속 계획에서 별도 처리 (Orchestrator 가 자기 cron + JdbcTemplate 으로 자기 DB 청소 — Agent 와 무관) |
| R6 | api-collector / api-provider / auth 등 다른 모듈도 sync_log 기록? | 확인 필요 — 없으면 본 작업으로 충분, 있으면 모듈별 동일 메커니즘 추가. 1차에선 Agent 모듈 4개 (bojo-dmz / bojo-internal / others-dmz / provide-dmz) 만 |
| R7 | 운영자 입력 누락 → sync_log 영원히 안 청소 | scheduler 가 system candidates 가 있는 Agent 만 골라 retention_config 빈 항목 채워주는 default policy 도 검토 가능. 단, 단일 진실원 깨짐 → 1차에선 운영자 명시 입력만 |
| R8 | retention_config JSON 스키마 변경 → 기존 저장값 backward-compat | 기존 `targets` 키 그대로 유지, `systemTargets` 신규 추가만. 기존 row 는 systemTargets 없음 → 자연스럽게 비활성. 마이그레이션 불필요 |

---

## 6. 단계 (Phase)

코드 변경 진행 전 단계별 합의:

- **Phase 0** — 본 계획서 검토 / Q1·Q2·Q3 결정 (이 단계, 사용자 응답 대기)
- **Phase 1** — common 측 변경
  - RetentionConfig / RetentionCandidate / RetentionCandidatesProvider 인터페이스 확장
  - DataRetentionController 4 layer 검증 확장
  - common 빌드 + 9개 Agent 모듈 libs/ 에 JAR 복사
- **Phase 2** — Agent 모듈 측 변경
  - 각 모듈 RetentionCandidatesProvider 구현체에 system 후보 노출
  - 각 Agent yml 에 `system-retention-candidates: [{table: sync_log, dateColumn: created_at, shared: true}]` 추가
  - 모듈 빌드 + bootRun 기동 테스트
- **Phase 3** — Orchestrator 측 변경
  - DataRetentionScheduler dedup + systemTargets inject
  - PUT 검증 확장
  - bootRun 테스트
- **Phase 4** — Frontend
  - retention 등록 화면 dropdown 에 "관리 테이블" 그룹 + shared 안내
  - npx tsc --noEmit 후 화면 검증 ([[feedback_one_page_at_a_time]])
- **Phase 5** — 통합 검증
  - DMZ Agent 12개 + Internal Agent 다수에서 sync_log 가 **중복 청소 없이 1번만** 실행되는지 로그 확인
  - 4 layer 검증 회귀 테스트 (기존 retention-candidates 입력 / system 후보 외 입력 / 빈 배열 Agent 등)
  - dev_logs/2026_05/14.md 갱신
  - verify/ 영향 범위 — `_invariants` 항목 추가 (sync_log retention dedup 보장)
- **Phase 6** — Jira 동기화 ([[feedback_jira_sync]]) + git 커밋 (사용자 명시 요청 시에만)

각 Phase 종료 후 사용자 보고 / 다음 Phase 진입 명시 승인.

---

## 7. 사용자에게 결정 요청 (Phase 0)

- **Q1** yml 키 명칭 — 분리형 (`system-retention-candidates`, 추천) vs 통합형 (`type: system` 마크)?
- **Q2** shared 테이블 중복 청소 방지 — Scheduler dedup (A, 추천) / advisory lock (B) / 그대로 (C) 중?
- **Q3** Orchestrator DB execution 등 본 작업 범위 포함? (제안: 본 계획서는 **Agent sync_log 만**, execution 은 후속 별도 계획)
- **Q4** sync_log 보존일 default 권장값 — **90일** 제안. 운영자 입력 없을 시 default 적용? 아니면 명시 입력 강제?

---

## 8. 본 작업이 건드리지 않는 것 (scope 명시)

- ([[feedback_no_scope_creep]]) 본 작업은 **Agent 의 sync_log retention 확장** 만.
- 다음은 명시적 제외:
  - Orchestrator DB execution / audit retention
  - api-collector / api-provider / auth 등 비-Agent 모듈의 자체 관리 테이블
  - Retention 운영 화면의 "DB 관리 메뉴 신설" (기존 화면 dropdown 확장만)
  - sync_log 외 신규 관리 테이블 등록 (메커니즘만 일반화, 실제 등재는 sync_log 만)
  - WHERE 매트릭스 확장 (system 후보엔 where-filters 무관 — 단순 cutoff DELETE)

---

## 9. 참고

- 단일 진실원 정책: `dev_plan/2026_05/08/retention-candidates-safety.md`
- 매트릭스: `docs/agent-retention-where.md`
- 메모리 키워드: [[feedback_retention_yml_target_only]] (이중 설정 방지 — 본 확장은 **별도 채널이라 충돌 없음**), [[feedback_agent_at_target]] (Agent target = 자기 JPA datasource = sync_log 위치), [[project_verify_system]] (검증 데이터로 sync_log 활용)
