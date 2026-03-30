# 내부망 배치 프로그램 → 우리 시스템 이관 목록

## 현재 우리가 처리하는 것 (bojo-int Agent)

| 구분 | 테이블 | 설명 |
|------|--------|------|
| 제원 | tm_gd970001 | 관측소 제원 |
| 관측 | pm_gd970201 | 관측자료 (수위/수질) |
| Link | tm_gd980002 | Link 추적 |
| 결과 | tm_gd970101 | 결과 매핑 |

---

## 신규 추가 대상 A: saeol.java (16개 테이블, DB→DB)

소스 DB → LINK_PLAN(변경 큐) → MERGE/DELETE → Tibero(GIMS)

| # | 테이블 | 설명 | PK (복합키) |
|---|--------|------|-------------|
| 1 | RGETSTGMS01 | 지표수이용신고 | sf_team_code, perm_nt_no, yy_gbn |
| 2 | RGETNOPMS01 | 지하수이용신고 | rel_trans_cgg_code, perm_nt_no |
| 3 | RGETNTGMS02 | 지표수수질검사 | sf_team_code, yy_gbn |
| 4 | RGETNWAVI06 | 지하수수질검사내역 | sf_team_code, perm_nt_no, qw_isp_sno, qw_isp_sort_code, list_code |
| 5 | RGETNPMMS01 | 펌프정보 | sf_team_code, perm_nt_no |
| 6 | RGETNKCNO01 | 케이싱정보 | sf_team_code, perm_nt_no, dig_ord |
| 7 | RGETNWAMS01 | 지표수환경기준 | sf_team_code, qw_isp_sort_code, list_code |
| 8 | RGETNWAVI05 | 지표수수질검사 | sf_team_code, perm_nt_no, qw_isp_sno |
| 9 | RGETNSIMS01 | 심화조사정보 | sf_team_code, regnum, sno |
| 10 | RGETNYYMS01 | 용년정보 | sf_team_code, regnum, sno |
| 11 | RGETNJHMS01 | 정화조정보 | sf_team_code, regnum, sno |
| 12 | RGETNSCKT01 | 스케치정보 | sf_team_code, list_code |
| 13 | RGETNKMTB01 | 공간매체테이블 | sf_team_code, list_code |
| 14 | RGETHKMIR01 | 현황정보 | sf_team_code, perm_nt_no, list_code, jgong_nt_sno |
| 15 | RGETNYCSG01 | 연락처 | sf_team_code, list_code |
| 16 | RGETNMNFE01 | 면적정보 | sno, regnum |

### saeol 특징
- LINK_PLAN = 변경 큐 (flag: I/U/D, link_idx 순번)
- TM_GD70001에서 마지막 처리 idx 관리
- MERGE INTO 사용 (Insert or Update)
- 타겟: Tibero (GIMS 운영DB)

---

## 신규 추가 대상 B: in_use 역컴파일 결과 (제주도 연계)

### API 호출 계열 → API Collector 커스텀 실행기

| # | 프로그램 | 역할 | API 엔드포인트 | 쓰는 테이블 | 특이사항 |
|---|---------|------|---------------|-------------|---------|
| 1 | InsertJeju | 수위 관측 실시간 수집 | water.jeju.go.kr/selectObsvData.json | insertJejuOb | site_code별 일일 수집 |
| 2 | InsetTb_jeju_jewon | 관측점 마스터 초기 로드 | water.jeju.go.kr/selectObsv.json | insetTb_jeju_jewon | 좌표변환 EPSG:5186→4326 |
| 3 | RgetnwaviProgram | 수질검사 수집 | water.jeju.go.kr/selectSujil.json | insetRgetnwavi05/06 | 항목명 한글→영문 매핑 |
| 4 | RgetstgmsProgram | 지하수이용시설 수집 (자동) | water.jeju.go.kr/selectJejuUse.json | insetRgetnpmms01, insetRgetstgms01 | 좌표변환+코드변환 복잡, 1000건 페이징 |
| 5 | yearProgram | 지하수이용시설 수집 (수동/연도) | 위와 동일 | insetRgetstgms01 | RgetstgmsProgram의 수동 버전 |

### DB→DB 이관 계열 → Agent (bojo-int) 또는 별도 배치

| # | 프로그램 | 역할 | 소스 | 타겟 테이블 | 특이사항 |
|---|---------|------|------|-------------|---------|
| 6 | JejuInToDB | 수자원 사용량 이관 | Source DB | TmGd31010Gms, PmGd31022 | 연도별 처리 |
| 7 | JewonDB | 관측점 메타 저장 | Source DB | TM_GD60001/10001/60130/60002 + Gd60101 3개 (총7개) | 관측점→7개 테이블 분산 |
| 8 | ObsvrdataDB | 관측데이터 이관 | Source DB | Pm60201/Pm60202 (센서별 3×2=6개) | 센서타입별 분기, RID 기반 증분 |
| 9 | RgetnDB | 수질시설 이관 | Source DB | insetRgetstgms01 | 단순 이관 |
| 10 | UseToIn | 사용량 레거시 이관 | Source DB | PM_GD31021/22, TM_GD31025 | SN 기반 증분, 음수값 보정 |

---

## 구현 방향 분류

### API Collector 커스텀 실행기 (5건)
제주도 API (`water.jeju.go.kr`) 호출 → 가공 → DB 적재

| 커스텀 실행기명 (안) | 원본 | 비고 |
|---------------------|------|------|
| jeju-obsv-data | InsertJeju | 실시간 수위 수집 |
| jeju-jewon-master | InsetTb_jeju_jewon | 관측점 마스터 (좌표변환) |
| jeju-water-quality | RgetnwaviProgram | 수질검사 (항목명 매핑) |
| jeju-facility | RgetstgmsProgram + yearProgram | 지하수이용시설 (페이징+복잡변환) |

### Agent (bojo-int) 확장 — DB→DB (5건 + saeol 16건)

| 구분 | 원본 | 테이블 수 | 비고 |
|------|------|----------|------|
| 제주 관측점 메타 | JewonDB | 7개 | 1 소스 → 7 타겟 분산 |
| 제주 관측데이터 | ObsvrdataDB | 6개 | 센서별 분기 |
| 제주 수자원사용 | JejuInToDB | 3개 | 연도별 |
| 수질시설 | RgetnDB | 1개 | 단순 이관 |
| 사용량 레거시 | UseToIn | 3개 | SN 증분 |
| 새올 16개 | saeol.java | 16개 | LINK_PLAN 기반 |

---

## 확인된 사항 (3/26)

### saeol (새올)
- **conn (소스) = DMZ DB**, **tConn (타겟) = 내부망 GIMS DB (Oracle/Tibero)**
- DMZ에서 읽기만 함 (쓰기 없음), 내부망에 MERGE/DELETE + 진행추적 UPDATE
- LINK_PLAN은 변경 큐가 아니라 **현재 처리 건 1개만 존재**
- **수동 실행** 배치 (스케줄 없음, 담당자가 필요 시 실행)
- DMZ에 실존하는 테이블: **5개** (RGETSTGMS01, RGETNPMMS01, RGETNWAVI05, RGETNWAVI06, RGETNMNRE01)
- 단, **구현은 16개 전부** 대응 (기존 소스 기능 범위 유지)
- 테스트는 실존 5개로 진행

### 미확인 사항
- 내부망에 16개 테이블 존재 여부 (Oracle 전환 후 확인)
- 제주 API 내부망 호출 가능 여부
- in_use DB→DB 5건의 소스 DB 위치
