# 약수터 데이터 분석 — v2 vs v3 비교 + API 현황

> **분석 일자**: 2026-04-14
> **목적**: 약수터 관련 테이블이 v3에서 어떻게 되었는지, 적재 API가 살아있는지 확인

---

## 1. 약수터 관련 테이블 현황

| 테이블 | 용도 | v2 사용 여부 | v3 사용 여부 |
|--------|------|------------|------------|
| `TM_GD20310` | 토양지하수(약수터) 제원정보 | **사용** — 배치 적재 + 드림서비스 조회 | **없음** (0건) |
| `TD_GD20310` | 토양지하수(약수터) 수질검사 결과 | **사용** — 배치 적재 + 드림서비스 조회 | **없음** (0건) |
| `TM_GD20502` | (약수터 제원 아님) 팝업관리 테이블 | 팝업관리 | 팝업관리 (약수터 무관) |
| `TM_GD01502` | 약수터 제원정보 (신규?) | **없음** | **없음** |
| `TM_GD32006` | 드림서비스 약수터 시설정보 | **없음** | **없음** |
| `TM_GD112006` | 드림서비스 약수터 시설정보 | **없음** | **없음** |
| `TD_GD010310` | 약수터 수질검사 결과 (신규?) | **없음** | **없음** |

> **TM_GD01502, TM_GD32006, TM_GD112006, TD_GD010310** — v2/v3 어디에서도 코드 참조 없음.
> 새올 DB 전용 테이블이거나 아직 구현 전인 테이블일 가능성 있음.

---

## 2. v2에서 약수터 기능이 어떻게 동작했는가

### 2-1. 데이터 적재 (data.go.kr 배치)
- **파일**: `AdminBatchServiceImpl.java`, `AdminBatchController.java`
- **API**: `http://apis.data.go.kr/1480523/WaterQualityService/getSgisDrinkWaterList`
- **스케줄**: `@Scheduled(cron = "0 00 01 01 * ?")` — 매월 1일 01:00
- **적재**: `TM_GD20310` (제원), `TD_GD20310` (수질결과)
- **건수**: 한 번에 30,000건 전량 수집

### 2-2. 관리자 수동 등록
- **파일**: `AdminSystemController.java`, `waterQualityView.jsp`, `waterQ.js`
- **기능**: 관리자가 직접 API 호출 → 결과 확인 → 수동 등록/엑셀 다운로드

### 2-3. 드림서비스 약수터 조회 (지도)
- **파일**: `igis_webServiceController.java` → `igis.selectWaterQualityDetail`
- **SQL**: `TM_GD20310 JOIN TD_GD20310` — 약수터명, 수질검사 결과 상세
- **화면**: 드림서비스 지도에서 약수터 레이어 클릭 → 팝업으로 상세정보 표시
- **관련 JS**: `igis_dream.js`, `igis_search_dream.js` — 약수터 탭, 상세정보 팝업, 인쇄

### 2-4. 지도 레이어
- `layer_toc_new.js`: `WT_WRCCT_WGS` 레이어 — 드림서비스 약수터 WFS 레이어
- 지도 탭에 "약수터" 탭 존재 (`map_tab_new.jsp`)

---

## 3. v3에서 약수터 상태

**v3에서 약수터 관련 코드/테이블이 전부 없음:**
- `TM_GD20310` — 0건
- `TD_GD20310` — 0건
- "약수터" 키워드 — 0건
- `getSgisDrinkWaterList` — 0건
- 배치 적재 코드 — 없음
- 드림서비스 약수터 조회 — 없음
- 관리자 수동 등록 화면 — 없음
- 지도 약수터 레이어 — 없음

> v3에서 의도적으로 제거했는지, 마이그레이션 누락인지는 담당자 확인 필요.

---

## 4. data.go.kr API 현황 (2026-04-14 테스트)

### 4-1. v2에서 사용하던 API

```
URL: http://apis.data.go.kr/1480523/WaterQualityService/getSgisDrinkWaterList
```

| 테스트 항목 | 결과 |
|------------|------|
| HTTP 응답 | **401 Unauthorized** |
| data.go.kr 포털 검색 | **검색 안 됨** (서비스명, 기관코드 모두) |
| 신규 키 발급 | **불가** (포털에서 해당 API가 안 보이므로 활용신청 자체 불가) |
| 기존 키 갱신 | **불가** (같은 이유) |

**결론: 엔드포인트는 살아있지만 (401, not 404), 포털에서 검색 불가 → 키 발급/갱신 불가 → 사실상 사용 불가능**

### 4-2. 같은 기관(국립환경과학원)의 현재 활성 API

| 항목 | v2에서 쓰던 것 | 현재 활성 API |
|------|--------------|--------------|
| 서비스명 | WaterQualityService | **Dwqualityservice** |
| 오퍼레이션 | `getSgisDrinkWaterList` | `getDrinkWaterORGWATR` |
| URL | `.../WaterQualityService/...` | `.../Dwqualityservice/...` |
| 데이터 대상 | **약수터** (먹는물 공동시설) 수질검사 | **먹는샘물 제조업체** 원수 수질검사 |
| 상태 | 사실상 폐기 | 활성 (자동승인) |

