# Agent 실행 로직 상세 문서

> 최종 업데이트: 2026-03-06

---

## 1. 전체 아키텍처

```
orchestrator_v2/
├── sync-agent-common/        # 공통 모듈 (JAR 라이브러리)
├── sync-agent-bojo/          # 통합 Agent (DMZ) - 12개 논리적 Agent
├── sync-agent-bojo-int/      # 내부 Agent (내부망) - 1개 논리적 Agent
├── sync-proxy-dmz/           # DB 프록시 (DMZ)
├── sync-proxy-internal/      # DB 프록시 (내부망)
└── sync-orchestrator/
    ├── backend/              # Spring Boot (port 8080)
    └── frontend/             # Next.js (port 3000)
```

### 핵심 설계 원칙

- **논리적 Agent 12개** = RCV 10(업체별) + Loader 1 + SND 1
- **물리적 프로세스 1개** (sync-agent-bojo:8082)에서 `agentCode`로 라우팅
- **파이프라인**: Source(외부) → [RCV] → IF_RSV → [Loader] → Target → [SND] → IF_SND
- **데이터 접근**: 읽기=JPA, 쓰기=JDBCTemplate batch

---

## 2. 모듈별 클래스 역할

### 2.1 sync-agent-common (공통 모듈)

경로: `sync-agent-common/src/main/java/com/sync/agent/common/`

#### Step 관련

| 클래스 | 파일 | 역할 |
|--------|------|------|
| `StepExecutor` | `step/StepExecutor.java` | Step 실행 인터페이스. `getStepId()`, `execute(StepContext)` 정의 |
| `StepContext` | `step/StepContext.java` | Step 실행 컨텍스트. executionId, datasourceId, params, sharedData 포함 |
| `StepResult` | `step/StepResult.java` | Step 실행 결과. status, readCount, writeCount, errorMessage |
| `Status` | `step/Status.java` | Step 상태 enum: `SUCCESS`, `FAILED`, `SKIPPED` |
| `SourceToIfStep` | `step/SourceToIfStep.java` | **핵심 클래스 (913줄)** — Source DB → IF 테이블 추출/적재 공통 구현 |
| `ExtractStepConfig` | `step/ExtractStepConfig.java` | SourceToIfStep 설정 (테이블명, PK, conflictKey 등) |
| `DataFetcher` | `step/DataFetcher.java` | 커스텀 데이터 조회 인터페이스 (CUSTOM_STAGING 모드용) |
| `ExecutionOptions` | `step/ExecutionOptions.java` | 구조화된 실행 옵션 (시간범위, 필터 파라미터) |
| `ExecutionModeDefinition` | `step/ExecutionModeDefinition.java` | 실행 모드 메타데이터 |

#### Pipeline 관련

| 클래스 | 파일 | 역할 |
|--------|------|------|
| `PipelineRunner` | `pipeline/PipelineRunner.java` | **핵심 클래스 (321줄)** — Step 시퀀스 순차 실행 엔진 |
| `PipelineResult` | `pipeline/PipelineResult.java` | 파이프라인 전체 결과 (stepResults, totalDurationMs) |
| `StepProgressCallback` | `pipeline/StepProgressCallback.java` | Step 진행 상황 콜백 인터페이스 |

#### 클라이언트/서비스

| 클래스 | 파일 | 역할 |
|--------|------|------|
| `OrchestratorClient` | `client/OrchestratorClient.java` | StepProgressCallback 구현. 실행 시작/완료를 Orchestrator에 HTTP 콜백 전송 |
| `ExecutionService` | `service/ExecutionService.java` | Agent 로컬 DB에 실행 이력 기록 |
| `ExecutionDataController` | `controller/ExecutionDataController.java` | Agent 내 실행 데이터 REST API. Orchestrator가 프록시로 호출 |

---

### 2.2 sync-agent-bojo (통합 Agent - DMZ)

경로: `sync-agent-bojo/src/main/java/com/sync/agent/bojo/`

#### 설정/라우팅

| 클래스 | 파일 | 역할 |
|--------|------|------|
| `AgentConfigLoader` | `config/AgentConfigLoader.java` | `config/agents/*.yml` 스캔 → `AgentDefinition` 파싱 |
| `AgentDefinition` | `config/AgentDefinition.java` | YAML 설정 POJO |
| `PipelineRegistry` | `config/PipelineRegistry.java` | `Map<agentCode, PipelineRunner>` — agentCode로 라우팅 |
| `RcvPipelineConfig` | `config/RcvPipelineConfig.java` | RCV 파이프라인 빌드 및 등록 (제원/관측/링크 3단계) |
| `LoaderPipelineConfig` | `config/LoaderPipelineConfig.java` | Loader 파이프라인 빌드 및 등록 |
| `SndPipelineConfig` | `config/SndPipelineConfig.java` | SND 파이프라인 빌드 및 등록 |
| `SyncDataSourceService` | `config/SyncDataSourceService.java` | ThreadLocal 기반 DataSource 격리 (동시 실행 분리) |

