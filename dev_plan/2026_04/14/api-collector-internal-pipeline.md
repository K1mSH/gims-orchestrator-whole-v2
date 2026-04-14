# API Collector 수집 데이터 → 내부망 전송 파이프라인

> 작성일: 2026-04-14
> 상태: 계획
> 전략 확인: ARCHITECTURE.md (SND→RCV→Loader 패턴, 읽기=JPA/쓰기=JDBC)

## 개요

api-collector(DMZ)가 수집한 **뉴스(tm_gd014001)**, **나라장터(tm_gd014000)** 데이터를
기존 SND → RCV → Loader 패턴으로 내부망 Oracle에 적재한다.

두 테이블 모두 이미 내부망 GIMS 기준으로 컬럼명이 설계되어 있어,
Oracle 타입 변환만 주의하면 된다.

## 대상 테이블

### tm_gd014001 (네이버 뉴스)

| 컬럼 | PG 타입 | Oracle 타입 | 비고 |
|------|---------|-------------|------|
| sn | BIGINT (IDENTITY) | NUMBER (IDENTITY) | PK |
| ttl | VARCHAR(500) | VARCHAR2(500) | 제목, NOT NULL |
| orgnl_url | VARCHAR(500) | VARCHAR2(500) | 원본URL, UK |
| link | VARCHAR(1000) | VARCHAR2(1000) | 링크 |
| expln | VARCHAR(4000) | VARCHAR2(4000) | 설명 |
| pstg_ymd | VARCHAR(500) | VARCHAR2(500) | 게시일자 |
| press_nm | VARCHAR(100) | VARCHAR2(100) | 언론사명, default '언론사' |
| vstr_cnt | BIGINT | NUMBER | 방문자수, default 0 |
| use_yn | VARCHAR(1) | CHAR(1) | 사용여부, default 'Y' |
| reg_ymd | VARCHAR(8) | VARCHAR2(8) | 등록일자 |

### tm_gd014000 (나라장터 입찰공고)

| 컬럼 | PG 타입 | Oracle 타입 | 비고 |
|------|---------|-------------|------|
| sn | BIGINT (IDENTITY) | NUMBER (IDENTITY) | PK |
| type | VARCHAR(100) | VARCHAR2(100) | 유형 (공사/용역/외자/물품) |
| bid_pbanc_no | VARCHAR(100) | VARCHAR2(100) | 입찰공고번호, UK |
| bid_pbanc_nm | VARCHAR(1000) | VARCHAR2(1000) | 입찰공고명 |
| dmd_inst_nm | VARCHAR(200) | VARCHAR2(200) | 수요기관명 |
| bid_ddln_dt | VARCHAR(100) | VARCHAR2(100) | 입찰마감일시 |
| bid_pbanc_dtl_lnkg | VARCHAR(1000) | VARCHAR2(1000) | 공고상세링크 |
| use_yn | VARCHAR(1) | CHAR(1) | 사용여부, default 'Y' |
| reg_ymd | VARCHAR(8) | VARCHAR2(8) | 등록일자 |

## 파이프라인 흐름

```
[api-collector (DMZ PG 29001, api_collector DB)]
  tm_gd014001 (뉴스)
  tm_gd014000 (나라장터)
      ↓
[Phase 1: SND — sync-agent-others (DMZ 8085)]
  dmz-others-snd-api-collect (신규 YAML)
  source-to-if 공통 Step 사용
  tm_gd014001 → if_snd_tm_gd014001
  tm_gd014000 → if_snd_tm_gd014000
      ↓
[Phase 2: RCV — sync-agent-bojo-int (Internal 8092)]
  internal-api-collect-rcv (신규 YAML)
  Proxy(8093) 경유로 DMZ IF_SND 읽기
  source-to-if 공통 Step 사용
  if_snd_tm_gd014001 → if_rsv_tm_gd014001
  if_snd_tm_gd014000 → if_rsv_tm_gd014000
      ↓
[Phase 3: Loader — sync-agent-bojo-int (Internal 8092)]
  internal-api-collect-loader (신규 YAML)
  IF_RSV → Oracle Target 적재
  if_rsv_tm_gd014001 → TM_GD014001
  if_rsv_tm_gd014000 → TM_GD014000
```

## Phase 1: SND (DMZ)

### 수정 파일

