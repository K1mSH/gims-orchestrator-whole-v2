# 공통 아키텍처와 인터페이스 구조 (Common Architecture & Interface Hierarchy)

> 이 문서는 GIMS 동기화 시스템의 **공통 모듈(sync-agent-common)이 정의하는 인터페이스**와
> **각 모듈이 이를 구현하는 구조**를 설명합니다.
> "왜 이렇게 나눠놨는지", "새 기능을 추가하려면 어디를 건드려야 하는지"를 이해할 수 있도록 작성했습니다.

---

## 전체 그림

sync-agent-common은 **규칙(인터페이스)**을 정의하고, 각 모듈은 그 규칙에 맞춰 **자기만의 구현**을 제공한다.

```
sync-agent-common (규칙 정의)
  ├─ StepExecutor         "데이터 처리 단계는 이렇게 생겨야 한다"
  ├─ StepFactory          "Step을 만드는 공장은 이렇게 생겨야 한다"
  ├─ DataSourceProvider    "DB 연결을 제공하는 서비스는 이렇게 생겨야 한다"
  ├─ DataFetcher           "데이터를 가져오는 방법은 이렇게 생겨야 한다"
  ├─ StepProgressCallback  "진행 상황 알림은 이렇게 보내야 한다"
  └─ StepDefinitionProvider "Step 목록 조회는 이렇게 해야 한다"

sync-agent-bojo (DMZ Agent 구현)
  ├─ DmzBojoLoadStep       ← StepExecutor 구현
  ├─ DmzBojoLoadStepFactory ← StepFactory 구현
  ├─ SyncDataSourceService ← DataSourceProvider 구현
  └─ ...

sync-agent-bojo-int (Internal Agent 구현)
  ├─ InternalBojoLoadStep  ← StepExecutor 구현
  ├─ SyncDataSourceService ← DataSourceProvider 구현
  └─ ...

sync-proxy-dmz / sync-proxy-internal
  └─ ProxyDataSourceService ← DataSourceProvider 구현
```

**이 구조의 핵심 이점**: common 모듈의 `PipelineRunner`는 `StepExecutor`라는 인터페이스만 알면 된다. 그 안에 들어있는 게 DMZ Loader인지, Internal Loader인지, RCV인지 **신경 쓸 필요가 없다**. 규칙만 지키면 어떤 Step이든 파이프라인에 끼워넣을 수 있다.

---

## 인터페이스란?

Java에서 인터페이스는 **"이 메서드들을 반드시 가져야 한다"는 계약서**다. 클래스가 인터페이스를 `implements`하면, 그 계약에 명시된 메서드를 전부 구현해야 한다.

```java
// 계약서 (interface)
public interface StepExecutor {
    String getStepId();           // Step 식별자를 알려줘야 한다
    StepResult execute(context);  // 실행하면 결과를 돌려줘야 한다
}

// 계약 이행 (implements)
public class DmzBojoLoadStep implements StepExecutor {
    public String getStepId() { return "dmz-bojo-load"; }       // 구현
    public StepResult execute(context) { /* 실제 로직 */ }      // 구현
}
```

**왜 인터페이스를 쓰나?**: PipelineRunner가 Step들을 순서대로 실행할 때, 각 Step이 뭔지 일일이 알 필요 없이 `step.execute(context)`만 호출하면 된다. DMZ Loader든 Internal Loader든 같은 방식으로 실행할 수 있다.

---

## 핵심 인터페이스 상세

### 1. StepExecutor — 파이프라인의 한 단계 [common 인터페이스]

파이프라인에서 실행되는 **모든 Step의 공통 규칙**.

```
규칙:
  getStepId()  → 이 Step의 고유 식별자 (예: "rcv-daejeon", "dmz-bojo-load")
  getStepName() → 표시용 이름 (기본값: stepId와 동일)
  execute(context) → 실행하고 결과(읽기N건, 쓰기M건, 에러메시지) 반환
```

#### 구현체 목록

