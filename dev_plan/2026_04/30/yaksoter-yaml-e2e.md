# 약수터 파이프라인 — YAML + E2E 마무리 (4/30)

> **작성일**: 2026-04-30
> **베이스 계획서**: `dev_plan/2026_04/29/yaksoter-resume-replace-api.md` (4/29 EOD — §A 완료, §B 이월)
> **이전 일지**: `dev_logs/2026_04/2026-04-29.md`
> **상태 (EOD)**: ✅ **완료** — SND 1026 + RCV 1026/1000 + Loader 1026/1000 손실 0. 진행 중 다수 정합 이슈 발견 + 별개 plan (`if-entity-auto-increment-standardization.md`) 으로 묶어 정합화 완료.

> **세부 진행 흐름**: `dev_logs/2026_04/2026-04-30.md` 참조

---

## 1. 4/30 작업 범위

| 단계 | 작업 |
|---|---|
| §0 | **선결 — 코드 수정** (SimpleLoadStep merge-key List 확장 + 약수터 4 엔티티 indexes) |
| §1 | YAML 3건 (SND / RCV / Loader) |
| §2 | 빌드 (sync-agent-common 무관 — bojo-int + others 만 / common 변경 없음) |
| §3 | Internal Oracle 약수터 4 테이블 사전 상태 점검 |
| §4 | Agent 2개 기동 (8085 + 8092) + 테이블 자동 생성/인덱스/길이 검증 |
| §5 | Orchestrator Agent 3건 등록 — **사용자 화면 직접 등록** (Claude 가 ID/값 안내 서포트) |
| §6 | E2E 수동 실행 (yyyy=2020, numOfRows=1000 한 사이클) |
| §7 | `/trace-source` 추적 검증 |
| §8 | dedup 검증 (B-1 기준 — 4/29 의 §B.5b 시나리오 갱신) |
| §9 | 회귀 / dev_log / 커밋 |

---

## 2. 코드 수정 (선결)

### 2-1. SimpleLoadStep merge-key List 확장

**배경 (4/30 발견)**: 4/29 EOD §3.5 의 "B-1 + SimpleLoadStep" 결정 시점에 SimpleLoadStep 의 mergeKey 단일 컬럼 한계 미인지. 약수터 자연키는 복수 컬럼.

| 테이블 | 자연키 |
|--------|-------|
| `TM_GD010310` (제원) | `BRNCH_NO + BRNCH_STD_CD` (2 컬럼) |
| `TD_GD010310` (수질) | `BRNCH_NO + YR + QTR + WTSMP_YMD` (4 컬럼) |

**수정 파일 2건**:

**(A) `SimpleLoadStep.java`**
- 필드: `private final String mergeKey;` → `private final List<String> mergeKeys;`
- 생성자 시그니처 변경
- `buildMergeSql(...)`:
  - USING 절: `(SELECT ? AS K1, ? AS K2 FROM DUAL)` (다중 컬럼)
  - ON 절: `t.K1 = s.K1 AND t.K2 = s.K2 ...` (AND 조합)
  - UPDATE 컬럼 분기: `!mergeKeys.contains(c.toUpperCase())` (key 컬럼 전부 제외)
- `buildMergeParams(...)`:
  - ON: 각 mergeKey 컬럼 값 순서대로
  - UPDATE: 비즈니스 컬럼 (SN, mergeKeys 전부 제외)
  - INSERT: 그대로
- 로그 메시지의 `mergeKey` 참조도 `String.join(",", mergeKeys)` 형태

**(B) `SimpleLoadStepFactory.java`**
- `String mergeKey = (String) config.get("merge-key");` → `List<String> mergeKeys = toList(config.get("merge-key"));`
- 기존 `toList()` 헬퍼 그대로 재사용 (이미 List/String 양쪽 처리 패턴 박혀있음 — source-table/target-table 처리 방식 동일)

