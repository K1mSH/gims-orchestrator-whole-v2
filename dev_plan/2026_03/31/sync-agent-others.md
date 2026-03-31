# sync-agent-others 모듈 생성 계획서

## 개요
API Collector가 DMZ DB에 적재한 데이터를 IF_SND 테이블로 퍼나르는 SND 전용 Agent 서비스.
기존 bojo(보조관측 전용)와 동일 구조, 담당 데이터만 다름. 운영 안정성을 위해 별도 서비스로 분리.

> 배경: `todo/09-dmz-agent-2.md`
> 서비스 매핑: `docs/useIncludeJeju/service-name-mapping.md` Phase 3

## 확정 사항

| 항목 | 값 |
|------|-----|
| 모듈명 | sync-agent-others |
| 포트 | 8085 |
| 역할 | DMZ DB → IF_SND 적재 (SND 전용) |
| 프레임워크 | sync-agent-common 기반 (bojo와 동일) |
| agent-code prefix | `dmz-others-snd-*` |

---

## SND 대상 분석 (레거시 기반)

### 판정 기준
SND 대상 = **DMZ에 데이터가 존재하고** + **Internal에서 읽어가는 레거시 프로그램이 확인된** 테이블

### 확정 (6개) — DMZ 적재 + Internal 전송 모두 확인

| # | DMZ 테이블 | DMZ 적재 | Internal 전송 프로그램 | GIMS 타겟 |
|---|-----------|---------|---------------------|----------|
| 1 | tb_jeju_jewon | D1 (JejuJewonExecutor) | JewonDB.java | TM_GD60001 외 7개 |
| 2 | tb_jeju | D2 (JejuObsvDataExecutor) | ObsvrdataDB.java | Pm60201/60202 |
| 3 | rgetstgms01 | D3 (JejuFacilityExecutor) | RgetnDB.java | RGETSTGMS01 (내부망) |
| 4 | use_legacy_data | D5 (AnyangUsageExecutor) | UseToIn.java | PM_GD31021, PM_GD31022 |
| 5 | use_status_data | DB에 존재 | UseToIn.java | TM_GD31025 |
| 6 | use_jeju_day | **외부 시스템이 적재** (아래 상세) | JejuInToDB.java | TmGd31010, PmGd31022 |

#### use_jeju_day 특이사항
- **적재**: 우리 API Collector가 아닌 **외부 시스템**이 DMZ DB에 직접 적재
  - 역컴파일 소스에 적재 프로그램 없음 (insertUseJeuDay SQL만 source.xml에 존재)
  - 실 DB 확인 결과 작년 데이터까지 존재 → 다른 담당이 운영 중
  - DDL: `USE_JEJU_DAY (OBSRVT_ID, OBSR_DE, USGQTY, LAST_MESURE_VALUE, FRST_MESURE_VALUE, DTA_STTUS_CODE)`, PK: (OBSRVT_ID, OBSR_DE)
- **전송**: JejuInToDB.java가 `select_use_jeju_day`로 읽어서 target에 전송
  - properties의 `tablename`에서 테이블명 목록을 읽어 루프
  - `word.equals("use_jeju_day")` 분기 → `select_tmGd31010` 존재 체크 후 조건부 INSERT
- **우리 역할**: 적재는 관여하지 않고, **IF_SND로 퍼나르기만** 담당

### 보류 (3개) — DMZ 적재는 하지만 Internal 전송 코드 미확인

| # | DMZ 테이블 | DMZ 적재 | Internal 전송 | 보류 사유 |
|---|-----------|---------|-------------|----------|
| 1 | rgetnpmms01 | D3 (JejuFacilityExecutor) | ❓ | target.xml에 `insertRgetnpmms01` MERGE SQL 존재, 하지만 이를 호출하는 Java 프로그램이 역컴파일 소스에 없음. RgetnDB.java는 rgetstgms01만 처리 |
| 2 | rgetnwavi05 | D4 (JejuWaterQualityExecutor) | ❓ | target.xml에 `insetRgetnwavi05` MERGE SQL 존재, 하지만 호출하는 Java 프로그램 없음. 서비스 매핑 문서에서도 "(미정)" |
| 3 | rgetnwavi06 | D4 (JejuWaterQualityExecutor) | ❓ | 동일. `insetRgetnwavi06` SQL만 존재 |

