# Agent YAML 작성 가이드

> 작성일: 2026-04-24
> 대상 독자: Agent(RCV / LOADER / SND / Provide)를 신규 추가하거나 수정하는 개발자
> 관련 코드: `infolink-agent-{bojo-dmz|bojo-internal|others-dmz|provide-dmz}/src/main/resources/config/agents/*.yml`

---

## 목차

1. [개요](#1-개요)
2. [최상위 구조](#2-최상위-구조)
3. [Step 공통 필드](#3-step-공통-필드)
4. [Factory 별 상세](#4-factory-별-상세)
    - 4.1 [`source-to-if` — 범용 카피 (필수 숙지)](#41-source-to-if--범용-카피)
    - 4.2 [`simple-load` — 범용 1:1 MERGE Loader](#42-simple-load--범용-11-merge-loader)
    - 4.3 [`source-to-if-link` — RCV Link 기반 증분](#43-source-to-if-link--rcv-link-기반-증분)
    - 4.4 [`link-update` — Link 테이블 갱신](#44-link-update--link-테이블-갱신)
    - 4.5 [`dmz-bojo-load` — DMZ Loader 전용](#45-dmz-bojo-load--dmz-loader-전용)
    - 4.6 [`internal-bojo-load` — Internal Loader 전용](#46-internal-bojo-load--internal-loader-전용)
    - 4.7 [`jeju-*-load` — 제주 커스텀 Loader (3종)](#47-jeju--load--제주-커스텀-loader-3종)
    - 4.8 [`use-load` — 이용량 Loader](#48-use-load--이용량-loader)
    - 4.9 [`saeol-link-plan-snd` — 새올 LINK_PLAN SND](#49-saeol-link-plan-snd--새올-link_plan-snd)
5. [옵션 레퍼런스](#5-옵션-레퍼런스)
6. [사례별 템플릿](#6-사례별-템플릿)
7. [주의사항 / 자주 하는 실수](#7-주의사항--자주-하는-실수)
8. [등록 절차](#8-등록-절차)

---

## 1. 개요

### 1.1 Agent YAML 이란?

`config/agents/*.yml` 파일은 **Agent의 파이프라인 정의**다. "어떤 소스에서 어떤 타겟으로, 어떤 방식으로 데이터를 옮길지"를 선언적으로 기술한다. 파일 하나 = 논리적 Agent 하나.

- Spring 부팅 시 `AgentConfigLoader` 가 `classpath:config/agents/*.yml` 을 스캔
- SnakeYAML 로 파싱하여 `AgentDefinition` 리스트 생성
- `PipelineAssembler` 가 각 Step 의 `factory-key` 로 적절한 `StepFactory` 를 찾아 실행 가능한 파이프라인으로 조립
- `PipelineRegistry` 에 `(agentCode, modeId) → PipelineRunner` 로 등록

### 1.2 파일 위치 (모듈별)

| 모듈 | 경로 |
|---|---|
| DMZ 통합 Agent (bojo) | `infolink-agent-bojo-dmz/src/main/resources/config/agents/` |
| Internal 통합 Agent (bojo-internal) | `infolink-agent-bojo-internal/src/main/resources/config/agents/` |
| DMZ Others SND | `infolink-agent-others-dmz/src/main/resources/config/agents/` |
| API 제공 Agent (provide) | `infolink-agent-provide/src/main/resources/config/agents/` |

### 1.3 파일명 규칙 (관례)

| 위치 | 규칙 | 예 |
|---|---|---|
| DMZ RCV | `dmz-bojo-rcv-{업체}.yml` | `dmz-bojo-rcv-daejeon.yml` |
| DMZ Loader | `dmz-bojo-loader.yml` | — |
| DMZ SND | `dmz-bojo-snd.yml` | — |
| Internal RCV | `internal-{그룹}-rcv.yml` | `internal-saeol-rcv.yml` |
| Internal Loader | `internal-{그룹}-loader.yml` | `internal-jeju-loader.yml` |
| DMZ Others SND | `dmz-others-snd-{그룹}.yml` | `dmz-others-snd-saeol.yml` |
| Provide | `provide-{테이블명}.yml` | `provide-tm-gd000203.yml` |

> 파일명 자체는 스캐너가 식별에 쓰지 않지만 (`agent-code` 가 진짜 식별자), 가독성을 위해 관례를 지킨다.

---

## 2. 최상위 구조

```yaml
agent-code: <고유 Agent 코드>    # 필수
type: <RCV | LOADER | SND>       # 필수
steps:                           # 필수 (배열, 1개 이상)
  - id: <Step 고유 ID>
    name: <Step 한글명>
    factory-key: <Factory 키>
    # … Factory 별 옵션
```

### 2.1 `agent-code` (필수)

- 전체 Agent 목록에서 **고유해야 함** (동일 모듈 내에서 충돌 금지)
- Orchestrator DB `agents` 테이블의 `agent_code` 와 **정확히 일치** 해야 함
- 관례: 파일명과 동일하게 (`provide-tm-gd000203`, `dmz-bojo-rcv-daejeon`)

### 2.2 `type` (필수)

| 값 | 의미 | 파이프라인 방향 |
|---|---|---|
| `RCV` | 수신 | 외부 소스 DB → IF 테이블 |
| `LOADER` | 적재 | IF 테이블 → 실테이블 (또는 provide: 외부 소스 → 제공 테이블) |
| `SND` | 송신 | 실테이블 → IF_SND 테이블 |

> **provide Agent는 관례상 `LOADER`** — 파이프라인 종점 성격이며, 소비자 관점에서 "적재된 제공 테이블"로 이해된다.

### 2.3 `steps` (필수, 배열)

- 파이프라인 내 Step 순서대로 기술
- 각 Step 은 독립적으로 실행되며 실패 시 후속 Step 이 스킵되거나 경고 로그 남김
- `source-table` / `target-table` 은 **단일 문자열 / 배열 양쪽 허용** — `AgentConfigLoader` 가 리스트로 정규화함
- 따라서 이 두 필드는 "1개든 여러 개든" 자유롭게 기술 가능

### 2.4 선택 필드

```yaml
# select-tables: 프론트 UI 용 소스 테이블 목록 (미지정 시 steps에서 자동 수집)
select-tables:
  - sec_jewon_view
  - sec_obsvdata_view

# table-mappings: 모니터링/검증용 명시적 매핑 (미지정 시 steps에서 자동 생성)
table-mappings:
  - name: jewon
    source: [sec_jewon_view]
    target: [if_rsv_sec_jewon]
```

대부분 생략 — 자동 생성으로 충분.

---

## 3. Step 공통 필드

모든 Step 은 아래 3개를 반드시 포함한다.

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `id` | ✅ | string | Step 고유 ID (Agent 내 고유). `jewon-extract` 같이 **역할 기반 네이밍** |
| `name` | ✅ | string | 한글 Step 이름 (로그/UI 표시용) |
| `factory-key` | ✅ | string | Factory 매핑 키 — [4. Factory 별 상세](#4-factory-별-상세) 참조 |

### 3.1 `id` 네이밍 관례

- 역할이 드러나게: `jewon-extract`, `obsvdata-snd-extract`, `saeol-stgms01-load`
- `agent-code` 와 동일하게 짓지 말 것 (정보 없음) — Step 1개 Agent라도 `provide-tm-gd000203-copy` 처럼 접미사로 역할 표시
- `id` 의 첫 `-` 이전 단어는 `SourceToTargetStepFactory.deriveMappingName()` 에서 SyncLog 의 `mappingName` 으로 사용됨 (`jewon-extract` → `jewon`). SyncLog 모니터링 가독성을 위해 첫 단어를 의미 있는 그룹명으로 정할 것.

---

## 4. Factory 별 상세

### 4.1 `source-to-if` — 범용 카피

> **가장 많이 쓰는 Factory.** RCV / SND / Internal RCV / Provide 공통. "외부 DB에서 우리 테이블로 옮기기" 패턴 대부분을 커버한다.

- Factory: `common` 모듈의 `SourceToTargetStepFactory`
- Step: `SourceToTargetStep` (SIMPLE_COPY 모드)
- 이름 유래: 초기에는 IF 테이블만 타겟으로 썼으나, 이후 일반화되어 provide 의 `api_prv_*` 등도 같은 Factory 처리. **`source-to-if` 이름은 20여 개 YAML 호환을 위해 유지** (향후 `source-to-target` 으로 마이그레이션 예정).

#### 지원 옵션

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `source-table` | ✅ | string / [string] | 소스 테이블명 (`SCHEMA.TABLE` 형식 지원) |
| `target-table` | ✅ | string / [string] | 타겟 테이블명 |
| `primary-key` | ○ | string | 소스 PK 컬럼 (복합은 콤마 구분: `"a,b,c"`) |
| `conflict-key` | ○ | string | UPSERT ON CONFLICT 기준 컬럼 (미지정 시 PK 사용) |
| `full-copy` | ○ | bool | `true` 면 `link_status` 필터 없이 전체 조회 (기본 `false`) |
| `skip-source-status-update` | ○ | bool | 소스에 `link_status` 갱신 스킵 (View/외부 DB) |
| `date-column` | ○ | string | 시간범위 실행 시 날짜 컬럼 |
| `time-column` | ○ | string | 시간범위 실행 시 시간 컬럼 |
| `target-meta-columns` | ○ | [string] | 타겟 메타 컬럼 명시 — 미지정 시 IF 표준 5종 사용. [5.4 절](#54-메타-컬럼-관련) 참조 |
| `exclude-insert-columns` | ○ | [string] | INSERT 제외 컬럼 — 미지정 시 `[id, sn]` (auto-increment 충돌 회피). [5.5 절](#55-insert-제외-컬럼) 참조 |

#### 예 1: RCV 기본 (`dmz-bojo-rcv-daejeon.yml`)

```yaml
- id: jewon-extract
  name: 제원 데이터 추출
  factory-key: source-to-if
  source-table: sec_jewon_view
  target-table: if_rsv_sec_jewon
  primary-key: obsv_code
  conflict-key: source_refs
  full-copy: true                 # 마스터 테이블은 전체 복사
  skip-source-status-update: true # 외부 View → 업데이트 불가
```

#### 예 2: Provide (`provide-tm-gd000203.yml`)

```yaml
- id: provide-tm-gd000203
  name: 공공관정 가뭄지원 복사
  factory-key: source-to-if
  source-table: TM_GD000203
  target-table: api_prv_tm_gd000203
  primary-key: SN
  conflict-key: source_refs
  target-meta-columns:            # provide 타겟은 link_status/extracted_at 없음
    - source_refs
    - execution_id
    - updated_at
```

#### 동작 흐름

1. 소스에서 `link_status IN ('PENDING','RESYNC','FAILED')` (또는 `full-copy: true` 이면 전체) 조회
2. PK 기준 중복 제거 (소스 View JOIN 중복 방지)
3. 타겟에 배치 UPSERT (ON CONFLICT / MERGE INTO, batchSize=1000)
4. 소스 `link_status` = SUCCESS / FAILED 업데이트 (`skip-source-status-update: false` 일 때)
5. SyncLog 저장 (매핑 단위 read/write/skip count)

---

### 4.2 `simple-load` — 범용 1:1 MERGE Loader

> IF → Target 1:1 MERGE. 비즈니스 변환 없이 키 기반으로 덮어씌우는 Internal Loader용.

- Factory: `bojo-internal` 모듈의 `SimpleLoadStepFactory`
- Step: `SimpleLoadStep`
- 사용처: API Collector 데이터 적재 (뉴스, 나라장터)

#### 지원 옵션

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `source-table` | ✅ | string / [string] | 소스 IF 테이블 (첫 번째만 사용) |
| `target-table` | ✅ | string / [string] | 타겟 테이블 (첫 번째만 사용) |
| `merge-key` | ✅ | string | MERGE ON 기준 컬럼 (UK) |

#### 예 (`internal-api-collect-loader.yml`)

```yaml
- id: news-load
  name: 뉴스 적재
  factory-key: simple-load
  source-table: IF_RSV_TM_GD014001
  target-table: TM_GD014001
  merge-key: ORGNL_URL
```

---

### 4.3 `source-to-if-link` — RCV Link 기반 증분

> Link 테이블(`link_ngwis`) 기반 증분 추출 — **bojo 전용**. 외부 업체의 관측 데이터가 Link 테이블에 신규 등록될 때마다 추출.

- Factory: `bojo` 모듈의 `LinkSourceToIfStepFactory`
- Step: `SourceToTargetStep` (CUSTOM_STAGING 모드, `LinkTableObsvDataFetcher` 주입)

#### 지원 옵션

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `source-table` | ✅ | string | 소스 View (예: `sec_obsvdata_view`) |
| `target-table` | ✅ | string | 타겟 IF 테이블 |
| `link-table` | ✅ | string | Link 테이블명 (보통 `link_ngwis`) |
| `link-jewon-source` | ✅ | string | 관측소 마스터 View (JOIN용) |
| `primary-key` | ○ | string | 소스 PK |
| `conflict-key` | ○ | string | UPSERT 기준 |
| `date-column` / `time-column` | ○ | string | 시간범위 실행 시 사용 |
| `exclude-insert-columns` | ○ | [string] | INSERT 제외 |

#### 예 (`dmz-bojo-rcv-daejeon.yml`)

```yaml
- id: obsvdata-extract
  name: 관측데이터 추출 (Link 기반)
  factory-key: source-to-if-link
  source-table: sec_obsvdata_view
  target-table: if_rsv_sec_obsvdata
  primary-key: id
  conflict-key: source_refs
  date-column: obsv_date
  time-column: obsv_time
  link-table: link_ngwis
  link-jewon-source: sec_jewon_view
```

> **참고**: 조건 실행(`conditions`) / 시간범위(`timeRange`) 가 있으면 Link 기반이 아니라 SIMPLE_COPY 경로로 자동 오버라이드됨 — 재동기화 목적 우선.

---

### 4.4 `link-update` — Link 테이블 갱신

> IF 테이블 적재 결과를 Link 테이블에 반영 (`link_ngwis.link_status` 갱신). RCV 의 마지막 Step.

- Factory: `bojo` 모듈의 `LinkUpdateStepFactory`
- Step: `LinkTableUpdateStep`

#### 지원 옵션

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `if-table` | ✅ | string | 갱신 근거가 될 IF 테이블 |
| `link-table` | ✅ | string | 갱신 대상 Link 테이블 |

#### 예

```yaml
- id: link-table-update
  name: Link 테이블 갱신
  factory-key: link-update
  if-table: if_rsv_sec_obsvdata
  link-table: link_ngwis
```

---

### 4.5 `dmz-bojo-load` — DMZ Loader 전용

> IF_RSV → DMZ Target (`sec_jewon`, `sec_obsvdata`) 적재. **bojo 전용** 단일 Step (Agent 전체 1개).

- Factory: `DmzBojoLoadStepFactory`
- Step: `DmzBojoLoadStep`

> **⚠️ 특이점**: 실제 IF/Target 테이블명은 **`application.yml` 의 `loader.*` 프로퍼티에서 읽음** (`@Value` 주입). YAML 의 `source-table` / `target-table` 은 **모니터링·표시용**이며 실행 동작에는 영향 없음.

#### 예 (`dmz-bojo-loader.yml`)

```yaml
- id: dmz-bojo-load
  name: DMZ 적재
  factory-key: dmz-bojo-load
  source-table: if_rsv_sec_jewon, if_rsv_sec_obsvdata
  target-table: sec_jewon, sec_obsvdata
```

> 위 예에서 `source-table` 이 콤마로 이어진 것은 레거시 표기 — 정식은 배열:
> ```yaml
> source-table: [if_rsv_sec_jewon, if_rsv_sec_obsvdata]
> ```

---

### 4.6 `internal-bojo-load` — Internal Loader 전용

> IF_RSV → GIMS 실테이블 (`TM_GD970001`, `PM_GD970201`, `TM_GD980002`, `TM_GD970101`) 적재. 제원·관측·Link·결과 4개 타겟에 분산 저장.

- Factory: `InternalBojoLoadStepFactory`
- Step: `InternalBojoLoadStep`

#### 지원 옵션

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `source-table` | ✅ | string / [string] | 보통 `IF_RSV_SEC_OBSVDATA` 1개 |
| `target-table` | ✅ | [string] | 4개 타겟 배열 — **테이블명 패턴으로 역할 자동 매핑** |

#### 타겟 역할 매핑 규칙 (하드코딩)

| 포함 패턴 | 역할 | 기본값 |
|---|---|---|
| `970001` | 제원 타겟 | `TM_GD970001` |
| `970201` | 관측데이터 타겟 | `PM_GD970201` |
| `980002` | Link 타겟 | `TM_GD980002` |
| `970101` | 결과 타겟 | `TM_GD970101` |

#### 예 (`internal-bojo-loader.yml`)

```yaml
- id: internal-bojo-load
  name: 내부망 GIMS 적재
  factory-key: internal-bojo-load
  source-table: IF_RSV_SEC_OBSVDATA
  target-table: [PM_GD970201, TM_GD970101, TM_GD980002]
```

---

### 4.7 `jeju-*-load` — 제주 커스텀 Loader (3종)

> 제주 데이터 전용. 제원 1→5 분산, 관측 단일/다심도 분기, 이용시설 시설+일자료 분산.

- Factory: `JejuLoadStepFactory` (3종 factory-key 모두 처리)

| factory-key | Step 클래스 | 설명 |
|---|---|---|
| `jeju-jewon-load` | `JejuJewonLoadStep` | 제원 1개 IF → 5개 타겟 분산 |
| `jeju-obsvdata-load` | `JejuObsvdataLoadStep` | 관측데이터 → 단일심도 / 다심도 분기 |
| `jeju-facility-load` | `JejuFacilityLoadStep` | 이용시설 → 시설 + 일자료 |

#### 지원 옵션 (공통)

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `source-table` | ✅ | string / [string] | IF 소스 (첫 번째만 사용) |
| `target-table` | ✅ | [string] | 타겟 배열 (Step 내부에서 역할 분리) |

#### 예 (`internal-jeju-loader.yml`)

```yaml
- id: jeju-jewon-load
  name: 제주 제원 적재
  factory-key: jeju-jewon-load
  source-table: IF_RSV_TB_JEJU_JEWON
  target-table: [TM_GD970001, TM_GD120001, TM_GD970130, TM_GD970002, TM_GD970101]

- id: jeju-obsvdata-load
  name: 제주 관측데이터 적재
  factory-key: jeju-obsvdata-load
  source-table: IF_RSV_TB_JEJU
  target-table: [PM_GD970201, PM_GD970202]
```

---

### 4.8 `use-load` — 이용량 Loader

> 이용량 2개 IF (`IF_RSV_USE_LEGACY_DATA`, `IF_RSV_USE_STATUS_DATA`) → 4개 타겟 (`PM_GD111021`, `PM_GD111022`, `TM_GD111024`, `TM_GD111025`) 적재.

- Factory: `UseLoadStepFactory`
- Step: `UseLoadStep`

#### 지원 옵션

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `source-table` | ✅ | [string] | IF 소스 배열 |
| `target-table` | ✅ | [string] | 타겟 배열 |

#### 예 (`internal-use-loader.yml`)

```yaml
- id: use-load
  name: 이용량 적재
  factory-key: use-load
  source-table: [IF_RSV_USE_LEGACY_DATA, IF_RSV_USE_STATUS_DATA]
  target-table: [PM_GD111021, PM_GD111022, TM_GD111024, TM_GD111025]
```

---

### 4.9 `saeol-link-plan-snd` — 새올 LINK_PLAN SND

> 새올 Oracle 의 `LINK_PLAN` 테이블에 쌓인 변경분을 감지하여 IF_SND 에 적재. 새올 소스 테이블은 `link_status` 컬럼이 없어 별도 방식 필요. **others 전용, 복합 PK 지원**.

- Factory: `SaeolLinkPlanSndStepFactory`
- Step: `SaeolLinkPlanSndStep`

#### 지원 옵션

| 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `table-mappings` | ✅ | [map] | 16개 테이블의 소스-타겟-PK-LINK_PLAN 키 매핑 |

##### `table-mappings` 항목 구조

| 하위 필드 | 필수 | 타입 | 설명 |
|---|:---:|---|---|
| `source-table` | ✅ | string | 새올 원본 테이블 (대문자) |
| `target-table` | ✅ | string | IF_SND 테이블 (대문자) |
| `primary-key` | ✅ | string | 소스 PK (콤마 구분 복합) |
| `link-plan-keys` | ✅ | map<string,string> | LINK_PLAN 컬럼 → 소스 컬럼 매핑 |

#### 예 (`dmz-others-snd-saeol.yml`)

```yaml
- id: saeol-link-plan-snd
  name: 새올 LINK_PLAN 기반 송신
  factory-key: saeol-link-plan-snd
  table-mappings:
    - source-table: RGETSTGMS01
      target-table: IF_SND_RGETSTGMS01
      primary-key: REL_TRANS_CGG_CODE,PERM_NT_NO,YY_GBN
      link-plan-keys:
        sf_team_code: REL_TRANS_CGG_CODE
        perm_nt_no: PERM_NT_NO
        yy_gbn: YY_GBN
    # … 총 16개
```

---

## 5. 옵션 레퍼런스

### 5.1 테이블 식별

| 필드 | 적용 Factory | 설명 |
|---|---|---|
| `source-table` | 전부 | 소스 테이블. `SCHEMA.TABLE` 형식 지원 (예: `SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033`) |
| `target-table` | 전부 | 타겟 테이블. 커스텀 Loader는 배열 필수 |
| `if-table` | `link-update` | 갱신 근거 IF 테이블 |
| `link-table` | `source-to-if-link`, `link-update` | Link 테이블 (`link_ngwis`) |
| `link-jewon-source` | `source-to-if-link` | JOIN용 관측소 마스터 View |

### 5.2 키 컬럼

| 필드 | 적용 Factory | 설명 |
|---|---|---|
| `primary-key` | `source-to-if`, `source-to-if-link`, `saeol-link-plan-snd` | 소스 PK. 복합은 콤마 구분 (`"col1,col2"`) |
| `conflict-key` | `source-to-if`, `source-to-if-link` | UPSERT 기준 컬럼 (미지정 시 `primary-key` 사용). **provide 는 항상 `source_refs`** |
| `merge-key` | `simple-load` | MERGE 기준 UK |
| `link-plan-keys` | `saeol-link-plan-snd` | LINK_PLAN ↔ 소스 컬럼 매핑 |

### 5.3 동작 플래그

| 필드 | 기본값 | 설명 |
|---|:---:|---|
| `full-copy` | `false` | `true` 면 `link_status` 필터 없이 전체 조회. 마스터(제원) 테이블에 사용 |
| `skip-source-status-update` | `false` | `true` 면 소스 `link_status` 갱신 스킵. 외부 View / Read-only 소스에 사용 |

### 5.4 메타 컬럼 관련

| 필드 | 기본값 | 설명 |
|---|---|---|
| `target-meta-columns` | IF 표준 5종 | 타겟에 실제로 있는 메타 컬럼만 명시. **provide 타겟**은 `[source_refs, execution_id, updated_at]` 3종으로 명시 필수 (타겟 DDL 에 `link_status`/`extracted_at` 없음) |

**IF 표준 5종**: `source_refs`, `link_status`, `extracted_at`, `updated_at`, `execution_id`

**provide 3종**: `source_refs`, `execution_id`, `updated_at`

> provide 에서 3종만 쓰는 것은 **설계 의도** — 파이프라인 종점이라 `link_status` 불필요, 외부 제공 관점에서 `extracted_at` 불필요. 내부 메타를 외부 제공 테이블에 노출하지 않는다는 원칙 반영.

### 5.5 INSERT 제외 컬럼

| 필드 | 기본값 | 설명 |
|---|---|---|
| `exclude-insert-columns` | `[id, sn]` | auto-increment PK 충돌 회피용. **후보 ∩ 타겟 실제 컬럼** 교집합만 최종 제외됨 |

#### 언제 오버라이드하나?

- **소스의 `id` / `sn` 이 비즈니스 값** (auto-increment 아님)이고 타겟에 보존해야 하는 경우
- 예: provide A7 TMP_MEGOKR_API — Oracle SN 이 비즈니스값이라 보존 필요, 대신 PG 내부 자동채번 `id` 는 제외
  ```yaml
  exclude-insert-columns: ["id"]  # 기본 [id, sn] 오버라이드 → sn 은 INSERT 포함
  ```
- 경고 로그: 제외된 컬럼에 소스 첫 레코드 값이 non-null 이면 `WARN` 1회 — "auto-increment 아닐 가능성 → YAML 오버라이드 검토"

### 5.6 시간 필터

| 필드 | 적용 Factory | 설명 |
|---|---|---|
| `date-column` | `source-to-if`, `source-to-if-link` | 시간범위 실행 시 날짜 컬럼 (예: `obsv_date`) |
| `time-column` | `source-to-if`, `source-to-if-link` | 시간범위 실행 시 시간 컬럼 (예: `obsv_time`). `date-column` 과 조합 또는 단독 사용 |

> 두 필드는 "시간범위 실행 (`startTime`/`endTime` param)" 시에만 활성화. 일반 실행에서는 `link_status` 필터가 우선.

---

## 6. 사례별 템플릿

### 6.1 신규 RCV 업체 추가 (bojo)

```yaml
# infolink-agent-bojo-dmz/src/main/resources/config/agents/dmz-bojo-rcv-<업체>.yml
agent-code: dmz-bojo-rcv-<업체>
type: RCV

steps:
  - id: jewon-extract
    name: 제원 데이터 추출
    factory-key: source-to-if
    source-table: sec_jewon_view
    target-table: if_rsv_sec_jewon
    primary-key: obsv_code
    conflict-key: source_refs
    full-copy: true
    skip-source-status-update: true

  - id: obsvdata-extract
    name: 관측데이터 추출 (Link 기반)
    factory-key: source-to-if-link
    source-table: sec_obsvdata_view
    target-table: if_rsv_sec_obsvdata
    primary-key: id
    conflict-key: source_refs
    date-column: obsv_date
    time-column: obsv_time
    link-table: link_ngwis
    link-jewon-source: sec_jewon_view

  - id: link-table-update
    name: Link 테이블 갱신
    factory-key: link-update
    if-table: if_rsv_sec_obsvdata
    link-table: link_ngwis
```

> 대문자 소스(keunsan 등)는 모든 테이블/컬럼을 대문자로.

### 6.2 Internal RCV 테이블 그룹 추가

```yaml
# infolink-agent-bojo-internal/src/main/resources/config/agents/internal-<그룹>-rcv.yml
agent-code: internal-<그룹>-rcv
type: RCV

steps:
  - id: <도메인>-rcv
    name: <한글명> 수신
    factory-key: source-to-if
    source-table: IF_SND_<테이블>
    target-table: IF_RSV_<테이블>
    primary-key: <PK>
    conflict-key: source_refs
    # Proxy 경유로 DMZ IF_SND 읽기 → full-copy 불필요
    # skip-source-status-update 필요 시 추가
```

### 6.3 Provide Agent 추가

```yaml
# infolink-agent-provide/src/main/resources/config/agents/provide-<테이블명>.yml

# ──────────────────────────────────────
# 패턴: 단순 복사 (Oracle 원본 → PG 제공 테이블 1:1)
# 소스: <SCHEMA.TABLE>
# 타겟: api_prv_<테이블명>
# 레거시: <레거시 API endpoint>
# ──────────────────────────────────────
agent-code: provide-<테이블명>
type: LOADER

steps:
  - id: provide-<테이블명>
    name: <한글명> 복사
    factory-key: source-to-if
    source-table: <SCHEMA.TABLE>
    target-table: api_prv_<테이블명>
    primary-key: <PK>
    conflict-key: source_refs
    target-meta-columns:            # provide 는 필수 명시
      - source_refs
      - execution_id
      - updated_at
```

> 예외: 소스 PK 컬럼명이 `id` / `sn` 이고 비즈니스값이면 `exclude-insert-columns` 오버라이드 — [5.5 절](#55-insert-제외-컬럼) 참조.

### 6.4 Internal Loader 추가 (기존 패턴 없는 신규)

신규 커스텀 로직(JOIN/PIVOT 등)이 필요하면:

1. `infolink-agent-bojo-internal/loader/step/` 에 `<Domain>LoadStep.java` 작성 (참고: `JejuJewonLoadStep`)
2. `<Domain>LoadStepFactory.java` 작성 (`getFactoryKey()` 에 신규 key)
3. 공통 헬퍼 사용: `LoaderStepHelper` (processJewon/processObsvdata/saveSyncLog) 및 조건실행/Retention
4. YAML 에 `factory-key: <신규-key>` 로 작성

---

## 7. 주의사항 / 자주 하는 실수

### 7.1 `factory-key: source-to-if` 명칭

- IF 테이블이 아닌 타겟(provide 의 `api_prv_*` 등) 에도 사용됨
- **이름과 실제 동작이 괴리** 있음 — 이름만 보고 "IF 전용인가?" 오해 금지
- 향후 `source-to-target` 으로 마이그레이션 예정이지만 **지금은 건드리지 말 것** (20여 YAML 호환 이슈)

### 7.2 `conflict-key: source_refs` 가 기본

- 외부 DB에 PK 중복이 존재하는 케이스 (112002 `PRMSN_DCLR_NO` 중복 등) 대비
- 같은 PK 여러 건이 와도 source_refs 가 다르면 각각 보존됨
- **provide 는 무조건 `source_refs`**, 다른 모듈도 가능하면 `source_refs` 권장

### 7.3 대소문자 처리

| 타겟 DB | 관례 |
|---|---|
| Oracle / Tibero | 모든 테이블/컬럼 **대문자** (소스/타겟/PK) |
| PostgreSQL | **소문자** |
| MySQL | **소문자** |
| keunsan (Oracle 스타일) | 외부 PG 지만 대문자 |

YAML에 쓴 대소문자는 그대로 보존 — JDBC metadata 조회 시 소문자/대문자/원본 3가지 variant 로 재시도하지만 관례 맞추는 게 안전.

### 7.4 복합 PK

- 배열 아니라 **콤마 구분 단일 문자열**:
  ```yaml
  primary-key: REL_TRANS_CGG_CODE,PERM_NT_NO,YY_GBN
  ```
- 내부에서 split(',') 로 분리되어 List로 처리됨

### 7.5 `target-meta-columns` 설계 의도

- IF 타겟(5종) vs provide 타겟(3종)은 **DDL 차이** 에서 비롯된 **설계상 의도된 구분**
- 단순 boilerplate 가 아님 — 외부 제공 테이블에 내부 파이프라인 상태 컬럼을 노출하지 않는 원칙
- "귀찮으니 기본값 자동 추론하자"는 개선 제안은 별도 이슈로 다루되, **지금은 명시적 명시 유지**

### 7.6 `exclude-insert-columns` 오버라이드는 주석 필수

- 기본값 `[id, sn]` 이 의도인지, 오버라이드가 의도인지 YAML 만 봐서는 모호함
- A7 TMP_MEGOKR_API 케이스처럼 "sn 은 비즈니스값이라 INSERT 포함 필요" 같은 **이유를 주석으로 명시**

### 7.7 Loader 의 YAML 필드가 "문서용"인 경우

- `dmz-bojo-load` 는 실제 테이블명을 `application.yml` 에서 읽음 — YAML 은 모니터링/가독성 목적
- `internal-bojo-load` 는 타겟명 패턴(`970001`/`970201`/…)으로 역할 매핑
- Factory 구현을 한 번이라도 확인하고 작성할 것 — **"YAML 만 고치면 된다"** 가정은 위험

### 7.8 provide 는 1 Agent = 1 소스 API

- 레거시 API endpoint 1개당 Agent 1개, 엔티티 1개, 타겟 테이블 1개 (메모리 `feedback_provide_target_per_api`)
- "컬럼 구조 비슷하니 기존 엔티티 공유" 하지 말 것
- `SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033` 과 `TM_GD112002` 는 거의 동일 구조지만 **API endpoint 다르면 타겟 분리**

---

## 8. 등록 절차

### 8.1 YAML 작성만으로는 실행 안 됨

- YAML 은 **Agent의 "내부 동작 정의"** 일 뿐
- Orchestrator DB 의 `agents` 테이블에 **별도 등록** 해야 실행 가능
- 즉 **YAML 과 Orchestrator DB 등록은 2단 구조**

### 8.2 등록 항목

| 필드 | 설명 |
|---|---|
| `agent_code` | YAML 의 `agent-code` 와 **정확히 일치** |
| `zone` | DMZ / INTERNAL |
| `agent_type` | RCV / LOADER / SND |
| `target_datasource_id` | 이 Agent의 타겟 DB ID (프록시 라우팅 필수 — 메모리 `project_header_manage_routing`) |
| `source_datasource_id` | 소스 DB ID |
| 기타 | 스케줄, 활성화 플래그 등 |

### 8.3 등록 방법

1. Frontend (`http://localhost:3000`) → Admin → Agents 화면에서 등록
2. 또는 Orchestrator REST API 호출
3. DB 직접 INSERT 는 권장하지 않음 (검증 로직 우회)

### 8.4 확인

Agent 서비스 기동 시 로그에 다음 나오면 정상:

```
[Bojo] 파이프라인 등록: agentCode=dmz-bojo-rcv-daejeon, type=RCV, modeId=default, steps=3
Loaded agent config: dmz-bojo-rcv-daejeon (type=RCV, steps=3)
```

Orchestrator 쪽에서 "알 수 없는 agentCode" 에러가 뜨면:
- YAML `agent-code` 와 DB `agent_code` 불일치
- Agent 서비스 재기동 안 됨
- 해당 모듈 classpath 에 YAML 이 포함되지 않음 (resources 폴더 위치 확인)

---

## 부록: Factory 한 장 요약

| factory-key | 모듈 | 용도 | 소스 | 타겟 | 주요 옵션 |
|---|---|---|---|---|---|
| `source-to-if` | common | **범용 카피** | 단일 | 단일 | `primary-key`, `conflict-key`, `full-copy`, `skip-source-status-update`, `target-meta-columns`, `exclude-insert-columns` |
| `source-to-if-link` | bojo | RCV Link 증분 | 단일 View | 단일 IF | + `link-table`, `link-jewon-source` |
| `link-update` | bojo | Link 테이블 갱신 | — | — | `if-table`, `link-table` |
| `dmz-bojo-load` | bojo | DMZ Loader | 하드코딩 | 하드코딩 | (application.yml) |
| `internal-bojo-load` | bojo-internal | Internal Loader | IF | 4개 타겟 | 테이블명 패턴 매칭 |
| `jeju-jewon-load` | bojo-internal | 제주 제원 1→5 | IF | 5개 | — |
| `jeju-obsvdata-load` | bojo-internal | 제주 관측 | IF | 2개 | — |
| `jeju-facility-load` | bojo-internal | 제주 이용시설 | IF | 1개 | — |
| `use-load` | bojo-internal | 이용량 2→4 | 2개 IF | 4개 | — |
| `simple-load` | bojo-internal | 1:1 MERGE | 단일 IF | 단일 | `merge-key` |
| `saeol-link-plan-snd` | others | 새올 LINK_PLAN | 16개 | 16개 IF_SND | `table-mappings` (배열) |

---

> **개정 이력**
> - 2026-04-24: 최초 작성. Factory 11종 / 옵션 전체 정리 / 사례별 템플릿 포함.
