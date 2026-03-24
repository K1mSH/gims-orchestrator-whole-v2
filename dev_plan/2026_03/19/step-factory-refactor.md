# Step Factory 리팩토링 — YAML 기반 Step 선언 구조 전환

## 목적

현재 agent-type(RCV/SND/LOADER)별로 PipelineConfig 자바 클래스가 Step을 하드코딩하고 있어,
새로운 데이터소스를 편입하려면 새 type + 새 Config 클래스가 필요하다.

**목표**: YAML에서 `steps:` 배열로 Step을 선언하면, 범용 Config가 자동으로 Runner를 조립하는 구조로 전환.

## 현재 구조 (문제점)

```
YAML: 테이블명, PK 등 파라미터만 정의
  ↓
RcvPipelineConfig (자바): 항상 SourceToIfStep(jewon) + SourceToIfStep(obsvdata) + LinkStep
SndPipelineConfig (자바): 항상 SourceToIfStep(jewon) + SourceToIfStep(obsvdata)
LoaderPipelineConfig (자바): 항상 DefaultLoadStep or InternalLoadStep
```
→ Step 구성이 type에 종속, YAML로 제어 불가

## 변경 후 구조

```
YAML: steps 배열로 Step type + 파라미터 선언
  ↓
StepFactoryRegistry: type 문자열 → Step 객체 생성
  ↓
PipelineAssembler: 모든 Agent를 YAML만으로 Runner 조립
```

---

## 1. YAML 포맷 변경

### RCV (변경 전)
```yaml
agent-code: dmz-bojo-rcv-daejeon
type: RCV
jewon:
  source-table: sec_jewon_view
  target-table: if_rsv_sec_jewon
  primary-key: obsv_code
  conflict-key: source_refs
  full-copy: true
  skip-source-status-update: true
obsvdata:
  source-table: sec_obsvdata_view
  target-table: if_rsv_sec_obsvdata
  primary-key: id
  conflict-key: source_refs
  date-column: obsv_date
  time-column: obsv_time
link:
  use-link-table: true
  table-name: link_ngwis
```

### YAML step 필드 설명

```yaml
steps:
  - id: jewon-extract              # Step 고유 식별자 (로그, 프론트 표시, selectedStepIds 매칭에 사용)
    name: 제원 데이터 추출            # 프론트엔드/로그에 표시되는 한글 이름
    factory-key: source-to-if      # StepFactoryRegistry에서 Factory를 찾는 매핑 키
                                   # → 이 문자열로 어떤 StepFactory가 Step 객체를 생성할지 결정
                                   # → 해당 Factory가 이 config 블록의 나머지 필드를 파싱
    source-table: sec_jewon_view   # 이하 필드는 factory-key별로 다름 — 해당 Factory만 해석
    ...
```

**id vs factory-key 구분:**
- `id`: 이 Step을 뭐라고 부를지. Agent마다 자유롭게 지정. (로그/프론트/step 선택 실행)
- `factory-key`: 이 Step을 어떤 Java 클래스가 만들지. 등록된 StepFactory의 키와 일치해야 함.
- 같은 `factory-key`의 Step을 여러 개 쓸 수 있음 (예: RCV의 jewon-extract, obsvdata-extract 둘 다 `factory-key: source-to-if`)
- `id`는 Agent 내 고유, `factory-key`는 전역 등록된 Factory 이름

