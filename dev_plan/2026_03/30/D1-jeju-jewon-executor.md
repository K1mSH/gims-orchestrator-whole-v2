# D1. JejuJewonExecutor 구현 계획서

## 개요
제주도 보조지하수관측망 관측점 마스터 데이터를 API에서 수집하여 DMZ DB에 적재하는 커스텀 실행기.
레거시: `InsetTb_jeju_jewon.java` → 신규: `JejuJewonExecutor.java`

## 레거시 소스 분석 (InsetTb_jeju_jewon.java)

### API 사양
- URL: `http://water.jeju.go.kr/obsvsystem/rest/selectObsv.json`
- Method: POST (파라미터 없이 전체 조회)
- 응답: `{ "data": [ {...}, {...}, ... ] }`
- 페이징 없음 (전체 한번에 반환)

### 데이터 처리 로직
1. API 응답의 data 배열 순회
2. 코드 변환 3종:
   - `wDtlSrv` → `ugrwtr_prpos_code`: "상수도" 포함→18, "농업" 포함→19, 그 외→40
   - `wDrinkYn` → `drnk_at`: "비음용"→0, 그 외→1
   - `wDevlocCi` → `legaldong_code`: "제주시"→6510000, "서귀포시"→6520000, 그 외→""
3. 좌표변환 (siteLitd가 비어있고 wX가 있을 때만):
   - `wX, wY` (EPSG:5186) → `siteLitd, siteLttd` (EPSG:4326)
4. INSERT (레거시는 UPSERT 아님, 우리는 UPSERT로 개선)

## 구현 항목

### 1. Mock API (`/mock/jeju/obsv`)
- MockApiController에 추가
- 레거시 API 응답 형식 시뮬레이션
- 10~20건 정도 샘플 데이터 (다양한 코드값 포함)
- 응답 형식:
```json
{
  "data": [
    {
      "obsrvt_id": "JJ001",
      "obsrvt_nm": "한림관측소",
      "wDtlSrv": "상수도",
      "wDrinkYn": "음용",
      "wDevlocCi": "제주시",
      "wX": "160000.0",
      "wY": "250000.0",
      "siteLitd": "",
      "siteLttd": "",
      ...기타 필드
    }
  ]
}
```

### 2. DB 테이블 생성 (`jeju_jewon`)
- 대상 DB: 보조망 DB (PG, localhost:29001, dev)
- **JPA 엔티티로 생성** (ddl-auto=update), 직접 DDL 작성 안 함
- `@Table`, `@Column(columnDefinition)`, `@Comment` 등으로 컬럼 comment 작성
- 엔티티는 테이블 생성 + 향후 읽기용, 쓰기는 JdbcTemplate

#### JejuJewon 엔티티 주요 컬럼
| 컬럼 | 타입 | 설명 |
|------|------|------|
| obsrvt_id (PK) | VARCHAR(50) | 관측점 ID |
| obsrvt_nm | VARCHAR(200) | 관측점명 |
| w_dtl_srv | VARCHAR(100) | 용도 원본값 |
| ugrwtr_prpos_code | VARCHAR(10) | 용도코드 (변환) |
| w_drink_yn | VARCHAR(50) | 음용여부 원본값 |
| drnk_at | VARCHAR(10) | 음용여부 (변환) |
| w_devloc_ci | VARCHAR(100) | 지역 원본값 |
| legaldong_code | VARCHAR(20) | 법정동코드 (변환) |
| w_x | VARCHAR(50) | 원본 X좌표 (EPSG:5186) |
| w_y | VARCHAR(50) | 원본 Y좌표 (EPSG:5186) |
| site_litd | VARCHAR(50) | 변환 경도 (EPSG:4326) |
| site_lttd | VARCHAR(50) | 변환 위도 (EPSG:4326) |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

※ 기타 API 원본 필드는 Mock 데이터 확정 후 추가

### 3. JejuJewonExecutor 구현
- 위치: `infolink-api-collector/src/main/java/com/infolink/collector/executor/JejuJewonExecutor.java`
- AnyangUsageExecutor 패턴 참고

#### 핵심 로직
```
1. API 호출 (POST, 파라미터 없음)
2. data 배열 파싱
3. 건별 처리:
   a. 코드변환 3종
   b. 좌표변환 (siteLitd 비어있고 wX 존재 시)
   c. JdbcTemplate UPSERT (obsrvt_id 기준 ON CONFLICT)
4. 결과 반환 (CustomExecutionResult)
```

#### 좌표변환
- 라이브러리: `org.locationtech.proj4j` (기존 레거시도 proj4j 사용)
- build.gradle에 의존성 추가 필요
- EPSG:5186 → EPSG:4326 변환
- 레거시는 siteLitd가 비어있을 때만 변환 (이미 좌표가 있으면 스킵)

### 4. 엔드포인트 등록 + E2E 테스트
- API Collector UI에서 등록
- 실행 방식: 커스텀 (JejuJewonExecutor)
- 타겟: 보조망 DB, jeju_jewon 테이블
- 수동 실행 → 로그 확인 → DB 검증

## 데이터 접근 방식: JdbcTemplate only

API Collector 커스텀 실행기는 JdbcTemplate만 사용한다.

**이유**: 타겟 DB가 `endpoint.getTargetDatasourceId()`로 런타임에 결정되는 동적 DataSource.
JPA 엔티티/Repository는 앱 기동 시 고정된 EntityManager에 바인딩되므로 동적 DataSource에서 사용 불가.

참고로 Agent 모듈(bojo/bojo-int)은 "읽기=JPA, 쓰기=JdbcTemplate" 패턴을 쓰는데,
이쪽은 DataSource가 앱 설정으로 고정되어 있어서 JPA가 동작하기 때문.

| 모듈 | DataSource | 읽기 | 쓰기 |
|------|-----------|------|------|
| Agent (bojo/bojo-int) | 고정 (앱 설정) | JPA | JdbcTemplate batch |
| API Collector 커스텀 실행기 | 동적 (런타임 결정) | JdbcTemplate | JdbcTemplate |

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| MockApiController.java | `/mock/jeju/obsv` 엔드포인트 추가 |
| JejuJewon.java | **신규** — JPA 엔티티 (테이블 생성 + 읽기용, comment 포함) |
| JejuJewonExecutor.java | **신규** — 커스텀 실행기 (JdbcTemplate only) |
| build.gradle | proj4j 의존성 추가 |
| (DB) jeju_jewon | DDL 실행 |

## 영향 범위
- infolink-api-collector 모듈만 수정
- 기존 기능에 영향 없음 (신규 추가)
