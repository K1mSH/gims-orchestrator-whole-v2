---
id: VER-013
title: 표준화 자료 권장 (NOT NULL / 길이 단축) 이 외부 API 적재 케이스에 부적합
status: OPEN
created: 2026-04-29
parts: [P3-collector, P9-api-provider, standardized-table-spec]
parallel_safe: true
assignee: forward
related: [project_yaksoter-pipeline]
---

## 증상 요약

`docs/Standardizedtable/TM_GD20310.txt` / `TD_GD20310.txt` 의 NULLABLE 권장이 **외부 API 응답 적재 케이스에서 71% 데이터 손실** 발생.

4/29 약수터 (`tm_gd010310`/`td_gd010310`) 첫 수동 실행 결과:
- 응답 1000행 중 **293행만 INSERT**, **707행 NOT NULL 위반으로 실패** (frontend 에선 "skip 707" 으로 표시되지만 실질은 fail)
- 위반 컬럼 (PG 가 첫 위반에서 rollback 하니 일부): `abl_yn`, `instl_ymd`, `pic_cnpl`
- 응답 실측에서 NULL 빈번: `INS_DATE`, `OFFICE_TEL`, `OFFICE`, `DAY_AVG`, `BUILDING_NO` 등 다수

## 재현 절차

1. dev DB 의 `tm_gd010310` / `td_gd010310` 에 NOT NULL 제약 적용 상태로
2. infolink-api-collector 에서 `getSgisDrinkWaterList` Endpoint 등록 후 yyyy=2020 수동 실행
3. `pg_log` 또는 collector 로그에서 `null value in column "instl_ymd" violates not-null constraint` 다수 발생 확인

## 기대 vs 실제

### 기대 (표준화 자료 의도 추정)
- `INFO_CRT_INST_NM`, `ADDR`, `STDG_CD`, `ABL_YN`, `INSTL_YMD`, `DAY01_AVG_USR_CNT`, `DEL_YN`, `PIC_NM`, `PIC_CNPL` = NOT NULL
- 사용자가 GIMS 화면에서 직접 입력하는 데이터 무결성 권장

### 실제
- 외부 API (data.go.kr `getSgisDrinkWaterList`) 가 NULL 을 빈번히 보냄 (운영기관별 데이터 충실도 다름)
- 레거시 v2 도 이를 알았기 때문에 `String.valueOf(null) === "null"` 라는 더러운 우회로 NOT NULL 만족 (`AdminBatchServiceImpl.waterQualityParamMapping():200`)
- v2 운영 DB 의 `INS_DATE` 등 컬럼에 `"null"` 4글자 문자열이 잔뜩 박혀있을 것 (안티패턴)

## 분석

표준화 자료의 N 권장 대상 = "**GIMS 자체 관리 / 사용자 직접 입력 데이터**" 의 입력 무결성 권장.
외부 API 응답 적재용 우리 테이블 = 입력 무결성 강제 불가 (외부 데이터를 우리가 컨트롤 못 함).

표준 표기를 그대로 적용하면 데이터 적재 실패 — 표준이 적재 방해 요인이 됨.

## 임시 조치 (4/29 적용 완료)

약수터 8 엔티티 + dev DB 4 테이블에서 NOT NULL 풀음:
- `YaksoterJewon` / `YaksoterWqResult` (collector)
- `IfSndTmGd010310` / `IfSndTdGd010310` (others)
- `IfRsvTmGd010310` / `IfRsvTdGd010310` / `TmGd010310` / `TdGd010310` (bojo-int)
- dev DB: `tm_gd010310` / `td_gd010310` / `if_snd_tm_gd010310` / `if_snd_td_gd010310` 의 `sn` PK 외 모든 컬럼 `DROP NOT NULL`
- 결과: 응답 1000행 모두 적재 (검증은 4/29 사용자 재실행 시점)

## 본 이슈 — 표준 관리 측에 건의

표준화 자료 (`docs/Standardizedtable/TM_GD20310.txt` / `TD_GD20310.txt`) 의 NULLABLE 컬럼:
- 사용자 직접 입력 데이터 무결성 권장 → **별첨 또는 주석 추가** ("외부 API 적재용 사본 테이블에선 풀어도 무방")
- 또는 NULLABLE 표기 자체를 Y 로 정정 — 외부 데이터 적재 가능성 인정

또 한 가지 — 약수터 외 다른 표준화 테이블도 동일 문제 가능성 (외부 API 적재 대상이라면). 표준 일괄 검토 필요할 수 있음.

## 추가 케이스 — 길이 단축 (4/29 발견)

수질 (`td_gd010310`) 첫 수동 실행 후 `value too long for type character varying(100)` 에러로 1행 fail. 위반 컬럼:
- `icpt_artcl` (INSP_RST = 부적합항목): 표준화 자료 = `VARCHAR(500) → VARCHAR(100)` 단축
- `icpt_actn_mttr` (FAIL_DESC = 부적합조치사항): 동일

### 분석

| | 레거시 v2 길이 | 표준화 권장 | 외부 응답 |
|--|------------:|-----------:|---------:|
| `INSP_RST` / `FAIL_DESC` | 500 | **100 (단축)** | 최대 500 |

부적합 항목 + 측정값 (예: `탁도(1.48), 일반세균(120), ...`) + 자유 서술 조치사항 — **운영 추적 핵심 정보**, 잘리면 안 됨. 1000 행 중 1행 발생 (0.1%) 이지만 데이터 손실은 부적합 케이스 추적 어려움.

### 임시 조치 (4/29 적용 완료)

- 약수터 4 엔티티 (`YaksoterWqResult` / `IfSndTdGd010310` / `IfRsvTdGd010310` / `TdGd010310`) 의 `icpt_artcl`/`icpt_actn_mttr` length 100 → 500 (레거시 동일)
- dev DB 2 테이블 (`td_gd010310`/`if_snd_td_gd010310`) `ALTER COLUMN TYPE varchar(500)`
- 결과: 길이 위반 해소

## 결정 사항 / 후속 조치

- [x] 약수터 8 엔티티 + dev DB 4 테이블 NOT NULL 풀기 (4/29 완료)
- [x] 약수터 4 엔티티 + dev DB 2 테이블 길이 100 → 500 (4/29 완료)
- [ ] Internal Oracle 측 약수터 테이블 (`TM_GD010310`/`TD_GD010310`/`IF_RSV_*`) 미생성 — bojo-int 첫 기동 시 변경된 엔티티 기준으로 ddl-auto 자동 생성
- [ ] 표준 관리 측에 표준화 자료 수정 건의 — **NULL 권장 + 길이 단축 권장 양쪽 모두**:
  - "외부 API 적재 케이스" 주석 추가 또는
  - NULLABLE = Y / 외부 응답 길이 그대로 정정
- [ ] 다른 표준화 테이블 일괄 검토 (외부 API 적재 대상 식별 — NULL/길이 양쪽 검사)

## 참고

- 4/14 분석: `dev_plan/2026_04/14/gims-yaksoter-table-analysis.md` (v2 vs v3 비교)
- 4/29 토론: yaksoter-resume-replace-api 세션 — "옵션 A vs B vs C" 비교 후 A 채택 (NULL 풀기, 누락 0)
- 레거시 우회 코드: `D:\dev\project\GIMS\GIMS_SOURCE\newgims_v2\src\gims\service\impl\AdminBatchServiceImpl.java:200`