#### API/실행

| 클래스 | 파일 | 역할 |
|--------|------|------|
| `PipelineController` | `controller/PipelineController.java` | Agent REST API 엔드포인트 (`/api/pipeline/*`) |
| `PipelineService` | `pipeline/PipelineService.java` | `@Async` 비동기 실행, ThreadLocal 설정, 콜백 호출 통합 |

#### RCV Step 구현체

| 클래스 | 파일 | 역할 |
|--------|------|------|
| `LinkTableObsvDataFetcher` | `rcv/fetcher/LinkTableObsvDataFetcher.java` | 증분 동기화 — link_ngwis에서 마지막 시점 이후만 조회 |
| `LinkTableUpdateStep` | `rcv/step/LinkTableUpdateStep.java` | IF에서 obsv_code별 MAX(date,time) → link_ngwis UPSERT |

#### Loader Step 구현체

| 클래스 | 파일 | 역할 |
|--------|------|------|
| `DaejeonLoadStep` | `loader/step/DaejeonLoadStep.java` | IF_RSV PENDING → Target 배치 UPSERT |

---

### 2.3 sync-agent-bojo-int (내부 Agent)

경로: `sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/`

| 클래스 | 파일 | 역할 |
|--------|------|------|
| `InternalLoadStep` | `loader/step/InternalLoadStep.java` | IF_RSV → GIMS Target 적재. 관측데이터 EAV 변환 (1행 → 3행) |

EAV 변환 대상:
- IEM_GWDEP (5): 지하수위
- IEM_GWTEMP (163): 지하수온도
- IEM_EC (52): 전기전도도

---

## 3. 실행 흐름 시퀀스

### 3.1 전체 흐름도

```
┌──────────────────────────────────────────────────┐
│ [1] Orchestrator 웹 UI — 사용자가 실행 버튼 클릭   │
└────────────────────┬─────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────┐
│ [2] ExecutionService.triggerExecution(agentId)    │
│     - Agent 상태 확인 (OFFLINE/RUNNING → 거부)    │
│     - executionId = "{agentCode}_{UUID}" 생성     │
│     - Agent 상태 → RUNNING                       │
│     - HTTP POST → Agent /api/pipeline/execute    │
│       (executionId, agentCode, DB정보, 시간범위)  │
└────────────────────┬─────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────┐
│ [3] PipelineController.execute(request)          │
│     - params 맵 구성                             │
│     - PipelineService.executeAsync() 호출        │
└────────────────────┬─────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────┐
│ [4] PipelineService.executeAsync() [@Async]      │
│     - PipelineRegistry.getRunner(agentCode)      │
│     - SyncDataSourceService.setCurrentDatasources│
│     - ExecutionService.startExecution() → 로컬 DB│
│     - OrchestratorClient.notifyStarted()         │
│       → POST /api/callback/started               │
└────────────────────┬─────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────┐
│ [5] PipelineRunner.run(executionId, params)      │
│     - selectedStepIds 필터링 (선택적 실행)        │
│     - 각 Step 순차 실행:                          │
│       ┌─ onStepStarted 콜백                      │
│       ├─ Step.execute(StepContext) → StepResult   │
│       ├─ onStepFinished 콜백                     │
│       └─ FAILED 시 즉시 중단                     │
└────────────────────┬─────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────┐
│ [6] 실행 완료 처리                                │
│     - ExecutionService.finishExecution(result)    │
│     - OrchestratorClient.notifyFinished(result)  │
│       → POST /api/callback/finished              │
│       → Step 결과 일괄 전송                       │
└────────────────────┬─────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────┐
│ [7] CallbackService.handleFinished()             │
│     - ExecutionHistory 업데이트                   │
│     - ExecutionStepHistory 저장 (Step별)          │
│     - Agent 상태 복원 → ONLINE                   │
└──────────────────────────────────────────────────┘
```

### 3.2 RCV 파이프라인 상세 (dmz-bojo-rcv-daejeon 예시)