### RCV (변경 후)
```yaml
agent-code: dmz-bojo-rcv-daejeon   # Orchestrator DB agent 테이블의 agent_code와 매칭
type: RCV                          # Orchestrator에서 Agent 유형 분류용 (파이프라인 로직에는 미사용)

steps:
  # Step 1: 제원 — 외부 DB에서 전체 복사
  - id: jewon-extract              # Step 식별자 (로그/프론트 표시용)
    name: 제원 데이터 추출
    factory-key: source-to-if      # → SourceToIfStepFactory (common)
    source-table: sec_jewon_view   # SELECT 대상 (외부 DB)
    target-table: if_rsv_sec_jewon # UPSERT 대상 (IF 테이블)
    primary-key: obsv_code         # source SELECT 시 PK
    conflict-key: source_refs      # UPSERT ON CONFLICT 키
    full-copy: true                # true: 매번 전체 복사 (증분 아님)
    skip-source-status-update: true # true: source에 link_status 업데이트 안함

  # Step 2: 관측데이터 — Link 테이블 기반 증분 추출
  - id: obsvdata-extract
    name: 관측데이터 추출 (Link 기반)
    factory-key: source-to-if-link  # → LinkSourceToIfStepFactory (bojo 전용)
    source-table: sec_obsvdata_view
    target-table: if_rsv_sec_obsvdata
    primary-key: id
    conflict-key: source_refs
    date-column: obsv_date         # 증분/시간범위 추출 시 비교 컬럼
    time-column: obsv_time
    link-table: link_ngwis         # link-based 전용: Link 매핑 테이블
    link-jewon-source: sec_jewon_view  # link-based 전용: fetcher가 참조할 jewon source

  # Step 3: Link 테이블 갱신
  - id: link-table-update
    name: Link 테이블 갱신
    factory-key: link-update        # → LinkUpdateStepFactory (bojo 전용)
                                    # 전용 Factory이므로 if-table/link-table은 Factory 내부에서 처리

# 프론트엔드용 (파이프라인 로직과 무관, 기존 유지)
select-tables: [sec_jewon_view, sec_obsvdata_view]   # 조건실행 WHERE 드롭다운
table-mappings:                                       # 모니터링/추적 UI
  - name: jewon
    source: [sec_jewon_view]
    target: [if_rsv_sec_jewon]
  - name: obsvdata
    source: [sec_obsvdata_view]
    target: [if_rsv_sec_obsvdata]
```

### SND (변경 후)
```yaml
agent-code: dmz-bojo-snd
type: SND

steps:
  # RCV와 동일한 source-to-if factory, 파라미터만 다름 (Target DB → IF_SND)
  - id: jewon-snd-extract
    name: 제원 데이터 송신 추출
    factory-key: source-to-if      # → SourceToIfStepFactory (common)
    source-table: sec_jewon
    target-table: if_snd_sec_jewon
    primary-key: id
    conflict-key: source_refs
    full-copy: true

  - id: obsvdata-snd-extract
    name: 관측데이터 송신 추출
    factory-key: source-to-if      # → SourceToIfStepFactory (common)
    source-table: sec_obsvdata
    target-table: if_snd_sec_obsvdata
    primary-key: id
    conflict-key: source_refs
    date-column: obsv_date
    time-column: obsv_time

select-tables: [sec_jewon, sec_obsvdata]
table-mappings: ...
```

### DMZ Loader (변경 후)
```yaml
agent-code: dmz-bojo-loader
type: LOADER

steps:
  - id: dmz-bojo-load              # Step 식별자
    name: DMZ 적재                  # 프론트/로그 표시명
    factory-key: dmz-bojo-load     # → DmzBojoLoadStepFactory (bojo 전용)
                                   # 전용 Factory이므로 테이블명 등은 Factory 내부에서 @Value로 해결
                                   # YAML에는 factory-key만 있으면 충분

select-tables: [if_rsv_sec_jewon, if_rsv_sec_obsvdata, sec_jewon]
table-mappings: ...
```

### Internal Loader (변경 후)
```yaml
agent-code: internal-bojo-loader
type: LOADER

steps:
  - id: internal-bojo-load         # Step 식별자
    name: 내부 적재
    factory-key: internal-bojo-load  # → InternalBojoLoadStepFactory (bojo-int 전용)
                                     # 전용 Factory이므로 세부 설정은 Factory 내부에서 처리

select-tables: [if_rsv_sec_jewon, if_rsv_sec_obsvdata]
table-mappings: ...
```

### 핵심: jewon만 필요한 새 Agent 예시
```yaml
agent-code: dmz-bojo-rcv-newvendor
type: RCV

steps:
  # source-to-if factory만 쓰면 common Factory로 처리 — 자바 코드 추가 불필요
  - id: jewon-extract
    name: 제원 추출
    factory-key: source-to-if
    source-table: new_jewon_view
    target-table: if_rsv_new_jewon
    primary-key: station_id
    conflict-key: source_refs
    full-copy: true

select-tables: [new_jewon_view]
table-mappings:
  - name: jewon
    source: [new_jewon_view]
    target: [if_rsv_new_jewon]
```
→ **YAML만 추가하면 새 Agent 등록 완료. 자바 코드 수정 불필요.**