| 파일 | 변경 |
|------|------|
| `sync-agent-others/.../config/agents/dmz-others-snd-api-collect.yml` | **신규** |

### YAML 설계

```yaml
# ── DMZ Others SND — API Collector 수집 데이터 (2테이블) ──
agent-code: dmz-others-snd-api-collect
type: SND

steps:
  - id: news-snd
    name: 뉴스 송신
    factory-key: source-to-if
    source-table: tm_gd014001
    target-table: if_snd_tm_gd014001
    primary-key: sn
    conflict-key: source_refs
    full-copy: true

  - id: nara-bid-snd
    name: 나라장터 송신
    factory-key: source-to-if
    source-table: tm_gd014000
    target-table: if_snd_tm_gd014000
    primary-key: sn
    conflict-key: source_refs
    full-copy: true
```

> **full-copy: true** — 뉴스/나라장터는 date-column이 없고,
> use_yn 변경 등 업데이트도 반영해야 하므로 전체 동기화.

### datasource 등록

SND의 source DB = api_collector (PG 29001)
- Orchestrator에 `api-collector-dmz` datasource가 이미 등록되어 있는지 확인 필요
- 없으면 등록: host=localhost, port=29001, db=api_collector, type=POSTGRESQL

## Phase 2: RCV (Internal)

### 수정 파일

| 파일 | 변경 |
|------|------|
| `sync-agent-bojo-int/.../config/agents/internal-api-collect-rcv.yml` | **신규** |

### YAML 설계

```yaml
# ── Internal RCV — API Collector 데이터 수신 ──
# DMZ IF_SND(PG) → 내부망 IF_RSV(Oracle) 적재
agent-code: internal-api-collect-rcv
type: RCV

steps:
  - id: news-rcv
    name: 뉴스 수신
    factory-key: source-to-if
    source-table: if_snd_tm_gd014001
    target-table: if_rsv_tm_gd014001
    primary-key: id
    conflict-key: source_refs
    full-copy: true

  - id: nara-bid-rcv
    name: 나라장터 수신
    factory-key: source-to-if
    source-table: if_snd_tm_gd014000
    target-table: if_rsv_tm_gd014000
    primary-key: id
    conflict-key: id
    full-copy: true
```

> RCV는 IF_SND → IF_RSV 단순 카피. PG→Oracle 간 타입 변환은 SourceToIfStep이 자동 처리.

## 엔티티

### IF_RSV 엔티티 (bojo-int, JPA 읽기용)

기존 패턴: 원본 데이터 컬럼 + IF 메타 5개 컬럼 (id, source_refs, link_status, extracted_at, updated_at, execution_id)

| 파일 | 테이블 | 비고 |
|------|--------|------|
| `entity/iftable/IfRsvTmGd014001.java` | IF_RSV_TM_GD014001 | 뉴스 IF 수신 |
| `entity/iftable/IfRsvTmGd014000.java` | IF_RSV_TM_GD014000 | 나라장터 IF 수신 |

> ddl-auto: update → JPA가 Oracle에 자동 생성. 수동 DDL 불필요.

#### IfRsvTmGd014001 컬럼

| 컬럼 | 타입 | 비고 |
|------|------|------|
| id | Long (IDENTITY) | PK |
| ttl | String(500) | 제목 |
| orgnlUrl | String(500) | 원본URL |
| link | String(1000) | 링크 |
| expln | String(4000) | 설명 |
| pstgYmd | String(500) | 게시일자 |
| pressNm | String(100) | 언론사명 |
| vstrCnt | Long | 방문자수 |
| useYn | String(1) | 사용여부 |
| regYmd | String(8) | 등록일자 |
| sourceRefs | String(4000) | IF 메타 |
| linkStatus | String(20) | IF 메타 (default: PENDING) |
| extractedAt | LocalDateTime | IF 메타 |
| updatedAt | LocalDateTime | IF 메타 |
| executionId | String | IF 메타 |

#### IfRsvTmGd014000 컬럼