| 구현 클래스 | 모듈 | 역할 |
|------------|------|------|
| **SourceToIfStep** | common | 소스 DB → IF 테이블로 데이터 복사 (RCV/SND 공통) |
| **DmzBojoLoadStep** | sync-agent-bojo | IF_RSV → Target 테이블로 적재 (DMZ Loader) |
| **InternalBojoLoadStep** | sync-agent-bojo-int | IF_RSV → GIMS Target으로 적재 (Internal Loader) |
| **LinkTableUpdateStep** | sync-agent-bojo | link_ngwis 테이블 갱신 (최신 날짜/시간 관리) |

#### 실행 흐름에서의 위치

```
파이프라인 (PipelineRunner)
  │
  ├─ Step 1: SourceToIfStep.execute(context)    ← RCV 단계
  │     └→ 외부 DB에서 데이터 읽기 → IF 테이블에 쓰기
  │
  ├─ Step 2: LinkTableUpdateStep.execute(context)  ← Link 갱신
  │     └→ link_ngwis에 최신 동기화 시점 기록
  │
  └─ Step 3: DmzBojoLoadStep.execute(context)   ← Loader 단계
        └→ IF 테이블에서 읽기 → Target 테이블에 쓰기
```

PipelineRunner [common]는 이 Step들을 **순서대로** 실행한다. 하나가 실패하면 멈춘다.

---

### 2. StepFactory — StepExecutor를 만드는 공장 [common 인터페이스]

StepFactory의 `create(config)` 메서드가 반환하는 것이 바로 위에서 설명한 **StepExecutor 구현체**다. 즉, Factory와 Step은 1:1로 짝지어진다:

```
StepFactory.create(config) → StepExecutor 구현체를 만들어 반환

SourceToIfStepFactory.create()       → new SourceToIfStep(설정값들)
DmzBojoLoadStepFactory.create()      → new DmzBojoLoadStep(설정값들)
LinkUpdateStepFactory.create()       → new LinkTableUpdateStep(설정값들)
InternalBojoLoadStepFactory.create() → new InternalBojoLoadStep(설정값들)
```

Factory가 필요한 이유: 같은 SourceToIfStep이라도 daejeon용과 bytek용은 소스 테이블이 다르다. YAML 설정을 읽어서 **적절한 값으로 초기화된 Step 객체**를 만들어주는 것이 Factory의 역할이다.

```
규칙:
  getFactoryKey() → YAML의 factory-key와 매칭되는 키 (예: "source-to-if")
  create(config)  → 설정을 받아서 StepExecutor 구현체를 만들어 반환
```

#### YAML 설정과의 연결

```yaml
# config/agents/daejeon.yml
steps:
  - step-id: rcv-daejeon
    factory-key: source-to-if        ← StepFactory의 getFactoryKey()와 매칭
    source-table: pm_gd970201
    target-table: if_rsv_obsvdata
```

앱 시작 시:
1. YAML 파일 로드 → `factory-key: "source-to-if"` 발견
2. StepFactoryRegistry에서 해당 키의 Factory 찾기 → `SourceToIfStepFactory`
3. Factory가 설정을 읽어 `SourceToIfStep` 객체 생성
4. PipelineRunner에 등록

#### 구현체 목록

| Factory (StepFactory 구현) | factory-key | 만드는 Step (StepExecutor 구현) | 모듈 |
|---------|------------|------------|------|
| **SourceToIfStepFactory** | `source-to-if` | → SourceToIfStep (단순 복사) | common |
| **LinkSourceToIfStepFactory** | `source-to-if-link` | → SourceToIfStep (Link 테이블 기반) | bojo |
| **DmzBojoLoadStepFactory** | `dmz-bojo-load` | → DmzBojoLoadStep | bojo |
| **LinkUpdateStepFactory** | `link-update` | → LinkTableUpdateStep | bojo |
| **InternalBojoLoadStepFactory** | `internal-bojo-load` | → InternalBojoLoadStep | bojo-int |

