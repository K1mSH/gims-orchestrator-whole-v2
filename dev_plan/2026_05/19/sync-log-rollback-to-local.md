# sync_log + execution 모두 local 박기 (= 어제 fix 부분 롤백)

> 2026-05-19 오후 — 본 사이클 계획서 (코드 변경 X, 사용자 승인 대기)
> 어제(2026-05-19 오전) `sync-log-target-rule-fix.md` 계획서/commit (`d90d415`) 의 후속 — 부분 롤백
> 본 세션 추가 조사로 어제 fix 의 본질이 잘못된 방향이었음 확인

---

## 1. 어제 fix 의 잘못된 방향 본질

### 1-1. 어제 박힌 룰
- "sync_log 적재 위치 = agent.target_datasource_id"
- 광역 변경: common SyncLogWriter 시그너처 (SyncLogRepository → DataSourceProvider) + 9 호출처 + backend resolveManagementDatasource = target

### 1-2. 본 세션 발견 — 사실 잘못된 룰

**검증 매트릭스**:

| Agent 그룹 | JPA primary (실제 INSERT 위치 — `execution` 행이 있는 곳) | target_datasource_id |
|---|---|---|
| dmz-bojo-* / dmz-others-snd-{yaksoter,jeju,use,api-collect} | **dmz container dev DB (29002)** | `dmz` |
| **dmz-others-snd-saeol** | dmz container dev DB (29002) ← agent JPA | `saeol-oracle` ← 어긋남 |
| internal-bojo-* | inner_common dev DB (29001) | `internal` |
| provide-* | api_provider PG (29006) | `api-provider` |

→ **`execution` 은 항상 agent JPA primary 에 박힘**. 어제 fix 가 sync_log 만 target 으로 옮긴 게 분리 발생 원인.

### 1-3. 어제 fix 후 회귀 (본 세션 발견)

- `sync_log` (어제 fix 후): saeol-Oracle 16 row ✅
- `execution` (어제 fix 와 무관, 그대로): **dmz container dev DB** (29002)
- backend `resolveManagementDatasource = target` → `X-Manage-Datasource-Id = saeol-oracle`
- ExecutionDataController 가 mgmtJdbc=saeol-Oracle 에서 execution 찾음 → **없음 → 500**

→ 어제 fix 가 detail/source/target/trace API 의 500 회귀를 일으킴. `dmz-others-snd-saeol` 한 케이스에서만 발견 (다른 agent 는 target == local 이라 우연히 동작).

### 1-4. 진짜 정공

**룰**: `sync_log + execution 모두 local (= agent JPA primary)` 박기. 36 agent 일관.

- agent JPA 가 자동으로 모든 entity 를 primary datasource 에 박음 → sync_log 도 JPA save 로 박으면 자동 local
- backend X-Manage 헤더 안 보냄 → agent ExecutionDataController 가 자동 fallback (defaultJdbcTemplate = JPA primary)
- 새올의 경우도 sync_log + execution 모두 dmz container dev DB → agent 가 자기 박은 곳 자기 봄 → detail 동작

→ 어제 fix 의 SyncLogWriter 동적 JDBC 우회 = 사실 불필요. JPA 표준 + backend 헤더 제거하면 끝났을 일.

---

## 2. 본 사이클 목표

1. sync_log INSERT 메커니즘 = **JPA save 로 복귀** (어제 SyncLogWriter 변경 12 파일 mirror 롤백)
2. backend `resolveManagementDatasource` = **null 반환** (또는 메서드+헤더 송신 자체 제거)
3. others-dmz `execution-data.enabled = true` (3/31 모듈 초기 누락 fix — VER-016)
4. saeol-Oracle `SYNC_LOG` 테이블 + DDL 파일 drop (무용지물)
5. agent_code 작명 규약 문서화 — 향후 안전 차원 (현 옵션 i 진행 시 routing 영향 없음, 단 prefix 가 module 구분 의미 유지 차원)
6. 회귀 검증 시나리오 통과 (saeol 처리현황 + detail/source/target/trace 동작)
7. VER-016 closure 확정

