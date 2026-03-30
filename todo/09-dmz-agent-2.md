# DMZ Agent 2호기 (이용량/제주/새올 담당)

> 서비스명 미정 — bojo(보조관측)와 동일 구조, 담당 데이터만 다름
> 회의 결과 (2026-03-30): 기존 bojo에 전부 넣으면 운영상 불안 → 별도 서비스로 분리

## 배경
- 기존 bojo-dmz(8082): 보조관측 RCV 10 + Loader 1 + SND 1 = 12개 Agent
- 신규 IF 테이블 관련 SND Agent가 추가로 필요 (제주 6쌍 + 새올 16쌍 + 이용량 2쌍)
- 하나의 서비스에 다 넣으면 장애 시 전체 영향 → 서비스 분리

## 요구사항

### 서비스 구조
- [ ] 서비스명 확정 (bojo와 구분되는 이름)
- [ ] 포트 확정 (8082 아닌 별도 포트)
- [ ] bojo와 동일한 Agent 프레임워크 사용 (sync-agent-common 기반)
- [ ] 별도 모듈 생성 (sync-agent-??? )

### 담당 범위
- [ ] 제주 보조관측 SND (jeju_jewon, jeju_obsv_data → IF_SND)
- [ ] 제주 이용량 SND (jeju_facility_pmms/stgms, jeju_wavi_05/06 → IF_SND)
- [ ] 이용량 레거시 SND (use_legacy_data, use_status → IF_SND)
- [ ] 새올 SND (RGETSTGMS01 외 16개 → IF_SND, 새올 DB=Tibero/Oracle 읽기)

### 서비스 구분
현재 Orchestrator가 Agent를 찾는 방식: `endpoint_url`(서비스 주소) + `agent_code`(파이프라인 식별)
→ 2호기는 **포트만 다르게** 하면 기존 코드 수정 없이 구분 가능

예시:
| 서비스 | endpoint_url | agent_code 예시 |
|--------|-------------|----------------|
| bojo (보조, 기존) | http://localhost:8082 | dmz-bojo-rcv-*, dmz-bojo-snd |
| 2호기 (신규) | http://localhost:8085 | dmz-???-snd-jeju, dmz-???-snd-saeol 등 |

- [ ] 포트 확정 (8085 예상)
- [ ] Orchestrator agent 테이블에 신규 Agent 등록 (endpoint_url = 2호기 주소)
- [ ] 프론트에서 어떤 Agent가 어떤 서비스인지 식별 가능해야 함
- [ ] 모니터링/실행이력에서 서비스별 구분

### 기술적 고려
- [ ] 보조망 DB (PG) + 새올 DB (Tibero/Oracle) 두 소스를 다뤄야 함
- [ ] 기존 bojo의 common 의존성 그대로 사용
- [ ] YAML 기반 Agent 설정 (기존 패턴 따름)