```
PipelineRunner.run()
│
├─ Step 1: jewon-extract (제원 추출)
│   SourceToIfStep
│   ├─ Source: sec_jewon_view (fullCopy=true → 전체 복사)
│   ├─ IF: if_rsv_sec_jewon
│   ├─ PK: obsv_code
│   ├─ conflictKey: source_refs
│   └─ 결과: 100건 read/write
│
├─ Step 2: obsvdata-extract (관측데이터 추출)
│   SourceToIfStep + LinkTableObsvDataFetcher
│   ├─ 각 obsv_code별:
│   │   ├─ link_ngwis에서 마지막 동기화 시점 조회
│   │   ├─ 그 이후 데이터만 Source에서 조회 (증분)
│   │   └─ link 없으면 Source의 최소 날짜부터 조회
│   ├─ IF: if_rsv_sec_obsvdata
│   ├─ 배치 UPSERT (1000건씩)
│   └─ 결과: 10,000건 read/write
│
└─ Step 3: link-table-update (링크 테이블 갱신)
    LinkTableUpdateStep
    ├─ IF에서 obsv_code별 MAX(obsv_date, obsv_time)
    ├─ link_ngwis UPSERT
    └─ 결과: 100건 write (다음 증분 기준점 갱신)
```

### 3.3 Loader 파이프라인 상세

```
PipelineRunner.run()
│
└─ Step: loader-step
    DaejeonLoadStep
    ├─ TargetRepositoryService 초기화
    │   ├─ spot_id 매핑 로드 (if_rsv → target)
    │   └─ result_id 매핑 로드
    ├─ IF_RSV에서 PENDING 레코드 읽기
    ├─ Batch UPSERT → Target 테이블
    │   ├─ sec_jewon (제원)
    │   └─ sec_obsvdata (관측데이터)
    └─ 상태 업데이트
        ├─ IF_RSV 레코드 → PROCESSED
        └─ link_ngwis → 갱신
```

---

## 4. SourceToIfStep 핵심 동작 (913줄)

Source DB에서 IF 테이블로 데이터를 추출하는 공통 Step. 모든 RCV Agent가 이 클래스를 사용한다.

### 실행 Phase

```
Phase 0: 컬럼 결정
  ├─ 설정에 명시된 컬럼 사용
  └─ 또는 Source DB 메타데이터에서 자동 감지

Phase 1: Source 데이터 조회
  ├─ SIMPLE_COPY 모드
  │   ├─ fullCopy=true → 전체 조회 (제원 등)
  │   ├─ 기간 지정 → WHERE time > ? AND time <= ?
  │   └─ 기본 → WHERE link_status = 'PENDING'
  └─ CUSTOM_STAGING 모드
      └─ DataFetcher 인터페이스로 N:M 조회 가능

Phase 2: IF 테이블에 배치 UPSERT
  ├─ PostgreSQL: INSERT ... ON CONFLICT(source_refs) DO UPDATE
  └─ MySQL: INSERT ... ON DUPLICATE KEY UPDATE

Phase 3: Source link_status 업데이트 (선택적)
  └─ skipSourceStatusUpdate=true이면 스킵

Phase 4: SyncLog 요약 저장
  └─ readCount, writeCount, 소요시간 기록
```

### IF 테이블 메타 컬럼

```sql
source_refs   JSONB     -- "E:5:15:GPM-3050" (zone:dsId:tbId:pk — 추적키)
link_status   VARCHAR   -- PENDING / RESYNC / SUCCESS / FAILED
extracted_at  TIMESTAMP -- 최초 추출 시간
updated_at    TIMESTAMP -- 마지막 수정 시간
execution_id  VARCHAR   -- 실행 ID (추적용)
```

---

## 5. 데이터 구조

### StepContext

```java
StepContext {
  executionId: "dmz-bojo-rcv-daejeon_abc123"
  pipelineId: "dmz-bojo-rcv-daejeon"
  sourceDatasourceId: "sec-daejeon"
  targetDatasourceId: "orchestrator-if"
  sourceZone: "EXTERNAL"
  sourceZoneShortCode: "E"
  sourceDatasourceDbId: 5
  sourceTableIds: {
    "sec_jewon_view": 15,
    "sec_obsvdata_view": 16
  }
  agentZone: "DMZ"
  params: {
    "startTime": LocalDateTime,
    "endTime": LocalDateTime,
    "selectedStepIds": ["jewon-extract"]
  }
  executionOptions: ExecutionOptions { ... }
  sharedData: {}  // Step 간 데이터 공유용
}
```

### StepResult