**(C) YAML 후방 호환**:
- 단일 키 (CSV 1건 또는 단일 String): 기존 4 모듈 파이프라인 (api-collect 2 / use / jeju / saeol 의 `news-load`/`nara-bid-load`/`use-load`/`jeju-load`/`saeol-load`) 그대로 동작
- 복수 키:
  - 옵션 1: CSV 한 줄 — `merge-key: BRNCH_NO,YR,QTR,WTSMP_YMD`
  - 옵션 2: YAML list — `merge-key: [BRNCH_NO, YR, QTR, WTSMP_YMD]`
  - **채택 = 옵션 2 (YAML list)** — 명시성 우선, CSV split 처리 추가 불필요. `toList()` 헬퍼는 List 면 그대로, String 이면 1건 list 로 변환 (기존 동작 유지).

**회귀 영향 점검**:
- 기존 단일 키 YAML 5건 동작: 단일 String → List(1) 정규화 후 동작. ON/UPDATE/INSERT 모두 단일 키 시퀀스로 동일하게 생성. **회귀 없음.**
- 다른 모듈에 SimpleLoadStep 사용처: bojo-int 만. (others/common 미사용) — 영향 없음.

### 2-2. 약수터 4 엔티티 indexes 추가 (VER-004 패턴)

| 엔티티 | 추가 |
|---|---|
| `IfRsvTmGd010310` | `indexes = @Index(name = "idx_if_rsv_tm_gd010310_exec_id", columnList = "execution_id")` |
| `IfRsvTdGd010310` | 동 (테이블명 치환) |
| `TmGd010310` | 동 (`idx_tm_gd010310_exec_id`) |
| `TdGd010310` | 동 (`idx_td_gd010310_exec_id`) |

> VER-004 본 처리 (bojo-int 전체 17개) 는 별도 트랙 — 본 작업 범위 X.
> 약수터 작업분만 패턴 따라 미리 박아 둠 (VER-004 재발 방지).

### 2-3. others 측 — 인덱스/UK 이미 박혀있음

- `IfSndTmGd010310` / `IfSndTdGd010310` 는 `indexes` + `uniqueConstraints(source_refs)` 4/29 시점부터 박혀있음. **수정 0**.

---

## 3. YAML 3건 (코드 신규 0)

### 3-1. `sync-agent-others/.../config/agents/dmz-others-snd-yaksoter.yml`

```yaml
# ── DMZ Others SND — 약수터 ──
# dmz dev (tm_gd010310, td_gd010310 — api-collector 적재) → IF_SND
agent-code: dmz-others-snd-yaksoter
type: SND

steps:
  - id: yaksoter-jewon-snd
    name: 약수터 제원 송신
    factory-key: source-to-if
    source-table: tm_gd010310
    target-table: if_snd_tm_gd010310
    primary-key: sn
    conflict-key: source_refs
    full-copy: true

  - id: yaksoter-wq-snd
    name: 약수터 수질 송신
    factory-key: source-to-if
    source-table: td_gd010310
    target-table: if_snd_td_gd010310
    primary-key: sn
    conflict-key: source_refs
    full-copy: true
```

### 3-2. `sync-agent-bojo-int/.../config/agents/internal-yaksoter-rcv.yml`

```yaml
# ── Internal RCV — 약수터 데이터 수신 ──
# DMZ IF_SND(PG) → Internal IF_RSV(Oracle), Proxy(8093) 경유
agent-code: internal-yaksoter-rcv
type: RCV

steps:
  - id: yaksoter-jewon-rcv
    name: 약수터 제원 수신
    factory-key: source-to-if
    source-table: if_snd_tm_gd010310
    target-table: if_rsv_tm_gd010310
    primary-key: sn
    conflict-key: source_refs
    full-copy: true
    skip-source-status-update: true

  - id: yaksoter-wq-rcv
    name: 약수터 수질 수신
    factory-key: source-to-if
    source-table: if_snd_td_gd010310
    target-table: if_rsv_td_gd010310
    primary-key: sn
    conflict-key: source_refs
    full-copy: true
    skip-source-status-update: true
```