같은 SourceToIfStep이라도 Factory가 다르면 내부 설정이 달라진다. `SourceToIfStepFactory`는 단순 복사 모드로, `LinkSourceToIfStepFactory`는 Link 테이블 기반 증분 모드로 SourceToIfStep을 만든다.

#### 새 Step 추가 방법

```
1. StepExecutor 구현 클래스 작성 (실제 로직)
2. StepFactory 구현 클래스 작성 (@Component 등록)
   - getFactoryKey()에서 고유 키 반환
3. YAML에 factory-key로 참조
→ 코드 배포 후 YAML만 수정하면 새 파이프라인 구성 가능
```

---

### 3. DataSourceProvider — DB 연결 제공자 [common 인터페이스]

파이프라인 Step이 DB에 접근할 때 사용하는 인터페이스. **"이 datasourceId의 DB 연결을 달라"고 요청**하면 적절한 JdbcTemplate을 반환한다.

```
규칙:
  getJdbcTemplate(datasourceId) → 해당 DB의 JdbcTemplate 반환
  getSourceDatasourceId()       → 현재 실행의 소스 DB ID
  getTargetDatasourceId()       → 현재 실행의 타겟 DB ID
  getDbType(datasourceId)       → "POSTGRESQL" 또는 "MYSQL" (SQL 문법 분기용)
```

#### 구현체와 차이점

```
                    DataSourceProvider (인터페이스)
                           │
          ┌────────────────┼────────────────┐
          │                │                │
   SyncDataSourceService  SyncDataSourceService  ProxyDataSourceService
    (sync-agent-bojo)     (sync-agent-bojo-int)   (proxy-dmz/internal)
          │                │                │
    4단계 Fallback:       4단계 Fallback:     2단계 Fallback:
    ThreadLocal            ThreadLocal        캐시
    → 캐시                 → 캐시              → Orchestrator API
    → Proxy API            → Proxy API        → 기본 DB
    → 기본 DB              → 기본 DB
```

| 구현체 | 모듈 | ThreadLocal | 연결정보 출처 |
|--------|------|-------------|-------------|
| SyncDataSourceService | bojo (DMZ) | 있음 | Proxy 경유 |
| SyncDataSourceService | bojo-int (Internal) | 있음 | Proxy 경유 |
| ProxyDataSourceService | proxy-dmz | 없음 | Orchestrator 직접 |
| ProxyDataSourceService | proxy-internal | 없음 | Orchestrator 직접 |

**Agent에 ThreadLocal이 있는 이유**: 동시에 여러 파이프라인이 실행될 수 있다. 스레드 A는 daejeon DB를, 스레드 B는 bytek DB를 대상으로 작업 중일 때, 각자의 DB 연결 정보가 섞이지 않아야 한다.

**Proxy에 ThreadLocal이 없는 이유**: Proxy는 파이프라인을 직접 실행하지 않는다. 연결 정보를 전달만 하므로 스레드별 구분이 필요 없다.

---

### 4. DataFetcher — 맞춤 데이터 조회기 [common 인터페이스]

SourceToIfStep [common]의 기본 동작은 소스 테이블 전체를 복사(SIMPLE_COPY)하는 것이다. 하지만 **특수한 조회 로직**이 필요한 경우가 있다. DataFetcher를 구현하면 SourceToIfStep의 데이터 조회 부분만 교체할 수 있다.

```
규칙 (FunctionalInterface — 메서드 하나):
  fetch(context) → 데이터 목록(List<Map>) 반환
```

| 구현체 | 모듈 | 역할 |
|--------|------|------|
| **LinkTableObsvDataFetcher** | bojo | link_ngwis 기반 증분 동기화 — 관측소별 마지막 동기화 시점 이후 데이터만 조회 |

```
SIMPLE_COPY (기본):
  SELECT * FROM pm_gd970201 WHERE link_status = 'PENDING'
  → 전체 미처리 건 조회

CUSTOM_STAGING + LinkTableObsvDataFetcher:
  link_ngwis에서 관측소별 마지막 날짜/시간 확인
  → 각 관측소마다 그 이후 데이터만 조회
  → 증분 동기화 (이미 가져온 건 다시 안 가져옴)
```

