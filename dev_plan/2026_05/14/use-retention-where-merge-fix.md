# 이용량 retention/where + MERGE/행단위 fix (2026-05-14)

배경: 5/13 계획서 `dev_plan/2026_05/13/use-retention-where-filters.md` 의 후속. 실 검증 진행하면서 다수 fix 발견 — yml + Scheduler + UseLoadStep + frontend UI 통합 사이클.

## 1. 이용량 yml retention/where (계획서 6 후보 → 13 후보 확장)

### 1-1. 후보 확장 — varchar 컬럼은 timestamp 메타 (extracted_at) 만

| 단계   | 테이블                   | retention 후보 (dateColumn) | 비고                          |
|--------|--------------------------|-----------------------------|-------------------------------|
| Source | use_legacy_data          | —                           | 외부 source — agent retention X |
| Source | use_status_data          | —                           | 〃                            |
| Source | use_jeju_day             | —                           | 〃                            |
| SND    | if_snd_use_legacy_data   | obsr_dt, extracted_at       | 2 후보                        |
| SND    | if_snd_use_status_data   | obsr_dt, extracted_at       | 2 후보                        |
| SND    | if_snd_use_jeju_day      | extracted_at                | obsr_de varchar → 별 트랙     |
| RCV    | IF_RSV_USE_LEGACY_DATA   | OBSR_DT, EXTRACTED_AT       | 2 후보                        |
| RCV    | IF_RSV_USE_STATUS_DATA   | OBSR_DT, EXTRACTED_AT       | 2 후보                        |
| RCV    | IF_RSV_USE_JEJU_DAY      | EXTRACTED_AT                | OBSR_DE varchar → 별 트랙     |
| Loader | PM_GD111021              | OBSRVN_DT                   | 1 후보                        |
| Loader | PM_GD111022              | —                           | varchar 일집계, 비추적         |
| Loader | TM_GD111024              | —                           | Link 스냅샷, 비추적            |
| Loader | TM_GD111025              | OBSRVN_DT, LAST_CHG_DT      | 2 후보                        |

**합계** — 후보 13개 / 대상 X 6개. RetentionCandidate.dateColumn Javadoc 룰 "등록일/설치일 류 부적합" 과 extracted_at 후보 박는 게 형식적 모순 — 별 트랙 검토 후보.

### 1-2. Where-filters (5, Loader 만)

| key           | table                     | column   | operators  | valueType  | label  |
|---------------|---------------------------|----------|------------|------------|--------|
| telno-legacy  | IF_RSV_USE_LEGACY_DATA    | TELNO    | LIKE, IN   | STRING     | 전화번호 |
| period-legacy | IF_RSV_USE_LEGACY_DATA    | OBSR_DT  | BETWEEN    | DATETIME   | 측정시간 |
| telno-status  | IF_RSV_USE_STATUS_DATA    | TELNO    | LIKE, IN   | STRING     | 전화번호 |
| period-status | IF_RSV_USE_STATUS_DATA    | OBSR_DT  | BETWEEN    | DATETIME   | 측정시간 |
| stat          | IF_RSV_USE_STATUS_DATA    | STAT     | IN, =      | STRING     | 상태   |

운영자 친화 label 단순화 (DB schema comment 패턴) — 괄호 부기 빼고 컬럼 의미만.

## 2. Backend Fix — `DataRetentionScheduler.enrichTargetDatasourceId`

### 발견
운영자 retention 설정 시 frontend UI 에서 targetDatasourceId 입력란 제거 (운영자에게 내부 ID 노출 X). 결과: retention_config JSON 에 `targetDatasourceId` 없음 → agent fallback `dataSourceProvider.getTargetDatasourceId()` 가 bojo-internal 의 module-default datasource (PG) 잡음 → Oracle 테이블 못 찾음 → PSQLException.

### Fix
`DataRetentionScheduler.executeRetentionCleanup` 가 cleanup body 만들 때 `agent.targetDatasourceId` 자동 inject. 운영자 입력 누락도 방어.