| 컬럼 | 타입 | 비고 |
|------|------|------|
| id | Long (IDENTITY) | PK |
| type | String(100) | 유형 |
| bidPbancNo | String(100) | 입찰공고번호 |
| bidPbancNm | String(1000) | 입찰공고명 |
| dmdInstNm | String(200) | 수요기관명 |
| bidDdlnDt | String(100) | 입찰마감일시 |
| bidPbancDtlLnkg | String(1000) | 공고상세링크 |
| useYn | String(1) | 사용여부 |
| regYmd | String(8) | 등록일자 |
| sourceRefs | String(4000) | IF 메타 |
| linkStatus | String(20) | IF 메타 (default: PENDING) |
| extractedAt | LocalDateTime | IF 메타 |
| updatedAt | LocalDateTime | IF 메타 |
| executionId | String | IF 메타 |

### Target 엔티티 (bojo-int, 참조용)

Loader는 JDBC로 쓰기 때문에 엔티티가 필수는 아니지만,
DynamicEntityManagerService 스캔 대상 + DDL 자동 생성을 위해 추가.

| 파일 | 테이블 | 비고 |
|------|--------|------|
| `entity/target/TmGd014001.java` | TM_GD014001 | 뉴스 Target |
| `entity/target/TmGd014000.java` | TM_GD014000 | 나라장터 Target |

> Target 엔티티는 IF 메타 컬럼 없음. 순수 비즈니스 컬럼만.

### 수정 파일 목록 (엔티티)

| 파일 | 변경 |
|------|------|
| `sync-agent-bojo-int/.../entity/iftable/IfRsvTmGd014001.java` | **신규** |
| `sync-agent-bojo-int/.../entity/iftable/IfRsvTmGd014000.java` | **신규** |
| `sync-agent-bojo-int/.../entity/target/TmGd014001.java` | **신규** |
| `sync-agent-bojo-int/.../entity/target/TmGd014000.java` | **신규** |

## Phase 3: Loader (Internal)

### 선택지

두 테이블 모두 **변환 없이 1:1 복사**에 가까움 (IF 컬럼명 = Target 컬럼명).
커스텀 Step 없이 **DefaultLoadStep** 또는 **source-to-if(SIMPLE_COPY)** 패턴으로 충분한지 검토.

#### Option A: source-to-if 재활용 (SIMPLE_COPY)
- IF_RSV를 source로, TM_GD014001/000을 target으로 지정
- 장점: 코드 변경 없음, YAML만 추가
- 단점: IF 메타 컬럼(source_refs, link_status 등)이 target에도 붙음 → 불필요

#### Option B: 전용 LoadStep (ApiCollectLoadStep)
- IF_RSV에서 PENDING 읽기 → target MERGE → IF 상태 업데이트
- 기존 Loader 패턴과 동일
- 1:1 매핑이라 로직 단순

#### 선택: Option B (전용 LoadStep)
- 기존 Loader 패턴 일관성 유지
- IF 메타 컬럼 분리, SyncLog 기록
- 단, 1:1 단순 매핑이라 **범용 SimpleLoadStep**으로 만들면 향후 재사용 가능

### 수정 파일

| 파일 | 변경 |
|------|------|
| `sync-agent-bojo-int/.../loader/step/SimpleLoadStep.java` | **신규** — 1:1 매핑 범용 Loader |
| `sync-agent-bojo-int/.../loader/factory/SimpleLoadStepFactory.java` | **신규** — Factory 등록 |
| `sync-agent-bojo-int/.../config/agents/internal-api-collect-loader.yml` | **신규** |
| `scripts/ddl/internal-oracle/create-api-collect-tables.sql` | **신규** — Oracle DDL |

### Loader YAML 설계

```yaml
# ── Internal Loader — API Collector 데이터 적재 ──
# IF_RSV(Oracle) → Target(Oracle) MERGE 적재
agent-code: internal-api-collect-loader
type: LOADER

steps:
  - id: news-load
    name: 뉴스 적재
    factory-key: simple-load
    source-table: IF_RSV_TM_GD014001
    target-table: [TM_GD014001]
    merge-key: ORGNL_URL

  - id: nara-bid-load
    name: 나라장터 적재
    factory-key: simple-load
    source-table: IF_RSV_TM_GD014000
    target-table: [TM_GD014000]
    merge-key: BID_PBANC_NO
```

> **merge-key**: PK(sn)는 IDENTITY라 환경별로 다름.
> UK(orgnl_url, bid_pbanc_no)가 비즈니스 키 → MERGE 기준으로 사용.

### SimpleLoadStep 설계

