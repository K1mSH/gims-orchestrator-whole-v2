# Step 5 카운터 회귀 복원 (5dfa203 패턴)

## 1. 발단

03 bojo Step 5 재실행 결과 사용자가 "카운터가 안 맞는다" 지적.

| 항목 | 사용자 기억 (과거 PASS 시점) | 현재 (5/8) |
|---|---|---|
| obsvdata write count | EAV 팽창 그대로 (44691) | **IF 단위 (14897)** |
| LINK 표시 | 별 row 보라색 (1206) | **카운터에서 사라짐** |
| 총합 | 44691 + 1206 = 45897 | 14897 |

사용자: "**팽창이 총합에 계산이 됐던거 같은데 맞아? 지금 우리 상태는 아니자나**"

git log + dev_logs 추적으로 **회귀 commit 식별**.

## 2. 회귀 추적 결과

### 2-1. 타임라인

| Commit | 일자 | 변경 | writeCount | LINK SyncLog |
|---|---|---|---|---|
| `b9dbbaa` | 2026-02-26 | 첫 InternalLoadStep | `+= inserted` (44691) | 없음 |
| **`5dfa203`** | **2026-03-04** | **"총건수 SyncLog 기반 집계로 변경 (EAV 팽창 보정)" + LINK SyncLog 별도** | `+= inserted` (44691) | **별 row 추가 (1206)** |
| **`da653f2`** | **2026-03-09** | **"RESYNC 상태 지원" — commit message 에 비공개로 카운터 의미 변경** | `= obsvSuccess` (**14897, IF 단위**) | **제거됨** |
| (이후 ~ 5/8) | | 변경 없음 | 14897 그대로 | 그대로 (제거 상태) |

### 2-2. 회귀 commit `da653f2` 분석

**commit message**:
```
fix: InternalLoadStep RESYNC 상태 지원 — Loader가 시간범위 실행 데이터를 읽지 못하는 문제 수정

WHERE link_status = 'PENDING' → IN ('PENDING', 'RESYNC')
```

**실 변경**:
1. ✅ WHERE 절 `'PENDING' → IN ('PENDING', 'RESYNC')` (commit message 명시)
2. ❌ `writeCount += inserted;` → `writeCount = obsvSuccess;` (**비공개 변경**)
3. ❌ LINK SyncLog 별 row 저장 호출 제거 (**비공개 변경**)

→ 1번이 본 의도, 2~3번은 commit message 에 명시되지 않은 채 끼어 들어간 회귀.

### 2-3. 사용자가 PASS 인정한 시점

**5dfa203 (3/4) ~ da653f2 직전 (3/9)** — 이 시기에:
- obsvdata write_count = 실 INSERT 행 (EAV 팽창 그대로 표시)
- LINK SyncLog: 별 row, 보라색 처리현황 표시
- 사용자 입장: PM/Link 둘 다 명시적으로 보임 → 직관 정합

### 2-4. 회귀가 5/8 까지 안 잡힌 이유

- `da653f2` 직후 (3/9 dev_log) 의 sample 데이터: 804건 IF
- 804 IF × 3 = 2412 EAV 팽창. 사용자 입장에서 차이 작음.
- 3/9 dev_log 의 `Internal Loader | SUCCESS | 804/804` 표기 — IF 단위 (804) 라 EAV 팽창 차이 미인지
- 5/8 1차 반입 통합 테스트에서 14897 IF (큰 데이터셋) 적재 → 14897 vs 44691 차이 부각 → 회귀 노출

## 3. 결론 및 수정 결정

### 3-1. 결론

`da653f2` 의 숨은 변경 (writeCount 의미 + LINK SyncLog 제거) 은 **회귀**. RESYNC fix 의도와 무관한 변경이 끼어든 것.

### 3-2. 수정하겠다 — 5dfa203 패턴 복원 + per-mapping 구조 유지

3/9 도입된 per-mapping SyncLog 구조 (`mappingName`/`sourceTables`/`targetTables`) 자체는 유지하면서, write count 의미 + LINK row 만 5dfa203 패턴으로 복원.

