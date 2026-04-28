# PRV 응답 alias v3 호환 정렬 계획

> 작성일: 2026-04-28
> 범위: 외부 노출 API (api-provider) 의 응답 컬럼 alias 를 **v3 레거시와 정확히 일치**하도록 정렬
> 발견 계기: B13 등록 화면에서 응답 필드명이 `WQ_INSP_SN` (표준화) 으로 표시되는데 실제 핸들러는 `qltwtrInspctSn` (camelCase) 으로 응답 — 화면/응답 미스매치 + Type B 11종 중 10종이 v3 응답 형식과 다름이 추가 확인됨
> 폐기 대상: 없음 (정책 명시화 + 핸들러 응답 alias 정정)

## 1. 정책 명시 (1줄 룰)

> **내부망 Oracle DB = 환경부 표준화 컬럼 / 외부 API 응답 = v3 레거시 alias (외부 사용자 호환)**

### 1.1 두 층의 표준이 다른 이유
- **DB 표준화**: 환경부 표준 컬럼명 도입 — 우리 운영 DB 의 통일된 명명 (예: `WQ_INSP_SN`/`CTPV_NM`)
- **API 응답 v3 호환**: v3 외부 사용자가 이미 v3 응답 alias 기준으로 코드 작성 — 깨면 외부 마이그레이션 비용 큼
- **결론**: 두 층 분리. 내부 DB ≠ 외부 API alias. 핸들러 SQL 안에선 표준화 컬럼 사용 후 응답 매핑 시 v3 alias 로 변환

### 1.2 권한 분리
- 표준화 적용 = 우리(GIMS 운영팀) 의지로 변경 가능
- v3 응답 alias = 외부 사용자 코드 호환 — **변경 금지** (변경 시 외부 영향 확정)

## 2. 영향 범위

| 모드 | 핸들러 수 | 현 상태 | 작업 |
|---|:--:|---|---|
| **Type B (CUSTOM)** | 11 | 10/11 응답 alias 가 v3 와 다름 (대문자 박힘) | **본 계획서 1차 — 핸들러 코드 수정** |
| **Type A (META)** | 12 | 미점검 (test_ops.py 결과 BRTC_NM 등 대문자 — v3 와 다를 가능성 높음) | **본 계획서 2차 — 운영자 등록 데이터 수정** |
| **frontend** | — | Type B 등록 화면이 datasource introspection (표준화) default 표시. metadata 의 v3 alias 무시 | **본 계획서 3차 — 화면 수정** |

## 3. Type B 11종 응답 alias 매핑표 (1차 작업 대상)

### 3.1 v3 SQL alias → OPNResultVO 필드 → JSON 응답 키

> iBatis 의 case-insensitive 매칭으로 SQL alias (대문자/혼합) 와 VO 필드 (소문자/camelCase) 가 매칭. JSON 직렬화 = VO 필드 그대로 (Jackson default).
> **응답 키 = VO 필드 형식**.