---

## 2. StepFactory 설계

### 인터페이스 (common)

```java
// common/.../pipeline/StepFactory.java
public interface StepFactory {
    /** 이 Factory의 매핑 키 ("source-to-if", "link-update" 등) — YAML factory-key와 매칭 */
    String getFactoryKey();

    /** YAML step config로 StepExecutor 생성 */
    StepExecutor create(Map<String, Object> stepConfig);
}
```

### 레지스트리 (common)

```java
// common/.../pipeline/StepFactoryRegistry.java
@Component
public class StepFactoryRegistry {
    private final Map<String, StepFactory> factories = new HashMap<>();

    // Spring이 모든 StepFactory 구현체를 자동 주입
    public StepFactoryRegistry(List<StepFactory> factoryList) {
        for (StepFactory f : factoryList) {
            factories.put(f.getFactoryKey(), f);
        }
    }

    public StepExecutor create(Map<String, Object> stepConfig) {
        String factoryKey = (String) stepConfig.get("factory-key");
        StepFactory factory = factories.get(factoryKey);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown factory-key: " + factoryKey
                + ". Registered: " + factories.keySet());
        }
        return factory.create(stepConfig);
    }
}
```

### Factory 구현체

| Step type | Factory 클래스 | 위치 | 생성하는 Step |
|-----------|---------------|------|-------------|
| `source-to-if` | SourceToIfStepFactory | **common** | SourceToIfStep (simple-copy) |
| `source-to-if-link` | LinkSourceToIfStepFactory | **bojo** | SourceToIfStep (link 기반 fetcher) |
| `link-update` | LinkUpdateStepFactory | **bojo** | LinkTableUpdateStep |
| `dmz-bojo-load` | DmzBojoLoadStepFactory | **bojo** | DefaultLoadStep |
| `internal-bojo-load` | InternalBojoLoadStepFactory | **bojo-int** | InternalLoadStep |

```java
// common — SourceToIfStepFactory (simple-copy 전용)
@Component
public class SourceToIfStepFactory implements StepFactory {
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    public String getFactoryKey() { return "source-to-if"; }

    public StepExecutor create(Map<String, Object> config) {
        ExtractStepConfig extractConfig = ExtractStepConfig.builder()
                .stepId((String) config.get("id"))
                .stepName((String) config.get("name"))
                .extractType(ExtractType.SIMPLE_COPY)
                .sourceTable((String) config.get("source-table"))
                .targetIfTable((String) config.get("target-table"))
                .primaryKeyColumn((String) config.get("primary-key"))
                .conflictKey((String) config.get("conflict-key"))
                .fullCopy(Boolean.TRUE.equals(config.get("full-copy")))
                .skipSourceStatusUpdate(Boolean.TRUE.equals(config.get("skip-source-status-update")))
                .dateColumn((String) config.get("date-column"))
                .timeColumn((String) config.get("time-column"))
                .build();

        SourceToIfStep step = new SourceToIfStep(extractConfig, dataSourceProvider, syncLogRepository);
        String mappingName = deriveMappingName((String) config.get("id"));
        step.setMappingName(mappingName);
        return step;
    }
}
```