| 변경 영역 | 변경 |
|---|---|
| `writeCount` (line 244) | `= obsvSuccess` → `+= inserted` (실 INSERT 행, EAV 팽창 그대로) |
| LINK SyncLog | obsvdata mapping 외에 link mapping row 추가 (`saveSyncLogMapping` 한번 더 호출) |
| obsvdata mapping target | `[targetObsvdataTable]` (단, YAML 의 `target-table: [PM_GD970201, TM_GD970101, TM_GD980002]` 를 어떻게 다룰지는 별 사이클 [VER-015]) |
| link mapping target | `[targetLinkTable]` (TM_GD980002) |

### 3-3. 수정 대상

**파일 1개**: `infolink-agent-bojo-internal/src/main/java/com/infolink/agent/bojo/loader/step/InternalBojoLoadStep.java`

**변경 1 — write count 단위 복원 (line 244 주변)**

```java
// 현재 (회귀)
if (!expandedRows.isEmpty()) {
    int inserted = targetRepo.batchInsertObsvdata(targetObsvdataTable, expandedRows);
    writeCount = obsvSuccess;  // 논리적 레코드 수 기준 (EAV 확장 행 수가 아닌 IF 처리 건수)
    log.info("[{}] EAV 관측데이터 {} 행 INSERT 완료 (IF 레코드 {} 건 기준)",
            getStepId(), inserted, obsvSuccess);
}
```

```java
// 수정 (5dfa203 패턴 복원)
int inserted = 0;
if (!expandedRows.isEmpty()) {
    inserted = targetRepo.batchInsertObsvdata(targetObsvdataTable, expandedRows);
    writeCount += inserted;  // 실제 INSERT 행 (EAV 팽창 포함)
    log.info("[{}] EAV 관측데이터 {} 행 INSERT 완료 (IF 레코드 {} 건 기준, EAV 1:3 확장)",
            getStepId(), inserted, obsvSuccess);
}
```

**변경 2 — saveSyncLogMapping 호출부 수정 + LINK row 추가**

obsvdata mapping 의 write 카운터를 inserted 단위로:
```java
// obsvdata mapping (현재) — write=obsvSuccess (회귀)
saveSyncLogMapping(executionId, stepId,
        configSourceTables != null ? configSourceTables : List.of(ifObsvdataTable),
        configTargetTables != null ? configTargetTables : List.of(targetObsvdataTable),
        (long) obsvReadCount, (long) obsvSuccess, (long) obsvFailed, 0L,
        ...);
```

```java
// obsvdata mapping (수정) — write=inserted
saveSyncLogMapping(executionId, "obsvdata",
        List.of(ifObsvdataTable),
        List.of(targetObsvdataTable),
        (long) obsvReadCount, (long) inserted, (long) obsvFailed, 0L,
        ...);

// link mapping (5dfa203 패턴 복원)
if (linkUpdated > 0) {
    saveSyncLogMapping(executionId, "link",
            List.of(ifObsvdataTable),  // source = IF (link key 출처)
            List.of(targetLinkTable),
            (long) linkUpdated, (long) linkUpdated, 0L, 0L,
            null, null);
}
```

> 주: 매핑 이름 `obsvdata` / `link` 은 5dfa203 ~ da653f2 직전 패턴 또는 5/7 dev_log §"per-mapping 방식 (mappingName/sourceTables[]/targetTables[]/readCount/writeCount)" 정합으로 결정. 둘 다 `step_id` (= internal-bojo-load) 는 동일.

### 3-4. 사용처 영향 점검 (회귀 룰 정합)