> **주의**: 현재 활성 API(`Dwqualityservice`)는 "먹는샘물(생수) 제조업체" 대상이라 약수터와는 **다른 데이터**.

### 4-3. "약수터" 키워드로 검색된 API (시도 단위)

| API명 | 제공기관 | 형식 |
|-------|---------|------|
| 경기도_약수터 수질검사 결과집계 현황 | 경기도 | XML |
| 경기도 광명시_약수터 수질검사 현황 | 경기도 광명시 | XML, JSON |
| 부산광역시_먹는물 공동시설(약수터) 수질검사 결과 정보 | 부산광역시 | XML, JSON |
| 경기도 안양시_약수터 수질검사_현황 | 경기도 안양시 | JSON |
| 충청북도_환경분야정보 도내주요약수터 | 충청북도 | JSON |
| 경기도_약수터 집계 현황 | 경기도 | XML |
| 전라남도_전남 약수터 | 전라남도 | XML |

> **전국 단위 약수터 수질검사 API는 현재 data.go.kr에 존재하지 않음.**
> 시도별 개별 API만 존재 — 전국 데이터가 필요하면 시도별 API를 조합해야 함.

---

## 5. API 공식 가이드 분석 (PDF 확인)

> **출처**: `OpenAPI활용가이드_국립환경과학원_수질DB(20221007).pdf` (59페이지)

### 5-1. 서비스 전체 구조

이 PDF는 `WaterQualityService` 전체 가이드로, v2에서 쓰던 API가 포함된 **상위 서비스**의 문서임.

| 순번 | 오퍼레이션 (영문) | 오퍼레이션 (국문) | 용도 |
|------|-----------------|-----------------|------|
| 1 | `getWaterMeasuringList` | 물환경 수질측정망 운영결과 DB | 하천/호소 수질 |
| **2** | **`getSgisDrinkWaterList`** | **토양지하수 먹는물 공동시설 운영결과 DB** | **약수터 (v2에서 사용)** |
| 3 | `getRealTimeWaterQualityList` | 수질자동측정망 운영결과 DB | 자동측정소 |
| 4 | `getRadioActiveMaterList` | 방사성물질측정망 운영결과 DB | 방사성물질 |
| 5 | `getWaterMeasuringListMavg` | 물환경 수질측정망 운영결과 월평균 DB | 하천/호소 월평균 |

- **서비스 URL**: `http://apis.data.go.kr/1480523/WaterQualityService`
- **서비스 시작일**: 2020-03-10
- **인증**: 서비스 Key 방식
- **응답 형식**: XML / JSON 선택
- **데이터 갱신**: 토양지하수 먹는물 공동시설 → **분기 1회** (전 분기 데이터 공개)

### 5-2. getSgisDrinkWaterList (약수터) 상세

#### 요청 파라미터

| 파라미터 | 설명 | 필수 | 예시 |
|---------|------|------|------|
| `serviceKey` | 서비스 키 | **필수** | - |
| `numOfRows` | 페이지 크기 | 옵션 | 10 (기본값) |
| `pageNo` | 페이지 번호 | 옵션 | 1 (기본값) |
| `resultType` | 결과형식 | 옵션 | XML (기본), JSON |
| `yyyy` | 연도 (다건 가능) | 옵션 | `2012,2013` |
| `sido` | 시도 | 옵션 | `강원` |
| `period` | 분기 | 옵션 | `2/4` 또는 `7월` |
| `sgg` | 시군구 | 옵션 | `강릉` |
| `legacyCodeNo` | 지점코드 (다건 가능) | 옵션 | `PUB_2337,PUB_2340` |
| `spotNm` | 지점명 | 옵션 | `태장봉샘터` |

> v2 코드에서는 `yyyy`만 사용하고 `numOfRows=30000`으로 전량 수집하는 방식이었음.

#### 응답 필드 — 시설 정보 (제원)

| 필드명 | 설명 | v2 적재 테이블 |
|--------|------|--------------|
| `legacyCodeNo` | 지점번호 (PUB_xxxx) | TM_GD20310 |
| `spotNm` | 지점명 (약수터명) | TM_GD20310 |
| `spotStdCode` | 지점표준코드 | TM_GD20310 |
| `infoCreatInsttNm` | 정보생성기관 (시도) | TM_GD20310 |
| `doNm` | 시도 | TM_GD20310 |
| `ctyNm` | 시군구 | TM_GD20310 |
| `adres` | 주소 | TM_GD20310 |
| `admcode` | 법정동코드 | TM_GD20310 |
| `ablAt` | 폐지여부 (Y/N) | TM_GD20310 |
| `ablDe` | 폐지일자 | TM_GD20310 |
| `dayAvg` | 1일평균이용자수 | TM_GD20310 |
| `charge` | 담당자 | TM_GD20310 |
| `insDate` | 설치일자 | TM_GD20310 |
| `office` | 담당부서명 | TM_GD20310 |
| `officeTel` | 담당자연락처 | TM_GD20310 |

