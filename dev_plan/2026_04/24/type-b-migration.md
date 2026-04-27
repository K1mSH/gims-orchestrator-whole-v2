# Type B 이식 계획

> ⚠️ **2026-04-27 폐기** — `dev_plan/2026_04/27/type-b-builtin-handler.md` 로 대체.
> 폐기 사유: PVD 적재 방식의 실시간 오차 + 추적 모델 부적합 + 이중 적재 부담 + 개발 편의 (커스텀 핸들러로 작업량 대폭 감소). 의사결정 이력 보존을 위해 본 문서는 삭제하지 않음.
>
> 작성일: 2026-04-24 (초안) / 2026-04-24 개정 (범위 및 리스크 보강)
> 범위: 레거시 Type B 18건 중 이식 대상 **Step 12종 작성 + operation 등록까지**
> 선행 문서:
>  - `docs/provide/LEGACY_API_MIGRATION_MAP.md` (25건 전체 매트릭스 — 이번 작업의 결정 근거)
>  - `docs/provide/PROVIDE_OPERATION_SPEC.md` (외부 제공 계층 operation 등록 스펙)
>  - `docs/dev_guideline/AGENT_YAML_GUIDE.md` (YAML 작성 규칙)
>  - `dev_plan/2026_04/22/provide-source-table-strategy.md` (원본 DDL 이름 체계)
>  - `scripts/diagnostic/b7b8-dblink-check.sql` (B7/B8 보류 사유 검증용 DB 진단 쿼리)

---

## 1. 목표

Type B 18건을 재분류한 결과를 바탕으로 **end-to-end 이식** (Agent Step → PG 적재 → 외부 제공 API operation 등록):

- **Step 12종 작성** (난이도 오름차순, 파일럿 B14 → 최상 B11/B12, B6 포함)
- 각 Step 은 **API별 전용 커스텀 Step** 패턴 (bojo-int 의 `JejuJewonLoadStep` 참조)
- **공통 헬퍼 즉시 재사용** (`SourceRefUtils`, `ConditionBuilder`, `IfTableService`)
- **자체 공통화는 Rule of Three** (3개 Step 작성 후 추출 판단)
- **외부 제공 계층 operation 등록** (Phase 6, 4/24 확립된 패턴 재사용):
  - 슬래시 포함 operationId 로 레거시 URL 재현
  - 대문자 alias (쌍따옴표 감싸기) 로 레거시 호환 응답
  - 자동 대문자 기능 Step 별 적합도 미리 판정

## 2. 범위 (확정)

### 2.1 작성 대상 (12종)

| 순서 | B# | Step 클래스 | 난이도 | 레거시 URL (operationId 후보) | 비고 |
|:--:|:--:|---|:--:|---|---|
| 1 (**파일럿**) | B14 | `ApiPrvInspectionLoadStep` | 저 | 🔧 내부 helper (URL 미매핑) | LEFT JOIN 1개, 복합 PK 3개. 공개 API 아닌 `waterQuality*` helper 겸 관리자용 |
| 2 | B6 | `ApiPrvViewGtestLoadStep` | 저 | `groundwaterMonitoringNetworkService/getGroundwaterQualityMeasurement` | VIEW_GTEST → **물리 테이블로 단순화**. GENNUM 단건 조회 |
| 3 | B17 | `ApiPrvUnregitsFclyLoadStep` | 중 | `unRegitsFclySmrize/unRegitsFclySmrize` | UNION ALL + 스칼라 집계 8개 (대전 전용) |
| 4 | B16 | `ApiPrvActualUseDjLoadStep` | 중 | `actualUseDetailDJ/actualUseDetailDJ` + `actualUseDetailKB/...` | CTE 2개 + 3JOIN + ROW_NUMBER + DECODE (대전/경북 공용) |
| 5 | B4 | `ApiPrvPermwellLoadStep` | 중 | `wellInfoService/getWellInfo` | Oracle 함수(`FN_GD_GET_GUBUN` 등) 대체 필요 |
| 6 | B5 | `ApiPrvGeneral105LoadStep` | 중 | `groundwaterMonitoringNetworkService/getSupplementaryGroundwater` | 5-way LEFT JOIN |
| 7 | B13 | `ApiPrvWaterQualityMfdsLoadStep` | 상 | `waterQualityMfdsInfo/waterQualityMfdsInfo` | 동적 PIVOT + 2JOIN + 스칼라 |
| 8 | B18 | `ApiPrvWqInputStatusDjLoadStep` | 상 | `gnlwtqltinfo_inputsittn/gnlwtqltinfo_inputsittn` | UNION + 3중 서브 + 3JOIN (대전) |
| 9 | B9 | `ApiPrvLinkageChartLoadStep` | 상 | `observationStationService/getGroundwaterMonitoringNetwork` | CTE + UNION + PIVOT (일 단위) |
| 10 | B10 | `ApiPrvObsStationTimeLoadStep` | 상 | `observationStationTimeService/observationStationTimeService` | PIVOT + 시 단위 + `datatype` 분기 — **엔티티 신규** |
| 11 | B11 | `ApiPrvWaterQualityLoadStep` | 최상 | `waterQualityInfo/waterQualityInfo` | 동적 PIVOT + 3JOIN + 스칼라 (범용) |
| 12 | B12 | `ApiPrvWaterQualityDjLoadStep` | 최상 | `waterQualityInfoDJ/waterQualityInfoDJ` + `waterQualityInfoKB/...` | 동적 PIVOT + 3JOIN (DJ+KB 공용) — **엔티티 신규** |