`feedback_no_regression_organic` 룰:
- ExecutionDataController.summary — sync_log 합산. 자동 정합 (44691 + 1206 = 45897).
- ExecutionDataController.tables — TableStatsDto 매핑 단위. obsvdata + link 두 행 자동 표시.
- Frontend `executions/[id]/page.tsx` — flatTableStats 변환 (매핑 → SOURCE/TARGET 행 펼침). target=TM_GD980002 가 새 행으로 추가 표시 — 5dfa203 시점 "보라색 LINK" 표시 룰 복원 검토 별 사이클.
- 다른 Loader (DefaultLoadStep / JejuLoadStep 등) — EAV 확장 없는 1:1 매핑이라 inserted == obsvSuccess. 변경 영향 0 (필요 시 일관성 차원에서 동일 패턴 적용 별 사이클).

### 3-5. 후속 절차

1. InternalBojoLoadStep.java 변경 (1 파일, 2 영역)
2. `cd infolink-agent-bojo-internal && ./gradlew clean build -x test`
3. agent-bojo-internal (8092) 재기동
4. **데이터 cleanup**:
   ```sql
   DELETE FROM PM_GD970201 WHERE EXECUTION_ID LIKE 'internal-bojo-loader_%';
   DELETE FROM TM_GD980002;
   UPDATE IF_RSV_SEC_OBSVDATA SET LINK_STATUS='PENDING', EXECUTION_ID=NULL WHERE LINK_STATUS='SUCCESS';
   COMMIT;
   ```
   (entity Long→Double fix 후 동일 cleanup 절차)
5. Step 5 재실행 (사용자 화면)
6. **검증 기대값**:
   - sync_log obsvdata row: read=14897, write=44691 (EAV 1:3 팽창 그대로)
   - sync_log link row: read=1206, write=1206
   - 총합 표시 = 44691 + 1206 = 45897
   - PM_GD970201 OBSRVN_DATA_VL = "6.27" (entity Double fix + 카운터 fix 동시 검증)

## 3-6. Target 추적 부분 fix (5/8 추가 — Agent 정의 유지 결정)

Step 5 재실행 후 사용자 화면 검증 시 발견:
- TARGET PM_GD970201 행 클릭 → 데이터 표시 ✅
- TARGET TM_GD970101 행 클릭 → "데이터가 없습니다" ❌
- TARGET TM_GD980002 행 클릭 → "데이터가 없습니다" ❌

### 원인
`/target` endpoint = `WHERE execution_id = ?` 매칭. 그런데:
- `ensureResultId` (TM_GD970101 INSERT) — `(hr_unit_id, obsrvn_artcl_id, brnch_id, unit_id, mthd_id)` 만 박음, **execution_id 미박음**
- `batchUpsertLink` (TM_GD980002 MERGE) — Link 컬럼만 박음, **execution_id 미박음**
- 두 테이블 모두 EXECUTION_ID 컬럼은 존재하나 NULL 박힘 → WHERE execution_id 매칭 0건

### 의미론적 결정 (사용자 5/8 — Agent 정의 정합 우선)

사용자가 Agent 정의 (Datasource & 테이블) 화면 = Source 1 / Target 3 이 정답이라고 명시. → **yaml `target-table: [PM_GD970201, TM_GD970101, TM_GD980002]` 그대로 유지**.

- **TM_GD970101 = ODM결과 (EAV rslt_id 매핑 마스터)**: ensureResultId 가 신규 row INSERT 시 execution_id 박는 정합 옵션은 있으나, 같은 row 가 여러 실행에서 재사용되어 추적 일관성 부족 → **별 사이클 (이번 fix 안 함)**.
- **TM_GD980002 = 보조수위측정망 연계현황 (Link)**: 매 실행 UPSERT. execution_id 박혀야 추적 정합 → **본 fix 적용**.

### 변경 (2 영역, yaml 변경 X)