```java
StepResult {
  stepId: "jewon-extract"
  status: SUCCESS | FAILED | SKIPPED
  readCount: 100
  writeCount: 100
  skipCount: 0
  durationMs: 5000
  errorMessage: null
  sourceTable: "sec_jewon_view"
  targetTable: "if_rsv_sec_jewon"
}
```

### PipelineResult

```java
PipelineResult {
  executionId: "dmz-bojo-rcv-daejeon_abc123"
  pipelineId: "dmz-bojo-rcv-daejeon"
  status: SUCCESS | FAILED
  stepResults: [StepResult, ...]
  totalDurationMs: 45000
  errorMessage: null
}
```

---

## 6. API 엔드포인트

### Orchestrator (port 8080)

| 메서드 | 경로 | 역할 |
|--------|------|------|
| POST | `/api/executions/{id}/run` | Agent 실행 트리거 |
| GET | `/api/executions/{id}` | 실행 상태 조회 |
| GET | `/api/executions/{executionId}/data/{dataType}` | 실행 데이터 조회 (프록시) |
| POST | `/api/callback/started` | Agent 시작 알림 수신 |
| POST | `/api/callback/finished` | Agent 완료 알림 수신 |

### Agent (port 8082)

| 메서드 | 경로 | 역할 |
|--------|------|------|
| POST | `/api/pipeline/execute` | 파이프라인 실행 (Orchestrator로부터) |
| POST | `/api/pipeline/resync` | 기간 지정 재동기화 |
| GET | `/api/pipeline/status/{executionId}` | 실행 상태 조회 |
| GET | `/api/pipeline/execution/{executionId}` | 실행 결과 상세 |
| GET | `/api/pipeline/{agentCode}/tables` | 파이프라인 테이블 정보 |
| GET | `/api/execution-data/{executionId}/summary` | 실행 요약 (SyncLog) |
| GET | `/api/execution-data/{executionId}/source` | Source 테이블 조회 |
| GET | `/api/execution-data/{executionId}/target-if` | IF 테이블 조회 |
| GET | `/health` | Agent 상태 + agentCode 목록 |

---

## 7. 실행 모드

| 모드 | 설명 | 동작 |
|------|------|------|
| **기본 (증분)** | 마지막 동기화 이후만 | link_ngwis 마지막 시점 기준, 그 이후 데이터만 조회 |
| **기간 지정 (RESYNC)** | 특정 기간 재동기화 | startTime/endTime 범위 조회, link_status=RESYNC 설정 |
| **선택적 Step** | 특정 Step만 실행 | selectedStepIds로 필터링, 나머지는 SKIPPED 처리 |

---

## 8. Agent YAML 설정

위치: `sync-agent-bojo/src/main/resources/config/agents/`

```
dmz-bojo-rcv-daejeon.yml    # 대전 RCV
dmz-bojo-rcv-bytek.yml      # 바이텍 RCV
dmz-bojo-rcv-chungnam.yml   # 충남 RCV
dmz-bojo-rcv-keunsan.yml    # 근산 RCV
dmz-bojo-rcv-infoworld.yml  # 인포월드 RCV
dmz-bojo-rcv-hydronet.yml   # 하이드로넷 RCV
...
dmz-bojo-loader.yml         # Loader
dmz-bojo-snd.yml            # SND
```

`AgentConfigLoader`가 `@PostConstruct` 시점에 모든 YAML 파일을 스캔하고 파싱하여 `AgentDefinition` 목록 생성. 이후 `RcvPipelineConfig`, `LoaderPipelineConfig`, `SndPipelineConfig`가 각각 해당 타입의 정의를 읽어 PipelineRunner를 빌드하고 `PipelineRegistry`에 등록한다.

---

## 9. ThreadLocal DataSource 격리

`SyncDataSourceService`가 요청별로 ThreadLocal에 DataSource 정보를 설정한다.

```
실행 시작
  → SyncDataSourceService.setCurrentDatasources(sourceInfo, targetInfo)
  → ThreadLocal<DataSourceInfo> currentSourceDatasource 설정
  → ThreadLocal<DataSourceInfo> currentTargetDatasource 설정

Step 실행 중
  → 각 Step에서 ThreadLocal을 통해 DataSource 접근
  → 동시 실행되는 다른 Agent와 격리

실행 완료
  → SyncDataSourceService.clearCurrentDatasources()
  → ThreadLocal 정리
```

이를 통해 하나의 물리 프로세스에서 여러 논리 Agent가 각각 다른 DataSource를 사용하면서도 안전하게 동시 실행될 수 있다.
