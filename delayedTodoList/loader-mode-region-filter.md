# Loader 지역별 실행모드 구현체 (보류)

## 상태: 보류 (우선순위 후순위)

## 보류 사유
- **범용성 문제**: 실행옵션은 보조망에만 국한된 기능이 아님. 다른 관측망(국가망 등)에서도 동일한 모드 교체 구조를 사용할 수 있어야 함. 보조망 특화 구현체를 먼저 만들면 설계가 보조망에 편향될 수 있음
- **설계 과정에서의 논의 경과**:
  - WHERE 조건 교체 → Step 내부 분기는 하드코딩과 다를 바 없어 기각
  - Step 분리(jewon/obsvdata) → 향후 경계가 바뀌면 대공사 필요하여 기각
  - Step 단위 monolithic 교체 + 공통 헬퍼 추출로 확정
  - 이 구조 자체는 완성했으나, 첫 구현체를 넣으려면 "어떤 조건으로 필터하는가"가 확정되어야 함
- **현행 확인 미완료**: obsrvt_id 지역코드 기반 필터가 유효한지 폐쇄망 DB 확인 필요
- 확인 없이 구현하면 잘못된 가정 위에 코드를 쌓게 됨

## 배경
- Loader 모드별 Step 교체 구조 완료 (3/11)
- 첫 번째 구현체로 "지역별 필터 모드" 검토

## 현황 파악

### obsrvt_id 명명법 (tm_gd970001)
- 형식: `{지역코드}-{업체코드}-G1-{순번}`
- 예시: `CN-CAN-G1-0001` (충청남도)
- 앞 2자리가 지역코드

### 확인된 지역코드 (개발 DB 기준)
| 코드 | 시도 |
|------|------|
| CN | 충청남도 |
| DG | 대구광역시 |
| DJ | 대전광역시 |
| GG | 경기도 |
| GN | 경상남도 |
| GW | 강원특별자치도 |
| IC | 인천광역시 |
| IL | 경기도 (?) |
| KS | 전라남도 (?) |
| SE | 서울특별시 |

### 미확인 사항 (현행 폐쇄망 DB에서 확인 필요)
- addr 컬럼 기준 시도와 obsrvt_id 지역코드 매핑이 일관적인지
- 관측데이터(pm_gd970201)가 지역코드별로 깔끔하게 나뉘는지
- 구버전 MyBatis target.xml의 실제 WHERE 조건

### 확인용 SQL
```sql
-- obsrvt_id 지역코드별 제원/관측데이터 건수 확인
SELECT
    substring(j.obsrvt_id, 1, 2) AS region_code,
    COUNT(DISTINCT j.obsrvt_id) AS jewon_cnt,
    COUNT(o.obsrvn_dta_id) AS obsv_cnt
FROM tm_gd970001 j
LEFT JOIN tm_gd970101 r ON r.spot_id = j.spot_id
LEFT JOIN pm_gd970201 o ON o.result_id = r.result_id
GROUP BY substring(j.obsrvt_id, 1, 2)
ORDER BY region_code;

-- obsrvt_id 지역코드 vs addr 시도 매핑 검증
SELECT
    substring(obsrvt_id, 1, 2) AS region_code,
    split_part(addr, ' ', 1) AS addr_sido,
    COUNT(*) AS cnt
FROM tm_gd970001
GROUP BY substring(obsrvt_id, 1, 2), split_part(addr, ' ', 1)
ORDER BY region_code, addr_sido;
```

## TODO (현행 확인 후)
- [ ] 현행 DB에서 위 SQL 실행 → 결과 기록
- [ ] 구버전 MyBatis target.xml WHERE 조건 확인
- [ ] 지역코드 기반 필터가 유효한지 판단
- [ ] 유효하면 구현체 설계 + 개발