```java
String enrichedConfig = enrichTargetDatasourceId(agent.getRetentionConfig(), agent.getTargetDatasourceId());
HttpEntity<String> request = new HttpEntity<>(enrichedConfig, headers);
```

### 검증
PM_GD111021 에 임의 row 9건 INSERT (BRNCH 999997~999999, OBSRVN_DT 2026-02-04/03-15/05-04) + retention 박기 (`PM_GD111021.OBSRVN_DT / 80일`) + cron 임시 단축 (`0/30 * * * * *`) → 3건 삭제 (2026-02-04 만). 통과.

## 3. UseLoadStep Fix

### 3-1. TM_GD111025 INSERT → MERGE 통일

**발견** — TM_GD111025 만 INSERT (PM 류는 MERGE). 같은 SN 재처리 시 PK 충돌.

**Fix** — SN PK 기반 MERGE. WHEN MATCHED 시 UPDATE (외부 갱신/운영자 재처리 안전).

```sql
MERGE INTO TM_GD111025 t USING (SELECT ? AS SN FROM DUAL) s ON (t.SN = s.SN)
WHEN MATCHED THEN UPDATE SET TELNO=?, OBSRVN_DT=?, LAST_CHG_DT=?, EXECUTION_ID=?, SOURCE_REFS=?
WHEN NOT MATCHED THEN INSERT (...) VALUES (...)
```

### 3-2. TM_GD111024 행단위 GREATEST 갱신

**발견** — 후처리 `updateLastReceive` 가 PM_GD111021 전체 BRNCH MAX 재계산. conditions 실행 시도 전체 갱신 → 운영자 부분 보정 의도와 불일치. 또 retention 으로 PM row 삭제 시 MAX 거꾸로 갈 가능성.

**Fix** — Phase 1 PM_GD111021 MERGE 직후 같은 row 의 BRNCH 단위 TM_GD111024 MERGE 추가:

```sql
MERGE INTO TM_GD111024 t USING (SELECT ? AS BRNCH_ID, CAST(? AS DATE) AS OBSRVN_DT FROM DUAL) s
  ON (t.BRNCH_ID = s.BRNCH_ID)
WHEN MATCHED THEN UPDATE SET OBSRVN_DT = GREATEST(t.OBSRVN_DT, s.OBSRVN_DT), EXECUTION_ID = ?
WHEN NOT MATCHED THEN INSERT (BRNCH_ID, OBSRVN_DT, EXECUTION_ID) VALUES (?, ?, ?)
```

후처리 `updateLastReceive` 메서드 제거.

**효과**:
- IN 한계 무관 (row 별 MERGE)
- GREATEST 단조 증가 보장 → retention 거꾸로 문제 동시 해결
- conditions 실행 시 처리된 BRNCH 만 갱신 — 운영자 의도 부합
- 일반 실행도 동일 흐름 — 분기 X

### 3-3. TM_GD111024 카운트 fan-in 반영

**발견** — 처리현황 TM_GD111024 카운트 = 2 (MERGE 호출 수) vs 실제 영향 row = 1 (BRNCH 53 fan-in). PK 단일 컬럼이라 같은 BRNCH 의 여러 row 처리 시 fan-in 발생.

**Fix** — `lastReceiveCount = lr.affectedBrnchIds.size()` 로 distinct BRNCH 수 사용 (실제 영향 row 수).

## 4. Frontend UI 정합

### 4-1. Retention 편집 모드 (InfoTab.tsx)
- `targetDatasourceId` 입력란 제거 → readonly 안내 ("보존 정책 적용 대상: {datasourceName} ({dbType})")
- 테이블 dropdown: unique table 만 (중복 제거)
- dateColumn dropdown: 선택된 테이블의 후보들 (다중이면 운영자 선택)
- view mode: `<code>` 태그 제거 (모노스페이스 → 일반 폰트)

### 4-2. Conditions UI (agents/[id]/page.tsx)
- 헤더 아래 안내 추가 ("조건 적용 대상: {datasourceName} ({dbType})")
- dropdown 옵션: `{column}({label}) - {table}` 형식 (column 우선, label 단순 comment)
- select width 18rem → 36rem

## 5. 검증

