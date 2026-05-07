# 레거시 프로그램 ↔ 신규 서비스명 매핑

> 레거시 소스 위치: `D:\dev\claude\copySource\test\`
> 작성일: 2026-03-30
> IF 테이블 방침: **테이블별 개별 IF 쌍 생성** (팀장님 합의 3/30)

## DMZ DB 환경

| DB | DBMS | 내용 | 비고 |
|----|------|------|------|
| **보조망 DB** | PostgreSQL | 제주(jewon/obsv/facility/wavi), 안양, 나라장터, 뉴스API, 제원, 이용량 레거시 등 | API Collector 적재 + 기존 보조 데이터 |
| **새올 DB** | **Tibero** (실서버) / **Oracle** (개발) | RGETSTGMS01 외 16개 테이블 | 새올 행정시스템이 관리, 우리는 **읽기만** (테이블 생성 X) |

> **개발 환경**: Tibero 도커는 1개월 데모 라이선스 제한 → 개발용은 Oracle XE로 대체 (SQL 호환)
> **실서버**: Tibero JDBC 드라이버 필요 (`com.tmax.tibero.jdbc.TbDriver`)

| 컨테이너 | 이미지 | 포트 | JDBC URL | 계정 | 용도 |
|---------|--------|------|---------|------|------|
| gims_dmz_saeol_oracle | gvenzl/oracle-xe:21-slim | 29005 | `jdbc:oracle:thin:@localhost:29005/XEPDB1` | k1m / 1111 | 새올 DB (Oracle 대체) |

---

## 전체 파이프라인 흐름

```
[외부 API] → [API Collector] → DMZ DB
                                  ↓
              [bojo-dmz SND agent] → IF_SND (DMZ) → [Proxy] → IF_RSV (Internal)
                                                                     ↓
                                                     [bojo-internal RCV] → IF_RSV
                                                                     ↓
                                                     [bojo-internal 커스텀 Loader] → GIMS 타겟
