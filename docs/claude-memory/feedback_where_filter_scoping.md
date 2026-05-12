---
name: WHERE 조건 스코핑 원칙
description: WHERE(conditions) 실행은 step별 행 필터일 뿐 — 로더는 yml where-filters로 의미있는 컬럼만 노출, 조건 N/A step은 skip
type: feedback
originSessionId: 798f3284-9847-49a9-a3fa-3e6bbbe74e7d
---
WHERE(conditions) 수동 실행 = **단계(step)별 행 필터**일 뿐. "엔티티 재실행 + 종속물 자동 추적" 기능이 아님.

- 한 Agent 가 여러 step(예: 보조 RCV = jewon step + obsvdata step)을 돌 때, 조건은 step 의 source/IF 테이블에 적용 가능한 것만 그 step 에 먹는다. **적용될 조건이 0개인 step → 그 step skip (0건 SUCCESS). 디폴트 link_status 폴백 절대 금지** (source 뷰엔 link_status 없어서 크래시 — 5/12 버그). `SourceToTargetStep.fetchSimpleCopy()` 에 컬럼 존재 필터 + skip 처리 완료. DMZ Loader 는 원래부터 이 시맨틱.
- jewon↔obsvdata 같은 단계 내 두 테이블은 **독립**(FK 없음). 제원에 조건 걸면 obsvdata 가 따라오지 *않는다* — 따라오게 하려면 양쪽 공통 컬럼 `obsv_code` 로 조건. obsv_code 는 지역을 담고 있어 "지역 필터" = `obsv_code LIKE 'GN-SAC-G1%'`.
- **로더는 yml `where-filters` 로 의미있는 `(table, column)` 만 선언/노출** (운영자는 로더 내부 로직 모름). 큐레이션 항목(`column: OBSV_CODE`, label/operators/hint) + 범용 인식자 `column: "*"`(테이블 전체 — 카피성 단계용) 혼용. `where-filters` 미선언 Agent = 현재 범용 동작 유지(하위호환). 백엔드는 선언한 Agent 에 한해 화이트리스트 밖 조건 거부. (계획: `dev_plan/2026_05/12/yml-declared-where-filters.md`, 구현 별도 사이클)
- 보조 Internal Loader 지역 실행 = `IF_RSV_SEC_OBSVDATA.obsv_code LIKE` 조건. 제원(`tm_gd970001`)은 **GIMS 마스터 = READ ONLY** (우리 파이프라인이 등록 안 함). dev_log 의 "제원 등록"은 테스트 환경 시딩.
- WHERE 실행 시 `link_ngwis` 등 증분 마커는 갱신 안 함 (`skipLinkUpdate=true`) — 재동기화지 증분 전진이 아니므로. 단 적재 행의 `execution_id`/`link_status` 는 갱신 (다음 단계가 PENDING 집어감).

**Why:** WHERE 의 실질 용도는 "특정 기간 / 특정 지역 데이터만 재동기화" 두 가지. 레거시 보조 내부망 loader 도 `getJewon` SQL 에 `obsrvt_id LIKE` 끼우는 방식이었고(= obsv_code 패턴), 우리 구조에선 그게 IF 테이블 obsv_code 조건으로 깔끔히 재현됨. 로직 있는 로더에서 운영자가 아무 컬럼이나 거는 건 위험 — 함정(제원 테이블에 달기, link_status 건드리기) + 크래시 원인.

**How to apply:** 새 로더/단계 만들 때 conditions 지원하려면 yml `where-filters` 에 의미있는 컬럼만 선언. RCV(단순 카피)는 미선언 OK. 조건 처리 코드 손댈 때 "이 step 에 적용 조건 없으면 skip" 시맨틱 깨지 말 것. WHERE 관련 질문 받으면 "step별 행 필터"라는 관점으로 답할 것.