| # | 핸들러 | v3 SQL ID | 우리 현재 응답 키 | v3 진짜 응답 키 (수정 후) | 비고 |
|:--:|---|---|---|---|---|
| 1 | **B5** SupplementaryGroundwater | opn.info_general_105 | GENNUM/JIGUNAME/OBSV_CODE/INPERM_NO/ADDR/PYOGO/SOUR_GOV/INSDATE/OBSV_TYPE/WELL/CASING_HEIGHT/GULDEP/GULDIA/GIGWANMETHOD/GIGWANITEM/GROUNDUSE/UWATER_POTA_YN (17, 대문자) | gennum/jiguname/obsv_code/inperm_no/addr/pyogo/sour_gov/insdate/obsv_type/well/casing_height/guldep/guldia/gigwanmethod/gigwanitem/grounduse/uwater_pota_yn (17, 소문자/snake_case) | OPNResultVO |
| 2 | **B6** GroundwaterQuality | opn.info_general_211215 | ADDR/JIGUNAME/WELLNUM/GROUNDUSE/DRINKOX/GUBUN (6) | addr/jiguname/wellnum/grounduse/drinkox/gubun (6, 소문자) | OPNResultVO |
| 3 | **B9** LinkageChartDaily | opn.linkage_analy_chart_general | GENNUM/YMD/ELEV/WTEMP/LEV/EC (6) | gennum/ymd/elev/wtemp/lev/ec (6, 소문자) | OPNResultVO |
| 4 | **B10** ObservationStationTime | opn.observationStationTimeService | GENNUM/YMD/ELEV/WTEMP/LEV/EC (6) | gennum/ymd/elev/wtemp/lev/ec (6, 소문자) | OPNResultVO |
| 5 | **B13** WaterQualityMfds | opn.waterQualityMfdsInfo | qltwtrInspctSn/.../usrNm (20, camelCase) **+ C0001~ (대문자)** | qltwtrInspctSn/.../usrNm (camelCase 그대로) **+ c0001~ (소문자)** | OPNResultVO. 동적 PIVOT 만 수정 |
| 6 | **B14** InspectionList | opn.searchInspection | JOSACODE/DTA_STDR_YEAR/QLTWTR_INSPCT_IEM_CODE/REMARK_CTNT (4, 대문자/snake) | josacode/dtaStdrYear/qltwtrInspctIemCode/remarkCtnt (4, camelCase) | **TmGd30310** VO |
| 7 | **B15** InspectionDistinct | opn.searchAllInspection | QLTWTR_INSPCT_IEM_CODE/REMARK_CTNT (2) | qltwtrInspctIemCode/remarkCtnt (2, camelCase) | TmGd30310 |
| 8 | **B16-DJ** ActualUseDetailDj | opn.actualUseDetailDJ | SIGUNGU/YEAR/DEPART/YMD/YN (5, 대문자) | sigungu/year/depart/ymd/**YN** (소문자 + YN 만 대문자) | ⚠️ YN 만 대문자 유지 (VO 필드가 대문자) |
| 9 | **B16-KB** ActualUseDetailKb | (DJ SQL 재호출) | 동일 | 동일 | OPNResultVO |
| 10 | **B17** UnregitsFclySmrize | opn.unRegitsFclySmrize | SIDO/SIGUNGU/TOTAL/USED/UNUSED/UNDEFINED/PERMISSION/REGISTER/RESTORE/NONE (10, 대문자) | sido/sigungu/total/used/unused/undefined/permission/register/restore/none (10, 소문자) | OPNResultVO |
| 11 | **B18** WqInputStatusDj | opn.gnlwtqltinfo_inputsittn | SIDO/SIGUNGU/YEAR/ODR/TOTAL/COMPLT/NCOMPLT (7, 대문자) | sido/sigungu/year/odr/total/complt/ncomplt (7, 소문자) | OPNResultVO |

### 3.2 핸들러별 변경 카운트

| 카테고리 | 핸들러 | 변경 컬럼 |
|---|---|:--:|
| 전부 소문자로 | B5, B6, B9, B10, B17, B18 | 6 핸들러 — 합계 53 컬럼 |
| 동적 PIVOT 만 소문자로 | B13 | C0001~ → c0001~ (가변, 코드풀 따라) |
| 전부 camelCase 로 | B14, B15 | 2 핸들러 — 합계 6 컬럼 |
| 일부 (소문자) + YN 유지 | B16-DJ, B16-KB | 2 핸들러 — 4 컬럼 (YN 제외) |

**총 10개 핸들러 수정** (B13 은 동적 컬럼만)

## 4. 수정 대상 코드 위치

각 핸들러:
- `metadata.column().aliasName` — UI 노출용 응답 필드명
- `handle()` 의 `row.put("키", rs.getXxx("SQL_ALIAS"))` — 실제 응답 키 + ResultSet 추출 alias
- 핸들러 내부 SQL 의 SELECT alias — ResultSet 추출용. v3 와 동일 alias 박는 게 안전 (대문자/camelCase 그대로)

> **주의**: SQL alias 와 row.put 키가 일치해야 ResultSet 추출 정상.
> v3 SQL 의 alias 가 대문자 (`GENNUM`) 인 경우 → SQL alias = `GENNUM` 유지 + row.put 키 = `gennum` (소문자).
> Oracle 의 ResultSet 메타데이터는 SQL alias 의 대문자 처리 (unquoted) — `rs.getString("GENNUM")` 으로 추출 가능.

## 5. 단계별 작업

### Phase 1: Type B 핸들러 응답 alias 수정 (10종)

| 단계 | 작업 |
|:--:|---|
| 1.1 | B5/B6/B9/B10/B17/B18 — row.put 키 6종 모두 소문자로 (53 컬럼) |
| 1.2 | B13 — 동적 PIVOT 컬럼 alias `C{code}` → `c{code}` (소문자) |
| 1.3 | B14/B15 — row.put 키 camelCase 로 (TmGd30310 일치) |
| 1.4 | B16-DJ/KB — row.put 키 소문자 + YN 만 대문자 유지 |
| 1.5 | metadata.column().aliasName 도 동일 변경 |
| 1.6 | 빌드 + 재기동 |
| 1.7 | 11종 호출 검증 — 응답 키 v3 와 정확히 일치 |
| 1.8 | 회귀 — Type A 12종 (`test_ops.py`) 200 OK |

### Phase 2: Type A 응답 alias 점검 + 수정 (12종 — 별도 세션)

| 단계 | 작업 |
|:--:|---|
| 2.1 | v3 의 megokrApi/drought119Api 응답 형식 확인 (별도 컨트롤러) |
| 2.2 | ApiPrvOperationColumn 의 alias_name 일괄 UPDATE — v3 명 |
| 2.3 | 운영자 등록 화면 default 매핑 자동화 (옵션) |
| 2.4 | 12종 호출 검증 |

### Phase 3: frontend 화면 수정 (별도 세션)

| 단계 | 작업 |
|:--:|---|
| 3.1 | Type B 등록 화면 = preview API 의 metadata 기반 (datasource introspection 끄기) |
| 3.2 | "Type B 는 응답 필드명 변경 불가" 안내 추가 |
| 3.3 | Type A 등록 화면 = v3 매핑 자동 채움 옵션 (선택) |

## 6. 검증 전략

### 6.1 핸들러별
- 응답 JSON 의 키 = v3 OPNResultVO/TmGd30310 필드 정확히 일치
- 응답 건수 = 기존 검증 시나리오와 동일 (alias 변경만)
- ApiPrvCallHistory 기록 정상

### 6.2 회귀 — 변경 없어야
- Type A 12종 → 200 OK 그대로 (분기 영향 없음)
- bojo / bojo-int / others / provide Agent → 영향 없음

### 6.3 외부 호환성 점검
- v3 응답 형식 = 우리 응답 형식 일치 (외부 사용자 코드 마이그레이션 0)

## 7. 산출물 체크리스트

- [ ] 핸들러 10종 응답 alias 수정 (B5/B6/B9/B10/B13/B14/B15/B16-DJ/B16-KB/B17/B18)
- [ ] 빌드/재기동 + 11종 호출 검증
- [ ] Type A 12종 회귀 검증
- [ ] dev_log 4/28 갱신 (이슈 발견 + 정책 + 수정 내용)
- [ ] memory 추가 (`feedback_provide_response_v3_compat`) — 사용자 승인 후
- [ ] (별도) Type A 알iyas 점검 — `dev_plan/2026_04/29/` 또는 같은 폴더 후속

## 8. 리스크 / 주의사항

### 8.1 SQL alias 와 row.put 키 분리
- SQL alias 는 v3 그대로 (`GENNUM`/`SIDO` 대문자) — ResultSet 추출 호환
- row.put 키는 v3 응답 형식 (`gennum`/`sido` 소문자) — JSON 응답
- 둘이 다르더라도 OK (alias 가 ResultSet 추출용일 뿐)

### 8.2 B13 의 동적 PIVOT 컬럼명
- 현재: `MAX(...) AS "C0001"` (큰따옴표 — case-sensitive 대문자)
- 변경: `MAX(...) AS "c0001"` (소문자 quoted)
- ResultSet 에서 `rs.getString("c0001")` (소문자 quoted 매칭)

### 8.3 B16 의 YN 컬럼
- VO 필드가 `private String YN;` (대문자!) — 그대로 유지
- 다른 컬럼 (sigungu/year/depart/ymd) 만 소문자로

### 8.4 ApiPrvOperationColumn DB 데이터
- Type B 핸들러는 카탈로그 register 시점에 metadata.column().aliasName 이 ApiPrvOperationColumn 에 INSERT 됨
- 핸들러 alias 변경 후 — 기존 등록된 11 핸들러의 alias_name 도 일괄 갱신 필요할 수 있음
- **방법**: 운영자 화면에서 등록 해제 후 재등록 (가장 깔끔) 또는 SQL 직접 UPDATE

## 9. 정책 메모리 추가 (사용자 승인 후)

```markdown
---
name: feedback_provide_response_v3_compat
description: provide 응답 키 = v3 레거시 alias 유지 (외부 호환성). 내부 DB 만 표준화
type: feedback
---

provide(API 제공) 핸들러의 외부 응답 JSON 키는 **v3 레거시 alias 그대로** 사용한다 — 내부망 Oracle 의 컬럼은 표준화 (환경부 표준) 으로 적용해도, 외부 응답 키만큼은 v3 호환 유지.

**Why:** v3 외부 사용자 코드가 응답 키 기준으로 작성됨. 표준화 명으로 바꾸면 일괄 깨짐. provide 모듈 본질 = 외부 제공 안정성 (`feedback_provide_layer_upsert` 와 일관).

**How to apply:**
- v3 SQL 의 OPNResultVO/TmGd30310 등 VO 필드명 = 응답 JSON 키
- 핸들러 SQL 의 SELECT alias 는 v3 그대로 (대문자/camelCase) — ResultSet 추출용
- row.put 키 = VO 필드 형식 (소문자/camelCase) — JSON 응답
- metadata.column().aliasName = VO 필드 형식
- 운영자 화면 default = v3 alias 로 (datasource introspection 으로 표준화 명 보여주면 안 됨 — Type B)
- 신규 핸들러 작성 시 v3 SQL 의 VO 클래스 + 필드명 먼저 확인
```

## 10. 승인 요청 사항

이 계획서에 대해 사용자 승인 후 **Phase 1 (Type B 11종 수정)** 부터 순차 진행.

확정 필요:
- **(a)** 정책 명시 (§1) OK? 정책 명시? 무슨말이야 이게
- **(b)** §3 매핑표 (v3 진짜 응답 키) 정확함 OK? (특히 B16 의 YN 만 대문자, B13 의 c0001~ 소문자) >> 이건 너가 레거시 코드보고 판단할 일임. 일일이 확인하는 과정은 내가 하지 않음
- **(c)** 11종 핸들러 일괄 수정 + 카탈로그 재등록 OK? 필요하다면 그건 그렇게 해야지
- **(d)** Phase 2/3 (Type A 점검 + frontend) 별도 세션 OK? 프론트까지는 같이 처리해줘야해,
- **(e)** memory 추가 (`feedback_provide_response_v3_compat`) OK? 오키
