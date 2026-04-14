# DMZ Agent 2호기 — sync-agent-others (8085)

> 기존 bojo(보조관측)와 동일 구조, 담당 데이터만 다름
> 운영 안정성 위해 별도 서비스 분리 (2026-03-30 회의)

## 확정 사항

| 항목 | 값 |
|------|-----|
| 모듈명 | sync-agent-others |
| 포트 | 8085 |
| 역할 | DMZ DB → IF_SND (SND 전용) |
| agent-code prefix | `dmz-others-snd-*` |

## [E1] 모듈 생성
- [x] sync-agent-others 디렉토리 + build.gradle
- [x] application.yml (port 8085, datasource)
- [x] PipelineRegistry, DatasourceConfig 등 공통 구조
- [x] OthersAgentApplication.java

## [E1] IF_SND 테이블 (6개)
- [x] if_snd_tb_jeju_jewon
- [x] if_snd_tb_jeju
- [x] if_snd_rgetstgms01
- [x] if_snd_use_legacy_data
- [x] if_snd_use_status_data
- [x] if_snd_use_jeju_day

## [E1] YAML 설정
- [x] dmz-others-snd-jeju.yml (3테이블: jewon, obsv, stgms)
- [x] dmz-others-snd-use.yml (3테���블: legacy, status, jejuday)

## [E1] Orchestrator 등록
- [x] agent 테이블에 신규 Agent 등록 + E2E 테스트

## [E1] 보류 (담당자 확인 후)
- [ ] rgetnpmms01 SND 추가 여부
- [ ] rgetnwavi05 SND 추가 여부
- [ ] rgetnwavi06 SND 추가 여부

> target.xml에 SQL은 있으나 호출하는 Java 없음.
> 실서버 배포 시 담당자에게 확인 → SND 추가 여부 결정.

**진행도: 10/13 = 77% (보류 3건 제외 시 100%)**
