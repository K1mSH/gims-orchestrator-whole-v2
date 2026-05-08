# Retention 안전장치 — yml retention-candidates 도입 (VER-016)

## 1. 발단

5/8 03 bojo 사이클 끝 사용자 요청 — "다른 Agent 도 똑같이 점검" → DMZ Others 5 Agent 의 Retention cleanup 검증 중 **의도 외 데이터 삭제 사고** 발생.

| Agent | 사용한 dateColumn | deletedCount | 평가 |
|---|---|---:|---|
| dmz-others-snd-saeol | `first_reg_dthr` | **12** | ❌ 컬럼 의미 부적합 (최초 등록일) |
| dmz-others-snd-yaksoter | `instl_ymd` | **437** | ❌ 컬럼 의미 부적합 (시설 설치일) |

총 449 row 의도 외 삭제. 사용자 룰 ("잘못 지워지면 머리 아프다") 위반.

## 2. 본질 분석 (사용자 통찰)

### 2-1. Retention 룰의 부적절한 일반화

표준 룰 = `WHERE dateColumn < cutoff` 일괄 삭제. **시계열 데이터에만 정합**.

| 테이블 성격 | 예시 | retention 적용 |
|---|---|:-:|
| 시계열 | PM_GD970201 (관측 obsrvn_dt), if_rsv_sec_obsvdata (obsv_date) | ✅ |
| 마스터 / 등록 | tm_gd970001, if_snd_tm_gd010310 (시설 설치) | ❌ |
| 인덱스 / Link | tm_gd980002 | ❌ |
| EAV 메타 | tm_gd970101 | ❌ |
| 이용실태 등록 | if_snd_rgetstgms01 | ❌ |

### 2-2. Agent boundary 미준수

운영자 화면의 retention 설정 dropdown 이 **datasource 의 모든 등록 테이블** 노출 (`InfoTab.tsx:296` `datasourceApi.getRegisteredTables(dsId)`). Agent 책임 영역 (`yml source-table / target-table`) 와 무관.

→ 운영자가 잘못 박으면 Agent A 가 Agent B 의 테이블도 cleanup 가능.

### 2-3. dateColumn 자유 입력

dateColumn 도 dropdown 이지만 **테이블의 모든 컬럼** 노출. retention 의미 (데이터 발생 시점) 가 아닌 메타 컬럼 (`first_reg_dthr` 등) 도 선택 가능.

### 2-4. 사고 매커니즘 정합

본 검증 자동화 시 dateColumn 후보를 schema 컬럼명만 보고 (`*_ymd`/`*_dt`) 자동 박았음 → 컬럼의 비즈니스 의미 무시 → 의도 외 삭제. **운영자도 동일 함정 빠질 수 있음**.

## 3. 해결 설계

### 3-1. 단일 진실원 (single source of truth) — yml `retention-candidates`

yml 에 retention 가능 (table, dateColumn) 후보 명시. dev 가 운영 의도 정확히 알고 검토 후 등록. 운영자는 후보만 선택 가능.

```yaml
agent-code: internal-bojo-loader
type: LOADER

retention-candidates:
  - table: pm_gd970201
    dateColumn: obsrvn_dt
    description: 관측 시계열 (EAV 1:3 적재)
  - table: if_rsv_sec_obsvdata
    dateColumn: obsv_date
    description: IF 수신 관측 데이터

steps:
  - id: internal-bojo-load
    factory-key: internal-bojo-load
    source-table: IF_RSV_SEC_OBSVDATA
    target-table: [PM_GD970201, TM_GD970101, TM_GD980002]
```

> 주: target-table 의 TM_GD970101 (메타) / TM_GD980002 (Link) 는 retention-candidates 에 **포함되지 않음** — 마스터/Link 라 retention 비대상.

### 3-2. retention-candidates 비어 있음 = retention 비대상 Agent

```yaml
agent-code: dmz-others-snd-yaksoter
type: SND

retention-candidates: []  # 또는 필드 자체 생략

steps:
  ...
```

