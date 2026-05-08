---
id: VER-015
title: Internal Bojo Loader 의 multi-target sync_log 표시 + TM_GD970101 추적 (확장)
status: OPEN
created: 2026-05-08
updated: 2026-05-08
parts: [P2-bojo, sync-log-mapping, frontend-display]
parallel_safe: true
assignee: forward
related: [step5-counter-regression-restore]
---

## 5/8 사용자 결정 (Agent 정의 정합 우선)

`step5-counter-regression-restore` 진행 중 — 사용자가 Agent 정의 (Datasource & 테이블 화면 의 Source 1 / Target 3) 그대로 유지 결정. yaml `target-table: [PM_GD970201, TM_GD970101, TM_GD980002]` 변경 X.

→ 본 issue 의 처리 방향이 변경됨: **TM_GD970101 을 target 에서 제거하지 않고**, sync_log 1 row 의 multi-target (3개) 을 frontend 가 정합하게 표시하는 룰 개선이 본질.

## 확장 — multi-target 표시 룰

`step5-counter-regression-restore` 통과 후 화면 잔여 이슈:

1. **TARGET 3개 모두 같은 write count (44691) 펼쳐 표시**
   - 실측: PM_GD970201=44691 / TM_GD970101=11 / TM_GD980002=1206
   - 화면: 3행 모두 44691 표시 (mapping write 가 모든 target 행에 펼쳐짐)
   - 안내문 "write 건수에는 집계/후처리 등 파생 데이터가 포함될 수 있습니다" 가 이 부분 적용
   - 정합화 옵션:
     - 각 target 별 실 적재 카운트 분리 메타 추가 (sync_log 스키마 변경)
     - frontend 의 표시 룰 — primary target (PM) 만 mapping write 표시, 나머지는 "보조 적재" 식 라벨
     - SyncLog 의 target_tables JSON 에 `[{"name":"PM_GD970201","count":44691},{"name":"TM_GD970101","count":11},{"name":"TM_GD980002","count":1206}]` 형식 분리 메타

2. **TM_GD970101 행 클릭 시 "데이터 없음"**
   - `ensureResultId` (TM_GD970101 신규 row INSERT) 가 execution_id 박지 않아서 `/target?tableName=TM_GD970101` 매칭 0건
   - 의미론: ensureResultId 가 마스터 룩업/생성이라 같은 row 가 여러 실행에서 재사용 → execution_id 박는 의미 약함
   - 옵션:
     - 신규 INSERT 시에만 execution_id 박기 (재사용 row 는 NULL 유지)
     - source_refs 박기 (PM 처럼 IF id 참조)
     - 표시 자체 비활성 (frontend 가 `상세` 버튼 회색)

## 영향

- Step 5 PASS 판정에 직접 영향은 없음 (실 데이터 적재 정합 + TM_GD980002 추적 OK)
- UX 차원 명확성 부족
- 04 others / 05 jeju 등 같은 multi-target 패턴 Loader 가 있다면 일괄 영향

## 5/8 추적 정책 확정 (사용자 결정)

`step5-counter-regression-restore` + `sync-log-multitarget-counts` + ensureResultId execution_id fix 적용 후 사용자 검토:

| Target | 추적 정책 | 이유 |
|---|---|---|
| **PM_GD970201** | ✅ 정수 추적 (PASS) | IF 의 gwdep/gwtemp/ec 직접 적재 (EAV 1:3), 1:1 매핑. source_refs = `["I:internal:IF_RSV_SEC_OBSVDATA:{id}"]` |
| **TM_GD970101** | ⚠️ 데이터 표시만 (행 펼침 추적 X — **의도**) | 마스터 메타 (brnch+IEM rsltId). IF 1:1 매칭 아님 (1 rsltId : N IF). 추적 비대상이 자연 |
| **TM_GD980002** | ⚠️ 데이터 표시만 (행 펼침 추적 X — **의도**) | Link 인덱스 (obsv_code 별 1 row, max date). IF 1:1 매칭 아님. Link 개념은 확실히 추적 X |

→ **현 상태가 의도 정합**. 본 issue 의 multi-target 카운트 분리 + ensureResultId fix 까지 끝났으니 **CLOSE 가능**. 잔여는 운영상 명시 (UI 라벨 등) 별 사이클 후보.

## 증상 요약

