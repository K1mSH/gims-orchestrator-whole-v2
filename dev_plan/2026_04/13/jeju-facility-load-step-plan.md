# I3: JejuFacilityLoadStep 구현 계획

> 작성일: 2026-04-13
> 상태: 계획

## 개요

제주 이용시설 데이터를 IF 테이블에서 GIMS 타겟 Oracle로 적재하는 Loader Step.
레거시 `JejuInToDB.java` + `target.xml`의 `insertTmGd31010Gms` / `insertPmGd31022` 로직을 이식한다.

## 소스/타겟

| 구분 | 테이블 | DB | 비고 |
|------|--------|-----|------|
| **Source** | IF_RSV_RGETSTGMS01 | PG 29001 (dev) | DMZ API Collector가 수집 |
| **Target 1** | TM_GD111010 (이용량시설) | Oracle 29004 | BRNCH_ID PK (IDENTITY), 69컬럼 |
| **Target 2** | PM_GD111022 (이용량일자료) | Oracle 29004 | BRNCH_ID + OBSRVN_YMD 복합PK |

## 레거시 로직 분석 (target.xml)

### insertTmGd31010Gms
```sql
MERGE INTO TM_GD31010 USING dual
ON (PRMISN_DCLR_NO = ?)
WHEN MATCHED THEN UPDATE SET 컬럼들...
WHEN NOT MATCHED THEN INSERT (SPOT_ID=SEQ.NEXTVAL, ...)
```
- **MERGE 키**: `PRMISN_DCLR_NO` (허가신고번호)
- INSERT 시 SPOT_ID(=BRNCH_ID)는 시퀀스 채번 → 우리는 IDENTITY 사용
- UPDATE에는 SPOT_ID 갱신 제외 (PK이므로)

### insertPmGd31022
```sql
MERGE INTO PM_GD31022 USING dual
ON (SPOT_ID = (SELECT SPOT_ID FROM TM_GD31010 WHERE OBSRVT_ID = ?) AND OBSR_DE = ?)
WHEN MATCHED THEN UPDATE SET USGQTY, LAST_MESURE_VALUE
WHEN NOT MATCHED THEN INSERT (SPOT_ID, OBSR_DE, USGQTY, LAST_MESURE_VALUE)
```
- **MERGE 키**: BRNCH_ID(OBSRVT_ID로 조회) + OBSRVN_YMD
- BRNCH_ID를 TM_GD111010에서 OBSVTR_ID로 역조회

## 실행 흐름

```
1. IF_RSV_RGETSTGMS01에서 PENDING 건 조회 (조건실행 지원)
2. 건별 순차 처리:
   a. source_refs 생성
   b. TM_GD111010 MERGE (PRMSN_DCLR_NO 기준)
      - NOT MATCHED → INSERT (BRNCH_ID는 IDENTITY 자동 채번)
      - MATCHED → UPDATE
   c. BRNCH_ID 조회 (MERGE 후 SELECT)
   d. PM_GD111022 MERGE (BRNCH_ID + OBSRVN_YMD 기준)
      - OBSRVN_YMD 없는 건은 skip (일자료 없는 시설만 등록 케이스)
   e. successIds / failedIds 수집
3. IF 상태 일괄 업데이트 (SUCCESS/FAILED)
4. SyncLog 기록
5. StepResult 반환
```

## 컬럼 매핑 (IF → Target)

### TM_GD111010 — 레거시 MERGE에서 사용하는 핵심 컬럼

| IF_RSV 컬럼 (snake) | GIMS 표준 컬럼 | 설명 |
|---------------------|---------------|------|
| PERM_NT_NO | PRMSN_DCLR_NO | 허가신고번호 (MERGE 키) |
| REGN_CODE | STDG_CD | 법정동코드 |
| BRTC_NM (가공) | CTPV_NM | 시도명 — IF에 없으면 "제주특별자치도" 고정 |
| SIGUN_NM (가공) | SGG_NM | 시군구명 |
| EMD_NM | EMD_NM | 읍면동명 |
| LI_NM | LI_NM | 리명 |
| SF_TEAM_CODE | LCLGV_CD | 지자체코드 |
| UWATER_SRV_CODE | UGWTR_USG_CN | 지하수용도 |
| UWATER_DTL_SRV_CODE | UGWTR_DTL_USG_CN | 지하수상세용도 |
| DIG_DIAM | DGG_CALBR | 굴착구경 |
| DPH | DGG_DPTH | 굴착깊이 |
| POTA_YN | DKPP_YN | 음용여부 |
| OBSRVT_ID | OBSVTR_ID | 관측소ID (PM 조회 키) |
| TELNO | TELNO | 전화번호 (I5 BRNCH_ID 조회 키) |
| USE_AT | USE_YN | 사용여부 |
| (없음, SYSDATE) | FRST_REG_DT | 최초등록일시 (INSERT시만) |