운영자 화면에서 **retention 설정 카드 자체 disabled** 처리 + "이 Agent 는 retention 비대상" 메시지. 사고 자체 발생 X.

### 3-3. 4 layer 검증

| Layer | 동작 | 차단 시점 |
|---|---|---|
| **A. yml** | dev 가 retention-candidates 명시 (운영 의도 검토) | 정의 단계 |
| **B. Frontend** | dropdown = retention-candidates 만 표시. 비어있으면 카드 비활성 | 입력 단계 |
| **C. Backend** | PUT /api/agents/{id}/retention 시 retention-candidates 외 거부 (HTTP 400) | 저장 단계 |
| **D. Agent (DataRetentionService)** | cleanup 호출 시 body 의 (table, dateColumn) 이 retention-candidates 안인지 검증. 외부면 거부 | 실행 단계 (최후 방어) |

A+B+C 만으로 운영자 사고 차단. D 는 외부 직접 호출 (curl 등) 차단용 — defense in depth.

## 4. 변경 범위

### 4-1. yml (Agent 별) — 운영 검토 후 등록

| Agent | retention-candidates |
|---|---|
| internal-bojo-loader | `[{pm_gd970201, obsrvn_dt}, {if_rsv_sec_obsvdata, obsv_date}]` |
| internal-bojo-rcv | `[{if_rsv_sec_obsvdata, obsv_date}]` |
| dmz-bojo-rcv-* (10개) | `[{if_rsv_sec_obsvdata, obsv_date}]` |
| dmz-bojo-loader | `[{sec_obsvdata, obsv_date}]` |
| dmz-bojo-snd | `[{if_snd_sec_obsvdata, obsv_date}]` |
| dmz-others-snd-saeol | `[]` (마스터/등록 — retention 비대상) |
| dmz-others-snd-yaksoter | `[]` (시설 등록) |
| dmz-others-snd-jeju | (jeju obsvdata 류면 후보, 마스터면 빈 배열) |
| dmz-others-snd-use | (운영 검토) |
| dmz-others-snd-api-collect | (news 류 retention 적용 후보) |
| internal-jeju/saeol/use/yaksoter/api-collect-rcv/loader | (각 운영 검토) |
| provide-* | `[]` (API 응답 캐시 — retention 별 의제) |

### 4-2. common 모듈 (AgentDefinition + AgentConfigLoader)

```java
public class AgentDefinition {
    ...
    private List<RetentionCandidate> retentionCandidates;  // 신규 필드
}

public record RetentionCandidate(
    String table,
    String dateColumn,
    String description
) {}
```

AgentConfigLoader 의 yml 파싱에 `retention-candidates` 추가. 누락 시 빈 List.

### 4-3. orchestrator-backend

새 endpoint `GET /api/agents/{id}/retention-candidates` — 운영자 화면이 dropdown 채울 때 호출.

PUT /api/agents/{id}/retention 시 검증:
```java
if (!isCandidate(agent, target.getTable(), target.getDateColumn())) {
    return ResponseEntity.badRequest().body(...);
}
```

### 4-4. frontend (InfoTab.tsx)

```ts
// 기존
const tables = await datasourceApi.getRegisteredTables(dsId);

// 변경
const candidates = await agentApi.getRetentionCandidates(agent.id);
setRetentionCandidates(candidates);
```

table dropdown = candidates 의 table list / dateColumn dropdown = 선택된 table 의 dateColumn (1개로 고정 가능).

candidates 빈 배열일 때:
```tsx
{candidates.length === 0 ? (
  <p>이 Agent 는 데이터 보존 정책 비대상입니다 (마스터 / 인덱스 / 메타 데이터).</p>
) : (
  // 설정 UI
)}
```

### 4-5. Agent (DataRetentionService) — 자체 검증