```
execute():
1. IF_RSV에서 PENDING 건 조회 (JPA native query)
2. 건별 순차 처리:
   a. IF 컬럼에서 메타(source_refs, link_status 등) 제외
   b. Target MERGE (merge-key 기준)
      - NOT MATCHED → INSERT (SN은 IDENTITY 자동 채번)
      - MATCHED → UPDATE
   c. successIds / failedIds 수집
3. IF 상태 일괄 업데이트
4. SyncLog 기록
```

> 범용 설계: merge-key와 exclude-columns만 YAML로 받으면
> 어떤 1:1 테이블이든 처리 가능.

## Oracle DDL

```sql
-- IF_RSV 테이블 (bojo-int JPA ddl-auto가 생성하므로 참고용)

-- Target 테이블 (수동 생성 필요)
CREATE TABLE TM_GD014001 (
    SN          NUMBER GENERATED BY DEFAULT AS IDENTITY,
    TTL         VARCHAR2(500)  NOT NULL,
    ORGNL_URL   VARCHAR2(500)  NOT NULL,
    LINK        VARCHAR2(1000),
    EXPLN       VARCHAR2(4000),
    PSTG_YMD    VARCHAR2(500),
    PRESS_NM    VARCHAR2(100)  DEFAULT '언론사',
    VSTR_CNT    NUMBER         DEFAULT 0,
    USE_YN      CHAR(1)        DEFAULT 'Y',
    REG_YMD     VARCHAR2(8),
    CONSTRAINT PK_TM_GD014001 PRIMARY KEY (SN),
    CONSTRAINT UK_TM_GD014001 UNIQUE (ORGNL_URL)
);

CREATE TABLE TM_GD014000 (
    SN                  NUMBER GENERATED BY DEFAULT AS IDENTITY,
    TYPE                VARCHAR2(100),
    BID_PBANC_NO        VARCHAR2(100),
    BID_PBANC_NM        VARCHAR2(1000),
    DMD_INST_NM         VARCHAR2(200),
    BID_DDLN_DT         VARCHAR2(100),
    BID_PBANC_DTL_LNKG  VARCHAR2(1000),
    USE_YN              CHAR(1)        DEFAULT 'Y',
    REG_YMD             VARCHAR2(8),
    CONSTRAINT PK_TM_GD014000 PRIMARY KEY (SN),
    CONSTRAINT UK_TM_GD014000 UNIQUE (BID_PBANC_NO)
);
```

## 작업 순서

| 순번 | 작업 | 모듈 |
|------|------|------|
| 1 | IF_RSV 엔티티 2개 + Target 엔티티 2개 생성 | sync-agent-bojo-int |
| 2 | Oracle Target DDL 작성 + 실행 | scripts/ddl |
| 3 | SND YAML 추가 | sync-agent-others |
| 4 | RCV YAML 추가 | sync-agent-bojo-int |
| 5 | SimpleLoadStep + Factory 구현 | sync-agent-bojo-int |
| 6 | Loader YAML 추가 | sync-agent-bojo-int |
| 7 | Orchestrator에 datasource/agent 등록 확인 | sync-orchestrator |
| 8 | 빌드 + E2E 테스트 | 전체 |

## 기존 패턴과의 비교

| 항목 | 기존 (제원/관측) | 이번 (뉴스/나라장터) |
|------|-----------------|-------------------|
| SND | source-to-if | source-to-if (동일) |
| RCV | source-to-if | source-to-if (동일) |
| Loader | 커스텀 Step (5테이블 체인 등) | **SimpleLoadStep** (1:1 매핑) |
| 변환 | 룩업, 집계, EAV 확장 등 | 없음 (컬럼명 동일) |
| MERGE 키 | obsv_code, source_refs 등 | UK (orgnl_url, bid_pbanc_no) |

## 리스크

- **PG→Oracle 타입 변환**: VARCHAR→VARCHAR2는 자동, BIGINT→NUMBER도 문제없음.
  단, PG의 `to_char(now(), 'YYYYMMDD')` 같은 default는 Oracle에서 동작 안 함
  → IF_RSV 적재 시점에 이미 값이 들어와 있으므로 DDL default로 충분
- **IDENTITY 충돌**: DMZ SN과 Oracle SN은 별도 채번 → MERGE는 UK 기준이라 무관
- **대량 데이터**: 뉴스/나라장터는 건수 적음 (일 수십~수백건) → 성능 이슈 없음
