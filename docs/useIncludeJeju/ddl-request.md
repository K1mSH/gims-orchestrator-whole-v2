# DDL 뽑아올 테이블 목록

> 작성일: 2026-03-30
> 표준화 문서에 있는 테이블(TM_GD*, PM_GD*, TC_GD*)은 제외

## DMZ 보조망 DB — 5개

| # | 테이블 | 용도 | 비고 |
|---|--------|------|------|
| 1 | TB_JEJU_JEWON | 제주 관측점 마스터 | |
| 2 | TB_JEJU | 제주 수위 관측 | SEQ_JEJU 시퀀스도 필요 |
| 3 | USE_LEGACY_DATA | 이용량 레거시 | |
| 4 | USE_STATUS | 이용량 상태 | |
| 5 | USE_JEJU_DAY | 제주 일일 이용량 | |

## 새올 DB (Tibero) — 16개

| # | 테이블 | 용도 |
|---|--------|------|
| 1 | RGETSTGMS01 | 이용실태 |
| 2 | RGETNOPMS01 | 지하수이용신고 |
| 3 | RGETNTGMS02 | 지표수수질검사 |
| 4 | RGETNWAVI06 | 지하수수질검사내역 |
| 5 | RGETNPMMS01 | 허가신고정보 |
| 6 | RGETNKCNO01 | 케이싱정보 |
| 7 | RGETNWAMS01 | 지표수환경기준 |
| 8 | RGETNWAVI05 | 지하수수질검사 |
| 9 | RGETNSIMS01 | 심화조사정보 |
| 10 | RGETNYYMS01 | 용년정보 |
| 11 | RGETNJHMS01 | 정화조정보 |
| 12 | RGETNSCKT01 | 스케치정보 |
| 13 | RGETNKMTB01 | 공간매체테이블 |
| 14 | RGETHKMIR01 | 현황정보 |
| 15 | RGETNYCSG01 | 연락처 |
| 16 | RGETNMNFE01 | 인력및장비관리내역관리 |

## 합계: 21개
