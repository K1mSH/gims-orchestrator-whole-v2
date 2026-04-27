# Type B 내장 핸들러 이식 계획 (4/24 계획 폐기 후 재설계)

> 작성일: 2026-04-27
> 범위: Type B **17종** (12 원본 + B15 분리 + B16/B12 의 KB endpoint + B7/B8 흡수) 을 **api-provider 내장 핸들러** 로 일괄 처리
> 원칙: **operationId 1개 = 핸들러 1개** (1:1, SQL 내용 겹쳐도 개별 루트로 독립 구현)
> 폐기 대상: `dev_plan/2026_04/24/type-b-migration.md` (PVD 적재 방식)
> 선행 문서:
>  - `docs/provide/LEGACY_API_MIGRATION_MAP.md` (25건 전체 매트릭스)
>  - `docs/provide/PROVIDE_OPERATION_SPEC.md` (Operation 등록 스펙 — Type A 메타등록형 기준)
>  - `dev_plan/2026_04/24/type-b-migration.md` (폐기 대상, 의사결정 이력 보존용)
>  - **레거시 v3 소스**: `D:\dev\claude\copySource\v3` — 핸들러 SQL 추출 원천 (`src/egovframework/sqlmap/com/gims/sql_opn.xml` + `src/gims/web/OPNController.java`)

> ## 검증 이력 (2026-04-27, v3 전수 비교)
>
> 4/24 계획서의 일부 항목이 추측이었음을 v3 비교로 확정:
>
> - ✅ B12/B16 의 **KB endpoint 실재** (XML 만 보면 SQL 없음 → controller 가 brtcNm='경상북도' 로 DJ SQL 재호출. 별도 endpoint 노출 — 1:1 핸들러 분리 정당)
> - ✅ B7/B8 operationId 확정: `observationStationService/getWaterLevelObservationStation` / `getRainfallStation`
> - ✅ B11/B12/B13 동적 PIVOT 의 **`searchMaxDtaStdrYear` fallback 누락 발견** — 핸들러 작성 시 같이 박아야
> - ⚠️ B14/B15 외부 URL 노출 여부 미확정 (OPNController `/data/{service}/{operation}` 매핑에 안 보임 → 내부 helper 추정, 사용자 확인 필요)
> - 그 외 17종 모두 v3 select id 매핑 OK

---

## 1. 배경 및 폐기 사유

### 1.1 4/24 계획 (PVD 적재 방식) 의 한계

4/24 계획은 Type B 12종을 **provide Agent 의 Step** 으로 구현 → 외부 Oracle → IF/PG 적재 → `api_prv_*` → DynamicQueryService 제공 흐름이었다. 그러나:

- **실시간성 오차**: PVD 적재 주기 사이에 5~10분 데이터 지연. 특히 B7/B8 (10분 단위 실시간 관측) 은 구조적 오차로 보류 처리됨
- **추적 모델 부적합**: 원본이 우리 관할 외 Oracle (NGW). source_refs/execution_id 추적해도 외부 DB 변경 추적 불가능. trace-source FOUND 조건 자체가 의미 흐려짐
- **구현 복잡도**: Step 12종 + Factory + YAML + 엔티티 3종 + DDL 16종 + Orchestrator Agent 12 등록 + JOSACODE 확정 + Phase 6 일괄 operation 등록
- **Type A 추적 모델과 이질적**: Type A 7건은 우리 PG 적재가 의미 있음 (가공·검색용 정적 데이터). Type B 는 본질이 **레거시 SQL wrapping**

### 1.2 새 방식 (커스텀 핸들러)

```
[외부 호출] → ApiGatewayController
                ├─ 매핑 분기 ─┐
                │              ├─ META   → DynamicQueryService → PG (Type A 그대로)
                │              └─ CUSTOM → CustomOperationHandler#N → (외부 Oracle | 우리 PG 사본)
                ↓
              ApiPrvCallHistory 기록 (양쪽 공통)
```

- **신규 적재 없음** — DDL 16종, 엔티티 3종, IF 테이블, source_refs/execution_id 신규 작성 모두 제거
- **응답 시점 직접 쿼리** — 실시간 오차 0 (B7/B8 도 살림 가능)
- **Java 코드로 핸들러 12종 박음** — 동적 PIVOT/CTE/UNION 자유롭게 표현
- **UI 는 안내 전용** — 기존 Operation 등록 화면 재활용, Type B 는 readonly + lock 표시

> ⚠️ **"외부 Oracle" 의 의미 정정 (2026-04-27 재정의)** — Type B 17종 중 **B7/B8 을 제외한 15종이 호출하는 source 테이블은 우리 관할** (레거시 SQL 에 스키마 prefix 없음 = 우리 GIMS 스키마 안에 존재). 단지 운영 인프라(GIMS Oracle 시스템) 가 우리가 운영하는 인프라가 아닐 뿐. 진짜 외부 관할 (스키마 prefix 명시) 은 **B7/B8** (`DBLINKUSR.*`) 2종. 정확한 매트릭스는 **§5.5** 참조.
> 핸들러 방식 채택 사유는 "외부 출처라 추적 곤란" 이 아니라 (그건 부정확) — **운영 인프라가 외부 + 실시간성 + 변환 자유도 + 이중 적재 부담** (§5.5.5).

## 2. 범위 (확정)

### 2.1 핸들러 17종 (난이도 오름차순)

> **1:1 원칙**: B12/B16 처럼 SQL 이 DJ/KB 공용 구조라도 **operationId 별 별도 핸들러 클래스** 로 분리. SQL 중복 허용.
> **v3 매핑 검증 완료** (2026-04-27): 17종 모두 v3 의 select id 와 매핑. KB 는 SQL 자체는 공용이지만 controller 에서 brtcNm 만 다르게 별도 endpoint 로 노출되어 있어 1:1 분리 정당.