**1) `batchUpsertLink` MERGE INTO 에 execution_id 추가** — `GimsTargetRepository.java`
```diff
- USING (SELECT ? AS obsvtr_id, ? AS brnch_id, ? AS last_obsrvn_ymd,
-        ? AS last_obsrvn_hr, ? AS chg_dt, ? AS frst_obsrvn_ymd, ? AS frst_obsrvn_hr FROM DUAL) s
+ USING (SELECT ? AS obsvtr_id, ? AS brnch_id, ? AS last_obsrvn_ymd,
+        ? AS last_obsrvn_hr, ? AS chg_dt, ? AS frst_obsrvn_ymd, ? AS frst_obsrvn_hr,
+        ? AS execution_id FROM DUAL) s
  WHEN MATCHED THEN UPDATE SET
    ...
+   t.execution_id = s.execution_id
  WHEN NOT MATCHED THEN INSERT
-   (obsvtr_id, brnch_id, ..., frst_obsrvn_hr)
+   (obsvtr_id, brnch_id, ..., frst_obsrvn_hr, execution_id)
  VALUES (s.obsvtr_id, ..., s.execution_id)
```
PG 분기도 동일 추가.

**2) `InternalBojoLoadStep` 의 linkRows.add 시 executionId 추가** — `InternalBojoLoadStep.java` (linkRows 끝에 executionId 인자 추가)

### sync_log 구조 — 1 mapping 유지 (link mapping 분리 X)

`5dfa203` 시점은 LINK SyncLog 별 row 였으나, 사용자 결정 (5/8) — **Agent 정의 정합 우선 + sync_log 1 row 유지**:
- `mapping_name="obsvdata"` 1 row
- `source_tables=["IF_RSV_SEC_OBSVDATA"]`
- `target_tables=["PM_GD970201","TM_GD970101","TM_GD980002"]` (yaml 그대로)
- `read=14897, write=44691` (5dfa203 패턴: writeCount += inserted, EAV 팽창 포함)

→ TM_GD980002 행 클릭은 batchUpsertLink 가 execution_id 박아서 추적 가능. TM_GD970101 행 클릭은 빈 응답 (별 사이클 fix).

## 3-7. Frontend Multi-target 표시 룰 (별 사이클)

화면 "테이블별 처리 현황" 표:
- mapping target 이 multi (3개) 일 때 mapping write count 가 모든 target 행에 펼쳐 표시
- 결과: TARGET PM_GD970201=44691 / TM_GD970101=44691 / TM_GD980002=44691 (실제 적재 다름)
- 사용자 직관 X — VER-015 확장 별 사이클로 fix.

## 4. 별 사이클 (본 fix 범위 밖)

- **da653f2 의 RESYNC fix 부분 (1번 변경 = WHERE 절)** 은 의도 정합. 유지.
- **dev_logs 5/7 의 Step 5 기대값 `write≈14897×3` 표기** — fix 후 실제 동작과 자연 정합 (45897). dev_logs 갱신 불필요.
- **다른 Loader (DefaultLoadStep / JejuLoadStep 등) 의 SyncLog 카운터 일관성** — 1:1 매핑이라 차이 없으나 "writeCount = inserted" 패턴 통일 검토 가능. 별 사이클.
- **Frontend 의 LINK 표시 (보라색)** — 5dfa203 시점 처리현황 색상 표시 룰. flatTableStats 변환 후 LINK 매핑이 자연 표시되면 충분. 색상은 추후 UX 차원.
- **ExecutionDataController.summary 의미 재검토** — sync_log 합산이 EAV 팽창 포함이라 사용자 직관 정합. 다만 "처리 IF 건수" (14897) 도 보고 싶을 수 있음 — 별 메트릭 추가 검토 가능 (별 사이클).

## 5. 묶음 — 5/8 03 bojo Step 5 PASS 게이트

본 fix 와 entity Long→Double fix (`bojo-internal-if-entity-standardization-fix.md`) 는 **둘 다 통과해야 Step 5 PASS**:

| fix | 검증 |
|---|---|
| Entity Long→Double | PM_GD970201 OBSRVN_DATA_VL = "6.27" (소수 보존) |
| **카운터 5dfa203 복원** | **sync_log write=44691 + link write=1206, 총합=45897** |

두 fix 가 같은 빌드 / 같은 cleanup / 같은 재실행 사이클로 묶이는 게 효율적.