### 3-3. `sync-agent-bojo-int/.../config/agents/internal-yaksoter-loader.yml`

```yaml
# ── Internal Loader — 약수터 데이터 적재 ──
# IF_RSV(Oracle) → Target(Oracle) MERGE 적재
# B-1: 자연키 UK + DO UPDATE (수질값/시설명 변경 시 최신값 갱신)
agent-code: internal-yaksoter-loader
type: LOADER

steps:
  - id: yaksoter-jewon-load
    name: 약수터 제원 적재
    factory-key: simple-load
    source-table: IF_RSV_TM_GD010310
    target-table: TM_GD010310
    merge-key: [BRNCH_NO, BRNCH_STD_CD]

  - id: yaksoter-wq-load
    name: 약수터 수질 적재
    factory-key: simple-load
    source-table: IF_RSV_TD_GD010310
    target-table: TD_GD010310
    merge-key: [BRNCH_NO, YR, QTR, WTSMP_YMD]
```

---

## 4. 빌드

| 모듈 | 명령 |
|---|------|
| sync-agent-common | **변경 없음** — 빌드 X |
| sync-agent-others | `cd sync-agent-others && ./gradlew clean build -x test` |
| sync-agent-bojo-int | `cd sync-agent-bojo-int && ./gradlew clean build -x test` |

> common 변경 없음 — JAR 재배포 불필요.

---

## 5. Internal Oracle 약수터 4 테이블 사전 상태 점검 (Agent 기동 직전)

> **이유**: bojo-int 첫 기동 시 자동 생성 = `nullable=true` / `length=500` / `indexes` 반영 전제. 만약 4/15 작업분으로 이전 스키마(NOT NULL, length=100, 인덱스 X)가 이미 만들어져 있으면 **자동 update 가 일부 변경만 반영** (특히 nullable 변경은 ddl-auto=update 제한). drop 후 재생성 필요할 수도.

### 점검 명령

```sql
-- Internal Oracle 컨테이너(gims_dmz_internal_oracle, 29004) 접속 후
SELECT table_name FROM user_tables WHERE table_name IN
  ('TM_GD010310', 'TD_GD010310', 'IF_RSV_TM_GD010310', 'IF_RSV_TD_GD010310');

-- 존재 시 컬럼 nullable / length 확인
SELECT column_name, nullable, data_length FROM user_tab_columns
WHERE table_name = 'TD_GD010310' AND column_name IN ('ICPT_ARTCL', 'ICPT_ACTN_MTTR');

-- 인덱스 확인
SELECT index_name, column_name FROM user_ind_columns
WHERE table_name = 'TD_GD010310';
```

### 분기

- **(a) 4 테이블 미존재** → 자동 생성 그대로 검증
- **(b) 존재 + 스키마 일치 (nullable=Y, length=500, idx 존재)** → 그대로 진행
- **(c) 존재 + 스키마 불일치** → DROP 4 테이블 후 자동 재생성. 데이터 손실 — 4/15 시점 잔존이라면 가치 없음. 사용자 확인 후 진행.

---

## 6. Agent 2개 기동 + 테이블 자동 생성 검증

### 기동

```bash
# sync-agent-others (8085)
cd sync-agent-others && ./gradlew bootRun

# sync-agent-bojo-int (8092)
cd sync-agent-bojo-int && ./gradlew bootRun
```

> Proxy DMZ(8083) + Proxy Internal(8093) 은 4/29 저녁부터 가동 유지. 재기동 불필요.

### 검증

- bojo-int 기동 로그에 4 테이블 DDL 발생 확인
- Oracle 안 4 테이블 `\d` / `desc` 로 nullable + length + 인덱스 확인
- IF_SND* (others) 는 PG dev 에 4/29 시점 이미 생성됨 — 자동 재생성 X

---

## 7. Orchestrator Agent 3건 등록 (사용자 화면 직접 등록)