cleanup 호출 시 body 의 targets 가 yml retention-candidates 와 일치하는지 검증. 외부면 응답 거부:
```json
{"error": "table 'xxx' is not in retention-candidates of agent 'yyy'"}
```

## 5. 마이그레이션 단계

### Phase 1 — yml 정의 (운영 검토 단계)
- 모든 Agent yml 에 retention-candidates 필드 추가 (또는 빈 배열)
- 운영자 + dev 가 각 Agent 별 후보 검토. 잘못 박으면 사고 가능성 그대로 → 신중

### Phase 2 — backend 파싱 + 검증 endpoint
- AgentDefinition + AgentConfigLoader 에 retention-candidates 파싱
- 새 endpoint GET /api/agents/{id}/retention-candidates
- PUT /api/agents/{id}/retention validation 추가

### Phase 3 — frontend 변경
- InfoTab.tsx 의 retention dropdown source 교체
- 빈 배열 시 비활성 메시지

### Phase 4 — Agent 자체 검증 (DataRetentionService)
- cleanup body 의 targets 가 retention-candidates 안인지 검증
- 외부 호출 (curl 등) 차단

### Phase 5 — 기존 retentionConfig 정리
- DB 의 retention_config 중 candidates 외 항목 — 운영자 검토 후 정리 또는 무효화
- 기존 자동 스케줄러 (DataRetentionScheduler) 가 무효 retentionConfig 호출 시 Agent 자체 검증으로 차단됨

## 6. 보조 안전장치 (선택, VER-016 별 의제)

본 yml-driven 화이트리스트 외에 추가 안전장치:

| 안전장치 | 효과 | 우선순위 |
|---|---|---|
| **Dry-run 모드** (`dryRun:true` body) | cleanup 응답에 wouldDelete 만 반환, 실 DELETE X | 중 (사용자 친화) |
| **Hard-cap** (테이블 X% 초과 거부) | 잘못된 retentionDays 시 일괄 삭제 차단 | 중 |
| **Audit log** (retention_audit 테이블) | 사후 추적 가능 | 낮 (운영 차원) |
| **최소 retentionDays 강제** (>=N) | retentionDays=0 같은 실수 차단 | 낮 |

본 fix (yml retention-candidates) 가 통과하면 사고 자체 발생 가능성 거의 0 — 보조 안전장치는 별 우선순위.

## 7. 운영자 가이드 (별 사이클)

운영자 화면에 안내 메시지 추가:
- "이 Agent 는 시계열 데이터 적재 Agent 이고 다음 테이블의 보존 정책을 설정할 수 있습니다."
- "마스터 / Link / 메타 테이블은 보존 정책 비대상입니다."

## 8. 본 사고 retrospective

- saeol 12 row 삭제 → 외부 DB 시설 마스터에 row 남아있어 SND 재실행으로 복구 가능 (다만 시점에 따라 차이 있음)
- yaksoter 437 row 삭제 → 같은 흐름으로 복구
- 본 fix (retention-candidates 빈 배열 = SND Agent 비대상) 적용 시 사고 자체 발생 X

→ 본 fix 의 우선순위 = **1차 반입 전 적용 권장** (운영 사고 사전 차단)

## 9. 5/8 시점 결정 사항

- 본 dev_plan 우선 작성 (사용자 검토 대기)
- 본 fix 의 변경 범위 큼 (5 영역) → 1차 반입 일정 vs 적용 시점 트레이드오프 결정
- 사용자 결정 시 Phase 1~4 단계 진행

## 10. 별 issue 등록

`verify/issues/OPEN/VER-016-retention-candidates-yml-whitelist.md` 신규 등록 — 본 dev_plan 의 잔여 항목 + 운영자 가이드 + 마이그레이션 진척 추적.

## 11. 관련 사이클

- VER-014 (Tibero NUMBER(p,s) DDL precision) — 별 사이클
- VER-015 (TM_GD970101 / TM_GD980002 추적 정책) — CLOSE 후보
- 본 VER-016 — 본 사고 사전 차단 + 운영 안전장치
