# 기존 bojo-loader 타겟 컬럼명 표준화

> 작성일: 2026-04-06
> 기준: GIMS_TABLE_표준화작업_Ver4.5.xlsx → 표준화사용테이블상세
> 상세 매핑: `dev_plan/2026_04/06/표준화_컬럼매핑.md`

## 목적
Oracle 29004의 기존 GIMS Target 4개 테이블을 환경부 표준 스키마로 전환.
현재 간소화 스키마 → 표준 전체 스키마로 **DROP + 재생성**.

## 대상 테이블

| 테이블 | 현재 컬럼수 | 표준 컬럼수 | 방식 |
|--------|-----------|-----------|------|
| TM_GD970001 (ODM관측소) | 18 (간소화) | 16 (표준) | DROP → CREATE |
| TM_GD970101 (ODM결과) | 4 (간소화) | 18 (표준) | DROP → CREATE |
| PM_GD970201 (ODM관측자료) | 7 | 5+메타 | DROP → CREATE |
| TM_GD980002 (Link) | 7 | 9 (표준) | DROP → CREATE |

## 주요 컬럼명 변경 (코드 영향)

### GimsTargetRepository.java에서 사용하는 컬럼

| 메서드 | 이전 컬럼 | 표준 컬럼 |
|--------|----------|----------|
| loadSpotIdMap | spot_id | brnch_id |
| loadSpotIdMap | obsrvt_id | obsvtr_id |
| loadSpotIdMap | spot_ty_mng_word_nm | brnch_type_mng_trm_nm |
| loadResultIdMap | result_id | rslt_id |
| loadResultIdMap | spot_id | brnch_id |
| loadResultIdMap | obsrvn_iem_id | obsrvn_artcl_id |
| ensureResultId | result_id | rslt_id |
| ensureResultId | time_unit_id | hr_unit_id |
| ensureResultId | obsrvn_iem_id | obsrvn_artcl_id |
| ensureResultId | spot_id | brnch_id |
| batchInsertObsvdata | result_id | rslt_id |
| batchInsertObsvdata | obsrvn_dta_value | obsrvn_data_vl |
| batchUpsertLink | obsrvt_id | obsvtr_id |
| batchUpsertLink | spot_id | brnch_id |

### InternalBojoLoadStep.java에서 사용하는 컬럼
- IF 테이블 컬럼(obsv_code, obsv_date 등)은 변경 없음
- GimsTargetRepository를 통해 접근하므로 Repository 수정으로 커버
- 단, `spotIdMap.get(obsvCode)` 등 Map 키는 Repository 내부에서 처리

### InternalBojoLoadStepFactory.java
- 생성자 파라미터에 테이블명 전달 → 변경 없음 (YAML이 아닌 코드 내 상수)
- 확인 필요: 테이블명/컬럼명 하드코딩 부분

## 실행 계획

```
1. Oracle DDL 스크립트 작성
   - 4개 테이블 DROP (CASCADE CONSTRAINTS)
   - 표준 DDL CREATE (환경부 표준 컬럼명 + 메타컬럼)
   - 인덱스/UK 재생성
   파일: scripts/oracle-standardize-bojo-target.sql

2. Java 코드 수정
   - GimsTargetRepository.java: 전 메서드 컬럼명 변경
   - InternalBojoLoadStep.java: 필요시 수정 확인
   - InternalBojoLoadStepFactory.java: 테이블명 상수 확인

3. DDL 실행 (Oracle 29004)

4. 빌드 테스트
   - cd sync-agent-bojo-int && ./gradlew clean build -x test

5. bojo-loader E2E 재검증
   - 기동 → Loader 실행 → pm_gd970201 적재 확인
```

## 주의사항
- 기존 데이터는 테스트용이므로 재적재 가능
- PM_GD970201에 EXECUTION_ID, SOURCE_REFS 메타컬럼 유지 (추적용)
- TM_GD980002에 LINK_TRGT_IP, LINK_TRGT_PORT_CN은 표준에 있지만 현재 미사용 → 일단 포함