---

### 5. StepProgressCallback — 진행 상황 알림 [common 인터페이스]

파이프라인이 실행되는 동안 "지금 몇 단계째인지" Orchestrator에 알려주는 인터페이스.

```
규칙:
  onStepStarted(executionId, stepId, stepName, 순서, 전체수)  → Step 시작 알림
  onStepFinished(executionId, result, 순서, 전체수)           → Step 완료 알림
```

| 구현체 | 모듈 | 동작 |
|--------|------|------|
| **OrchestratorClient** | common | Orchestrator에 HTTP 콜백 (실제로는 완료 시 일괄 전송) |
| **CompositeStepCallback** | bojo, bojo-int | OrchestratorClient를 감싸서 예외 무시 (콜백 실패가 파이프라인을 멈추지 않도록) |

CompositeStepCallback [bojo, bojo-int]이 감싸는 이유: 콜백 전송에 실패해도 (네트워크 문제 등) 파이프라인 자체는 계속 실행되어야 한다. 데이터 동기화가 진행 알림 실패 때문에 멈추면 안 된다.

---

### 6. StepDefinitionProvider — Step 메타데이터 조회 [common 인터페이스]

UI에서 "이 Agent는 어떤 Step들로 구성되어 있는지" 보여줄 때 사용하는 인터페이스.

```
규칙:
  getStepDefinitions(agentCode) → 해당 Agent의 Step 목록 (stepId, stepName, 순서 등)
  getRegisteredAgentCodes()     → 등록된 전체 Agent 코드 목록
```

| 구현체 | 모듈 | 역할 |
|--------|------|------|
| **PipelineRegistry** | bojo | DMZ Agent 12개의 Step 구성 정보 제공 |
| **PipelineRegistry** | bojo-int | Internal Agent의 Step 구성 정보 제공 |

PipelineRegistry는 `(agentCode, modeId)` 복합키로 파이프라인을 관리한다. 같은 Agent라도 실행 모드에 따라 다른 Step 조합을 쓸 수 있다.

---

## YAML → 런타임 파이프라인 조립 과정

인터페이스들이 실제로 협력하는 과정을 보면 전체 구조가 보인다:

```
앱 시작 시:

1. AgentConfigLoader  [bojo, bojo-int 각각 자체 구현]
   └→ config/agents/*.yml 파일들 읽기
   └→ daejeon.yml, bytek.yml, ... → AgentDefinition 객체들로 변환

2. PipelineAssembler  [bojo, bojo-int 각각 자체 구현]
   └→ 각 AgentDefinition의 steps 순회
   └→ step의 factory-key로 StepFactoryRegistry에서 Factory 찾기
   └→ Factory.create(config) → StepExecutor 생성
   └→ StepExecutor들을 PipelineRunner에 묶기

   ※ StepFactoryRegistry  [common 모듈]
     Spring이 @Component로 등록된 모든 StepFactory 빈을 자동 수집
     factory-key → Factory 매핑을 관리하는 서비스 로케이터

3. PipelineRegistry  [bojo, bojo-int 각각 자체 구현, StepDefinitionProvider 인터페이스 구현]
   └→ (agentCode, modeId) → PipelineRunner 매핑 저장
   └→ 실행 요청 시 agentCode로 PipelineRunner 찾아서 실행

실행 시:

4. 실행 요청 도착 (HTTP)
   └→ PipelineRegistry에서 agentCode로 PipelineRunner 조회
   └→ PipelineRunner.run(context)  [common 모듈 — 모든 Agent 공통]
       └→ Step 1: step.execute(context) → StepResult
       └→ Step 2: step.execute(context) → StepResult
       └→ ...
       └→ 전체 결과 취합 → PipelineResult
   └→ OrchestratorClient로 결과 콜백  [common 모듈]
```