### Claude 서포트 — 사용자가 화면에서 입력할 값

| Agent | agent-code | type | sourceDsId | targetDsId | 비고 |
|---|---|---|---|---|---|
| ① 약수터 SND | `dmz-others-snd-yaksoter` | SND | `dev` (api-collector) | `dev` (PG IF_SND) | sync-agent-others (8085) |
| ② 약수터 RCV | `internal-yaksoter-rcv` | RCV | `dev` (PG IF_SND, **Proxy 경유 — X-Manage-Datasource-Id**) | `internal` (Oracle IF_RSV) | sync-agent-bojo-int (8092) |
| ③ 약수터 Loader | `internal-yaksoter-loader` | LOADER | `internal` (Oracle IF_RSV) | `internal` (Oracle Target) | sync-agent-bojo-int (8092) |

### 등록 후 검증
- Orchestrator UI Agent 목록에 3건 표시
- 각 Agent 의 step 카탈로그가 YAML 과 일치 (PipelineRegistry 기동 시 로그)

---

## 8. E2E 수동 실행 (한 사이클)

### 데이터 진입 상태 (4/29 EOD)

- `tm_gd010310`: 854 unique 적재됨
- `td_gd010310`: 1000/1000 적재됨

### 실행 순서

1. **SND** (`dmz-others-snd-yaksoter`) 수동 실행
   - 검증: PG dev `if_snd_tm_gd010310` / `if_snd_td_gd010310` 양쪽 row + `link_status=PENDING` + `execution_id` 채워짐

2. **RCV** (`internal-yaksoter-rcv`) 수동 실행
   - 검증: Oracle `IF_RSV_TM_GD010310` / `IF_RSV_TD_GD010310` 양쪽 row + `LINK_STATUS=PENDING` + `EXECUTION_ID` 채워짐

3. **Loader** (`internal-yaksoter-loader`) 수동 실행
   - 검증:
     - Oracle `TM_GD010310` 854 / `TD_GD010310` 1000 row 적재 + `EXECUTION_ID` + `SOURCE_REFS`
     - IF_RSV 의 `LINK_STATUS=SUCCESS` 전환

### SyncLog 확인
- `sync_log` 4 건 (SND 2 + RCV 2 + Loader 2) 또는 step 별 합산 확인

---

## 9. `/trace-source` 추적 검증 (1건 역추적)

- Target Oracle `TM_GD010310` 임의 1행 선택 → `execution_id` + `source_refs` 추출
- bojo-int → Proxy Internal → Orchestrator → Proxy DMZ → others 역방향 traverse
- 최종 `tm_gd010310` 1행 응답 확인
- 수질 측도 동일 (`TD_GD010310` → `td_gd010310`)

> source_refs 분기: Loader 의 source_refs = `["I:internal:IF_RSV_TM_GD010310:<id>"]` 패턴 → IF_RSV 의 source_refs (`["I:dev:if_snd_tm_gd010310:<id>"]`) → IF_SND source_refs (`["I:dev:tm_gd010310:<id>"]`) 식 체이닝.

---

## 10. dedup 검증 (B-1 기준 — 4/29 의 §B.5b 시나리오 갱신)

> **B-1 (4/29 채택)**: 자연키 UK + DO UPDATE / Source 단 4키 dedup. **두 행 공존 안 함**.
> 4/29 §B.5b 의 B-2 시나리오 (수질값 변조 → 두 행 공존) 는 무효. 아래로 갱신.

| # | 시나리오 | 기대 결과 |
|---|---|---|
| (1) | 같은 yyyy 두 번 실행 (응답 동일) | Target row 변화 0 (DO UPDATE 시 동일 값 재기록 — count 동일) |
| (2) | API Collector 만 두 번 실행 (응답 동일) | Source PG `tm_gd010310` / `td_gd010310` row 변화 0 (UPSERT DO UPDATE 자연키) |
| (3) | 수질값 변조 후 (DB 직접) 재실행 (수질만) | Target `TD_GD010310` 1행 갱신 (최신값) — 두 행 공존 X |
| (4) | 시설명/주소 변조 후 재실행 (제원만) | Target `TM_GD010310` 1행 갱신 — 두 행 공존 X |

