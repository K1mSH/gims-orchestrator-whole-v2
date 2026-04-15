# GIMS 레거시 시스템 (v2) — 외부 제공 API 분석

> **분석 대상**: `D:\dev\project\GIMS\GIMS_SOURCE\newgims_v2`
> **분석 일자**: 2026-04-14
> **목적**: GIMS DB 데이터를 외부에 제공하는 API 파악

---

## 요약

| # | API/서비스 | 제공 데이터 | 소스 테이블 | 인증 |
|---|-----------|-----------|------------|------|
| 1 | **MEGOKR 환경부 API** | 수질검사, 공공지하수 | TM_GD30301, TM_GD30302, TM_GD00203, WT_DREAM_PERMWELL_PUBLIC | API Key |
| 2 | **가뭄119 API** | 긴급 관정 정보 | SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 | API Key |
| 3 | **OPN 공공 API** | 관정, 관측망, 수질, 관측소 | TM_GD30310, TM_GD10001 등 | API Key |

---

## 1. MEGOKR API (환경부 데이터 제공)

### 개요
- **방향**: GIMS → 외부 (데이터 제공)
- **핵심 파일**:
  - `src/gims/dao/MegokrApiDAO.java`
  - `src/egovframework/sqlmap/com/gims/sql_megokrapi.xml`

### 제공 데이터
| SQL ID | 인터페이스 | 제공 데이터 | 소스 테이블 |
|--------|-----------|-----------|------------|
| `selectNgw03` | EGSP-IF_NGW_03 | 수질검사 개요 | TM_GD30301 |
| `selectNgw04` | EGSP-IF_NGW_04 | 수질검사 결과 (125+ 항목) | TM_GD30302 |
| `selectNgw08` | EGSP-IF_NGW_08 | 공공 지하수 | TM_GD00203 |
| `selectNgw09` | EGSP-IF_NGW_09 | 공공 지하수 상세 | WT_DREAM_PERMWELL_PUBLIC |

### 특성
- API Key 검증: `selectApiKey()` → `TM_GD21301` 테이블
- 사용 이력 저장: `insertApiHistory()` → `TH_GD21301`

---

## 2. 가뭄119 API (긴급 관정 데이터 제공)

### 개요
- **방향**: GIMS → 외부 (가뭄 긴급 대응용)
- **핵심 파일**:
  - `src/gims/dao/Drought119ApiDAO.java`
  - `src/egovframework/sqlmap/com/gims/sql_drought119api.xml`

### 제공 데이터
- 소스 테이블: `SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033`
- 관정 위치(좌표), 규격(심도/구경), 양수기 마력, 연간 취수허가량 등
- 페이징: 최대 1,000건/요청

### 특성
- API Key 검증 동일 패턴

---

## 3. OPN 공공 API (지하수 데이터 서비스)

### 개요
- **방향**: GIMS → 외부 (공공 데이터 서비스)
- **핵심 파일**: `src/gims/web/OPNController.java`

### 제공 서비스
- 개발이용관정 조회서비스
- 관측망 조회서비스 (국가/해수침투/농어촌)
- 수질측정망 조회서비스
- 관측소 데이터 조회서비스
- 지역 수도 정보 조회서비스

### 특성
- 응답 형식: JSON / XML (파라미터로 선택)
- 사용 이력 저장: `opnService.insertApiUseLog()`
- ArcGIS WMS/WFS 프록시도 이 컨트롤러에 포함