### 2.2 제외

- **B1/B2/B3**: A7 `api_prv_tmp_megokr_api` 재활용 + `api_prv_tm_gd110301` 별도 타겟 (B1 은 4/24 operation 등록 완료)
- **B15**: B14 의 DISTINCT 결과 → 외부층 또는 관리자 화면 이식 시 처리
- **B3**: EAV PIVOT 필요 → Type B 범위 내 `ApiPrvNgw04LoadStep` 으로 처리하되 **별도 이슈** (현재 Step 12종 포함하지 않음. 우선순위 재검토 필요)

### 2.3 보류 (명확한 사유 — 4/24 재검토 반영)

- **B7** `info_observation_station1` (수위관측소) — **3중 불확실성**
  - (1) 소스 테이블 일부 부재: 현행 DB `DBLINKUSR` 에서 `V_WR_HACHEON_MST`, `V_WP_WKSDAMSBSN` 미발견 (레거시 `copySource/v3` 버전과 현행 DB 스키마 차이 의심)
  - (2) 실시간성 요구: 10분 단위 관측값 → PVD 주기 이식 구조로는 오차 5~10분 발생
  - (3) 실사용 여부 미확인 — 공개 API 카탈로그 활성/호출이력 미확인
- **B8** `info_observation_station0` (우량관측소) — **소스 부재**
  - 현행 DB 에 `DUBRFOBSIF`, `DUBMMRF` 미발견 — 이식 자체 불가능
  - 레거시 → 현행 이름 변경 or 폐기 의심
- **재개 조건** (B7/B8 공통):
  - 담당자 확인: 해당 API 가 현재도 서비스 중이며 실사용 존재
  - 현행 DB 의 정확한 소스 테이블/스키마 확정
  - 허용 오차 수준 합의 (Agent cron 주기 결정 근거)
- **검증 쿼리**: `scripts/diagnostic/b7b8-dblink-check.sql` 에 8 섹션 진단 쿼리 저장 (2026-04-24)

### 2.4 이번 범위에서 건드리지 않는 것

- `ApiPrvTmGd110301` (B1 operation 에 활용 중) — **유지**
- `ApiPrvTmGd110302`, `ApiPrvNgw04` (중복 판정) — Type B 완료 후 정리 검토
- 외부 API 제공 계층의 레거시 URL 매핑 (현재 Type A 12건 등록 완료, Type B 확장은 Phase 6)

## 3. 선행 조건 (Phase 0)

### 3.1 Oracle 원본 DDL 일괄 생성

**위치**: `scripts/ddl/internal-oracle/provide-source/`

**필요한 원본 테이블** (중복 제거 16종):