```java
// bojo — LinkSourceToIfStepFactory (link 기반, bojo 전용)
@Component
public class LinkSourceToIfStepFactory implements StepFactory {
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    public String getFactoryKey() { return "source-to-if-link"; }

    public StepExecutor create(Map<String, Object> config) {
        LinkTableObsvDataFetcher fetcher = new LinkTableObsvDataFetcher(
                dataSourceProvider,
                (String) config.get("link-jewon-source"),
                (String) config.get("source-table"),
                (String) config.get("link-table")
        );

        ExtractStepConfig extractConfig = ExtractStepConfig.builder()
                .stepId((String) config.get("id"))
                .stepName((String) config.get("name"))
                .extractType(ExtractType.CUSTOM_STAGING)
                .customDataFetcher(fetcher)
                .sourceTable((String) config.get("source-table"))
                .targetIfTable((String) config.get("target-table"))
                .primaryKeyColumn((String) config.get("primary-key"))
                .conflictKey((String) config.get("conflict-key"))
                .dateColumn((String) config.get("date-column"))
                .timeColumn((String) config.get("time-column"))
                .build();

        SourceToIfStep step = new SourceToIfStep(extractConfig, dataSourceProvider, syncLogRepository);
        String mappingName = deriveMappingName((String) config.get("id"));
        step.setMappingName(mappingName);
        return step;
    }
}
```

```java
// bojo — LinkUpdateStepFactory
@Component
public class LinkUpdateStepFactory implements StepFactory {
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    public String getFactoryKey() { return "link-update"; }

    public StepExecutor create(Map<String, Object> config) {
        return new LinkTableUpdateStep(
                dataSourceProvider,
                (String) config.get("if-table"),
                (String) config.get("link-table"),
                syncLogRepository
        );
    }
}
```

---

## 3. PipelineAssembler — 타입별 Config 통합

```java
// 각 모듈(bojo, bojo-int)에 1개씩
@Configuration
public class PipelineAssembler {
    private final AgentConfigLoader agentConfigLoader;
    private final PipelineRegistry pipelineRegistry;
    private final StepFactoryRegistry stepFactoryRegistry;

    @PostConstruct
    public void registerAll() {
        for (AgentDefinition def : agentConfigLoader.getAgentDefinitions()) {
            List<StepExecutor> steps = new ArrayList<>();
            List<StepDefinition> stepDefs = new ArrayList<>();

            int order = 1;
            for (Map<String, Object> stepConfig : def.getSteps()) {
                StepExecutor step = stepFactoryRegistry.create(stepConfig);
                steps.add(step);

                stepDefs.add(StepDefinition.builder()
                        .stepId((String) stepConfig.get("id"))
                        .stepName((String) stepConfig.get("name"))
                        .displayOrder(order++)
                        .enabledByDefault(true)
                        .build());
            }

            PipelineRunner runner = new PipelineRunner(def.getAgentCode(), steps);
            pipelineRegistry.register(def.getAgentCode(), def.getType(), runner, stepDefs);
        }
    }
}
```

---

## 4. AgentDefinition 변경

### 변경 전
```java
public class AgentDefinition {
    private String agentCode;
    private String type;
    private TableConfig jewon;        // RCV/SND 전용
    private TableConfig obsvdata;     // RCV/SND 전용
    private LinkConfig link;          // RCV 전용
    private Map<String, String> ifTable;      // Loader 전용
    private Map<String, String> targetTable;  // Loader 전용
    private StepConfig step;          // Loader 전용
    ...
}
```

### 변경 후
```java
public class AgentDefinition {
    private String agentCode;
    private String type;
    private List<Map<String, Object>> steps;  // 범용 step 목록
    private List<String> selectTables;        // 유지
    private List<TableMapping> tableMappings; // 유지
}
```
→ type별 전용 필드(jewon, obsvdata, link, ifTable 등) 제거, `steps` 리스트로 통합

### AgentConfigLoader 변경

기존 jewon/obsvdata/link 개별 파싱 로직 제거.
`steps:` 배열을 그대로 `List<Map<String, Object>>`로 파싱.

---

## 5. 삭제 대상

| 파일 | 모듈 | 사유 |
|------|------|------|
| RcvPipelineConfig.java | bojo, bojo-int | PipelineAssembler로 대체 |
| SndPipelineConfig.java | bojo | PipelineAssembler로 대체 |
| LoaderPipelineConfig.java | bojo, bojo-int | PipelineAssembler로 대체 |
| AgentDefinition.TableConfig | bojo, bojo-int | steps Map으로 대체 |
| AgentDefinition.LinkConfig | bojo | steps Map으로 대체 |
| AgentDefinition.StepConfig | bojo, bojo-int | steps Map으로 대체 |