| # | B# | 핸들러 클래스 | 레거시 v3 SQL ID | 외부 operationId | 비고 |
|:--:|:--:|---|---|---|---|
| 1 (**파일럿**) | B14 | `InspectionListHandler` | `opn.searchInspection` | `searchInspection` (외부 노출 미확정 — helper 추정) | LEFT JOIN 1개, 복합 PK 3개 |
| 2 | B15 | `InspectionDistinctHandler` | `opn.searchAllInspection` | `searchAllInspection` (동일 — helper 추정) | B14 와 source 동일, GROUP BY 2 컬럼 — **별도 SQL 박음** |
| 3 | B6 | `GroundwaterQualityHandler` | `opn.info_general_211215` | `groundwaterMonitoringNetworkService/getGroundwaterQualityMeasurement` | VIEW_GTEST 단건 조회 |
| 4 | B17 | `UnregitsFclySmrizeHandler` | `opn.unRegitsFclySmrize` | `unRegitsFclySmrize/unRegitsFclySmrize` | UNION ALL + 스칼라 집계 8개 (대전 전용) |
| 5-1 | B16 | `ActualUseDetailDjHandler` | `opn.actualUseDetailDJ` | `actualUseDetailDJ/actualUseDetailDJ` | CTE 2개 + 3JOIN + ROW_NUMBER + DECODE (controller 가 brtcNm='대전광역시' set) |
| 5-2 | B16 | `ActualUseDetailKbHandler` | `opn.actualUseDetailDJ` (재호출) | `actualUseDetailKB/actualUseDetailKB` | DJ SQL 그대로 + brtcNm='경상북도' — **controller 분기 패턴, 별도 핸들러** |
| 6 | B4 | `WellInfoHandler` | `opn.info_permwell` | `wellInfoService/getWellInfo` | Oracle 함수 호출 (`FN_GD_GET_GUBUN` 등) |
| 7 | B5 | `SupplementaryGroundwaterHandler` | `opn.info_general_105` | `groundwaterMonitoringNetworkService/getSupplementaryGroundwater` | 5-way LEFT JOIN |
| 8 | B13 | `WaterQualityMfdsHandler` | `opn.waterQualityMfdsInfo` | `waterQualityMfdsInfo/waterQualityMfdsInfo` | **동적 PIVOT** + 2JOIN + 스칼라 (⚠ inspection fallback 필요) |
| 9 | B18 | `WqInputStatusDjHandler` | `opn.gnlwtqltinfo_inputsittn` | `gnlwtqltinfo_inputsittn/gnlwtqltinfo_inputsittn` | UNION + 3중 서브 + 3JOIN (대전) |
| 10 | B9 | `LinkageChartDailyHandler` | `opn.linkage_analy_chart_general` | `observationStationService/getGroundwaterMonitoringNetwork` | CTE + UNION + PIVOT (일 단위) |
| 11 | B10 | `ObservationStationTimeHandler` | `opn.observationStationTimeService` | `observationStationTimeService/observationStationTimeService` | PIVOT + 시 단위 + datatype 분기 |
| 12 | B11 | `WaterQualityInfoHandler` | `opn.waterQualityInfo` | `waterQualityInfo/waterQualityInfo` | **동적 PIVOT** + 3JOIN + 스칼라 (⚠ inspection fallback 필요) |
| 13-1 | B12 | `WaterQualityInfoDjHandler` | `opn.waterQualityInfoDJ` | `waterQualityInfoDJ/waterQualityInfoDJ` | 동적 PIVOT + 3JOIN + brtcNm='대전광역시' set |
| 13-2 | B12 | `WaterQualityInfoKbHandler` | `opn.waterQualityInfoDJ` (재호출) | `waterQualityInfoKB/waterQualityInfoKB` | DJ SQL 그대로 + brtcNm='경상북도' — **controller 분기 패턴** |
| 14 | B7 | `WaterLevelObservationHandler` | `opn.info_observation_station1` | `observationStationService/getWaterLevelObservationStation` | DBLINK + 실시간 10분 (§2.2 참조) |
| 15 | B8 | `RainfallObservationHandler` | `opn.info_observation_station0` | `observationStationService/getRainfallStation` | DBLINK + 실시간 10분 (§2.2 참조) |

> **B11/B12/B13 의 inspection fallback 패턴** (v3 검증 결과):
> ```
> List<TmGd30310> inspection = opnService.searchInspection(year, josacode);
> if (inspection.isEmpty()) {
>     String maxYear = opnService.searchMaxDtaStdrYear();
>     inspection = opnService.searchInspection(maxYear, josacode);  // 재시도
> }
> Set<String> codes = inspection 의 QLTWTR_INSPCT_IEM_CODE 수집  // 동적 PIVOT 컬럼 풀
> // 그 후 동적 PIVOT SQL 조립
> ```
> 핸들러 작성 시 fallback + 코드풀 추출 + 동적 SQL 조립 패턴 함께 박음. (⚠ 4/24 §2.1 표에 미언급)

**핸들러 클래스 총 17개 / operationId 총 17개** (DJ/KB 별도, B7/B8 포함)

**모든 핸들러 공통 분류**: "원본 DB 직접 조회 — 관할 외 운영 시스템 데이터" (UI 안내문에 표시)

### 2.2 B7/B8 — 진짜 외부 (DBLINK)

B7/B8 은 4/24 시점에 보류였으나, 본 방식 (핸들러 + 외부 직접) 으로 가면 오히려 가장 정합적인 케이스 (실시간성 해소 + 추적 모델 신경 쓸 필요 없음). 본 범위 §2.1 의 #14, #15 로 포함.