#### 응답 필드 — 수질검사 결과

| 필드명 | 설명 | v2 적재 테이블 |
|--------|------|--------------|
| `yyyy` | 검사년도 | TD_GD20310 |
| `period` | 분기 | TD_GD20310 |
| `samp_date` | 채수일자 | TD_GD20310 |
| `inspCheck` | 검사여부 | TD_GD20310 |
| `insp_date` | 검사일자 | TD_GD20310 |
| `acceptYn` | 적합여부 | TD_GD20310 |
| `suit` | 적합건수 | TD_GD20310 |
| `unsuit` | 부적합건수 | TD_GD20310 |
| `inspRst` | 부적합 항목 | TD_GD20310 |
| `failDesc` | 부적합시 조치사항 | TD_GD20310 |

#### 응답 필드 — 수질 측정항목 (65개+)

**세균류**: 일반세균저온, 일반세균중온, 총대장균군, 대장균, 분원성대장균군, 분원성연쇄상구균, 녹농균, 살모넬라, 쉬겔라, 아황산환원혐기성포자형성균, 여시니아균

**중금속**: 납, 비소, 셀레늄, 수은, 시안, 크롬, 카드뮴, 보론, 우라늄

**유기화합물**: 페놀, 다이아지논, 파라티온, 페니트로티온, 카바릴, 1.1.1-트리클로로에탄, 테트라클로로에틸렌, 트리클로로에틸렌, 디클로로메탄, 벤젠, 톨루엔, 에틸벤젠, 자일렌, 1.1디클로로에틸렌, 사염화탄소, 1,2-디브로모-3-클로로프로판, 1,4-다이옥산

**일반항목**: 경도, 과망간산칼륨소비량, 냄새, 색도, 동(Cu), 세제(ABS), 수소이온농도(pH), 아연, 염소이온, 철, 망간, 탁도, 황산이온, 알루미늄, 암모니아성질소, 질산성질소, 불소, 브롬산염

#### 응답 예제 (XML)
```xml
<response>
  <header>
    <resultCode>00</resultCode>
    <resultMsg>NORMAL SERVICE</resultMsg>
  </header>
  <body>
    <items>
      <item>
        <ablAt>N</ablAt>
        <acceptYn>적합</acceptYn>
        <admcode>42130</admcode>
        <adres>강원도 원주시 봉산동 96-2</adres>
        <dayAvg>150</dayAvg>
        <insDate>1999.11.01</insDate>
        <inspCheck>검사</inspCheck>
        <itemBac>불검출</itemBac>
        <itemGenbaclow>2.0</itemGenbaclow>
        <itemKmn>불검출</itemKmn>
        <itemNo3am>불검출</itemNo3am>
        <itemNo3n>2.7</itemNo3n>
        <itemTotbac>불검출</itemTotbac>
        <legacyCodeNo>PUB_2331</legacyCodeNo>
        <office>환경과</office>
        <officeTel>033-747-3055</officeTel>
        <period>2/4</period>
        <spotNm>밤나무골약수</spotNm>
        <spotStdCode>42130E060000009</spotStdCode>
        <suit>47</suit>
        <unsuit>0</unsuit>
        <yyyy>2013</yyyy>
      </item>
    </items>
    <numOfRows>1</numOfRows>
    <pageNo>1</pageNo>
    <totalCount>25458</totalCount>
  </body>
</response>
```

### 5-3. 서비스 제공자 정보

| 항목 | 내용 |
|------|------|
| 담당자 | 조훈제 |
| 소속 | 국립환경과학원 토양지하수연구과 |
| 연락처 | 032-590-7465 |
| 이메일 | syjo76@korea.kr |

---

## 6. 확인 필요 사항

1. **v3에서 약수터 기능이 의도적 제거인지, 마이그레이션 누락인지?**
2. **TM_GD01502, TM_GD32006, TM_GD112006, TD_GD010310** — 이 테이블들이 어디서 나온 이름인지? (새올 DB? 신규 설계?)
3. **API 살아있는지 최종 확인 필요**:
   - PDF 가이드(2022.10.07)에는 분명히 `getSgisDrinkWaterList`가 존재
   - 하지만 data.go.kr 포털에서 현재 검색 불가 + 401 응답
   - **포털에서 내려간 것이지 API 자체가 폐기된 건 아닐 수 있음** → 담당자(조훈제, 032-590-7465)에게 직접 확인 권장
4. **약수터 데이터를 살려야 한다면**, 적재 방법 선택지:
   - (A) 기존 API가 살아있다면 → 키 재발급 요청 (담당자 직접 연락)
   - (B) API가 폐기됐다면 → 시도별 개별 API 조합 또는 다른 데이터 소스 확보