`infolink-agent-bojo-internal/src/main/resources/config/agents/internal-bojo-loader.yml`:
```yaml
factory-key: internal-bojo-load
source-table: IF_RSV_SEC_OBSVDATA
target-table: [PM_GD970201, TM_GD970101, TM_GD980002]
```

→ sync_log 의 `target_tables` JSON 에 `TM_GD970101` 포함됨. 5/8 03 bojo Step 5 실측 결과:
```
target_tables = ["PM_GD970201","TM_GD970101","TM_GD980002"]
```

## MEMORY 기록과 모순

5/7 dev_logs 또는 MEMORY 의 "Source 추적 설계 (3/10 확정, E2E 검증 완료)" 섹션:
> "tm_gd970101: Internal Loader 의 target/SyncLog 에서 완전 제거됨"

→ 실측 (YAML + sync_log) 와 모순. 어느 쪽이 정답인지 추적 필요.

## 모호성 (TM_GD970101 의 의미)

- TM_GD970101 = ODM결과 (EAV rslt_id 매핑 테이블)
- `InternalBojoLoadStep.java:204~206` 의 `ensureResultId` 가 brnch_id+IEM 별로 신규 row 를 INSERT 하는 케이스 있음
  ```java
  long rsltGwdep = targetRepo.ensureResultId(targetResultTable, rsltIdMap, brnchId, IEM_GWDEP);
  long rsltGwtemp = targetRepo.ensureResultId(...);
  long rsltEc = targetRepo.ensureResultId(...);
  ```
- 즉 Loader 가 **간접 INSERT 하는 target** 임은 사실
- 그러나 사용자 시각에서 추적 / 카운터의 "target" 으로 표기할지는 정책 문제

## 관점

| 입장 | 근거 |
|---|---|
| Target 으로 유지 | Loader 가 실제 INSERT 하는 테이블이므로 source_refs 추적 시 출처. 표기 정합. |
| Target 에서 제거 | Loader 의 본 의미 있는 target = PM_GD970201 (관측데이터). TM_GD970101 은 EAV 메타 / 사전 마스터 성격. 사용자 화면에서 행 클릭 시 의미 있는 데이터 X. |

→ MEMORY 의 "완전 제거됨" 표현이 반영하려던 의도가 무엇인지 불명. 5/7 dev_logs 또는 그 이전 dev_logs (3/9~3/11) 에서 추적 필요.

## 영향

- Frontend 의 처리 현황 표 / target 클릭 시 데이터 목록 — TM_GD970101 행 클릭 시 EAV 메타 데이터 (rslt_id, brnch_id, iem_id 등) 표시. 사용자 직관 부합 X.
- 사용자가 "target 결과 안 보임" 문의 (5/8 09:30) 시 cleanup 영향 외에 본 표기 모순도 점검 대상으로 떠오름.
- Step 5 PASS 판정에 직접 영향은 없음 — 본 fix(`step5-counter-regression-restore`) 와 별 사이클.

## 처리 방향 (옵션)

A. **YAML target-table 에서 TM_GD970101 제거** — MEMORY 기록 정합. 단 source_refs 추적이 PM_GD970201 행에만 의존하게 됨 (TM_GD970101 은 자동 생성 메타라 사용자 추적 대상 X).

B. **TM_GD970101 유지 + Frontend 에서 EAV 메타 target 은 별 표기** — 색상/라벨 분리 (5dfa203 시점 LINK 보라색 패턴 참고).

C. **현 상태 유지 + MEMORY 기록 정정** — "완전 제거됨" → "Loader 의 EAV 메타 target 으로 표기 유지" 로 갱신.

## 결정 시점

03 bojo Step 5 PASS 판정 후 ~ 04 others 시작 전 사이. dev_logs 추적으로 MEMORY 표현의 정확한 출처 + 의도 확인 후 결정.

## 참고

- `dev_plan/2026_05/08/step5-counter-regression-restore.md` §3-2 "tm_gd970101 은 이미 5/7 기준 제거됨" 표현은 본 issue 등록 시점 검증 전 표현. 검증 결과 yaml 에 그대로 들어 있음.
- `infolink-agent-bojo-internal/src/main/resources/config/agents/internal-bojo-loader.yml` (현재 yaml)
- `infolink-agent-bojo-internal/src/main/java/com/infolink/agent/bojo/loader/step/InternalBojoLoadStep.java:204~206, 289~291`