## 6. 주의사항

### LinkTableObsvDataFetcher 위치
- 현재 `bojo/rcv/fetcher/`에 그대로 유지
- bojo 전용 `LinkSourceToIfStepFactory`가 참조하므로 이동 불필요
- common의 `SourceToIfStepFactory`는 simple-copy만 처리, link 로직 없음

### Loader Step 클래스 리네이밍
- `DefaultLoadStep` → `DmzBojoLoadStep` (bojo)
- `InternalLoadStep` → `InternalBojoLoadStep` (bojo-int)
- factory-key / Factory 클래스명 / Step 클래스명을 일관되게 맞춤
- 참조하는 파일(LoaderStepHelper, application.yml 등)의 import/Bean명도 변경 필요

### DmzBojoLoadStep Bean 주입
- 현재 LoaderPipelineConfig에서 Spring Bean으로 주입받는 구조
- StepFactory 패턴에서는 Factory가 직접 생성하므로, Step의 의존성을
  Factory가 갖도록 변경 필요

### YAML 마이그레이션
- 12개 DMZ YAML + Internal YAML 전부 일괄 변환
- steps 추가 + 기존 jewon/obsvdata/link/if-table/target-table 섹션 제거

---

## 7. 작업 순서

### Phase 1: 기반 구조
1. `StepFactory` 인터페이스 (common)
2. `StepFactoryRegistry` (common)
3. `SourceToIfStepFactory` (common, simple-copy 전용)

### Phase 2: Step 리네이밍
4. `DefaultLoadStep` → `DmzBojoLoadStep` (bojo)
5. `InternalLoadStep` → `InternalBojoLoadStep` (bojo-int)

### Phase 3: 모듈별 Factory
6. `LinkSourceToIfStepFactory` (bojo, link 기반)
7. `LinkUpdateStepFactory` (bojo)
8. `DmzBojoLoadStepFactory` (bojo)
9. `InternalBojoLoadStepFactory` (bojo-int)

### Phase 4: 통합
10. `AgentDefinition` 변경 (steps 필드)
11. `AgentConfigLoader` steps 파싱 로직
12. `PipelineAssembler` 작성 (bojo, bojo-int)
13. 기존 RcvPipelineConfig, SndPipelineConfig, LoaderPipelineConfig 삭제

### Phase 5: YAML 마이그레이션
14. DMZ YAML 12개 변환
15. Internal YAML 변환

### Phase 6: 검증
16. bojo 빌드 테스트
17. bojo-int 빌드 테스트
18. (가능하면) 앱 기동 테스트

---

## 8. 변경 영향 범위

### 변경 없는 파일
- PipelineRegistry — 그대로 유지 (Runner 등록/조회만)
- PipelineRunner — 그대로 유지 (Step 순차 실행만)
- PipelineService — 그대로 유지 (Runner 실행만)
- PipelineController — 그대로 유지
- SourceToIfStep — 그대로 유지 (로직 변경 없음)
- LinkTableUpdateStep — 그대로 유지

### 변경 파일 (리네이밍)
- DefaultLoadStep → DmzBojoLoadStep (bojo) — 클래스명/파일명 변경, 로직 변경 없음
- InternalLoadStep → InternalBojoLoadStep (bojo-int) — 클래스명/파일명 변경, 로직 변경 없음

### 변경 파일 (구조)
- AgentConfigLoader (bojo, bojo-int) — steps 파싱
- AgentDefinition (bojo, bojo-int) — 필드 구조 변경

### 신규 파일
- StepFactory.java (common)
- StepFactoryRegistry.java (common)
- SourceToIfStepFactory.java (common)
- LinkSourceToIfStepFactory.java (bojo)
- LinkUpdateStepFactory.java (bojo)
- DmzBojoLoadStepFactory.java (bojo)
- InternalBojoLoadStepFactory.java (bojo-int)
- PipelineAssembler.java (bojo, bojo-int)

### 삭제 파일
- RcvPipelineConfig.java (bojo, bojo-int)
- SndPipelineConfig.java (bojo)
- LoaderPipelineConfig.java (bojo, bojo-int)
