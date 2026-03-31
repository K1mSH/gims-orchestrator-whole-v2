# D4. JejuWaterQualityExecutor 구현 계획서

## 개요
제주도 수질검사 데이터를 API에서 수집하여 DMZ DB에 적재하는 커스텀 실행기.
레거시: `RgetnwaviProgram.java` → 신규: `JejuWaterQualityExecutor.java`

> 서비스 매핑 문서: `docs/useIncludeJeju/service-name-mapping.md` D4 항목
> SQL 매핑 XML: `D:\dev\claude\copySource\test\internal\in_use\source.xml`

## 레거시 소스 분석 (RgetnwaviProgram.java)

### API 사양
- URL: `http://water.jeju.go.kr/obsvsystem/rest/selectSujil.json`
- Method: POST
- 파라미터: `data_date` (연도, 작년)
- **페이징 없음** (전체 한번에 반환)
- 응답: `{ "data": [ {...}, ... ] }`

### 데이터 처리 로직

#### 1. 항목명 한→영 매핑 (itemName → listCode, 11종)

| 한글 (itemName) | 영문 (listCode) |
|----------------|----------------|
| 탁도(Turbidity) | Turbidity |
| 총대장균군 | TotalColiforms |
| 일반세균 | Coliforms |
| 암모니아성질소 | NH3-N |
| 색도(Colority) | Color |
| 불소(F) | F |
| 분원성대장균군 | Ec/Fe Coliforms |
| 망간(Mn) | Mn |
| 맛(Taste) | Taste |
| 냄새(Odor) | Odor |
| 알루미늄(Al) | Al |

#### 2. 코드변환

| API 필드 | 변환 | DB 컬럼 |
|----------|------|---------|
| `acceptNum` | lastIndexOf("-") 이후 문자열 | qwIspSno |
| `checkAddress` | "제주시" 포함→"6510000" / else→"6520000" | relTransCggCode |
| `codeNm` | "음용수(원수)"→"A" / else→"D" | qwIspSortCode |

#### 3. 직접 사용 필드
- `permissionNum` → PERM_NT_NO (PK)
- `dataIndate` → FIRST_REG_DTHR
- `dataValue` → RT (rgetnwavi06 전용)

### DB: 2개 테이블에 MERGE (source.xml 확인 완료)

```sql
-- insetRgetnwavi05: 수질검사
MERGE INTO RGETNWAVI05 USING dual
ON (PERM_NT_NO = #permissionNum#)
WHEN MATCHED THEN UPDATE SET
  REL_TRANS_CGG_CODE, QW_ISP_SNO, QW_ISP_SORT_CODE, FIRST_REG_DTHR
WHEN NOT MATCHED THEN INSERT (5컬럼)

-- insetRgetnwavi06: 수질검사내역
MERGE INTO RGETNWAVI06 USING dual
ON (PERM_NT_NO = #permissionNum# AND LIST_CODE = #listCode#)
WHEN MATCHED THEN UPDATE SET
  REL_TRANS_CGG_CODE, QW_ISP_SNO, QW_ISP_SORT_CODE, RT, ELIG_YN='1'
WHEN NOT MATCHED THEN INSERT (7컬럼)
```

**PK 확정:**
- rgetnwavi05: `PERM_NT_NO` (단일키)
- rgetnwavi06: `PERM_NT_NO` + `LIST_CODE` (복합키)

**하드코딩 값:**
- ELIG_YN = '1' (rgetnwavi06)

---

## 구현 항목

### 1. Mock API (`/mock/jeju/water-quality`)
- MockApiController에 추가
- `data_date` 파라미터 지원
- 페이징 없음, data 배열 직접 반환
- 10~15건, 다양한 항목명(11종 중 5~6종) + 다양한 codeNm/checkAddress
- 같은 permissionNum에 여러 항목(listCode)이 오는 케이스 포함

### 2. DB 테이블 (JPA 엔티티 2개)

대상 DB: 보조망 DB (PG, localhost:29001, dev)

#### `rgetnwavi05` (수질검사 — 5컬럼)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| perm_nt_no (PK) | VARCHAR(50) | 허가신고번호 |
| rel_trans_cgg_code | VARCHAR(10) | 시군구코드 |
| qw_isp_sno | VARCHAR(20) | 수질검사일련번호 |
| qw_isp_sort_code | VARCHAR(5) | 검사구분코드 (A/D) |
| first_reg_dthr | TIMESTAMP | 등록일시 |

#### `rgetnwavi06` (수질검사내역 — 7컬럼, 복합PK)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| perm_nt_no (PK1) | VARCHAR(50) | 허가신고번호 |
| list_code (PK2) | VARCHAR(30) | 항목코드 (영문) |
| rel_trans_cgg_code | VARCHAR(10) | 시군구코드 |
| qw_isp_sno | VARCHAR(20) | 수질검사일련번호 |
| qw_isp_sort_code | VARCHAR(5) | 검사구분코드 |
| rt | VARCHAR(50) | 검사결과값 |
| elig_yn | VARCHAR(2) | 적합여부 (고정값 1) |

### 3. JejuWaterQualityExecutor 구현

#### 핵심 로직
```
1. data_date: 동적 파라미터 YEAR 타입 재사용 (offset=-1 → 작년)
2. API 호출 (페이징 없음, 1회)
3. data 배열 건별 처리:
   a. 항목명 한→영 매핑 (itemName → listCode)
   b. acceptNum → qwIspSno (하이픈 뒤)
   c. checkAddress → relTransCggCode (제주시/서귀포시)
   d. codeNm → qwIspSortCode (음용수→A / else→D)
4. UPSERT 2회:
   - rgetnwavi05 (ON CONFLICT perm_nt_no)
   - rgetnwavi06 (ON CONFLICT perm_nt_no, list_code)
5. 결과 반환
```

### 4. 엔드포인트 등록 + E2E 테스트
- API Collector UI에서 등록
- 실행기 타입: 커스텀 (JejuWaterQualityExecutor)
- data_date: 동적 파라미터 (YEAR, -1)
- 수동 실행 → 로그 확인 → DB 검증 (2개 테이블)

---

## 레거시 대비 비교

| 항목 | 레거시 | 신규 |
|------|--------|------|
| DB 저장 | MERGE (Oracle) | UPSERT (PG ON CONFLICT) — 동일 동작 |
| PK | wavi05: PERM_NT_NO / wavi06: PERM_NT_NO+LIST_CODE | 동일 |
| 에러 처리 | MalformedURLException catch만 | 건별 skip + warn 로그 |
| 연도 관리 | 올해-1 하드코딩 | 동적 파라미터 YEAR (D3와 동일) |
| 항목 매핑 | if-else 11종 | Map 상수 (명시적) |

## 수정 대상 파일

| 모듈 | 파일 | 변경 내용 |
|------|------|----------|
| **api-collector** | MockApiController.java | `/mock/jeju/water-quality` 추가 |
| **api-collector** | Rgetnwavi05.java | **신규** — JPA 엔티티 (5컬럼) |
| **api-collector** | Rgetnwavi06.java | **신규** — JPA 엔티티 (7컬럼, 복합PK) |
| **api-collector** | JejuWaterQualityExecutor.java | **신규** — 커스텀 실행기 |

## 영향 범위
- infolink-api-collector 모듈만 수정
- 기존 기능에 영향 없음 (신규 추가)
- D3에서 추가한 YEAR 동적 파라미터 재사용
