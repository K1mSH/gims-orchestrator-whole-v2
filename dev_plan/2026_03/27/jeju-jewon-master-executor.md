# jeju-jewon-master 커스텀 실행기 구현 계획

## 목적
제주도 보조지하수관측망 관측점 마스터 데이터를 API로 수집하여 DMZ DB(PG)에 적재

## 파이프라인 위치
```
제주 API (water.jeju.go.kr)
  ↓ API Collector 커스텀 실행기 (DMZ) ← 이번 구현 범위
DMZ DB (PG 29001/api_collector) — jeju_jewon
  ↓ Agent bojo-int (나중에, IF 구조 결정 후)
내부망 Oracle — TM_GD60001 등 GIMS 테이블
```

## 원본 프로그램
- `InsetTb_jeju_jewon.java` (class 역컴파일)
- 패키지: `kr.go.sec.SecLinkUpdate`
- 현행: DMZ에서 실행, 제주 테이블은 DMZ DB(현행 Tibero → 우리 PG)

## API 사양
- **URL**: `POST http://water.jeju.go.kr/obsvsystem/rest/selectObsv.json`
- **파라미터**: 없음 (전체 관측점 조회)
- **Content-Type**: `application/x-www-form-urlencoded`
- **응답**: JSON — `data` 배열에 관측점 목록

## 로직 (역컴파일 분석)

### 1. API 호출
- POST 요청, 파라미터 없이 전체 목록 조회
- 응답 JSON에서 `data` 배열 추출

### 2. 코드 변환 (3종)

| API 필드 | 조건 | DB 컬럼 | 변환값 |
|----------|------|---------|--------|
| wDtlSrv | 상수도 | ugrwtr_prpos_code | 18 |
| wDtlSrv | 농업 | ugrwtr_prpos_code | 19 |
| wDtlSrv | 그 외 | ugrwtr_prpos_code | 40 |
| wDrinkYn | 비음용 | drnk_at | 0 |
| wDrinkYn | 그 외 | drnk_at | 1 |
| wDevlocCi | 제주시 | legaldong_code | 6510000 |
| wDevlocCi | 서귀포시 | legaldong_code | 6520000 |
| wDevlocCi | 그 외 | legaldong_code | (빈문자열) |

### 3. 좌표변환
- 입력: `wX`, `wY` (EPSG:5186 — 중부원점TM)
- 출력: `siteLitd`(경도), `siteLttd`(위도) (EPSG:4326 — WGS84)
- 빈 값이면 좌표변환 스킵
- Java 좌표변환 라이브러리: proj4j

### 4. DB 적재
- 적재 대상: **DMZ PG** (29001/api_collector) — `jeju_jewon` 테이블
- 현행 iBatis SQL ID: `insetTb_jeju_jewon`
- UPSERT (site_code 기준 전체 갱신 — 마스터 데이터)

## 구현

### 1. Mock API
- `POST /mock/jeju/obsv`
- 응답 예시:
```json
{
  "data": [
    {
      "siteCode": "JJ-001",
      "siteName": "한림 관측소",
      "wDtlSrv": "상수도",
      "wDrinkYn": "음용",
      "wDevlocCi": "제주시",
      "wX": "123456.789",
      "wY": "234567.890",
      "siteLitd": "",
      "siteLttd": "",
      ...기타 필드
    }
  ]
}
```
- 관측점 20건 정도 Mock

### 2. DB 테이블
```sql
-- api_collector DB (PG 29001)
CREATE TABLE jeju_jewon (
    id SERIAL PRIMARY KEY,
    site_code VARCHAR(20) NOT NULL UNIQUE,
    site_name VARCHAR(100),
    ugrwtr_prpos_code VARCHAR(10),     -- 용도코드 (변환)
    drnk_at VARCHAR(2),                -- 음용여부 (변환)
    legaldong_code VARCHAR(10),        -- 지역코드 (변환)
    site_litd VARCHAR(20),             -- 경도 (좌표변환)
    site_lttd VARCHAR(20),             -- 위도 (좌표변환)
    -- 원본 필드 (API 응답 그대로)
    w_dtl_srv VARCHAR(50),
    w_drink_yn VARCHAR(20),
    w_devloc_ci VARCHAR(50),
    w_x VARCHAR(20),
    w_y VARCHAR(20),
    collected_at TIMESTAMP DEFAULT NOW()
);
```
> ※ 실제 API 응답 필드가 더 많을 수 있음 — Mock 구현 후 조정

### 3. JejuJewonExecutor
```
1. API 호출 (POST, 파라미터 없음)
2. 응답 JSON → data 배열 파싱
3. 각 레코드:
   a. 코드 변환 (wDtlSrv→ugrwtr_prpos_code, wDrinkYn→drnk_at, wDevlocCi→legaldong_code)
   b. 좌표변환 (wX,wY → siteLitd,siteLttd) — proj4j
   c. HashMap에 변환 결과 세팅
4. 배치 UPSERT (site_code 기준)
5. 결과 반환 (CustomExecutionResult)
```

### 4. 좌표변환 의존성
- `build.gradle`에 proj4j 추가: `implementation 'org.locationtech.proj4j:proj4j:1.3.0'`
- 또는 GeoTools (무거움) → proj4j 권장

## 수정 대상 파일
| 파일 | 내용 |
|------|------|
| `infolink-api-collector/build.gradle` | proj4j 의존성 추가 |
| `.../mock/MockJejuController.java` | Mock API (신규) |
| `.../executor/JejuJewonExecutor.java` | 커스텀 실행기 (신규) |
| `.../util/CoordinateConverter.java` | 좌표변환 유틸 (신규, 재사용) |
| DDL | jeju_jewon 테이블 생성 |

## 영향 범위
- API Collector 모듈 내 신규 파일만 — 기존 코드 변경 없음
- CoordinateConverter는 jeju-facility에서도 재사용