| 표준화 후 이름 | 레거시 이름 | 용도 | 쓰이는 B# | 기존 DDL |
|---|---|---|---|:--:|
| `TM_GD110301` | TM_GD30301 | 수질측정망검사개요 | B11, B12, B18 | 이미 있음 |
| `TM_GD110302` | TM_GD30302 | 수질측정망검사결과 (EAV) | B11, B12, B18 | 이미 있음 |
| `TM_GD110310` | TM_GD30310 | 수질검사항목별기준 | **B14** (파일럿) | ❌ **신규** |
| `TC_GD000002` | TC_GD00002 | 공통코드 | B11, B13, **B14** | ❌ **신규** |
| `TC_GD000100` | TC_GD00100 | 공통법정동코드 | B4, B16 | ❌ **신규** |
| `RGETNPMMS01` | (동일) | 허가신고정보 (이전이름 유지, 매핑 없음) | B4 | ❌ **신규** |
| `TM_GD120001` | TM_GD10001 | 관측망 | B5, B9, B10, B11, B12, B18 | 이미 있음 (A5) |
| `TM_GD970001` | TM_GD60001 | ODM관측소 | B5 | ❌ **신규** |
| `TM_GD970002` | TM_GD60002 | ODM관측소사양 | B5 | ❌ **신규** |
| `TM_GD970101` | TM_GD60101 | ODM결과 | B5, B9, B10 | ❌ **신규** |
| `TM_GD970130` | TM_GD60130 | ODM관정사양 | B5 | ❌ **신규** |
| `TM_GD980002` | TM_GD70002 | 보조수위측정망 연계기록 | B5 | ❌ **신규** |
| `PM_GD970201` | PM_GD60201 | ODM관측자료 | B9, B10 | ❌ **신규** |
| `TM_GD110350` | TM_GD70201 | 정기수질검사개요 | B13 | ❌ **신규** |
| `TM_GD110351` | TM_GD70202 | 정기수질검사결과 | B13 | ❌ **신규** |
| `TM_GD010910` | TM_GD20910 | 홈페이지사용자 | B13 | ❌ **신규** |
| `TM_GD010930` | TM_GD20930 | 홈페이지이용실태담당지자체 | B16 | ❌ **신규** |
| `RGETNTGMS02` | (동일) | 이용실태조사완료 (이전이름 유지) | B16 | ❌ **신규** |
| `TM_GD023001` | TM_GD00301 | 미등록지하수시설 | B17 | ❌ **신규** |
| `VIEW_GTEST` | (동일) | 수질측정망 통합 (실제 Oracle 뷰 — 개발 Oracle 에서는 **물리 테이블로 단순화**) | **B6** | ❌ **신규** |

**공통 규칙**:
- 환경부표준 컬럼명 기반 (표준화 매핑 문서 참조)
- 추적 컬럼 5종 추가: `LINK_STATUS`, `EXECUTION_ID`, `SOURCE_REFS`, `EXTRACTED_AT`, `UPDATED_AT`
- 멱등성 패턴 (ORA-00955/ORA-01430 무시)
- 샘플 데이터 3~5건 (각 Step 검증용, PENDING 상태)
- `GRANT SELECT, INSERT, UPDATE, DELETE ON {schema}.{table} TO K1M` 부여

**원본 참조**: NGW 원본 TSV (`docs/Standardizedtable/_converted/`) + `standardized_mapping.json`

### 3.2 엔티티 신규 작성 3종

| 엔티티 | 대응 B# | 타겟 테이블 | 해당 Step 작성 시점에 같이 |
|---|:--:|---|:--:|
| `ApiPrvViewGtest` | B6 | `api_prv_view_gtest` | Step #2 시점 |
| `ApiPrvObsStationTime` | B10 | `api_prv_obs_station_time` | Step #10 시점 |
| `ApiPrvWaterQualityDj` | B12 | `api_prv_water_quality_dj` | Step #12 시점 |

엔티티 스펙:
- `@Id sn IDENTITY` + `@UniqueConstraint(source_refs)` (provide 통일 패턴)
- provide 3종 추적 컬럼 (`source_refs`, `execution_id`, `updated_at`)
- 레거시 SQL 의 SELECT 컬럼을 비즈니스 필드로 매핑

### 3.3 Oracle 함수 대체 (B4 전용)

B4 `info_permwell` 은 Oracle 사용자정의 함수 3개 호출:
- `FN_GD_GET_GUBUN(PERM_NT_FORM_CODE, 1)` — 구분 코드 → 문자열
- `FN_GD_GET_CMMTNDCODE('NGW_0003', code)` — 공통코드 → 내용
- `FN_GD_GET_CMMTNDCODE('NGW_0013', code)` — 공통코드 → 내용

**옵션**:
- **(A)** 함수를 개발 Oracle 에 재현 (실운영 스키마 재현 원칙 — 계획서 4/22 의 원칙 2)
- **(B)** Step 코드에서 TC_GD000002 JOIN 으로 대체 (Oracle 함수 제거)

