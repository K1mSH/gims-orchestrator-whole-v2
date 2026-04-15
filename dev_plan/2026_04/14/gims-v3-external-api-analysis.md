# GIMS v3 — 외부 API → DB 적재 분석

> **분석 대상**: `D:\dev\claude\copySource\v3`
> **분석 일자**: 2026-04-14
> **목적**: 외부 API를 호출하여 DB에 적재하는 코드만 파악

---

## 요약

| # | API/서비스 | 외부 엔드포인트 | 스케줄 | 응답 형식 | 적재 테이블 | 상태 |
|---|-----------|---------------|--------|----------|------------|------|
| 1 | **VWorld 주소→좌표** | api.vworld.kr | 요청 시 | JSON | TC_GD00100 | **활성** |
| 2 | **지하수 관측망 연계** | 192.168.235.4:8080 등 | 매일 14:35 | JSON | udwater 테이블군 | **비활성** (주석) |

---

## 1. VWorld 주소→좌표 변환 + DB 적재

### 개요
- **역할**: PNU(필지번호) → 주소 변환 → VWorld API로 좌표 조회 → DB 저장
- **v2에는 없는 기능** — v3에서 추가됨
- **핵심 파일**:
  - `src/gims/service/impl/LocalApiServiceImpl.java` — API 호출 + 좌표 변환
  - `src/egovframework/sqlmap/com/gims/sql_localapi.xml` — SQL 매핑

### 외부 API 정보
```
URL: http://api.vworld.kr/req/search
Method: GET
Parameters:
  - key: 3CF1BE39-3C5E-3306-9D62-DE6A4427E4BD
  - query: (주소 문자열, URL 인코딩)
  - request: search
  - type: address
```

### 데이터 처리 흐름
```
PNU 코드 (19자리)
  → TC_GD00100 테이블에서 법정주소 조회
  → VWorld API 호출 (HttpURLConnection GET)
  → JSON 응답 파싱 (org.json.JSONObject)
  → X/Y 좌표 추출
  → EPSG 좌표계 변환 (3857 → 2097 → 4326)
  → 도/분/초 변환
  → TC_GD00100 테이블에 좌표 UPDATE
```

### DB 적재
| 테이블 | 용도 | SQL ID |
|--------|------|--------|
| `TC_GD00100` | 법정동 코드 + 좌표 | `localApiDAO.*` |

### 특성
- PNU → 주소 → 좌표의 3단계 변환
- 좌표계 변환 로직 내장 (EPSG:3857 ↔ 2097 ↔ 4326)
- 외부 REST API 엔드포인트로도 노출: `/localApi/ngw001`
- 온디맨드 (스케줄 아님)

---

## 2. 지하수 관측망 연계 (내부 REST API)

### 개요
- **v2와 동일한 구조** — 코드 거의 일치
- **핵심 파일**:
  - `src/gims/util/ScheduleUtil.java`
  - `src/gims/service/impl/UdwaterLinkServiceImpl.java`

### 외부 API 정보
| 관측망 | URL | 데이터 |
|--------|-----|--------|
| 국가 지하수 | `http://192.168.235.4:8080/rest/A001` | 제원 |
| 국가 지하수 상세 | `http://192.168.235.4:8080/rest/down/A001` | 제원 상세 |
| 국가 관측자료 | `http://192.168.235.4:8080/rest/downobsrv/A001` | 관측 데이터 |
| 환경부 수질 | `http://192.168.235.4:8080/rest/downwq/A001` | 수질검사 |
| 농어촌/해수침투 | `http://211.241.74.56:88/rest/...` | 제원/관측 |

### 데이터 처리 흐름
```
HttpURLConnection (GET, 페이징)
  → JSON 응답 (Gson 역직렬화)
  → JewonVO[] 배열 변환
  → udwaterLinkDAO.mergeJewon (MERGE/UPSERT)
```

### 특성
- **현재 비활성**: 코드에서 `callAPI()` 호출이 주석 처리됨
- **페이징**: `recordCountPerPage=1000`, `firstIndex` 순차 증가
- **스케줄 설정**: 매일 14:35 (비활성 상태)
- Gson 라이브러리로 JSON 파싱
- MERGE (UPSERT) 방식으로 적재
- **v2와 차이 없음**

---

## v2 vs v3 적재 비교

| 적재 항목 | v2 | v3 |
|----------|----|----|
| 약수터 수질검사 (data.go.kr → TM_GD20310, TD_GD20310) | **있음** (매월 배치) | 없음 |
| VWorld 좌표 적재 (api.vworld.kr → TC_GD00100) | 없음 | **있음** (온디맨드) |
| 지하수 관측망 연계 (내부 REST → udwater) | 있음 (비활성) | 있음 (비활성) |
