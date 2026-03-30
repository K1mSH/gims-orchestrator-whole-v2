# D2. JejuObsvDataExecutor 구현 계획서

## 개요
제주도 보조지하수관측망 수위 관측 데이터를 API에서 수집하여 DMZ DB에 적재하는 커스텀 실행기.
레거시: `InsertJeju.java` → 신규: `JejuObsvDataExecutor.java`

## 레거시 소스 분석 (InsertJeju.java)

### 플로우
1. `select_Tb_jeju_jewon`으로 제원 테이블에서 전체 site_code(obsrvt_id) 목록 조회
2. 오늘 날짜로 `data_time` 생성 (yyyyMMdd + "000000")
3. site_code별 루프:
   - POST `selectObsvData.json` (site_code, data_time 파라미터)
   - 응답 data 배열 → 건별 INSERT

### API 사양
- URL: `http://water.jeju.go.kr/obsvsystem/rest/selectObsvData.json`
- Method: POST
- 파라미터: `site_code` (관측점 ID), `data_time` (조회 시작일시 yyyyMMddHHmmss)
- 응답: `{ "data": [ { "siteCode":"SC001", "siteName":"한림", "dataTime":"20260330120000", "gl":"3.5", "scond":"250", "wTemp":"15.2", "mSn":"S11" }, ... ] }`

### 타겟 테이블: TB_JEJU
| 컬럼 | 타입 | 설명 | 매핑 |
|------|------|------|------|
| RID | VARCHAR(22) PK | 시퀀스 (SEQ_JEJU) | 자동증분 |
| OBSRVT_ID | VARCHAR(30) | 관측점 ID | API: siteCode |
| YMD | VARCHAR(8) | 관측일 | dataTime 앞 8자리 |
| DATA_TIME | VARCHAR(6) | 관측시각 | dataTime 뒤 6자리 |
| GL | VARCHAR(20) | 지하수위 | API: gl |
| SCOND | VARCHAR(20) | 전기전도도 | API: scond |
| WTEMP | VARCHAR(20) | 수온 | API: wTemp |
| MSN | VARCHAR(20) | 센서 식별 | API: mSn |

### 레거시 특이사항
- RID는 Oracle 시퀀스 `SEQ_JEJU.NEXTVAL` → PG에서는 SERIAL 또는 시퀀스
- INSERT only (UPSERT 아님) — 같은 관측점+시각 데이터가 중복 들어올 수 있음
- 우리는 UPSERT로 개선 (obsrvt_id + ymd + data_time + msn 복합키)

## 구현 항목

### 1. Mock API (`/mock/jeju/obsv-data`)
- MockApiController에 추가
- site_code, data_time 파라미터 받아서 응답
- site_code별 2~3건씩, 센서 타입(S11, S21, S22) 다양하게

### 2. TbJeju JPA 엔티티 (테이블 자동 생성)
- 실제 DDL 기반 (TB_JEJU)
- RID: PG에서 SERIAL 사용 (Oracle SEQ_JEJU 대응)
- @Comment 포함

### 3. JejuObsvDataExecutor 구현
- 위치: `infolink-api-collector/.../executor/JejuObsvDataExecutor.java`

#### 핵심 로직
```
1. TB_JEJU_JEWON에서 site_code(obsrvt_id) 목록 조회 (JdbcTemplate)
2. 오늘 날짜로 data_time 생성
3. site_code별 루프:
   a. API 호출 (POST, site_code + data_time)
   b. data 배열 파싱
   c. dataTime → ymd(8자리) + time(6자리) 분리
   d. UPSERT (obsrvt_id + ymd + data_time + msn 복합키)
4. 전체 결과 합산 반환
```

### 4. 엔드포인트 등록 + E2E 테스트

## 데이터 접근 방식: JdbcTemplate only
API Collector 커스텀 실행기는 동적 DataSource 특성상 JdbcTemplate만 사용.
JPA 엔티티는 테이블 자동 생성 + 향후 읽기용.

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| MockApiController.java | `/mock/jeju/obsv-data` 엔드포인트 추가 |
| TbJeju.java | **신규** — JPA 엔티티 (TB_JEJU, 테이블 생성용) |
| JejuObsvDataExecutor.java | **신규** — 커스텀 실행기 |

## 개발 대상 서비스

| 서비스 | 모듈 | 포트 | 역할 |
|--------|------|------|------|
| **infolink-api-collector** | infolink-api-collector | 8084 | Executor 구현 + Mock API + 엔티티 |

- 기존 D1(JejuJewonExecutor)과 동일한 서비스에서 개발
- API Collector가 외부 API 호출 → DMZ 보조망 DB(dev, 29001)에 적재
- 커스텀 실행기 패턴: `CustomExecutor` 인터페이스 구현 → `CustomExecutorRegistry` 자동 등록

### 데이터 흐름
```
[제주 API / Mock]                [API Collector (8084)]              [DMZ 보조망 DB (29001/dev)]
selectObsvData.json  ──호출──→  JejuObsvDataExecutor  ──UPSERT──→  tb_jeju (관측 데이터)
                                      ↑
                              tb_jeju_jewon에서 site_code 목록 조회
```

## 영향 범위
- infolink-api-collector 모듈만 수정
- D1(JejuJewonExecutor)에 의존: site_code 목록을 tb_jeju_jewon에서 조회
- 기존 기능에 영향 없음 (신규 추가)
