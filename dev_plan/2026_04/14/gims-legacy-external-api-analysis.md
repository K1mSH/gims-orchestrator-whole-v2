# GIMS 레거시 시스템 (v2) — 외부 API → DB 적재 분석

> **분석 대상**: `D:\dev\project\GIMS\GIMS_SOURCE\newgims_v2`
> **분석 일자**: 2026-04-14
> **목적**: 외부 API를 호출하여 DB에 적재하는 코드만 파악

---

## 요약

| # | API/서비스 | 외부 엔드포인트 | 스케줄 | 응답 형식 | 적재 테이블 | 상태 |
|---|-----------|---------------|--------|----------|------------|------|
| 1 | **약수터 수질검사** | data.go.kr | 매월 1일 01:00 | JSON | TM_GD20310, TD_GD20310 | **활성** |
| 2 | **지하수 관측망 연계** | 192.168.235.4:8080 등 | 매일 14:35 | JSON | udwater 테이블군 | **비활성** (주석) |

---

## 1. 약수터 수질검사 데이터 수집 (data.go.kr)

### 개요
- **역할**: 공공데이터포털에서 전국 약수터 수질검사 결과를 가져와 GIMS DB에 적재
- **핵심 파일**:
  - `src/gims/service/impl/AdminBatchServiceImpl.java` — 배치 로직
  - `src/gims/web/AdminBatchController.java` — 스케줄 트리거
  - `src/egovframework/egovProps/globals.properties` — API URL/키 설정

### 외부 API 정보
```
URL: http://apis.data.go.kr/1480523/WaterQualityService/getSgisDrinkWaterList
Method: GET
Parameters:
  - serviceKey: (발급 키, globals.properties)
  - pageNo: 1
  - numOfRows: 30000  (전량 수집)
  - resultType: JSON
  - yyyy: (대상년도)
```

### 데이터 처리 흐름
```
HttpURLConnection (GET)
  → JSON 응답 수신 (BufferedReader)
  → org.json.simple.JSONValue.parseWithException()
  → getSgisDrinkWaterList.item[] 배열 추출
  → 65개 필드 매핑 (세균, 중금속, 화학물질, pH, 탁도 등)
  → DB 적재
```

### DB 적재
| 테이블 | 용도 | SQL ID |
|--------|------|--------|
| `TM_GD20310` | 제원정보 (시설 기본) | `adm.insertWaterQualityInfo` |
| `TD_GD20310` | 수질검사 결과 | `adm.insertWaterQualityCheck` |

- **Upsert 로직**: 시설명 또는 주소 변경 시 UPDATE, 아니면 SKIP
- **중복 방지**: 검사 결과는 기존 존재 여부 체크 후 INSERT

### 스케줄
```java
@Scheduled(cron = "0 00 01 01 * ?")  // 매월 1일 01:00
```

### 특성
- 한 번에 30,000건 전량 수집 (페이징 없이 단건 호출)
- HttpURLConnection 사용 (동기 블로킹)
- API 키가 `globals.properties`에 하드코딩
- 재시도 로직 없음

---

## 2. 지하수 관측망 연계 (내부 REST API)

### 개요
- **역할**: 국가 지하수 관측망/농어촌 관측망/해수침투 관측망 데이터를 REST API로 수집
- **핵심 파일**:
  - `src/gims/util/ScheduleUtil.java` — 스케줄 정의
  - `src/gims/service/impl/UdwaterLinkServiceImpl.java` — 연계 서비스

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