| B# | 핸들러 | v3 SQL ID | 외부 operationId | source | 잔여 확인 |
|:--:|---|---|---|---|---|
| B7 | `WaterLevelObservationHandler` | `opn.info_observation_station1` | `observationStationService/getWaterLevelObservationStation` | `DBLINKUSR.DUBWLOBSIF` 외 4개 (**진짜 외부 — DBLINK**) | 현행 DB 의 `V_WR_HACHEON_MST`/`V_WP_WKSDAMSBSN` 실명/대체 테이블 확인 |
| B8 | `RainfallObservationHandler` | `opn.info_observation_station0` | `observationStationService/getRainfallStation` | `DUBRFOBSIF` 외 2개 (DBLINK 의심) | 정확한 schema prefix + `DUBRFOBSIF`/`DUBMMRF` 실명 확인 |

**DBLINK 권한**: 기존 운영 계정으로 DBLINK 사용 가능 가정 (사용자 확인). Phase 0 별도 권한 작업 없음
**개발 환경**: internal-oracle 에 DBLINK 시뮬 어려움 → Mock 핸들러 (개발용) + 운영 Oracle 직접 테스트 단계 분리

### 2.3 제외 (4/24 와 동일)

- **B1/B2**: A7 (`api_prv_tmp_megokr_api`) 재활용 + B1 별도 타겟. **이미 4/24 operation 등록 완료**
- **B3**: EAV PIVOT — 별도 이슈로 보류 (커스텀 핸들러 방식으로 가능, 우선순위 재검토)
- ~~B15~~: **본 범위에 포함** (1:1 원칙 — §2.1 #2)

### 2.4 건드리지 않는 것

- **Type A 12건** — `api_prv_*` 적재 + 메타등록형 operation 그대로 유지
- **provide Agent (sync-agent-provide)** — Type A 7종 적재 파이프라인 유지, Type B 핸들러는 api-provider 에 박음
- **중복 엔티티 3개** (`ApiPrvTmGd110301/110302/Ngw04`) — 별도 작업

## 3. 아키텍처 설계

### 3.1 인터페이스 정의

`gims-api-provider` 모듈 내 신규:

```
gims-api-provider/
  src/main/java/com/gims/provider/
    custom/
      CustomOperationHandler.java        ← 인터페이스
      CustomHandlerRegistry.java         ← 핸들러 자동 수집 + operationId 매핑
      CustomHandlerBootstrap.java        ← 부팅 시 ApiPrvOperation 자동 등록
      handler/
        InspectionListHandler.java       ← B14 (파일럿)
        GroundwaterQualityHandler.java   ← B6
        ... (12종)
    config/
      LegacyOracleConfig.java            ← 외부 Oracle datasource 직결 (자체 설정)
    controller/
      ApiGatewayController.java          ← 분기 추가 (META vs CUSTOM)
```

**`CustomOperationHandler` 인터페이스**:

```java
public interface CustomOperationHandler {
    /** operation 카탈로그용 — operationId, 표시명, 설명, 파라미터 스펙, 응답 컬럼 스펙 */
    CustomOperationMetadata getMetadata();

    /** 실제 호출 — 파라미터 받아 외부 Oracle 쿼리 후 결과 List 반환 */
    List<Map<String, Object>> handle(Map<String, String> params);
}
```

**`CustomOperationMetadata`** — 부팅 시 `ApiPrvOperation`/`ApiPrvOperationColumn`/`ApiPrvOperationParam` 으로 변환되는 POJO. UI 안내 표시에 그대로 사용.

**description 형식 가이드** (스키마 변경 없이 기존 컬럼 활용):
```
관련 테이블: TM_GD110301, TM_GD110302, TM_GD120001, TC_GD00002
변환: 동적 PIVOT + 3JOIN + 스칼라
```
- 첫 줄에 **관련 테이블 목록** (메인/추가 구분 없이 콤마)
- 둘째 줄에 변환 패턴 요약
- `ApiPrvOperation.description` (length=1000) 에 그대로 박힘 — `table_name` 컬럼은 메인 1개만 (Type A 호환), 다중 표시는 description 활용
- **사유**: DB 스키마 변경 없음 → DynamicQueryService 회귀 위험 0, Type A 영향 0, 작업량 최소

### 3.2 외부 Oracle 연결 (기존 ProviderDataSourceService 패턴 재사용)

**별도 datasource Bean 만들지 않음** — 기존 `ProviderDataSourceService` 가 이미 `datasource_id` 받아 Proxy 경유로 동적 JdbcTemplate 생성 + Hikari 캐싱. Type A 와 동일 메커니즘 활용 (운영 패턴 일원화).

```java
// 핸들러 내부
JdbcTemplate jdbc = dataSourceService.getJdbcTemplate("internal");  // datasource_id 만 지정
```

- **개발 환경**: Orchestrator datasource 메타에 `internal` (host=localhost, port=29004, XEPDB1, ORACLE) 이미 등록됨 — **신규 등록 불필요**
- **운영 환경**: 운영자가 운영 GIMS Oracle 을 datasource 메타에 등록 후 `datasource_id` 전달. 환경변수로 default override 가능 (`${LEGACY_DS_ID:internal}`)
- **Orchestrator datasource 메타 활용** — Type A 와 동일하게 단일 진실원
- 핸들러 SQL 은 **레거시 SQL 그대로** (스키마 prefix 없이) 박음. 운영 default 스키마가 운영 GIMS 스키마와 다르면 운영 datasource 메타의 계정으로 맞춤 (또는 SYNONYM)
- ⚠️ **`LegacyOracleConfig` 별도 Bean 폐기** — 4/27 결정. 단일 `application.yml` 환경변수 placeholder도 폐기 (모든 datasource 정보는 Orchestrator datasource 메타에서)

### 3.3 부팅 시 자동 등록 (`CustomHandlerBootstrap`)

```java
@Component
@RequiredArgsConstructor
public class CustomHandlerBootstrap implements ApplicationRunner {
    private final List<CustomOperationHandler> handlers;
    private final ApiPrvOperationRepository operationRepo;
    // ...

    @Override
    public void run(ApplicationArguments args) {
        for (CustomOperationHandler handler : handlers) {
            CustomOperationMetadata meta = handler.getMetadata();
            upsertOperation(meta);          // operationType=CUSTOM, isLocked=true
            upsertColumns(meta);
            upsertParams(meta);
        }
    }
}
```

- 멱등 (있으면 update, 없으면 insert)
- `ApiPrvOperation` 에 `operation_type` ('META' / 'CUSTOM') 컬럼 추가 + `is_locked` 컬럼 추가
- `is_locked=true` 면 UI/API 의 update/delete 차단 (Service 레이어에서 보호)

### 3.4 `ApiGatewayController` 분기

```java
@GetMapping("/**")
public ResponseEntity<?> handle(HttpServletRequest req, ...) {
    String operationId = extractOperationId(req);
    ApiPrvOperation op = operationRepo.findByOperationId(operationId);

    if ("CUSTOM".equals(op.getOperationType())) {
        CustomOperationHandler handler = handlerRegistry.get(operationId);
        return handler.handle(params);   // 외부 Oracle 직접 쿼리
    } else {
        return dynamicQueryService.execute(op, params);  // Type A 기존 흐름
    }
    // ApiPrvCallHistory 기록은 공통 (AOP 또는 finally)
}
```

### 3.5 호출이력 기록 (`ApiPrvCallHistory`)

- META/CUSTOM 동일하게 기록 — 우리쪽 책임 유지
- 기록 항목: operationId, 파라미터, 응답건수, 처리시간, 에러여부
- AOP (`@Around`) 로 일원화 — 두 분기 모두 자동 기록

## 4. UI 변경 (기존 화면 재활용)

### 4.1 Operation 목록 (`app/api-provide/page.tsx`)

**컬럼 정리**:
- **제거**: `테이블`, `Datasource` (Type B 는 다중 테이블이라 단일 표시 의미 약함, datasource 도 META=`api-provider`/CUSTOM=`internal` 이분만이라 정보 가치 낮음)
- **신규**: `타입` (META/CUSTOM 배지)

**최종 6컬럼**: 이름 / 오퍼레이션 ID / 타입 / 활성 / 등록일 / 작업

**Type B (CUSTOM) 행**:
- 타입 셀에 **잠금 아이콘** 동반 표시
- 호버 시 "원본 DB 직접 조회 — 관할 외 운영 시스템 데이터"
- 정렬/필터에 타입 추가

**제거된 정보 보완**: 테이블/datasource 는 상세 화면 + `description` 에서 확인 가능 (다중 테이블도 description 텍스트로)

### 4.2 Operation 상세/등록 화면

- CUSTOM 진입 시:
  - 모든 입력필드 **readonly**
  - 저장/삭제 버튼 **비활성**
  - 상단에 안내 배너: **"이 Operation 은 시스템 내장 핸들러입니다. 원본 GIMS Oracle 직접 조회 — 적재되지 않은 실시간 데이터입니다. 수정 불가."**
  - 컬럼/파라미터 탭은 **그대로 표시** (사용자가 응답 형태/요청 파라미터 확인 가능 — "안내" 효과)
  - 설명란(`description`) 에 **관련 테이블 목록** 표시 (다중 테이블도 description 으로 안내)

### 4.3 백엔드 보호

- `ApiPrvOperationService.update()`/`delete()` 진입 시 `is_locked=true` 면 `IllegalStateException`
- 컬럼/파라미터도 동일 보호

## 5. DB 환경 (외부 Oracle = 개발용 internal-oracle 재활용)

### 5.1 현황 (`scripts/diagnostic` 로 사전 확인 완료된 테이블)

internal-oracle K1M 스키마에 이미 존재하는 표준화 명명 테이블:
`TM_GD110301/110302`, `TM_GD120001`, `TM_GD130001`, `TM_GD970001/002/101/130`, `TM_GD980002`, `PM_GD970201`, `TC_GD00002`, `TC_GD00100`, `TM_GD000203`, `RGE*` 시리즈, `TMP_MEGOKR_API` 등

### 5.2 신규 DDL 7종 추가 (표준화 이름)

`scripts/ddl/internal-oracle/provide-source/` 에 추가:

| 신규 테이블 | 용도 | 핸들러 | 상태 |
|---|---|---|:--:|
| `TM_GD110310` | 수질검사항목별기준 | B14/B15 (`InspectionListHandler`/`InspectionDistinctHandler`) | ✅ |
| `VIEW_GTEST` | 수질측정망 통합 (개발은 물리 테이블 단순화) | B6 (`GroundwaterQualityHandler`) | ✅ |
| `TM_GD023001` | 미등록지하수시설 | B17 (`UnregitsFclySmrizeHandler`) | ✅ |
| `TM_GD010930` | 홈페이지이용실태담당지자체 | B16 (`ActualUseDetailDjHandler`/`KbHandler`) | ⏳ |
| `TM_GD110350` | 정기수질검사개요 | B13 (`WaterQualityMfdsHandler`) | ⏳ |
| `TM_GD110351` | 정기수질검사결과 | B13 | ⏳ |
| `TM_GD010910` | 홈페이지사용자 | B13 | ⏳ |

> ⚠️ **B5 의 신규 DDL 미반영**: §3.1 분석에 따르면 B5 (`SupplementaryGroundwaterHandler`) 가 5-way LEFT JOIN 으로 `TM_GD970001/002/130`, `TM_GD980002` 사용. 4/27 §5.1 의 internal-oracle 현황엔 970001/002/101/130 + 980002 가 있다고 표기되어 있어 **신규 DDL 불필요할 가능성** — B5 작성 시 컬럼명 정합성 (위 5.5 의 컬럼명 3가지 버전 사례) 점검 후 결정.

### 5.3 Oracle 함수 재현 (B4 전용)

WellInfoHandler (B4) 가 Oracle 함수 호출:
- `FN_GD_GET_GUBUN(PERM_NT_FORM_CODE, 1)` — 구분 코드 → 문자열
- `FN_GD_GET_CMMTNDCODE('NGW_0003', code)` — 공통코드 → 내용
- `FN_GD_GET_CMMTNDCODE('NGW_0013', code)` — 공통코드 → 내용

→ **함수 DDL 도 internal-oracle 에 재현** (옵션 A 유지). B4 핸들러 작성 시점에 추가.

### 5.4 샘플 데이터

각 신규 테이블에 검증용 3~5건씩 INSERT (PENDING/SAMPLE 표시 컬럼 불필요 — 적재 없음). 핸들러 테스트 호출 시 정상 응답 확인용.

### 5.5 관할 매트릭스

#### 5.5.1 관할 정의 (스키마 prefix 기준 — 핵심)

레거시 SQL 의 FROM/JOIN 구문에 **스키마 prefix 가 명시된 것** 만 진짜 외부 관할. prefix 없으면 우리 GIMS 스키마 안에 있는 = 우리 관할. (표준화 명명 여부와 별개.)

| 구분 | 의미 | 예시 |
|---|---|---|
| **우리 관할** | 레거시 SQL 에 스키마 prefix **없음** — 우리 GIMS 스키마 내 존재 | `TM_GD*`, `TC_GD*`, `PM_GD*`, `TD_GD*`, `RGE*` 시리즈, `VIEW_GTEST`, `TMP_MEGOKR_API` 등 |
| **진짜 외부 관할** | 레거시 SQL 에 **다른 스키마 prefix 명시** — 외부 시스템 직접 참조 | `SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033` (A4), `DBLINKUSR.DUBWLOBSIF` 외 (B7) |

> 즉 RGE\*, VIEW_GTEST 는 표준화 안 된 명명이지만 우리 GIMS 스키마 안에 있어 **우리 관할**. 표준화 여부 ≠ 관할.

#### 5.5.2 Type B 15종 핸들러 source 매트릭스 (B7/B8 제외, v3 검증 후)

| # | 핸들러 (B#) | source 원문 (v3 검증) | prefix 있는 source | 분류 |
|:--:|---|---|:--:|:--:|
| 1 | `InspectionListHandler` (B14) | `TM_GD110310` + `TC_GD00002` | 0 | 우리 관할 |
| 2 | `InspectionDistinctHandler` (B15) | `TM_GD110310` + `TC_GD00002` | 0 | 우리 관할 |
| 3 | `GroundwaterQualityHandler` (B6) | `VIEW_GTEST` | 0 | 우리 관할 |
| 4 | `UnregitsFclySmrizeHandler` (B17) | `TM_GD023001` | 0 | 우리 관할 |
| 5 | `ActualUseDetailDjHandler` (B16-DJ) | `TC_GD00100` + `TM_GD010930` + `RGETNTGMS02` | 0 | 우리 관할 |
| 6 | `ActualUseDetailKbHandler` (B16-KB) | (DJ 와 동일) | 0 | 우리 관할 |
| 7 | `WellInfoHandler` (B4) | `RGETNPMMS01` + `TC_GD00100` + 함수 3종 | 0 | 우리 관할 |
| 8 | `SupplementaryGroundwaterHandler` (B5) | `TM_GD120001` 외 5개 | 0 | 우리 관할 |
| 9 | `WaterQualityMfdsHandler` (B13) | `TM_GD110350`+`110351`+`010910` + inspection helper | 0 | 우리 관할 |
| 10 | `WqInputStatusDjHandler` (B18) | `TM_GD120001`+`110301`+`110302` | 0 | 우리 관할 |
| 11 | `LinkageChartDailyHandler` (B9) | `PM_GD970201`+`970101`+`120001` | 0 | 우리 관할 |
| 12 | `ObservationStationTimeHandler` (B10) | `PM_GD970201`+`970101`+`120001` | 0 | 우리 관할 |
| 13 | `WaterQualityInfoHandler` (B11) | `TM_GD120001`+`110301`+`110302`+`TC_GD00002` + inspection helper | 0 | 우리 관할 |
| 14 | `WaterQualityInfoDjHandler` (B12-DJ) | `TM_GD120001`+`110301`+`110302` + inspection helper | 0 | 우리 관할 |
| 15 | `WaterQualityInfoKbHandler` (B12-KB) | (DJ 와 동일) | 0 | 우리 관할 |

→ **위 15종 모두 우리 관할** (스키마 prefix 0건). DJ/KB 는 SQL 동일·brtcNm 만 다름 (controller 분기 패턴) — 1:1 원칙 유지.

#### 5.5.3 진짜 외부 관할 — B7/B8 + A4

| API | source 원문 | prefix | 비고 |
|:--:|---|---|---|
| **A4** (이식 완료) | `SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033` | `SDE_NGWS` | 이미 PVD 적재로 우리 PG 사본 보유 |
| **B7** (Phase 6) | `DBLINKUSR.DUBWLOBSIF` 외 4개 | `DBLINKUSR` | DBLINK — 본 범위 #14 |
| **B8** (Phase 6) | `DUBRFOBSIF` 외 2개 | (표기 누락, DBLINK 표시) | 실제 SQL 확인 시 prefix 가능성 — 본 범위 #15 |

#### 5.5.4 의미 정정

- **Type B 17종 중 15종 = 데이터 모델 + 데이터 위치(우리 GIMS 스키마) 모두 우리 관할** — 단지 **운영 인프라가 외부 GIMS Oracle 시스템** (우리가 운영 안 함)
- **B7/B8 (총 2종) = 진짜 외부 (DBLINK 통한 다른 시스템 참조)** — Phase 6 에서 처리

#### 5.5.5 핸들러 방식 결정 사유 재정리

이전엔 "외부 관할이라 추적 곤란" 으로 표현했지만 부정확. 정확한 사유:

| # | 사유 | 강도 |
|:--:|---|---|
| 1 | **운영 인프라가 외부** GIMS Oracle 시스템 — 우리 PG 가 아님 → PVD 적재해도 외부 변경 이벤트 추적 불가 | 강 |
| 2 | **실시간성** — PVD 적재 주기 (5~10분 ~ 시간) 만큼 데이터 지연 | 강 |
| 3 | **변환 자유도** — 동적 PIVOT/CTE/UNION 등 Java 핸들러로 자연스럽게 표현 | 강 |
| 4 | **이중 적재 부담** — 외부 GIMS Oracle 안에 이미 우리 표준화 데이터 존재. 우리 PG 에 또 사본 두는 의미 약함 | 강 |
| 5 | **개발 편의** — Agent 화 하면 PG 테이블 + 엔티티 + Step + Factory + YAML + Orchestrator Agent 등록 + 별도 operation 등록 (작업량 ↑↑). 핸들러 = Java 1개 + 부팅 자동 등록으로 끝 | 강 |
| (참고) | 이전 "데이터 명명이 외부라서 추적 곤란" 표현 — 부정확 (15종이 우리 관할) | **폐기** |

#### 5.5.6 PG 사본 활용은 본 범위 밖 (참고)

핸들러 source 중 일부는 이미 Type A 적재로 우리 PG (`api_prv_*`) 에 사본 존재 (예: `api_prv_tm_gd110301`/`110302`/`120001`). 핸들러 입력으로 활용 시 외부 Oracle 부하 감소 가능. 그러나:

- Type B 본질이 **실시간 + 외부 직접** 으로 결정됨 (위 5.5.5 사유)
- B11/B12/B18 도 어차피 동적 PIVOT 핸들러 → 사본 쓰든 외부 쓰든 Java 코드량 차이 없음
- 사본 활용 시 PVD 시간차 + `api_prv_tm_gd*` 의 이중 용도 부담

→ **이번 범위는 외부 Oracle 직접 단일 패턴**. 운영 후 부하 이슈 발생 시 별도 검토.

## 6. Phase 별 마일스톤

### Phase 0: 인프라 구축 — ✅ 완료

- [x] `CustomOperationHandler` 인터페이스 + `CustomOperationMetadata` POJO 정의
- [x] `CustomHandlerRegistry` (operationId → handler 매핑) + `@Component` 자동 수집
- ~~`LegacyOracleConfig`~~ **폐기** — 기존 `ProviderDataSourceService` 활용 (Orchestrator datasource 메타의 `internal` 으로 동적 JdbcTemplate). §3.2 참조
- [x] `ApiPrvOperation` 스키마 확장 — `operation_type` ('META'/'CUSTOM'), `is_locked` boolean 컬럼 추가
- [x] `CustomHandlerBootstrap` — 부팅 시 멱등 등록
- [x] `ApiPrvOperationService` 보호 로직 — `is_locked=true` 면 update/delete 차단
- [x] `ApiGatewayController` 분기 추가 (META/CUSTOM) + `ApiPrvManageController.testOperation` 도 분기 추가
- [x] `ApiPrvCallHistory` 기록 — finally 패턴 (AOP 대신 명시적 save, 외부 호출만 기록 / test endpoint 는 운영자 행위라 제외)
- [x] **회귀**: 기존 Type A 12건 호출 200 OK 전수 (`scripts/temp/test_ops.py`)

### Phase 1: 파일럿 (B14 InspectionListHandler) — ✅ 완료

- [x] `TM_GD110310` DDL + 샘플 INSERT
- [x] `InspectionListHandler` 구현 (LEFT JOIN, 복합 PK)
- [x] 부팅 후 `ApiPrvOperation` 자동 등록 확인 (id=15, operation_type=CUSTOM, is_locked=t)
- [x] UI 진입 readonly 표시 확인 (헤더 🔒 + 안내 배너 + 탭 readonly 뷰)
- [x] curl 호출 정상 응답 + `ApiPrvCallHistory` 기록 확인
- [x] **핸들러 템플릿 확정** — 이후 핸들러가 이 뼈대 따라감
- [x] 문법 검증 (LEFT JOIN edge case + WHERE 필터 + ORDER BY + OFFSET/FETCH + totalCount + Direct SQL 일치)

### Phase 2: 저~중 난이도 (B15 → B6 → B17 → B16 → B4 → B5) — 진행 중

- [x] **B15 InspectionDistinctHandler** — B14 와 source 동일, 별도 SQL 박음 (1:1 원칙 검증) — DISTINCT 효과 확인 완료 (B14 7건 vs B15 6건)
- [x] B6 `GroundwaterQualityHandler` (+ VIEW_GTEST DDL)
- [x] B17 `UnregitsFclySmrizeHandler` (+ TM_GD023001 DDL) — 합계/시군구별/모순카운트 검증 완료
- [ ] B16 `ActualUseDetailDjHandler` + `ActualUseDetailKbHandler` (+ TM_GD010930 DDL) — DJ/KB 별도 핸들러, brtcNm 만 다름 (controller 분기 패턴)
- [ ] B4 `WellInfoHandler` (+ Oracle 함수 3개 재현 DDL — `FN_GD_GET_GUBUN`/`FN_GD_GET_CMMTNDCODE`)
- [ ] B5 `SupplementaryGroundwaterHandler` (5-way JOIN, TM_GD970001 등 신규 DDL)
- [x] **공통화 없음** — 1:1 원칙 유지, SQL 중복 허용

### Phase 3: 상 난이도 4종 (B13 → B18 → B9 → B10)

- [ ] B13 `WaterQualityMfdsHandler` (+ TM_GD110350/110351/010910 DDL) — **동적 PIVOT + inspection fallback** (`searchInspection` + `searchMaxDtaStdrYear`)
- [ ] B18 `WqInputStatusDjHandler` (UNION + 3중 서브)
- [ ] B9 `LinkageChartDailyHandler` (일 단위) + 사전 helper SQL `linkage_analy_chart_general` 의 fallback 패턴 확인 필요
- [ ] B10 `ObservationStationTimeHandler` (시 단위, datatype 분기)

### Phase 4: 최상 난이도 (B11 → B12)

- [ ] B11 `WaterQualityInfoHandler` (동적 PIVOT 범용) — **inspection fallback** + 코드풀 추출 + 동적 SQL 조립
- [ ] B12 `WaterQualityInfoDjHandler` + `WaterQualityInfoKbHandler` — **DJ/KB 별도 핸들러**, brtcNm 분기 (controller 패턴)

### Phase 5: UI 마감 + 통합 검증

- [x] Operation 목록: 타입 컬럼 + 잠금 표시 (Phase 1 단계에 흡수 완료)
- [x] Operation 상세: readonly 모드 + 안내 배너 + InfoTab/ColumnsTab/ParamsTab readonly 뷰
- [ ] 17종 전수 호출 테스트 (`scripts/temp/test_custom_handlers.py` 작성)
- [x] 응답 컬럼 alias 대문자 (4/24 패턴 — 핸들러가 `Map<String,Object>` 반환 시 대문자 키 사용)
- [ ] ApiPrvCallHistory 17건 호출 기록 확인
- [ ] 기존 Type A 12건 회귀 호출 (Phase 0 시점 1차 OK, 17종 완료 후 재실행)

### Phase 6: B7/B8 (진짜 외부 — DBLINK) — Phase 4 후

- [ ] B7/B8 source 실명/대체 테이블 담당자 확인
- [ ] B7 `WaterLevelObservationHandler` (10분 단위 수위, DBLINK)
- [ ] B8 `RainfallObservationHandler` (우량, DBLINK)
- [ ] 개발 환경: DBLINK 시뮬 어려우니 Mock 핸들러 + 운영 Oracle 직접 단계 검증

## 7. 검증 전략

### 7.1 핸들러 별

- 응답 건수 = internal-oracle 샘플 수와 일치
- 응답 컬럼명 = 레거시 응답 스펙과 일치 (대문자 alias)
- ApiPrvCallHistory 기록: operationId, 파라미터, 건수, 처리시간
- 에러 케이스: 외부 Oracle 다운 → 5xx + 명확한 에러 메시지

### 7.2 회귀 (Phase 0 + Phase 5)

- **Type A 12건** 호출 → 응답 동일 (분기 추가 영향 없음)
- **provide Agent (sync-agent-provide)** 적재 → Type A 적재 흐름 변경 없음
- **bojo / bojo-int / others** 영향 없음 — api-provider 단독 변경

### 7.3 운영 전환 점검

- 환경변수 `LEGACY_ORACLE_URL` / `LEGACY_ORACLE_USER` / `LEGACY_ORACLE_PASSWORD` 주입 가이드 (Docker compose / 런타임 — `application-prod.yml` 별도 운영 X)
- 운영 Oracle 의 표준화 명명 일치 여부 (사용자 확인 사항)
- 운영 Oracle 계정의 default 스키마 = 운영 GIMS 스키마 일치 여부
- 운영 Oracle 의 함수 (`FN_GD_GET_GUBUN` 등) 존재 확인

## 8. 산출물 체크리스트

### 8.1 코드 (api-provider 모듈)

- [x] `CustomOperationHandler` 인터페이스 + `CustomOperationMetadata` POJO
- [x] `CustomHandlerRegistry` + `CustomHandlerBootstrap`
- ~~`LegacyOracleConfig`~~ 폐기 (ProviderDataSourceService 재사용)
- [x] `ApiGatewayController` 분기 + `ApiPrvManageController.testOperation` 분기 + `ApiPrvCallHistory` finally 기록 + 분기 로그 (`[Gateway] CUSTOM/META 분기`)
- [x] `ApiPrvOperationService` 보호 (`is_locked`)
- [ ] 핸들러 **17종** (`handler/*.java`) — 진행 (현재 4종 완료: B14/B15/B6/B17)
- [x] **공통화 헬퍼 없음** (1:1 원칙 — SQL 중복 허용)

### 8.2 DDL

- [x] `ApiPrvOperation` ALTER (operation_type, is_locked) + 백필 META
- [ ] internal-oracle DDL **7종** + 샘플 INSERT — 진행 (현재 3종 완료: TM_GD110310 / VIEW_GTEST / TM_GD023001)
  - 잔여: TM_GD010930 (B16), TM_GD970001 외 4종 (B5), TM_GD110350/110351/010910 (B13)
  - + Oracle 함수 3종 (B4 시점)
- [ ] Oracle 함수 **3종** 재현 (B4 시점) — `FN_GD_GET_GUBUN`, `FN_GD_GET_CMMTNDCODE`(NGW_0003 / NGW_0013)

### 8.3 UI (frontend)

- [x] `app/api-provide/page.tsx` — 타입 컬럼 추가, 테이블/Datasource/작업 컬럼 제거
- [x] `app/api-provide/[id]/page.tsx` — readonly + 안내 배너 + 헤더 🔒 + 삭제 버튼 잠금 시 비표시
- [x] `InfoTab` / `ColumnsTab` / `ParamsTab` — 잠금 시 readonly 뷰
- [x] `ApiPrvOperation` 타입 정의에 `operationType` / `isLocked` 추가
- [x] `app/agents/page.tsx` — 작업 컬럼 제거 (사용자 요청)

### 8.4 문서

- [ ] `dev_logs/2026_04/2026-04-27.md` 작업 일지 (Phase 마무리 후)
- [ ] `docs/provide/LEGACY_API_MIGRATION_MAP.md` 상태 업데이트 (PVD 제거, 커스텀 마킹)
- [ ] `docs/provide/PROVIDE_OPERATION_SPEC.md` 에 §9 "커스텀 핸들러형 Operation" 신설
- [ ] `docs/provide/CUSTOM_HANDLER_GUIDE.md` **신규** — 핸들러 추가 가이드 (운영자/개발자 참조)

### 8.5 보존

- [x] 4/24 계획서 폐기 사유 헤더 추가 완료 (`dev_plan/2026_04/24/type-b-migration.md` 상단)

## 9. 리스크 / 주의사항

### 9.1 외부 Oracle 의존

- **응답 지연**: 캐시 없음 → 매 호출 외부 Oracle 쿼리. 응답시간 SLA 정의 필요
- **Oracle 가용성 = API 가용성**: Oracle 다운 시 12종 전부 5xx
- **부하 전이**: 외부 API 호출 트래픽이 그대로 운영 Oracle 부하로
- **권한**: 운영 환경에서 GIMS Oracle 의 SELECT 권한 + 함수 EXECUTE 권한 사전 확보 필요

### 9.2 Type A 와의 운영 일관성

- 두 운영 모드 (META, CUSTOM) 가 같은 Operation 목록에 섞임 → UI 에서 명확한 시각 구분 필수
- 호출 패턴/응답 형태는 외부 사용자에게 동일하게 보여야 (백엔드 분기는 투명)
- 디버깅 시 분기 어디에 들어갔는지 로그 명시 필요

### 9.3 운영 vs 개발 명명 일치

- 사용자 확인: 운영 Oracle 의 표준화 적용 여부 (internal-oracle 명명과 일치 가정)
- 만약 운영 Oracle 명명이 다르면 → 운영 Oracle 측에 SYNONYM 추가 또는 default 스키마 조정 (핸들러 SQL 운영용 분기는 피하고 싶음)

### 9.4 메모리 룰 엄수

- `feedback_no_internal_exposure`: UI 안내 배너에 내부 구조 (datasource 이름 등) 노출 금지 — "원본 DB" 같은 일반 용어로
- `feedback_no_regression_organic`: Phase 0/5 회귀 검증 필수
- `feedback_provide_target_per_api`: **operationId = 핸들러 1:1** 강제 (DJ/KB 공용도 별도 핸들러)
- `feedback_module_specific_stays`: 공통화 헬퍼 안 쓰지만, 향후 추출 시에도 api-provider 내부에 유지 (common 으로 올리지 않음)
- `feedback_no_scope_creep`: 중복 엔티티 3개 정리는 별도 작업

## 10. 4/24 자산 활용

| 자산 | 4/24 위치 | Type B 핸들러 활용 |
|---|---|---|
| 슬래시 operationId 지원 | `ApiGatewayController` | 핸들러 등록 시 operationId 에 `/` 포함 (`waterQualityInfoDJ/waterQualityInfoDJ`) |
| 응답 alias 대소문자 보존 | `DynamicQueryService` | 핸들러는 직접 대문자 키 반환 (`Map.put("BRTC_NM", ...)`) |
| Operation 등록 패턴 | `register_ops.py` | 부팅 자동 등록으로 대체 (스크립트 불필요) |
| 호출 테스트 스크립트 | `test_ops.py` | `test_custom_handlers.py` 로 복사·확장 |
| 진단 SQL | `scripts/diagnostic/b7b8-dblink-check.sql` | B7/B8 재개 검토 시 활용 |

## 11. 승인 요청 사항

이 계획서에 대해 사용자 승인 후 **Phase 0 (인프라 구축)** 부터 순차 진행.

특히 확정 필요:
- **(a)** 핸들러 작성 순서 17종 이대로 OK? (B14 파일럿 → B15 → B6 → ... → B11/B12 최상 → B7/B8 마지막)
- **(b)** `application.yml` 에 `legacy-oracle` 자체 datasource 박는 방식 OK? (Orchestrator datasource 메타 등록 X) >> 계정및 db정보는 같고 사실상 스키마만 다른거라서 쿼리상으로 스키마 명시하면 기존 내부망 오라클 정보로 쓸수 있긴해..?
- **(c)** `ApiPrvOperation` 에 `operation_type` + `is_locked` 컬럼 추가 OK?
- **(d)** 부팅 시 `CustomHandlerBootstrap` 멱등 등록 OK? (기존 운영자 등록 메타 덮어쓰지 않음), 응 그래야지..?
- **(e)** UI Operation 상세 화면 — Type B 진입 시 readonly + 안내 배너 OK? (별도 안내 메뉴 안 만듦)
- **(f)** B7/B8 — **본 범위 흡수** OK? (DBLINK 권한은 기존 운영 계정으로 가능 가정)
- **(g)** Oracle 함수 (B4 전용) 재현 옵션 A (DDL 추가) 유지 OK?

---

## 폐기 처리

`dev_plan/2026_04/24/type-b-migration.md` 헤더에 폐기 사유 1줄 추가 (삭제하지 않음 — 의사결정 이력 보존):

```markdown
> ⚠️ 2026-04-27 폐기 — `dev_plan/2026_04/27/type-b-builtin-handler.md` 로 대체.
> 폐기 사유: PVD 적재 방식의 실시간 오차 + 추적 모델 부적합. 커스텀 핸들러 방식으로 전환.
```