→ **(A) 선호**: 레거시 SQL 을 그대로 활용 가능, 이식 단순. B4 작업 시점(Step #4)에 함수 DDL 추가.

### 3.4 공통 헬퍼 재사용 (추가 구현 불필요)

| 헬퍼 | 위치 | 용도 |
|---|---|---|
| `SourceRefUtils.build` / `buildComposite` / `toJsonSingle` | `common` | source_refs 생성 |
| `ConditionBuilder.isResyncExecution` / `buildIfTableQuery` | `common` | 조건실행/시간범위 WHERE 빌드 |
| `IfTableService` | `common` | IF 테이블 공통 조회 (Type B 에선 Oracle 소스라 직접 사용 안 함, 대신 JdbcTemplate 직접) |

### 3.5 JOSACODE 공통코드 확정 (Type A A5 등록 검증 겸)

4/24 Type A A5 (국가지하수/해수침투/농촌지하수/기초조사) operation 등록 시 **JOSACODE default 값을 추정치(104/112/113/215)** 로 기록. Type B 작업 시 `TC_GD000002` (`UGRWTR_CMMN_GRP_CODE='NGW_xxxx'`) 공통코드로 확정 필요. 영향 대상:

- A5 #1~#4 operation 의 `josacode` default 재검증
- B11/B12 등 JOSACODE 기반 필터가 있는 Step 구현 시 동일 값 사용

### 3.6 외부 제공 계층 operation 등록 패턴 (4/24 확립)

Phase 6 에서 Type B 완료 후 operation 11~18건 추가 등록 시 재활용할 4/24 확립 인프라:

- **슬래시 포함 operationId** (`ApiGatewayController` 의 `/**` 매칭)
- **응답 alias 대소문자 보존** (`DynamicQueryService` 의 쌍따옴표 alias)
- **자동 대문자 alias 채움** (`ColumnsTab.tsx`)
- **Python 일괄 등록 스크립트** (`scripts/temp/register_ops.py`) — 재활용 가능
- **전수 호출 테스트 스크립트** (`scripts/temp/test_ops.py`) — 재활용 가능

### 3.7 Orchestrator DB Agent 등록

각 Step 별 Agent 는 Orchestrator 의 `agents` 테이블에도 등록 필요:
- Phase 0 시점에 Agent 12종 미리 등록 (inactive 상태로)
- Phase 1~5 각 Step 완료 후 active 전환
- `targetDatasourceId` = api_provider PG datasource

## 4. Step 템플릿 설계 (파일럿 B14 기준)

### 4.1 참조 패턴

**`bojo-int/loader/step/JejuJewonLoadStep`** — 커스텀 Loader 의 정석.
- `StepExecutor` 구현
- `execute(StepContext context)` 에서 소스 조회 → 가공 → 타겟 UPSERT → SyncLog
- 조건실행 분기는 `ConditionBuilder` 사용
- source_refs 는 `SourceRefUtils` 사용

### 4.2 파일럿 B14 뼈대

```
sync-agent-provide/
  src/main/java/com/sync/agent/provide/
    loader/
      step/
        ApiPrvInspectionLoadStep.java         ← 신규
      factory/
        ApiPrvInspectionLoadStepFactory.java  ← 신규
    entity/target/
      ApiPrvInspection.java                   ← 기존
  src/main/resources/
    config/agents/
      provide-inspection.yml                  ← 신규
```

**Step 뼈대** (`ApiPrvInspectionLoadStep.java`):

```java
@Slf4j
public class ApiPrvInspectionLoadStep implements StepExecutor {

    private final String stepId;
    private final String stepName;
    private final List<String> sourceTables;     // [TM_GD110310, TC_GD000002]
    private final String targetTable;             // api_prv_inspection
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    // 생성자 생략

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        int readCount = 0;
        int writeCount = 0;
        int skipCount = 0;

        try {
            String sourceDsId = context.getSourceDatasourceId();
            String targetDsId = context.getTargetDatasourceId();
            JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);

            // 1. 조건실행 판별 (조건실행이면 기존 PK 필터 무시, 주어진 조건 그대로)
            boolean isResync = ConditionBuilder.isResyncExecution(context.getExecutionOptions());

            // 2. 소스 JOIN 조회 (Oracle)
            String sourceSql =
                "SELECT A.JOSACODE, A.DTA_STDR_YEAR, A.QLTWTR_INSPCT_IEM_CODE, " +
                "       B.CODE_CTNT AS REMARK_CTNT " +
                "FROM   TM_GD110310 A " +
                "LEFT JOIN (SELECT UGRWTR_CMMN_CODE, CODE_CTNT FROM TC_GD000002 " +
                "           WHERE UGRWTR_CMMN_GRP_CODE = 'NGW_0026') B " +
                "  ON A.QLTWTR_INSPCT_IEM_CODE = B.UGRWTR_CMMN_CODE " +
                "ORDER BY A.QLTWTR_INSPCT_IEM_CODE";
            List<Map<String, Object>> rows = sourceJdbc.queryForList(sourceSql);
            readCount = rows.size();

            // 3. 레코드별 source_refs 생성 + UPSERT
            String upsertSql = buildUpsertSql(targetTable);  // PG ON CONFLICT source_refs DO UPDATE
            for (Map<String, Object> row : rows) {
                try {
                    Object josacode = row.get("JOSACODE");
                    Object year = row.get("DTA_STDR_YEAR");
                    Object iemCode = row.get("QLTWTR_INSPCT_IEM_CODE");
                    Object codeCn = row.get("REMARK_CTNT");

                    // 복합 PK source_refs (TM_GD110310 이 primary, TC 는 참조)
                    String sourceRef = SourceRefUtils.buildComposite(
                        context, "TM_GD110310", josacode, year, iemCode);
                    String sourceRefsJson = SourceRefUtils.toJsonSingle(sourceRef);

                    targetJdbc.update(upsertSql,
                        josacode, year, iemCode, codeCn,
                        sourceRefsJson, context.getExecutionId(), LocalDateTime.now());
                    writeCount++;
                } catch (Exception e) {
                    log.error("[{}] Failed record: {}", stepId, row, e);
                    skipCount++;
                }
            }

            // 4. SyncLog 저장
            saveSyncLog(context, readCount, writeCount, skipCount);

            return StepResult.builder()
                .stepId(stepId).status(Status.SUCCESS)
                .readCount(readCount).writeCount(writeCount).skipCount(skipCount)
                .durationMs(System.currentTimeMillis() - startTime)
                .sourceTable(String.join(",", sourceTables))
                .targetTable(targetTable)
                .build();

        } catch (Exception e) {
            log.error("[{}] Step failed", stepId, e);
            return StepResult.failed(stepId, e.getMessage(),
                System.currentTimeMillis() - startTime);
        }
    }

    private String buildUpsertSql(String table) {
        return "INSERT INTO " + table + " " +
               "(ugwtr_exmn_cd, data_crtr_yr, wq_insp_artcl_cd, code_cn, " +
               " source_refs, execution_id, updated_at) " +
               "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?) " +
               "ON CONFLICT (source_refs) DO UPDATE SET " +
               " code_cn = EXCLUDED.code_cn, " +
               " execution_id = EXCLUDED.execution_id, " +
               " updated_at = EXCLUDED.updated_at";
    }

    private void saveSyncLog(StepContext context, int read, int write, int skip) {
        // SyncLog 저장 — 기존 다른 Step 참조
    }
}
```

**Factory** (`ApiPrvInspectionLoadStepFactory.java`):

```java
@Component
@RequiredArgsConstructor
public class ApiPrvInspectionLoadStepFactory implements StepFactory {
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @Override public String getFactoryKey() { return "api-prv-inspection-load"; }

    @Override
    public StepExecutor create(Map<String, Object> config) {
        return new ApiPrvInspectionLoadStep(
            (String) config.get("id"),
            (String) config.get("name"),
            (List<String>) config.get("source-table"),
            toSingle(config.get("target-table")),
            dataSourceProvider, syncLogRepository);
    }
}
```

**YAML** (`provide-inspection.yml`):

```yaml
# ──────────────────────────────────────
# 패턴: Type B 전처리 (LEFT JOIN)
# 소스: TM_GD110310 + TC_GD000002 (공통코드 NGW_0026)
# 타겟: api_prv_inspection
# 레거시: OPN searchInspection / searchAllInspection (내부 helper + 관리자)
# ──────────────────────────────────────
agent-code: provide-inspection
type: LOADER

steps:
  - id: provide-inspection-load
    name: 수질검사항목 적재
    factory-key: api-prv-inspection-load
    source-table: [TM_GD110310, TC_GD000002]
    target-table: api_prv_inspection
```

## 5. 공통화 전략 (Rule of Three)

**파일럿 + 2번째 Step 까지**: 각 Step 내부에 UPSERT SQL 직접 작성 (중복 허용)

**3번째 Step 작성 시점**: 다음을 확인
- (i) UPSERT SQL 생성 패턴이 동일한가? (INSERT + ON CONFLICT source_refs DO UPDATE + 3종 메타)
- (ii) source_refs 생성 패턴이 동일한가? (복합 PK → SourceRefUtils.buildComposite + toJsonSingle)
- (iii) SyncLog 저장 패턴이 동일한가?

**(i)~(iii) 모두 동일하면** → `ProvideStepHelper` 신규 (provide 모듈 내 — common 에 올리지 않음, `feedback_module_specific_stays`) 추출:
- `helper.buildUpsertSql(table, bizColumns, conflictKey)` — UPSERT SQL 템플릿
- `helper.saveSyncLog(context, stepId, read, write, skip)` — SyncLog 저장

**하나라도 변주가 있으면** → 유지, Step 별 개별 구현.

## 6. Phase 별 마일스톤

### Phase 0: 선행 (DDL + 엔티티 + Agent 등록)
- [ ] 원본 Oracle DDL **16종** 일괄 작성 (`scripts/ddl/internal-oracle/provide-source/`)
  - VIEW_GTEST 는 실제 뷰 아닌 **물리 테이블로 단순화** (B6)
- [ ] Oracle 컨테이너에 DDL 실행 (샘플 데이터 포함)
- [ ] Oracle 함수 3개(`FN_GD_GET_GUBUN`, `FN_GD_GET_CMMTNDCODE`) 재현 DDL
- [ ] Orchestrator 에 Agent **12종** 등록 (DB `agents` 테이블, inactive 로 초기화)
- [ ] JOSACODE 공통코드 실값 확정 (`TC_GD000002` 그룹코드 기반)

### Phase 1: 파일럿 (B14 Inspection)
- [ ] `ApiPrvInspectionLoadStep` + Factory + YAML 작성
- [ ] common JAR 재배포 불필요 (provide 모듈 단독)
- [ ] Agent 재기동 → 실행
- [ ] 검증: read/write = 샘플 수, source_refs 포맷 확인, trace-source FOUND
- [ ] Step 템플릿 확정 (이후 11종이 이 뼈대 따라가기)

### Phase 2: 저~중 난이도 (B6 → B17 → B16 → B4 → B5)
- [ ] **B6 `ApiPrvViewGtestLoadStep`** — 단순 SELECT (VIEW_GTEST 단순화 테이블)
- [ ] Step 4종 (B17/B16/B4/B5) 순차 작성
- [ ] **공통화 판단** (Step 3~4번째 시점에 `ProvideStepHelper` 추출 여부)
- [ ] B4 작업 시 Oracle 함수 DDL 추가

### Phase 3: 상 난이도 (B13 → B18 → B9 → B10)
- [ ] B10 작업 전 `ApiPrvObsStationTime` 엔티티 작성
- [ ] 동적 PIVOT / CTE / UNION 패턴 대응
- [ ] B13 은 검사항목 codeList 선조회 필요 (우리는 B14 타겟 재활용 or 별도 query)

### Phase 4: 최상 난이도 (B11 → B12)
- [ ] B12 작업 전 `ApiPrvWaterQualityDj` 엔티티 작성
- [ ] 동적 PIVOT — `qltwtrInspctIemCodes` 선조회 → CASE WHEN 하드코딩
- [ ] B12 는 DJ + KB 공용 SQL, brtcNm 파라미터 받기 (Step 내부 로직으로)

### Phase 5: 통합 검증
- [ ] 12종 Agent 전수 E2E 실행
- [ ] 기존 provide 7종 (Type A) + 중복 엔티티 3종 의 회귀 영향 없음 확인
- [ ] trace-source 전수 검증
- [ ] `docs/provide/LEGACY_API_MIGRATION_MAP.md` 상태 업데이트

### Phase 6: 외부 제공 계층 operation 등록 (**4/24 확립 패턴 재사용**)
- [ ] Python 일괄 등록 스크립트 생성 (`scripts/temp/register_type_b_ops.py`)
  - `scripts/temp/register_ops.py` (Type A 용) 구조 베이스 — Operation + Columns + Params 각각
- [ ] Type B 12 Step × 평균 1.5 operation = **약 18개 operation** 등록
  - B12 (DJ+KB), B16 (DJ+KB), A5 이미 등록된 4건 참고
  - B14 는 내부 helper 라 공개 operation 여부 팀 확인 후
- [ ] 레거시 URL 재현: operationId = `{service}/{operation}` (슬래시 포함, `api/data/` prefix 제거)
- [ ] 대문자 alias: 자동 대문자 기능 적합 여부 Step별 사전 판정 (§2.1 참조)
- [ ] 전수 호출 테스트 (`test_ops.py` 확장)
- [ ] 응답 JSON 필드명 대소문자 확인 — 쌍따옴표 alias 효과
- [ ] `PROVIDE_OPERATION_SPEC.md` 에 Type B 스펙 섹션 추가 (또는 별도 문서 분할)

## 7. 검증 전략

### 7.1 Step 별 (각 Phase 마다)

- read/write count = 샘플 데이터 수와 일치
- source_refs 포맷 확인: `IC:{dsId}:{tbId}:{pk_value}` 또는 복합 PK 는 `pk1|pk2|pk3`
- trace-source FOUND (provide Agent 의 `/trace-source` API 로 역추적)
- 레거시 SQL 결과와 컬럼/값 spot check 비교

### 7.2 회귀 (Phase 5)

- **provide 기존 7종** (A1~A7 + 중복 엔티티 3개) 실행 → read/write 수치 변동 없음
- **공통 헬퍼 수정 없음** 가정 — provide 모듈 내부만 수정하므로 common JAR 영향 없음
- **bojo / bojo-int / others** 영향 없음 — provide 는 독립 모듈

### 7.3 스냅샷

Phase 0 직전에 현재 provide PG 테이블 상태 스냅샷 저장:
```
dev_plan/2026_04/24/regression_snapshot/snap_before.sql
```
(각 api_prv_* 테이블 count + 최신 source_refs 샘플)

Phase 5 완료 후 비교.

## 8. 산출물 체크리스트

### 8.1 코드

- [ ] Step **12종** (`ApiPrv*LoadStep.java`)
- [ ] Factory **12종** (`ApiPrv*LoadStepFactory.java`)
- [ ] 엔티티 **3종** (`ApiPrvViewGtest`, `ApiPrvObsStationTime`, `ApiPrvWaterQualityDj`)
- [ ] (Rule of Three 조건 시) `ProvideStepHelper`

### 8.2 YAML

- [ ] `provide-inspection.yml` (B14)
- [ ] `provide-view-gtest.yml` (B6) — **신규**
- [ ] `provide-unregits-fcly.yml` (B17)
- [ ] `provide-actual-use-dj.yml` (B16)
- [ ] `provide-permwell.yml` (B4)
- [ ] `provide-general-105.yml` (B5)
- [ ] `provide-water-quality-mfds.yml` (B13)
- [ ] `provide-wq-input-status-dj.yml` (B18)
- [ ] `provide-linkage-chart.yml` (B9)
- [ ] `provide-obs-station-time.yml` (B10)
- [ ] `provide-water-quality.yml` (B11)
- [ ] `provide-water-quality-dj.yml` (B12)

### 8.3 DDL

- [ ] 원본 Oracle DDL **16종** (`scripts/ddl/internal-oracle/provide-source/`, VIEW_GTEST 포함)
- [ ] Oracle 함수 DDL (`FN_GD_GET_GUBUN`, `FN_GD_GET_CMMTNDCODE` 재현)
- [ ] 엔티티 3종은 JPA 자동 생성 (gims_api_provider_pg)

### 8.4 Orchestrator DB 등록

- [ ] Agent **12종** `agents` 테이블 등록

### 8.5 Operation 등록 (Phase 6)

- [ ] Type B Operation 약 18건 등록 (12 Step × 평균 1.5 operation, DJ/KB 공용 포함)
- [ ] `PROVIDE_OPERATION_SPEC.md` 확장

### 8.6 문서

- [ ] `dev_logs/2026_04/YYYY-MM-DD.md` 작업 일지 (각 Phase 완료 시)
- [ ] `docs/provide/LEGACY_API_MIGRATION_MAP.md` 상태 업데이트 (Phase 별 완료 표시)
- [ ] 본 계획서 최종 상태로 업데이트 (체크리스트 체크)

## 9. 리스크 / 주의사항

### 9.1 Step 구현 리스크

- **Oracle 동적 PIVOT (B11/B12/B13)**: iBatis `<iterate>` 문법을 Java 에서 재현해야 함. `qltwtrInspctIemCodes` 를 먼저 조회 (B14 타겟 재활용 가능?) → CASE WHEN 을 동적 조립
- **B9/B10 소스 중복**: 두 Step 이 `PM_GD970201 + TM_GD970101 + TM_GD120001` 공유. 읽기는 분리 (granularity 다름)
- **B12 (DJ + KB 공용)**: Step 내부에서 `brtcNm` 을 파라미터로 받아 2번 실행 or 1번 실행 후 분리 — 구현 시점에 결정

### 9.2 보류 건 재개 조건 (B7/B8)

- **B7/B8 보류 사유 3중 불확실성**:
  - 현행 DB 에 소스 테이블 일부 또는 전부 부재 (버전 차이 의심)
  - 10분 단위 실시간 관측값 → PVD 주기 이식 구조적 오차 (5~10분)
  - 실사용 여부 미확인 — 폐기/비활성 가능성
- **재개 조건**: 담당자 확인으로 (a) 해당 API 가 현재도 서비스 중, (b) 현행 DB 의 정확한 소스 테이블 이름, (c) 허용 오차 수준 합의
- **검증 쿼리**: `scripts/diagnostic/b7b8-dblink-check.sql` 실행 후 결과 반영

### 9.3 외부 제공 계층 이슈 (Phase 6 대비)

- **`Service.update()` operationId immutable**: 현재 UI/API 로 operationId 수정 불가 (보호 로직). 수정 필요하면 한 줄 패치 or DB 직접 UPDATE
- **JOSACODE 공통코드 추정값**: Type A A5 등록 시 `104/112/113/215` 로 가정. Type B 에서 동일 기준으로 filter 시 실값 확정 필요 (`TC_GD000002`)
- **자동 대문자 alias 적합도 Step별 차이**: 표준화로 이름이 달라지는 대부분 Step 은 수동 입력 필요. A7 처럼 `wt_* → WT_*` 직선 대응인 경우에만 자동 버튼 효과 큼

### 9.4 메모리 룰 엄수

- `feedback_provide_target_per_api`: 타겟 분리 원칙
- `feedback_provide_layer_upsert`: UPSERT + UK(source_refs)
- `feedback_module_specific_stays`: 공통화는 provide 모듈 내부에 제한
- `feedback_no_scope_creep`: 중복 엔티티 3개(`ApiPrvTmGd110301/110302/Ngw04`) 건드리지 말 것 — 별도 작업

## 10. 4/24 확립 인프라 활용 (교훈)

Type A operation 등록·호출 검증 과정에서 확립된 자산 — Phase 6 때 그대로 재사용:

| 자산 | 위치 | Type B 활용 |
|---|---|---|
| 슬래시 operationId 지원 | `ApiGatewayController.java` | 레거시 URL 그대로 (`wellInfoService/getWellInfo` 등) |
| 응답 alias 대소문자 보존 | `DynamicQueryService.java` | 쌍따옴표 alias — 자동/수동 alias 모두 대문자 유지 |
| 자동 대문자 alias UI | `ColumnsTab.tsx` | A7 유사 건(이름 직선 대응) 에서 등록 시간 단축 |
| 일괄 등록 스크립트 | `scripts/temp/register_ops.py` | 구조 복사해서 Type B 용 `register_type_b_ops.py` 작성 |
| 전수 호출 테스트 | `scripts/temp/test_ops.py` | Type B operation 추가 후 재실행 |
| DB 진단 쿼리 | `scripts/diagnostic/b7b8-dblink-check.sql` | B7/B8 재개 시 DB 상태 선확인 |

## 11. 승인 요청 사항

이 계획서에 대해 사용자 승인 후 **Phase 0 (DDL 일괄 생성 + Agent 사전 등록)** 부터 순차 진행.

특히 확정 필요:
- **(a)** Step 작성 순서 12종 이대로 OK? (B6 Phase 2 앞부분 추가)
- **(b)** Oracle 함수 재현(**옵션 A**) 방향 OK?
- **(c)** 공통화 **Rule of Three** 전략 OK?
- **(d)** Phase 0 시점에 **Agent 12종 Orchestrator DB inactive 등록**까지 포함 OK?
- **(e)** 중복 엔티티 3개 정리는 **별도 작업** 으로 미루기 OK?
- **(f)** Phase 6 외부 제공 계층 operation 등록은 Type B 완료 후 일괄 (4/24 패턴 재사용) OK?

---

## 개정 이력

- 2026-04-24 (초안): 최초 작성. `docs/provide/LEGACY_API_MIGRATION_MAP.md` 재분류 결과 반영.
- 2026-04-24 (개정): Type A operation 등록·호출 검증 경험 반영.
  - §1 목표: end-to-end (Step + operation 등록) 명시
  - §2.1: Step **11 → 12** (B6 VIEW_GTEST 포함), 각 Step 에 레거시 URL/operationId 후보 추가
  - §2.3: B7/B8 보류 사유 강화 (소스 부재 + 실시간성 + 실사용 의심)
  - §3.1: DDL **15 → 16** (VIEW_GTEST 단순 테이블)
  - §3.2: 엔티티 **2 → 3** (`ApiPrvViewGtest` 추가)
  - §3.5: JOSACODE 공통코드 확정 선행 조건 추가
  - §3.6: 4/24 확립 인프라 목록
  - §3.7: Orchestrator DB Agent 사전 등록
  - §6: Phase 6 (외부 제공 계층 operation 등록) 신규
  - §9: 9.1~9.4 리스크 세분화 (보류 재개 조건, 외부 계층 이슈 추가)
  - §10: 4/24 확립 인프라 활용 섹션 신규
  - §11: 승인 요청 (f) Phase 6 추가