---

## 3. 정공 메커니즘 — fallback 활용

ExecutionDataController 의 `mgmtJdbc` 가 이미 fallback 메커니즘 보유:

```java
@RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId
JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
```

`SyncDataSourceService.getJdbcTemplate` 의 3단계 fallback (line 128-130):

```java
// 3. Spring 기본 DataSource fallback (Agent 로컬 DB = IF/Target DB)
log.debug("[Others] 데이터소스 '{}' 해석 불가, 기본 JdbcTemplate 사용 (로컬 DB)", datasourceId);
return defaultJdbcTemplate;
```

→ **`manageDsId=null` 이면 자동으로 `defaultJdbcTemplate` (= agent JPA primary 의 JdbcTemplate) 사용**.

따라서:
- backend 가 X-Manage 헤더 안 보냄 → manageDsId=null → defaultJdbcTemplate 사용 → agent JPA primary 에서 조회
- agent JPA primary = sync_log + execution 박은 곳 (JPA save 결과)
- 36 agent 일관 동작

**prefix 매핑 휴리스틱 / agent_code 기반 추론 불필요**. 사용자 직관 정확.

---

## 4. 변경 매트릭스 — Phase A ~ E

### Phase A. 어제 fix 코드 mirror 롤백 (12 파일)

| 파일 | 어제 변경 | 본 사이클 (mirror) |
|------|----------|-------------------|
| `infolink-agent-common/.../sync/SyncLogWriter.java` | 시그너처 (DataSourceProvider) + 내부 동적 JDBC INSERT | **시그너처 (SyncLogRepository) + JPA save 로 복귀** |
| `infolink-agent-common/.../step/SourceToTargetStep.java` | 호출 인자 (dataSourceProvider) | **(syncLogRepository) 로 역변경** |
| `infolink-agent-bojo-dmz/.../loader/step/LoaderStepHelper.java` | DataSourceProvider 필드 추가 + 호출 치환 | **필드 제거 + 원래 syncLogRepository 호출 복원** |
| `infolink-agent-bojo-internal/.../{InternalBojo,Simple,Use,JejuFacility,JejuJewon,JejuObsvdata}LoadStep.java` (6) | 호출 인자 치환 | **모두 역변경** |
| `infolink-agent-others-dmz/.../snd/step/SaeolLinkPlanSndStep.java` | 직접 SyncLog.builder 패턴 → SyncLogWriter 통합 | **직접 패턴 복원** (100% mirror) |
| 9 모듈 `libs/infolink-agent-common-1.0.0-SNAPSHOT.jar` | 어제 변경 결과 | **본 사이클 변경 결과로 재복사** |

### Phase B. backend resolveManagementDatasource 메서드 제거

| 파일 | 변경 |
|------|------|
| `infolink-orchestrator-backend/.../service/ExecutionService.java` | `resolveManagementDatasource` 메서드 제거 + `buildHeaders` 에서 X-Manage 헤더 송신 코드 제거 (헤더 안 보냄). `buildHeaders` 자체 단순화 또는 제거 가능. |

→ agent 가 manageDsId=null 받음 → defaultJdbcTemplate fallback → JPA primary 자동 사용.

### Phase C. others-dmz enabled=true + saeol-Oracle SYNC_LOG drop

| 항목 | 작업 |
|------|------|
| `infolink-agent-others-dmz/src/main/resources/application.yml:29` | `execution-data.enabled: false` → `true` |
| saeol-Oracle (29005) SYNC_LOG 테이블 | DROP TABLE |
| `scripts/ddl/saeol-tibero/sync-log.sql` | 파일 삭제 (운영 Tibero 미적용이라 안전) |
| `dev_plan/2026_05/19/sync-log-target-rule-fix.md` | 유지 (어제 계획서 — 잘못된 방향 기록도 가치) |
| `verify/issues/OPEN/VER-016-...md` | 본 사이클 통과 시 CLOSE 이동 |