| 클래스 | 소속 모듈 | 역할 |
|--------|----------|------|
| AgentConfigLoader | bojo, bojo-int (각자 구현) | YAML 파일 로드 → AgentDefinition 변환 |
| PipelineAssembler | bojo, bojo-int (각자 구현) | AgentDefinition → StepExecutor 조립 → PipelineRunner 구성 |
| StepFactoryRegistry | common (공통) | factory-key → StepFactory 매핑 관리 |
| PipelineRunner | common (공통) | Step들을 순서대로 실행하는 실행기 |
| PipelineRegistry | bojo, bojo-int (각자 구현) | agentCode → PipelineRunner 라우팅 |
| OrchestratorClient | common (공통) | 실행 결과를 Orchestrator에 콜백 |

```
┌─────────────────────────────────────────────────────────┐
│                    앱 시작 시 조립                        │
│                                                         │
│  YAML 파일 → AgentConfigLoader → PipelineAssembler      │
│                                       │                  │
│                          StepFactoryRegistry             │
│                         ┌──────┼──────┐                  │
│                   Factory_A  Factory_B  Factory_C        │
│                      │         │          │              │
│                   Step_A    Step_B     Step_C            │
│                      └────┬────┘──────┘                  │
│                      PipelineRunner                      │
│                           │                              │
│                    PipelineRegistry                      │
│                  (agentCode → Runner)                    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                     실행 시                               │
│                                                         │
│  HTTP 요청 (agentCode: "daejeon")                       │
│       │                                                  │
│  PipelineRegistry.get("daejeon") → PipelineRunner       │
│       │                                                  │
│  Runner.run(context)                                     │
│       ├→ StepExecutor_1.execute(context) → 결과1        │
│       ├→ StepExecutor_2.execute(context) → 결과2        │
│       └→ StepExecutor_3.execute(context) → 결과3        │
│       │                                                  │
│  PipelineResult (전체 취합)                              │
│       │                                                  │
│  OrchestratorClient.notifyFinished()                    │
└─────────────────────────────────────────────────────────┘
```

---

## 인터페이스 전체 관계도

```
sync-agent-common (규칙)          sync-agent-bojo (DMZ 구현)         sync-agent-bojo-int (INT 구현)
─────────────────────────         ──────────────────────────         ─────────────────────────────

StepExecutor ◄─────────────────── DmzBojoLoadStep                   InternalBojoLoadStep
  │                                LinkTableUpdateStep
  └── SourceToIfStep (common 자체 구현)

StepFactory ◄──────────────────── DmzBojoLoadStepFactory             InternalBojoLoadStepFactory
  │                                LinkSourceToIfStepFactory
  └── SourceToIfStepFactory        LinkUpdateStepFactory
      (common 자체 구현)

DataSourceProvider ◄──────────── SyncDataSourceService               SyncDataSourceService

                    ◄─── sync-proxy-dmz: ProxyDataSourceService
                    ◄─── sync-proxy-internal: ProxyDataSourceService

DataFetcher ◄────────────────── LinkTableObsvDataFetcher

StepProgressCallback ◄───────── CompositeStepCallback                CompositeStepCallback
  └── OrchestratorClient
      (common 자체 구현)

StepDefinitionProvider ◄──────── PipelineRegistry                    PipelineRegistry
```

---

## 확장 포인트 요약

이 구조 덕분에 **새로운 기능을 추가할 때 기존 코드를 수정하지 않아도** 된다:

| 추가 대상 | 해야 할 일 | 건드리지 않는 것 |
|----------|-----------|----------------|
| 새 Step 종류 | StepExecutor 구현 + StepFactory 구현 (@Component) | PipelineRunner, 기존 Step들 |
| 새 Agent (업체) | YAML 파일 추가 | 코드 수정 없음 |
| 새 데이터 조회 방식 | DataFetcher 구현 | SourceToIfStep |
| 새 DB 종류 | DataSourceProvider 구현에서 URL/드라이버 분기 추가 | Step, Factory |
| 새 진행 알림 채널 | StepProgressCallback 구현 | 파이프라인 로직 |

이것이 인터페이스 기반 설계의 핵심이다: **규칙을 지키는 새 구현을 끼워넣으면, 나머지는 알아서 동작한다.**
