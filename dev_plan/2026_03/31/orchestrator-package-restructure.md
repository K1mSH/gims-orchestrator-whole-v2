# Orchestrator Backend 패키지 구조 리팩토링 계획서

## 목적
Orchestrator Backend의 도메인 기반 패키지 구조를 **레이어 기반**으로 변경하여 다른 모듈(bojo, bojo-int, api-collector, common)과 통일

## 현재 구조 (도메인 기반)
```
com.sync.orchestrator/
├── config/                    (2)  SchedulerConfig, WebConfig
├── domain/
│   ├── agent/                 (10) Agent, AgentController, AgentDto, AgentHealthScheduler,
│   │                               AgentRepository, AgentService, AgentStatus, AgentTable, AgentType,
│   │                               DataRetentionScheduler
│   ├── callback/              (3)  CallbackController, CallbackDto, CallbackService
│   ├── datasource/            (9)  Datasource, DatasourceColumn, DatasourceController, DatasourceDto,
│   │                               DatasourceRepository, DatasourceService, DatasourceTable,
│   │                               DatasourceTableRepository, DbType
│   ├── execution/             (8)  ExecutionController, ExecutionDto, ExecutionHistory,
│   │                               ExecutionHistoryRepository, ExecutionService, ExecutionStatus,
│   │                               ExecutionStepHistory, ExecutionStepHistoryRepository
│   ├── schedule/              (6)  Schedule, ScheduleController, ScheduleDto,
│   │                               ScheduleExecutor, ScheduleRepository, ScheduleService
│   └── zone/                  (2)  ZoneConfig, ZoneConfigRepository
└── SyncOrchestratorApplication.java
```

## 변경 후 구조 (레이어 기반)
```
com.sync.orchestrator/
├── config/                    (2)  SchedulerConfig, WebConfig — 변경 없음
├── controller/                (5)  AgentController, CallbackController, DatasourceController,
│                                   ExecutionController, ScheduleController
├── dto/                       (4)  AgentDto, CallbackDto, DatasourceDto, ExecutionDto, ScheduleDto
├── entity/                    (12) Agent, AgentTable, AgentType, AgentStatus,
│                                   Datasource, DatasourceColumn, DatasourceTable, DbType,
│                                   ExecutionHistory, ExecutionStepHistory, ExecutionStatus,
│                                   Schedule, ZoneConfig
├── repository/                (7)  AgentRepository, DatasourceRepository, DatasourceTableRepository,
│                                   ExecutionHistoryRepository, ExecutionStepHistoryRepository,
│                                   ScheduleRepository, ZoneConfigRepository
├── scheduler/                 (3)  AgentHealthScheduler, DataRetentionScheduler, ScheduleExecutor
├── service/                   (5)  AgentService, CallbackService, DatasourceService,
│                                   ExecutionService, ScheduleService
└── SyncOrchestratorApplication.java
```

## 클래스별 이동 매핑 (40개)

| 클래스 | 현재 위치 | 이동 후 | 비고 |
|--------|----------|---------|------|
| **config/** | | | |
| SchedulerConfig | config/ | config/ | 변경 없음 |
| WebConfig | config/ | config/ | 변경 없음 |
| **controller/** | | | |
| AgentController | domain/agent/ | controller/ | import 변경 |
| CallbackController | domain/callback/ | controller/ | import 변경 |
| DatasourceController | domain/datasource/ | controller/ | import 변경 |
| ExecutionController | domain/execution/ | controller/ | import 변경 |
| ScheduleController | domain/schedule/ | controller/ | import 변경 |
| **dto/** | | | |
| AgentDto | domain/agent/ | dto/ | import 변경 |
| CallbackDto | domain/callback/ | dto/ | import 변경 |
| DatasourceDto | domain/datasource/ | dto/ | import 변경 |
| ExecutionDto | domain/execution/ | dto/ | import 변경 |
| ScheduleDto | domain/schedule/ | dto/ | import 변경 |
| **entity/** | | | |
| Agent | domain/agent/ | entity/ | import 변경 |
| AgentStatus | domain/agent/ | entity/ | enum |
| AgentTable | domain/agent/ | entity/ | @Entity |
| AgentType | domain/agent/ | entity/ | enum |
| Datasource | domain/datasource/ | entity/ | import 변경 |
| DatasourceColumn | domain/datasource/ | entity/ | @Embeddable |
| DatasourceTable | domain/datasource/ | entity/ | @Entity |
| DbType | domain/datasource/ | entity/ | enum |
| ExecutionHistory | domain/execution/ | entity/ | import 변경 |
| ExecutionStatus | domain/execution/ | entity/ | enum |
| ExecutionStepHistory | domain/execution/ | entity/ | import 변경 |
| Schedule | domain/schedule/ | entity/ | import 변경 |
| ZoneConfig | domain/zone/ | entity/ | import 변경 |
| **repository/** | | | |
| AgentRepository | domain/agent/ | repository/ | import 변경 |
| DatasourceRepository | domain/datasource/ | repository/ | import 변경 |
| DatasourceTableRepository | domain/datasource/ | repository/ | import 변경 |
| ExecutionHistoryRepository | domain/execution/ | repository/ | import 변경 |
| ExecutionStepHistoryRepository | domain/execution/ | repository/ | import 변경 |
| ScheduleRepository | domain/schedule/ | repository/ | import 변경 |
| ZoneConfigRepository | domain/zone/ | repository/ | import 변경 |
| **scheduler/** | | | |
| AgentHealthScheduler | domain/agent/ | scheduler/ | import 변경 |
| DataRetentionScheduler | domain/agent/ | scheduler/ | import 변경 |
| ScheduleExecutor | domain/schedule/ | scheduler/ | import 변경 |
| **service/** | | | |
| AgentService | domain/agent/ | service/ | import 변경 |
| CallbackService | domain/callback/ | service/ | import 변경 |
| DatasourceService | domain/datasource/ | service/ | import 변경 |
| ExecutionService | domain/execution/ | service/ | import 변경 |
| ScheduleService | domain/schedule/ | service/ | import 변경 |

## 크로스 도메인 의존성 (변경 후에도 정상 동작)

레이어 기반에서는 같은 패키지 내 import가 되므로 오히려 단순해짐:
- `CallbackService` → Agent, AgentRepository, ExecutionHistory 등 → 모두 entity/, repository/
- `ExecutionService` → Agent, Datasource, ZoneConfig 등 → 모두 entity/, repository/
- `ScheduleService` → Agent, AgentRepository → 모두 entity/, repository/
- `DatasourceService` → Agent, ZoneConfig → 모두 entity/, repository/
- `AgentHealthScheduler` → ExecutionHistory 등 → 모두 entity/, repository/

## 작업 순서

1. **entity/ 패키지 생성 + 엔티티/enum 이동** (12개) — 의존성 최하위
2. **repository/ 패키지 생성 + 이동** (7개) — entity만 의존
3. **dto/ 패키지 생성 + 이동** (5개) — entity/enum만 의존
4. **service/ 패키지 생성 + 이동** (5개) — entity, repository, dto 의존
5. **scheduler/ 패키지 생성 + 이동** (3개) — service, entity, repository 의존
6. **controller/ 패키지 생성 + 이동** (5개) — service, dto 의존
7. **domain/ 디렉토리 삭제** (빈 폴더)
8. **빌드 테스트** (`./gradlew clean build -x test`)

## 영향 범위
- sync-orchestrator/backend **만** 수정 (다른 모듈 영향 없음)
- package 선언 + import 문만 변경, 로직 변경 없음
- ComponentScan 범위: `com.sync.orchestrator` 하위 전체 → 영향 없음