> **보류 판단**: 역컴파일 누락인지, 실제 미사용인지 현시점에서 확인 불가.
> 실서버 배포 시 담당자에게 확인 후 SND 추가 여부 결정.
> SQL 매핑은 준비되어 있으므로 추가 시 YAML + IF 테이블만 생성하면 됨.

### 새올 (16개) — 별도 구조, 추후 진행

| # | 소스 | 내용 | 비고 |
|---|------|------|------|
| - | 새올 Oracle (29005) 16개 테이블 | DMZ 새올 DB에서 직접 읽기 → IF_SND | saeol.java에서 확인, 별도 datasource 설정 필요 |

> 새올은 API Collector 경유가 아닌 **DB 직접 읽기** 패턴.
> Tibero(실서버)/Oracle(개발) 드라이버 분기 필요.
> 현재 Phase에서는 제주+이용량 6개 우선, 새올은 후순위.

---

## 1차 구현 범위 (확정 6개)

### SND Agent YAML

| agent-code | 소스 테이블 | IF_SND 타겟 |
|-----------|-----------|------------|
| dmz-others-snd-jeju-jewon | tb_jeju_jewon | if_snd_tb_jeju_jewon |
| dmz-others-snd-jeju-obsv | tb_jeju | if_snd_tb_jeju |
| dmz-others-snd-jeju-stgms | rgetstgms01 | if_snd_rgetstgms01 |
| dmz-others-snd-use-legacy | use_legacy_data | if_snd_use_legacy_data |
| dmz-others-snd-use-status | use_status_data | if_snd_use_status_data |
| dmz-others-snd-use-jejuday | use_jeju_day | if_snd_use_jeju_day |

### IF_SND 테이블 DDL (보조망 DB, PG 29001/dev)
6개 IF_SND 테이블 생성. 각 테이블은 소스와 동일 스키마 + `execution_id`, `source_refs` 컬럼 추가.

---

## 구현 항목

### 1. 모듈 생성 (sync-agent-bojo 복제 기반)
- `sync-agent-others/` 디렉토리 생성
- build.gradle: sync-agent-common 의존성
- application.yml: port 8085, datasource 설정 (보조망 PG)
- 패키지: `com.sync.agent.others`
- bojo의 공통 구조 그대로: PipelineRegistry, PipelineService, Agent YAML 로딩

### 2. IF_SND 테이블 DDL
6개 테이블 (JPA 엔티티 또는 직접 DDL)

### 3. SND Agent YAML 설정
- `config/agents/dmz-others-snd-jeju.yml` (제주 3테이블)
- `config/agents/dmz-others-snd-use.yml` (이용량 3테이블)

기존 bojo의 `dmz-bojo-snd.yml` 패턴 참고

### 4. Orchestrator 등록
- agent 테이블에 신규 Agent 등록 (endpoint_url: http://localhost:8085)

## 프로젝트 구조

```
orchestrator_v2/
├── sync-agent-bojo/          # 기존 (보조관측, 8082)
├── sync-agent-bojo-int/      # 기존 (내부망, 8092)
├── sync-agent-others/        # 신규 (제주/이용량 SND, 8085)
│   ├── build.gradle
│   ├── src/main/java/com/sync/agent/others/
│   │   ├── OthersAgentApplication.java
│   │   ├── config/
│   │   │   ├── PipelineRegistry.java
│   │   │   └── DatasourceConfig.java
│   │   └── ...
│   └── src/main/resources/
│       ├── application.yml
│       └── config/agents/
│           ├── dmz-others-snd-jeju.yml
│           └── dmz-others-snd-use.yml
└── sync-agent-common/        # 공통 (변경 없음)
```

## 수정 대상

| 모듈 | 파일 | 변경 |
|------|------|------|
| **sync-agent-others** | 전체 | **신규 모듈** |
| sync-agent-common | (없음) | 변경 없음 — 기존 SND Step 재사용 |
| sync-orchestrator/backend | agent 테이블 | 신규 Agent 등록 |

## 영향 범위
- 신규 모듈 추가 (기존 모듈 변경 없음)
- sync-agent-common의 SND 관련 Step/Service 그대로 재사용
- Orchestrator에서 endpoint_url(8085)로 구분 (기존 코드 변경 불필요)
