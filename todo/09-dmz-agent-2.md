# DMZ Agent 2호기 — sync-agent-others (8085)

> 기존 bojo(보조관측)와 동일 구조, 담당 데이터만 다름
> 운영 안정성 위해 별도 서비스 분리 (2026-03-30 회의)
> 계획서: `dev_plan/2026_03/31/sync-agent-others.md`

## 확정 사항

| 항목 | 값 |
|------|-----|
| 모듈명 | sync-agent-others |
| 포트 | 8085 |
| 역할 | DMZ DB → IF_SND (SND 전용) |
| agent-code prefix | `dmz-others-snd-*` |

## SND 대상 — 확정 (6개)

레거시 Internal 전송 프로그램이 확인된 테이블만 확정.

| # | DMZ 테이블 | DMZ 적재 | Internal 프로그램 | GIMS 타겟 |
|---|-----------|---------|------------------|----------|
| 1 | tb_jeju_jewon | D1 JejuJewonExecutor | JewonDB | TM_GD60001 외 7개 |
| 2 | tb_jeju | D2 JejuObsvDataExecutor | ObsvrdataDB | Pm60201/60202 |
| 3 | rgetstgms01 | D3 JejuFacilityExecutor | RgetnDB | RGETSTGMS01 (내부망) |
| 4 | use_legacy_data | D5 AnyangUsageExecutor | UseToIn | PM_GD31021, PM_GD31022 |
| 5 | use_status_data | DB에 존재 | UseToIn | TM_GD31025 |
| 6 | use_jeju_day | **외부 시스템 적재** | JejuInToDB | TmGd31010, PmGd31022 |

### use_jeju_day 특이사항
- 우리 API Collector가 적재하지 않음. 외부 시스템이 DMZ DB에 직접 적재.
- 역컴파일에 적재 프로그램 없으나 실DB에 작년 데이터까지 존재 → 다른 담당이 운영 중.
- 우리 역할: **IF_SND로 퍼나르기만** 담당 (적재 관여 X).
- JejuInToDB.java가 `select_use_jeju_day`로 읽어서 target TmGd31010/PmGd31022에 전송.

## SND 대상 — 보류 (3개)

DMZ 적재는 하지만, Internal로 보내는 레거시 프로그램이 확인되지 않은 테이블.

| # | DMZ 테이블 | DMZ 적재 | 보류 사유 |
|---|-----------|---------|----------|
| 1 | rgetnpmms01 | D3 JejuFacilityExecutor | target.xml에 `insertRgetnpmms01` MERGE SQL 존재하지만 호출하는 Java 없음. RgetnDB는 rgetstgms01만 처리. |
| 2 | rgetnwavi05 | D4 JejuWaterQualityExecutor | target.xml에 `insetRgetnwavi05` SQL 존재하지만 호출하는 Java 없음. |
| 3 | rgetnwavi06 | D4 JejuWaterQualityExecutor | 동일. `insetRgetnwavi06` SQL만 존재. |

> **판단**: 역컴파일 누락인지 실제 미사용인지 현시점 확인 불가.
> **조치**: 실서버 배포 시 담당자에게 확인 → SND 추가 여부 결정.
> **추가 시**: YAML + IF 테이블 생성만 하면 됨 (SQL 매핑은 이미 준비).

## SND 대상 — 새올 (16개, 후순위)

| 소스 | 구조 | 비고 |
|------|------|------|
| 새올 Oracle (29005) 16개 테이블 | DB 직접 읽기 → IF_SND | API Collector 경유 아님 |

> Tibero(실서버)/Oracle(개발) 드라이버 분기 필요. 제주+이용량 완료 후 진행.

## 구현 체크리스트

### 모듈 생성
- [ ] sync-agent-others 디렉토리 + build.gradle
- [ ] application.yml (port 8085, datasource)
- [ ] PipelineRegistry, DatasourceConfig 등 공통 구조
- [ ] OthersAgentApplication.java

### IF_SND 테이블
- [ ] if_snd_tb_jeju_jewon
- [ ] if_snd_tb_jeju
- [ ] if_snd_rgetstgms01
- [ ] if_snd_use_legacy_data
- [ ] if_snd_use_status_data
- [ ] if_snd_use_jeju_day

### YAML 설정
- [ ] dmz-others-snd-jeju.yml (3테이블: jewon, obsv, stgms)
- [ ] dmz-others-snd-use.yml (3테이블: legacy, status, jejuday)

### Orchestrator 등록
- [ ] agent 테이블에 신규 Agent 등록 (endpoint_url: http://localhost:8085)

### 보류 (담당자 확인 후)
- [ ] rgetnpmms01 SND 추가 여부
- [ ] rgetnwavi05 SND 추가 여부
- [ ] rgetnwavi06 SND 추가 여부

### 새올 (후순위)
- [ ] 새올 16개 테이블 SND 설계
- [ ] Tibero/Oracle 드라이버 분기

**진행도: 0/10 (보류 3 + 새올 2 제외)**