### Phase D. 문서 + MEMORY 갱신

| 파일 | 변경 |
|------|------|
| `docs/dev_guideline/AGENT_YAML_GUIDE.md` | `agent-code` 작명 규약 섹션 신규 (prefix = dmz/internal/provide module 구분 의미, 작명 규약 어기지 말 것 권장 — 본 fix 후 routing 에 영향은 없으나 module 가시성 차원) |
| `MEMORY.md` + `docs/claude-memory/` | 신규 [[feedback_sync_log_execution_local]] — sync_log + execution = agent JPA primary (= local) 박힘 룰. backend X-Manage 헤더 안 보내고 fallback 활용. |
| `dev_logs/2026_05/2026-05-19.md` | 오후 진입 + 본 사이클 진행 결과 추가 |
| `verify/issues/CLOSED/VER-016-...md` | 본 사이클 통과 시 OPEN → CLOSED 이동 |

### Phase E. 빌드 + 부팅 + 회귀 검증

1. common clean build
2. JAR 9 모듈 libs/ 복사
3. 9 모듈 clean build (사용자 룰 — 1 by 1 부팅, [[feedback_service_boot_one_by_one]])
4. saeol-Oracle SYNC_LOG drop 실행
5. dmz container dev DB 의 saeol execution 행 정리 여부 결정 (회귀 검증 클린업) — 사용자 결정 필요
6. 새올 SND 수동 실행 (LINK_PLAN_CURSOR=0 reset 후)
7. 회귀 검증 — sync_log + execution 둘 다 dmz container dev DB 박힘 + detail/source/target/trace API 동작 ✅

---

## 5. 영향 범위 검증 (다른 35 agent 깨짐 방어)

| Agent | 어제 fix 후 sync_log 위치 | 본 사이클 후 위치 | 변경 |
|-------|--------------------------|------------------|:-:|
| dmz-bojo-* (12) | dmz container dev DB (target=dmz, module default 일치) | dmz container dev DB (JPA primary) | ✅ 동일 |
| dmz-others-snd-{yaksoter,jeju,use,api-collect} (4) | dmz container dev DB | dmz container dev DB | ✅ 동일 |
| **dmz-others-snd-saeol** | **saeol-Oracle (어제 fix)** | **dmz container dev DB** | ❌ **변경** (어제 fix 효과 무효화) |
| internal-bojo-* (2) | inner_common dev DB | inner_common dev DB | ✅ 동일 |
| internal-{jeju,use,saeol,yaksoter,api-collect}-* (8) | (해당 agent JPA primary) | (해당 agent JPA primary) | ✅ 동일 |
| provide-* (8) | api_provider PG | api_provider PG | ✅ 동일 |

→ **새올 1 agent 만 위치 변경** (= 어제 fix mirror). 다른 35 agent 위치 변경 0.

### 검증 클린업 (saeol-Oracle 16 row sync_log)

drop 시 16 row 손실. 모두 어제 회귀 검증용 테스트 데이터 (운영 의미 없음). drop OK.

---

## 6. 회귀 검증 시나리오

| # | 시나리오 | 기대 |
|---|---------|------|
| 1 | 새올 SND 수동 실행 (LINK_PLAN_CURSOR=0 reset 후) | dmz container dev DB 의 sync_log + execution 둘 다 신규 row 박힘 ✅ |
| 2 | 새올 처리현황 화면 | sync_log row 표시 ✅ |
| 3 | **새올 detail/source/target/trace 화면** | **500 → 정상 표시 ✅** (본 사이클 핵심 회복) |
| 4 | provide-tm-gd000203 수동 실행 | api_provider PG sync_log + execution 박힘 + 처리현황 + detail 표시 ✅ |
| 5 | dmz-bojo-* 1개 샘플 | dmz container dev DB 박힘 ✅ |
| 6 | internal-bojo-* 1개 샘플 | inner_common dev DB 박힘 + detail 표시 ✅ |
| 7 | api-collector / dmz-others 4개 (saeol 외) | dmz container dev DB 박힘 ✅ |
| 8 | **detail 화면 다른 module** (provide, bojo) | 5/14 이전부터 동작했던 것 그대로 ✅ (회귀 0) |