```

### 서비스별 역할

| 서비스 | 위치 | 역할 | 비고 |
|--------|------|------|------|
| **API Collector** | DMZ (8084) | 외부 API → DMZ DB 적재 | 커스텀 실행기 |
| **bojo SND agent** (신규) | DMZ (8082) | DMZ DB → IF_SND 적재 | YAML 추가, 기존 SND 패턴 재사용 |
| **Proxy** | DMZ↔Internal | IF_SND → IF_RSV 전달 | 기존 그대로 |
| **bojo-internal RCV** (신규) | Internal (8092) | IF_RSV 수신 | YAML 추가, 기존 source-to-if 재사용 |
| **bojo-internal Loader** (신규) | Internal (8092) | IF_RSV → GIMS 타겟 적재 | **커스텀 Step 필요** (C안: 패키지 분리) |

---

## 1. DMZ 서비스 (API Collector 커스텀 실행기)

외부 API에서 데이터를 수집하여 DMZ DB에 적재

| # | 신규 서비스명 | 레거시 프로그램 | 소스 | 적재 테이블 (DMZ DB) | 특수 처리 |
|---|-------------|---------------|------|-------------------|----------|
| D1 | **JejuJewonExecutor** | InsetTb_jeju_jewon | selectObsv.json (POST) | `tb_jeju_jewon` | 좌표변환(5186→4326), 코드변환 3종 |
| D2 | **JejuObsvDataExecutor** | InsertJeju | selectObsvData.json (POST) | `tb_jeju` | site_code별 루프, 일일 수집 |
| D3 | **JejuFacilityExecutor** | RgetstgmsProgram + yearProgram | selectJejuUse.json (POST) | `rgetnpmms01` + `rgetstgms01` | 1000건 페이징, 좌표변환, 코드변환(8종), yearProgram은 동적파라미터 YEAR로 통합 |
| D4 | **JejuWaterQualityExecutor** | RgetnwaviProgram | selectSujil.json (POST) | `rgetnwavi05` + `rgetnwavi06` | 항목명 한→영 매핑, 용도구분(A/D) 분기 |
| D5 | **AnyangUsageExecutor** | (신규) | Mock API | `anyang_api_fac` + `anyang_api_data` + `use_legacy_data` | 이미 구현 (구조만) |

---

## 2. DMZ → Internal 전송 (IF 테이블)

### bojo-dmz SND agent (신규 YAML)

DMZ DB 테이블 → IF_SND 적재 (기존 SND 패턴 재사용)

### bojo-internal RCV (신규 YAML)

IF_RSV 수신 (기존 source-to-if Step 재사용)

### IF 테이블 목록 (제주)

| # | DMZ 소스 테이블 | IF_SND (DMZ) | IF_RSV (Internal) | 도메인 |
|---|---------------|-------------|-------------------|--------|
| 1 | tb_jeju_jewon | if_snd_tb_jeju_jewon | if_rsv_tb_jeju_jewon | 관측점 마스터 |
| 2 | tb_jeju | if_snd_tb_jeju | if_rsv_tb_jeju | 수위 관측 |
| 3 | rgetnpmms01 | if_snd_rgetnpmms01 | if_rsv_rgetnpmms01 | 허가신고정보 |
| 4 | rgetstgms01 | if_snd_rgetstgms01 | if_rsv_rgetstgms01 | 이용실태 |
| 5 | rgetnwavi05 | if_snd_rgetnwavi05 | if_rsv_rgetnwavi05 | 수질검사 |
| 6 | rgetnwavi06 | if_snd_rgetnwavi06 | if_rsv_rgetnwavi06 | 수질검사내역 |

### IF 테이블 목록 (새올 — 16개 전부 실존 확인 3/30)

| # | DMZ 소스 테이블 | IF_SND (DMZ) | IF_RSV (Internal) | 도메인 |
|---|---------------|-------------|-------------------|--------|
| 7 | RGETSTGMS01 | if_snd_rgetstgms01 | if_rsv_rgetstgms01 | 이용실태 |
| 8 | RGETNPMMS01 | if_snd_rgetnpmms01 | if_rsv_rgetnpmms01 | 허가신고정보 |
| 9 | RGETNWAVI05 | if_snd_rgetnwavi05 | if_rsv_rgetnwavi05 | 지하수수질검사 |
| 10 | RGETNWAVI06 | if_snd_rgetnwavi06 | if_rsv_rgetnwavi06 | 지하수수질검사내역 |
| 11 | RGETNMNFE01 | if_snd_rgetnmnfe01 | if_rsv_rgetnmnfe01 | 인력장비관리 |
| 12 | RGETNOPMS01 | if_snd_rgetnopms01 | if_rsv_rgetnopms01 | 지하수이용신고 |
| 13 | RGETNTGMS02 | if_snd_rgetntgms02 | if_rsv_rgetntgms02 | 지표수수질검사 |
| 14 | RGETNKCNO01 | if_snd_rgetnkcno01 | if_rsv_rgetnkcno01 | 케이싱정보 |
| 15 | RGETNWAMS01 | if_snd_rgetnwams01 | if_rsv_rgetnwams01 | 지표수환경기준 |
| 16 | RGETNSIMS01 | if_snd_rgetnsims01 | if_rsv_rgetnsims01 | 심화조사정보 |
| 17 | RGETNYYMS01 | if_snd_rgetnyyms01 | if_rsv_rgetnyyms01 | 용년정보 |
| 18 | RGETNJHMS01 | if_snd_rgetnjhms01 | if_rsv_rgetnjhms01 | 정화조정보 |
| 19 | RGETNSCKT01 | if_snd_rgetnsckt01 | if_rsv_rgetnsckt01 | 스케치정보 |
| 20 | RGETNKMTB01 | if_snd_rgetnkmtb01 | if_rsv_rgetnkmtb01 | 공간매체테이블 |
| 21 | RGETHKMIR01 | if_snd_rgethkmir01 | if_rsv_rgethkmir01 | 현황정보 |
| 22 | RGETNYCSG01 | if_snd_rgetnycsg01 | if_rsv_rgetnycsg01 | 연락처 |

### IF 테이블 목록 (이용량)

| # | DMZ 소스 테이블 | IF_SND (DMZ) | IF_RSV (Internal) | 도메인 |
|---|---------------|-------------|-------------------|--------|
| 23 | use_legacy_data | if_snd_use_legacy_data | if_rsv_use_legacy_data | 사용량 레거시 |
| 24 | use_status_data | if_snd_use_status_data | if_rsv_use_status_data | 사용량 상태 |

> **합계: IF 테이블 24쌍 (48개)** — 제주 6 + 새올 16 + 이용량 2

---

## 3. Internal 서비스 (bojo-internal 커스텀 Loader)

bojo-int에 패키지 분리하여 추가 (C안).
기존 bojo-internal 파이프라인과 적재 패턴이 다르므로 커스텀 Step으로 구현.

```
bojoint/
├── loader/step/InternalBojoLoadStep.java    ← 기존 (보조관측 EAV)
├── jeju/step/                               ← 제주 전용 (신규)
│   ├── JejuJewonLoadStep.java               (I1)
│   ├── JejuObsvdataLoadStep.java            (I2)
│   ├── JejuFacilityLoadStep.java            (I3)
│   ├── JejuRgetnLoadStep.java               (I4)
│   └── JejuUsageLoadStep.java               (I5)
└── saeol/step/                              ← 새올 전용 (신규)
    └── SaeolMergeLoadStep.java              (I6)
