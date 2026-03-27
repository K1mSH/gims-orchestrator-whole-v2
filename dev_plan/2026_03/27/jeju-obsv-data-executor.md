# jeju-obsv-data 커스텀 실행기 구현 계획

## 목적
제주도 보조지하수관측망 수위 관측 데이터를 실시간 수집하여 DMZ DB에 적재

## 원본 프로그램
- InsertJeju.java (역컴파일)
- API: `water.jeju.go.kr/selectObsvData.json`
- 방식: site_code별 루프, 일일 수집

## 구현 범위

### 1. Mock API
- `GET /mock/jeju/obsv-data?siteCode={code}&date={yyyyMMdd}`
- 응답: 관측 데이터 배열 (site_code, obsv_date, obsv_time, water_level, water_temp, ec 등)
- 10개 관측점 × 24시간 = 240건 정도

### 2. DB 테이블
- `jeju_obsv_data` (DMZ PG api_collector DB)
  - id (SERIAL PK)
  - site_code VARCHAR(20)
  - obsv_date DATE
  - obsv_time VARCHAR(8)
  - water_level NUMERIC
  - water_temp NUMERIC
  - ec NUMERIC
  - collected_at TIMESTAMP
  - UNIQUE(site_code, obsv_date, obsv_time)

### 3. JejuObsvDataExecutor
- 커스텀 실행기 인터페이스 구현
- site_code 목록 조회 (제원 테이블 또는 설정)
- site_code별 루프 → API 호출 → 파싱 → UPSERT
- 날짜 파라미터: 실행 시 당일 또는 지정일

### 4. 엔드포인트 등록
- executorType: jeju-obsv-data
- URL: Mock API URL (나중에 실제 URL로 교체)
- 스케줄: 매일 1회

## 수정 대상 파일
- `infolink-api-collector/.../mock/MockJejuController.java` (신규)
- `infolink-api-collector/.../executor/JejuObsvDataExecutor.java` (신규)
- DDL: api_collector DB에 테이블 생성

## 선행 작업
- 커스텀 실행기 구조 (12장) 완성 필요 — 인터페이스/레지스트리/분기 확인