---

## 7. commit 메시지 안

```
fix(sync-log): 어제 fix 부분 롤백 — sync_log + execution 모두 local (= JPA primary) 박기

원인: 어제 fix(d90d415, sync_log = agent.target 룰)가 본 세션 detail 회귀 발견.
어제 fix 가 sync_log 만 saeol-Oracle 로 옮기고 execution 은 JPA primary 그대로 두어
backend mgmtJdbc=saeol-oracle 가 execution 못 찾아 detail/source/target/trace API 500.

본 세션 발견:
- agent JPA = 모든 entity 를 primary datasource 에 박음
- 36 agent 검증: execution + sync_log 항상 같은 곳 (JPA primary) 박혀야 일관
- ExecutionDataController 가 이미 fallback 메커니즘 보유 (manageDsId=null → defaultJdbcTemplate)
- 어제 fix 의 SyncLogWriter 동적 JDBC 우회 + backend resolveManagementDatasource = 사실 불필요

정공:
- SyncLogWriter 시그너처 (DataSourceProvider) → (SyncLogRepository) mirror 롤백. 9 호출처 역변경.
- 새올 SaeolLinkPlanSndStep 직접 패턴 복원 (100% mirror).
- backend resolveManagementDatasource 메서드 제거 + buildHeaders 의 X-Manage 헤더 송신 코드 제거.
  → manageDsId=null → SyncDataSourceService.getJdbcTemplate(null) → defaultJdbcTemplate fallback.
- others-dmz application.yml execution-data.enabled = true (3/31 모듈 초기 누락 fix, VER-016).
- saeol-Oracle SYNC_LOG 테이블 + DDL 파일 drop (어제 만든 무용지물).

회귀 검증:
- 새올 SND 수동 실행 → dmz container dev DB sync_log + execution 박힘.
- 처리현황 + detail/source/target/trace 모두 정상 표시.
- 다른 35 agent (provide/bojo/internal) 위치 변경 0, 회귀 0.
- VER-016 closure.

본 fix 후 룰: sync_log + execution = agent JPA primary. backend 헤더 안 보내고 agent fallback 활용.
36 agent 일관, 휴리스틱 없음.
```

---

## 8. 추적 (사용자 결정 미정 항목)

- [ ] §4 Phase E 5번: dmz container dev DB 의 saeol execution 5건 (5/8~5/19) 정리 여부 (drop 으로 클린 검증 vs 보존)
- [ ] §6 검증 매트릭스 8개 모두 vs 새올 + 1 샘플 vs 사용자 선택
- [ ] §4 Phase D MEMORY 신규 entry 명/내용 사용자 검토

---

## 9. 별 트랙 (본 사이클 외)

- **인프라 모순** — agent JPA primary URL (yml ENC = localhost:29001) vs 실제 INSERT 위치 (29002 dmz container). docker compose 의 hostname 매핑 또는 ENC 풀이 검증. 본 사이클 영향 없음 (agent 자기 connection 그대로 사용) 이라 별 트랙.
- (c3) `agent.local_datasource_id` 컬럼 추가 — 향후 backend 가 명시적 local 필요 시. 현재 fallback 으로 해결됨.
- internal-bojo-* execution 정확한 위치 (inner_common 29001 vs internal Oracle 29004) — 본 사이클 fact 검증으로 동작에 영향 없음. agent 가 자기 박은 곳 자기 보면 됨.
