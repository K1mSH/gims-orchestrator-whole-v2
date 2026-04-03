# 제주/이용량 내부망 RCV 파이프라인

> 작성일: 2026-04-03
> 순서: 제주 RCV → 이용량 RCV → Loader (별도 계획)

## 목적
DMZ Others Agent(8085)의 IF_SND 데이터를 내부망 IF_RSV로 수신하는 파이프라인 구축

## 현황
- **DMZ SND**: 완료 (dmz-others-snd-jeju.yml, dmz-others-snd-use.yml)
- **내부망 IF_RSV 테이블**: 6개 모두 **미생성** (Oracle 29004)
- **내부망 RCV YAML**: 미작성

## 작업 내용

### Step 1: IF_RSV Oracle DDL 생성 (6개 테이블)

IF_SND(PG) 구조를 Oracle 타입으로 변환. 기존 새올 IF_RSV DDL 패턴 참고.
PK는 자동증분 ID (GENERATED AS IDENTITY), source_refs에 UK.

| # | 테이블명 | PK | 비즈니스 컬럼 수 | 비고 |
|---|---------|----|----|------|
| 1 | IF_RSV_TB_JEJU_JEWON | ID (IDENTITY) | 22 | obsrvt_id가 원본PK → source_refs |
| 2 | IF_RSV_TB_JEJU | ID (IDENTITY) | 11 | rid가 원본PK |
| 3 | IF_RSV_RGETSTGMS01 | — | — | **이미 존재** (새올 RCV용) → 스킵 |
| 4 | IF_RSV_USE_LEGACY_DATA | ID (IDENTITY) | 9 | sn이 원본PK |
| 5 | IF_RSV_USE_STATUS_DATA | ID (IDENTITY) | 8 | sn이 원본PK |
| 6 | IF_RSV_USE_JEJU_DAY | ID (IDENTITY) | 10 | 복합PK(obsrvt_id+obsr_de) → source_refs |

> IF_RSV_RGETSTGMS01은 새올 내부망에서 이미 생성됨 → 제주 SND도 같은 IF_RSV에 적재

**Oracle 타입 변환 규칙:**
- `varchar(N)` → `VARCHAR2(N)`
- `text` → `VARCHAR2(4000)`
- `bigint` → `NUMBER(19)`
- `numeric` → `NUMBER`
- `double precision` → `NUMBER`
- `timestamp` → `TIMESTAMP`

### Step 2: 제주 RCV YAML (internal-jeju-rcv.yml)

```yaml
agent-code: internal-jeju-rcv
type: RCV
steps:
  - id: jeju-jewon-rcv
    source-table: if_snd_tb_jeju_jewon     # DMZ PG (Others 8085 경유 Proxy)
    target-table: IF_RSV_TB_JEJU_JEWON     # Internal Oracle 29004
  - id: jeju-obsv-rcv
    source-table: if_snd_tb_jeju
    target-table: IF_RSV_TB_JEJU
  - id: jeju-stgms-rcv
    source-table: if_snd_rgetstgms01
    target-table: IF_RSV_RGETSTGMS01       # 새올과 공유
table-mappings: 3개
```

### Step 3: 이용량 RCV YAML (internal-use-rcv.yml)

```yaml
agent-code: internal-use-rcv
type: RCV
steps:
  - id: use-legacy-rcv
    source-table: if_snd_use_legacy_data
    target-table: IF_RSV_USE_LEGACY_DATA
  - id: use-status-rcv
    source-table: if_snd_use_status_data
    target-table: IF_RSV_USE_STATUS_DATA
  - id: use-jejuday-rcv
    source-table: if_snd_use_jeju_day
    target-table: IF_RSV_USE_JEJU_DAY
table-mappings: 3개
```

### Step 4: Orchestrator 등록 SQL

- Agent 2개 등록 (internal-jeju-rcv, internal-use-rcv)
- 테이블 등록 (source: IF_SND 6개 @ext_others_if, target: IF_RSV 5개 @internal)

### Step 5: Others DMZ datasource 등록 확인

내부망 RCV가 Proxy 경유로 Others(8085) DB를 읽어야 함.
→ Orchestrator에 `ext_others_if` datasource가 등록되어 있는지 확인 필요.

## 수정 대상 파일

| 파일 | 작업 |
|------|------|
| `scripts/oracle-init-internal-jeju-use.sql` | **신규** — IF_RSV 5개 DDL |
| `sync-agent-bojo-int/config/agents/internal-jeju-rcv.yml` | **신규** |
| `sync-agent-bojo-int/config/agents/internal-use-rcv.yml` | **신규** |
| `scripts/orchestrator-internal-jeju-use-register.sql` | **신규** — Agent + 테이블 등록 |

## 영향 범위

- sync-agent-bojo-int: YAML 추가만 (코드 수정 없음)
- 내부망 Oracle: DDL 실행
- Orchestrator DB: Agent/테이블 등록
- **기존 파이프라인에 영향 없음**

## 주의사항

- IF_RSV_RGETSTGMS01은 새올과 제주 양쪽에서 사용 → 새올 RCV와 제주 RCV 동시 실행 시 source_refs로 구분됨 (새올: Oracle PK 기반, 제주: PG perm_nt_no 기반)
- Others Agent(8085)의 IF_SND는 PG, 내부망 IF_RSV는 Oracle → SourceToIfStep에서 PG→Oracle 크로스DB 이미 지원됨 (새올 RCV에서 검증 완료)