```

| # | 신규 Step | 레거시 | IF_RSV 소스 | 타겟 테이블 (GIMS) | 특수 처리 |
|---|----------|--------|-----------|-------------------|----------|
| I1 | **JejuJewonLoadStep** | JewonDB | if_rsv_tb_jeju_jewon | `TM_GD60001`, `TM_GD10001`, `TM_GD60130`, `TM_GD60002`, `GD60101_Gl`, `GD60101_Wtemp`, `GD60101_Scond` | 1→**7타겟** 분산, 고정값(V1~V10) |
| I2 | **JejuObsvdataLoadStep** | ObsvrdataDB | if_rsv_tb_jeju | `Pm60201(Gl/Wtemp/Scond)`, `Pm60202(Gl/Wtemp/Scond)` | 센서분기(S11→60201, S2x→60202), lc_sn, RID 증분 |
| I3 | **JejuFacilityLoadStep** | JejuInToDB | if_rsv_rgetstgms01 | `TmGd31010Gms`, `TmGd31010`, `PmGd31022` | 존재체크 후 조건부 INSERT, 연도별 |
| I4 | **JejuRgetnLoadStep** | RgetnDB | if_rsv_rgetstgms01 | `RGETSTGMS01` (내부망) | 단순 전체 이관 |
| I5 | **JejuUsageLoadStep** | UseToIn | if_rsv_use_legacy_data + if_rsv_use_status_data | `PM_GD31021`, `PM_GD31022`, `TM_GD31025` | SN 증분, 음수→0 보정, 후처리 |
| I6 | **SaeolMergeLoadStep** | saeol | if_rsv_rgetstgms01 외 15개 | 내부망 동일 16개 테이블 | flag(I/U/D)→MERGE/DELETE, TM_GD70001 진행추적, 동적 SQL |

---

## 4. 테이블 전체 흐름도

### 제주 보조관측망

```
[제주 API]                [DMZ DB]          [IF_SND]                [IF_RSV]                [GIMS]
selectObsv.json  ─D1─→ tb_jeju_jewon   → if_snd_tb_jeju_jewon → if_rsv_tb_jeju_jewon ─I1─→ TM_GD60001 외 7개
selectObsvData   ─D2─→ tb_jeju         → if_snd_tb_jeju       → if_rsv_tb_jeju       ─I2─→ Pm60201/60202 (6개)
```

### 제주 이용량

```
[제주 API]                [DMZ DB]         [IF_SND]               [IF_RSV]               [GIMS]
selectJejuUse    ─D3─→ rgetnpmms01    → if_snd_rgetnpmms01  → if_rsv_rgetnpmms01  ─(I4)─→ RGETNPMMS01 (내부망)
                        rgetstgms01    → if_snd_rgetstgms01  → if_rsv_rgetstgms01  ─(I3)─→ TmGd31010Gms 등