> (3)/(4) 는 운영 의도 = "정정/재측정 시 최신값 반영" 과 일치 (B-1 정합).

---

## 11. 회귀 / dev_log / 커밋

### 회귀
- **다른 파이프라인 simple-load 5건** (news-load / nara-bid-load / use-load / jeju-load / saeol-load) 단일 키 동작 — 1건 수동 실행 (api-collect 또는 use 중 1) 으로 회귀 확인. simple-load 코드 변경의 영향 가드.
- bojo-int 기동 시 PipelineRegistry 로그 정상 (Step factory 매핑 OK)
- 호스트 dev 영향 0

### dev_log
- `dev_logs/2026_04/2026-04-30.md` 신규 — 오전/오후 + 파트별 진행도 + 핵심 결정 + 회귀 결과

### 커밋
- 수정 파일 묶음:
  - `sync-agent-bojo-int/.../loader/step/SimpleLoadStep.java` (List 확장)
  - `sync-agent-bojo-int/.../loader/factory/SimpleLoadStepFactory.java` (toList 적용)
  - `sync-agent-bojo-int/.../entity/{iftable,target}/{IfRsv,Tm,Td}Gd010310.java` (4 엔티티 indexes)
  - `sync-agent-others/.../config/agents/dmz-others-snd-yaksoter.yml` (신규)
  - `sync-agent-bojo-int/.../config/agents/internal-yaksoter-{rcv,loader}.yml` (신규 2)
  - `dev_plan/2026_04/30/yaksoter-yaml-e2e.md` (신규)
  - `dev_logs/2026_04/2026-04-30.md` (신규)
- 커밋 메시지: `feat(yaksoter): SND/RCV/Loader YAML + SimpleLoadStep 복수 merge-key + Target 인덱스`
- 4/29 약수터 작업분 + Type A 후처리 등 미커밋분 묶을지 별도 커밋할지 — 사용자 확인

---

## 12. 위험 / 가드

| # | 위험 | 가드 |
|---|------|------|
| 1 | SimpleLoadStep List 확장이 기존 5 파이프라인 회귀 유발 | `toList()` 정규화로 단일 String → List(1) 자동 처리 + 1건 수동 실행 회귀 |
| 2 | bojo-int 첫 기동 시 약수터 4 테이블 자동 생성 안 됨 (다른 엔티티가 자동 생성 막는 경우) | 기동 로그 확인 + 미생성 시 DDL 수동 적용 |
| 3 | Internal Oracle 에 이전 4/15 스키마 잔존 → nullable/length 자동 update 못 따라감 | §5 점검에서 분기 (DROP 후 재생성) |
| 4 | 응답 1000 행 중 수질 컬럼 NULL 비율 높아 Loader 가 NULL 처리 실패 | 4/29 entity NULL 풀기 완료 → 발생 시 §6 후 재현 + 추가 디버그 |
| 5 | `/trace-source` 의 Loader 분기 (source_refs IN) 가 약수터 entity 패턴과 안 맞을 가능성 | execute 후 1건 수동 시연 — 안 맞으면 별도 task 후보 |

---

## 13. 진입 게이트

| Q | 상태 |
|---|------|
| Q1: SimpleLoadStep List 확장 (안 a) 채택 | ✅ 4/30 확인 |
| Q2: 4 엔티티 indexes 만 박기 (VER-004 본 처리는 별도 트랙) | ✅ 4/30 확인 |
| Q3: Agent 등록 = 사용자 화면 직접 (Claude 서포트) | ✅ 4/30 확인 |
| Q4: Internal Oracle 사전 상태 점검 = §5 시점 | ✅ 4/30 확인 |
| Q5: 본 plan 확정 → §0 부터 진행 | 🟡 사용자 OK 대기 |
