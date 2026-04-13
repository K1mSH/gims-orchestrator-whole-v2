# I3: JejuFacilityLoadStep 구현 계획

> 작성일: 2026-04-13
> 상태: 구현 완료 (3차 재검토 — 레거시 디컴파일 분석 반영)
> 전략 확인: ARCHITECTURE.md 1.2.6 (읽기=JPA, 쓰기=JDBC), 엔티티 소유권 규칙 확인 완료

## 개요

제주 이용시설 **제원(시설정보)** 데이터를 IF 테이블에서 GIMS 타겟 Oracle로 적재하는 Loader Step.
레거시 `JejuInToDB.java` → `select_rgetstgms01` + `insertTmGd31010Gms` 로직을 이식한다.

> **주의**: 레거시 target.xml에 `insertPmGd31022`(일자료)도 있지만, 디컴파일 분석 결과
> PM의 소스는 RGETSTGMS01이 아닌 **USE_JEJU_DAY** 별도 테이블이다.
> PM_GD111022 적재는 **I5(UseLoadStep)**가 `IF_RSV_USE_LEGACY_DATA` 경유로 별도 처리한다.

## 레거시 흐름 (JejuInToDB.java 디컴파일 확인)

```
[1단계 — I3 범위]
  select_rgetstgms01 (RGETSTGMS01 + TC_GD00002 + TC_GD00100 JOIN)
    → insertTmGd31010Gms (TM_GD31010 MERGE ON PRMISN_DCLR_NO)
    → TM 시설 제원만 적재

[2단계 — I5 범위 (별도 Step)]
  select_use_jeju_day (USE_JEJU_DAY 테이블 — RGETSTGMS01과 별개)
    → select_tmGd31010 (OBSRVT_ID로 BRNCH_ID 존재 확인)
    → insertTmGd31010 (없으면 간이 INSERT: SPOT_ID + OBSRVT_ID만)
    → insertPmGd31022 (BRNCH_ID + OBSR_DE MERGE)
    → PM 일자료 적재
```

## 소스/타겟

| 구분 | 테이블 | DB | 비고 |
|------|--------|-----|------|
| **Source** | IF_RSV_RGETSTGMS01 | Oracle 29004 (internal) | DMZ SND → 내부 RCV 경유 |
| **Target** | TM_GD111010 (이용량시설) | Oracle 29004 (internal) | 87컬럼 엔티티 생성 완료 |
| **룩업** | TC_GD00002 (공통코드) | Oracle 29004 (internal) | NGW_0003(용도 4건), NGW_0013(상세용도 34건) |
| **룩업** | TC_GD00100 (법정동코드) | Oracle 29004 (internal) | 제주 218건 |

> ~~PM_GD111022~~ — I3 범위가 아님. I5(UseLoadStep)가 USE_JEJU_DAY 소스로 별도 적재.

## 룩업 변환 (레거시 select_rgetstgms01 JOIN 재현)

레거시에서 RGETSTGMS01을 SELECT할 때 3개 테이블을 JOIN하여 코드→명칭 변환:

| 룩업 | 레거시 JOIN | 우리 구현 |
|------|-----------|----------|
| **TC_GD00002 (NGW_0003)** | `SUBSTR(UGWTR_COM_CD,2,2) = UWATER_SRV_CODE` → `CD_CN` | `loadUsageMap()` — code.substring(1) 매칭 |
| **TC_GD00002 (NGW_0013)** | `UGWTR_COM_CD = UWATER_DTL_SRV_CODE` → `CD_CN` | `loadDetailUsageMap()` — 직접 매칭 |
| **TC_GD00100** | `STDG_CD = REGN_CODE` → `CTPV_NM, SGG_NM, EMD_NM, LI_NM` | `loadAddrMap()` — 법정동코드→주소 |
| **USE_AT** | `CASE WHEN LNHO_RAISE_YN=0 AND END_NT_YN=0 THEN 'Y' ELSE 'N'` | `resolveUseYn()` — 두 조건 모두 체크 |

## 실행 흐름

```
1. 룩업 맵 3개 로딩 (TC_GD00002 NGW_0003/NGW_0013, TC_GD00100)
2. IF_RSV_RGETSTGMS01에서 PENDING 건 조회 (JPA native query, 조건실행 지원)
3. 건별 순차 처리:
   a. source_refs 생성
   b. 룩업 변환 (코드→명칭, 법정동코드→주소, USE_YN 판별)
   c. TM_GD111010 MERGE (PRMSN_DCLR_NO 기준)
      - NOT MATCHED → INSERT (BRNCH_ID는 IDENTITY 자동 채번)
      - MATCHED → UPDATE
   d. successIds / failedIds 수집
4. IF 상태 일괄 업데이트 (SUCCESS/FAILED)
5. SyncLog 기록
6. StepResult 반환
```

## 컬럼 매핑 (IF → Target)

### TM_GD111010 — 레거시 insertTmGd31010Gms 컬럼 대조