selectSujil      ─D4─→ rgetnwavi05    → if_snd_rgetnwavi05  → if_rsv_rgetnwavi05  ─(미정)→ 내부망 수질
                        rgetnwavi06    → if_snd_rgetnwavi06  → if_rsv_rgetnwavi06  ─(미정)→ 내부망 수질
```

### 이용량 레거시 + 새올

```
[DMZ DB]                  [IF_SND]                   [IF_RSV]                   [GIMS]
use_legacy_data       → if_snd_use_legacy_data    → if_rsv_use_legacy_data    ─I5─→ PM_GD31021, PM_GD31022
use_status_data       → if_snd_use_status_data    → if_rsv_use_status_data    ─I5─→ TM_GD31025
RGETSTGMS01 외 15개    → if_snd_rgetstgms01 ...   → if_rsv_rgetstgms01 ...   ─I6─→ 내부망 동일 16개 (MERGE/DELETE)
```

### 안양 이용량 (DMZ 완결)

```
[외부 API]                [DMZ DB]
Mock API         ─D5─→ anyang_api_fac + anyang_api_data + use_legacy_data
```

---

## 개발 순서 (DB 흐름 기준)

### Phase 1: 제주 보조관측망 (DMZ)
```
D1. JejuJewonExecutor (마스터 먼저)
 └→ D2. JejuObsvDataExecutor (site_code 참조)
```

### Phase 2: 제주 이용량 (DMZ)
```
D3. JejuFacilityExecutor (이용시설 먼저)
 └→ D4. JejuWaterQualityExecutor (수질검사)
```

### Phase 3: DMZ → Internal 전송 인프라
```
- IF 테이블 DDL 생성 (24쌍)
- bojo-dmz SND agent YAML 추가
  - 보조망 DB (PG) → 제주/이용량 IF_SND
  - 새올 DB (Tibero/Oracle) → 새올 IF_SND ← 실서버 시 Tibero 드라이버 필요
- bojo-internal RCV YAML 추가
```

### Phase 4: Internal 커스텀 Loader (bojo-internal 패키지 분리)
```
I1. JejuJewonLoadStep (1→7 분산) — 커스텀
I2. JejuObsvdataLoadStep (센서 분기) — 커스텀
I3. JejuFacilityLoadStep (이용량) — 커스텀
I4. JejuRgetnLoadStep (이용실태) — 단순 이관
I5. JejuUsageLoadStep (레거시 이용량) — 커스텀
I6. SaeolMergeLoadStep (새올 16개) — 별도 구조
```

---

## 레거시 이름 해석 참고

| 접두사/패턴 | 의미 | 해당 프로그램 |
|------------|------|-------------|
| Rgetn~ | 조회/수집 계열 | RgetnwaviProgram, RgetnDB |
| Rgetstgms | 이용실태 (시설관리) | RgetstgmsProgram |
| Rgetnpmms | 펌프/허가신고 | (테이블명으로만 존재) |
| Rgetnwavi | 수질검사 | RgetnwaviProgram |
| Inset~/Insert~ | 적재/삽입 | InsetTb_jeju_jewon, InsertJeju |
| ~DB | 내부망 DB 이관 | JewonDB, ObsvrdataDB, RgetnDB |
| ~Program | DMZ API 수집 | RgetstgmsProgram, RgetnwaviProgram |
| ~ToIn / ~InToDB | 외부→내부 이관 | UseToIn, JejuInToDB |