> 69컬럼 전체가 아닌 **레거시에서 실제 MERGE하는 컬럼** 위주로 1차 구현.
> 나머지 컬럼은 IF 소스에 데이터가 있는지 확인 후 2차 확장.

### PM_GD111022

| IF_RSV 컬럼 | GIMS 표준 컬럼 | 설명 |
|------------|---------------|------|
| (TM_GD111010에서 조회) | BRNCH_ID | FK |
| YOBSR_DE 또는 OBSR_DE | OBSRVN_YMD | 관측일자 (8자리) |
| USGQTY | USE_QNT | 사용량 |
| LAST_MESURE_VALUE | LAST_MSRMT_VL | 최종측정값 |

## DDL 변경 필요

**TM_GD111010 컬럼 추가 필요** — 현재 I5용 최소 컬럼(BRNCH_ID, TELNO)만 존재.
I3에서 사용하는 컬럼을 ALTER TABLE로 추가해야 함.

```sql
-- scripts/oracle-alter-gd111010-facility.sql (신규)
ALTER TABLE TM_GD111010 ADD (
    PRMSN_DCLR_NO       VARCHAR2(30),
    OBSVTR_ID            VARCHAR2(30),
    GRNDS_GWEL_NO        VARCHAR2(50),
    STDG_CD              VARCHAR2(10),
    CTPV_NM              VARCHAR2(40),
    SGG_NM               VARCHAR2(40),
    EMD_NM               VARCHAR2(30),
    LI_NM                VARCHAR2(40),
    BNJ                  VARCHAR2(20),
    LCLGV_CD             VARCHAR2(7),
    UGWTR_USG_CN         VARCHAR2(500),
    UGWTR_DTL_USG_CN     VARCHAR2(100),
    DGG_CALBR            NUMBER(10),
    DGG_DPTH             NUMBER(10),
    DKPP_YN              CHAR(1),
    USE_YN               CHAR(1),
    REG_ID               VARCHAR2(50),
    FRST_REG_DT          DATE
);

-- MERGE 키 유니크 인덱스
CREATE UNIQUE INDEX UQ_TM_GD111010_PRMSN ON TM_GD111010(PRMSN_DCLR_NO);
-- OBSVTR_ID 조회용 인덱스 (PM_GD111022 MERGE 시 사용)
CREATE INDEX IDX_TM_GD111010_OBSVTR ON TM_GD111010(OBSVTR_ID);
```

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| **JejuFacilityLoadStep.java** | **신규** — I3 Step 구현 |
| **JejuLoadStepFactory.java** | FACTORY_KEYS에 `jeju-facility-load` 추가, create()에 분기 추가 |
| **internal-jeju-loader.yml** | steps에 jeju-facility-load 항목 추가 |
| **oracle-alter-gd111010-facility.sql** | **신규** — TM_GD111010 컬럼 추가 DDL |

## I1 패턴과의 차이점

| 항목 | I1 (JejuJewonLoadStep) | I3 (JejuFacilityLoadStep) |
|------|----------------------|--------------------------|
| Source IF | IF_RSV_TB_JEJU_JEWON | IF_RSV_RGETSTGMS01 |
| Target 수 | 5개 테이블 체인 | 2개 (시설 + 일자료) |
| MERGE 키 | OBSVTR_ID | PRMSN_DCLR_NO (허가신고번호) |
| FK 연결 | BRNCH_ID → GWEL_NO 체인 | BRNCH_ID만 (단순) |
| 컬럼 수 | 10~15개/테이블 | TM: ~18개, PM: 4개 |
| 난이도 | 높음 (5테이블 체인) | 중간 (2테이블, 단순 FK) |

## 테스트 계획

1. **DDL 실행** — ALTER TABLE + 인덱스 생성 확인
2. **IF 테스트 데이터** — IF_RSV_RGETSTGMS01에 PENDING 건 수동 INSERT
3. **E2E 실행** — Orchestrator에서 internal-jeju-loader 실행
4. **검증 항목**:
   - TM_GD111010 MERGE 정상 (신규 INSERT + 기존 UPDATE)
   - PM_GD111022 MERGE 정상 (BRNCH_ID FK 연결)
   - OBSRVN_YMD 없는 건 → PM skip, TM만 처리
   - IF 상태 SUCCESS/FAILED 업데이트
   - SyncLog 기록 (source/target 테이블명, 건수)
5. **멱등성** — 동일 데이터 재실행 시 UPDATE만 발생

## 참고

- I5(UseLoadStep)가 이미 PM_GD111022를 사용 중 — DDL은 생성 완료
- I5는 TM_GD111010에서 TELNO로 BRNCH_ID를 조회, I3은 OBSVTR_ID로 조회 → 용도 다름
- IF_RSV_RGETSTGMS01은 DMZ API Collector(infolink-api-collector)가 새올 DB에서 수집