| IF_RSV 컬럼 | 레거시 alias | TM_GD111010 컬럼 | 변환 |
|------------|-------------|-----------------|------|
| PERM_NT_NO | PRMISN_DCLR_NO | PRMSN_DCLR_NO | 직접 (**MERGE 키**) |
| REGN_CODE | LEGALDONG_CODE | STDG_CD | 직접 |
| (TC_GD00100 룩업) | BRTC_NM | CTPV_NM | 법정동 룩업 |
| (TC_GD00100 룩업) | SIGUN_GU_NM | SGG_NM | 법정동 룩업 |
| (TC_GD00100 룩업) | EMD_NM | EMD_NM | 법정동 룩업 |
| (TC_GD00100 룩업) | LI_NM | LI_NM | 법정동 룩업 |
| SF_TEAM_CODE | SF_ASSCT_CODE | LCLGV_CD | 직접 |
| UWATER_SRV_CODE | (NGW_0003 룩업) → UGRWTR_PRPOS_CTNT | UGWTR_USG_CN | SUBSTR 룩업 |
| UWATER_DTL_SRV_CODE | (NGW_0013 룩업) → UGRWTR_DETAIL_PRPOS_CTNT | UGWTR_DTL_USG_CN | 직접 룩업 |
| DIG_DIAM | WELL_CALBR | DGG_CALBR | 직접 |
| DPH | WELL_DPH_VALUE | DGG_DPTH | 직접 |
| POTA_YN | — | DKPP_YN | 직접 (레거시 SELECT에 없으나 IF에 존재) |
| LNHO_RAISE_YN + END_NT_YN | USE_AT | USE_YN | CASE문 재현 |
| (없음) | TELNO | TELNO | IF에 컬럼 없음 (레거시도 SELECT에 없음) |
| (SYSDATE) | FRST_REGIST_DT | FRST_REG_DT | INSERT 시 SYSDATE |

> 레거시 `select_rgetstgms01`에 없는 필드(OBSRVT_ID, REGIST_ID, TELNO 등)는
> RGETSTGMS01 원본에도 해당 컬럼이 없으므로, Java에서 별도 세팅하는 것이 아니라 null로 들어감.

## 수정 대상 파일 (최종)

| 파일 | 변경 내용 |
|------|----------|
| **JejuFacilityLoadStep.java** | **신규** — I3 Step 구현 (IF 읽기: JPA, 룩업: JDBC, MERGE: JDBC) |
| **JejuLoadStepFactory.java** | FACTORY_KEYS에 `jeju-facility-load` 추가, create()에 분기 추가 |
| **internal-jeju-loader.yml** | steps에 jeju-facility-load 항목 추가 |
| **create-lookup-tables.sql** | **신규** — TC_GD00002 + TC_GD00100 DDL + 실데이터 INSERT |

## I1 패턴과의 차이점

| 항목 | I1 (JejuJewonLoadStep) | I3 (JejuFacilityLoadStep) |
|------|----------------------|--------------------------|
| Source IF | IF_RSV_TB_JEJU_JEWON | IF_RSV_RGETSTGMS01 |
| Target | 5개 테이블 체인 | **1개** (TM_GD111010만) |
| MERGE 키 | OBSVTR_ID | PRMSN_DCLR_NO (허가신고번호) |
| 룩업 테이블 | 없음 | TC_GD00002(2그룹) + TC_GD00100 |
| 난이도 | 높음 (5테이블 체인) | 중간 (1테이블 + 3룩업) |

## 테스트 계획

1. **빌드** — `./gradlew clean build -x test` 통과
2. **룩업 데이터 확인** — TC_GD00002 38건, TC_GD00100 218건 투입 확인
3. **IF 테스트 데이터** — IF_RSV_RGETSTGMS01에 PENDING 건 확인 (없으면 수동 INSERT)
4. **E2E 실행** — Orchestrator에서 internal-jeju-loader 실행
5. **검증 항목**:
   - TM_GD111010 MERGE 정상 (신규 INSERT + 기존 UPDATE)
   - 룩업 변환: UGWTR_USG_CN에 코드가 아닌 명칭(생활용 등)이 들어가는지
   - 법정동 룩업: CTPV_NM/SGG_NM/EMD_NM/LI_NM이 정상 변환되는지
   - USE_YN: LNHO_RAISE_YN=0 AND END_NT_YN=0 → 'Y' 확인
   - IF 상태 SUCCESS/FAILED 업데이트
   - SyncLog 기록
6. **멱등성** — 동일 데이터 재실행 시 UPDATE만 발생

## 참고

- **PM_GD111022는 I3 범위가 아님** — 레거시 디컴파일(JejuInToDB.java) 확인 결과, PM의 소스는 USE_JEJU_DAY 테이블이며 I5(UseLoadStep)가 IF_RSV_USE_LEGACY_DATA → PM_GD111021 → 일집계 → PM_GD111022 경로로 별도 처리
- I5는 TM_GD111010에서 TELNO로 BRNCH_ID를 조회 — I3이 시설 제원을 먼저 적재해야 I5가 BRNCH_ID를 찾을 수 있음 (실행 순서 의존)
- IF_RSV_RGETSTGMS01은 DMZ SND → 내부 RCV 경유로 Oracle 29004에 적재됨
- 룩업 테이블 DDL + 데이터: `scripts/ddl/internal-oracle/create-lookup-tables.sql`
- 레거시 디컴파일 소스: `copySource/test/internal/in_use/decompiled/`