### 검증 1 — Retention (B fix)
PM_GD111021 임의 9건 INSERT → 30s 단축 cron → 3건 삭제 (100일 전) / 보존 6건. **통과**.

### 검증 2 — TM_GD111025 MERGE 통일 + conditions 실행
TELNO LIKE '01239072110' → status 측 SUCCESS 5건 다 read → MERGE UPDATE 5건. PK 충돌 X. **통과**.

### 검증 3 — TM_GD111024 행단위 GREATEST + 카운트 fan-in
같은 conditions 재실행 → BRNCH 53 의 2 legacy row 처리 → TM_GD111024 의 BRNCH 53 1 row 만 새 회차 (다른 BRNCH 보존). 처리현황 카운트 1 (fan-in 반영). **통과**.

### 검증 4 — 일반 실행 (PENDING 만)
신규 PENDING row 3 INSERT (legacy 2 + status 1) → conditions 없이 실행 → PENDING 3 read → write 7 (PM_GD111021 2 + PM_GD111022 2 + TM_GD111024 2 + TM_GD111025 1). **통과**.

## 6. 변경 파일

### 6-1. yml
- `infolink-agent-others-dmz/.../config/agents/dmz-others-snd-use.yml` — retention 5 후보
- `infolink-agent-bojo-internal/.../config/agents/internal-use-rcv.yml` — retention 5 후보
- `infolink-agent-bojo-internal/.../config/agents/internal-use-loader.yml` — retention 3 후보 + where 5 필터

### 6-2. backend
- `infolink-orchestrator-backend/.../scheduler/DataRetentionScheduler.java` — `enrichTargetDatasourceId` 헬퍼

### 6-3. agent (bojo-internal)
- `infolink-agent-bojo-internal/.../loader/step/UseLoadStep.java` — TM_GD111025 MERGE + TM_GD111024 행단위 GREATEST + 카운트 fan-in

### 6-4. frontend
- `infolink-orchestrator-frontend/components/agent/InfoTab.tsx` — retention UI 정합
- `infolink-orchestrator-frontend/app/(main)/agents/[id]/page.tsx` — conditions UI 정합
- `infolink-orchestrator-frontend/app/(main)/agents/[id]/page.module.css` — select width 조정

### 6-5. docs
- `docs/agent-retention-where.md` — 신규 36 agent 매트릭스 (use 부분 ✅ 정정 포함)

## 7. 별 트랙

1. **RetentionCandidate.dateColumn Javadoc 룰 완화** — "등록일/설치일 류 부적합" 표현이 extracted_at 후보 박는 것과 형식적 모순. common 1줄 + 9 모듈 JAR 복사 영향 — 별 사이클.
2. **retention cascade 도메인 정책** — retention 후 TM_GD111024 OBSRVN_DT 거꾸로 가는 문제는 본 fix 의 GREATEST 로 자연 풀림. 다만 PM 의 MAX 가 줄어든 의미와 TM 의 "최근 수신" 의미의 도메인 정합 명시 필요.
3. **`UseLoadStep` link_status filter 비대칭** — conditions 실행 시 link_status filter 자동 빠짐 (의도된 운영자 재처리). 운영자 가이드 명시 필요.
4. **IF row EXECUTION_ID** — Loader 가 IF 의 LINK_STATUS 만 갱신, EXECUTION_ID 는 rcv 회차 유지. 추적 일관성 검토.
5. **TM_GD111010 ↔ TELNO 시스템 정합** — 어제 dev_log 별 todo 그대로.

## 8. 메모리 후보 (사용자 승인 후)
- "운영자 retention 설정 시 targetDatasourceId 미입력 → Scheduler 가 agent.targetDatasourceId 자동 inject. 운영자 UI 에서 datasource id 노출 X (내부 개념)."
- "TM_GD111024 류 단일 BRNCH PK 스냅샷 테이블 — 행단위 GREATEST 패턴으로 단조 증가 + IN 한계 무관 + conditions 부분 갱신 자연."
- "Loader 의 fan-in 발생 테이블 (PK 단일 컬럼) 의 처리현황 카운트 = distinct affected key 수 (MERGE 호출 수가 아님)."
