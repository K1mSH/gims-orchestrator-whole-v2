# DB 프록시 전용 Agent 분리 계획

> 작성일: 2026-03-03
> 상태: 검토 대기

---

## 1. 현재 구조 (AS-IS)

### 문제점: 대표 Agent가 2가지 역할을 겸임

```
[DMZ Zone]
  sync-agent-bojo (port 8082)
    ├── 파이프라인 실행 (12개 논리적 Agent: RCV 10 + Loader 1 + SND 1)
    └── DB 프록시 역할 ← ZoneConfig.proxyAgentUrl = "http://localhost:8082"
        ├── /api/execution-data/**  (실행 데이터 조회, Trace)
        ├── /api/datasource/test-connection  (DB 연결 테스트)
        ├── /api/datasource/search-tables    (테이블 스키마 조회)
        └── /api/datasource/search-columns   (컬럼 스키마 조회)

[INTERNAL Zone]
  sync-agent-bojo-int (port 8092)
    ├── 파이프라인 실행 (2개 논리적 Agent: RCV 1 + Loader 1)
    └── DB 프록시 역할 ← ZoneConfig.proxyAgentUrl = "http://localhost:8092"
        └── (동일한 프록시 엔드포인트)
```

**문제:**
- 파이프라인 실행 중 무거운 동기화 작업이 프록시 응답에 영향
- 프록시와 파이프라인의 책임이 혼재 (단일 책임 원칙 위배)
- 프록시 에이전트를 독립적으로 스케일/재시작 불가

---

## 2. 목표 구조 (TO-BE)

```
[DMZ Zone]
  sync-agent-bojo (port 8082)          ← 파이프라인 전용 (변경 없음)
    └── 파이프라인 실행만 담당

  sync-proxy-dmz (port 8083) [신규]    ← DB 프록시 전용
    ├── /api/execution-data/**
    ├── /api/datasource/**
    └── /health

[INTERNAL Zone]
  sync-agent-bojo-int (port 8092)      ← 파이프라인 전용 (변경 없음)
    └── 파이프라인 실행만 담당

  sync-proxy-internal (port 8093) [신규] ← DB 프록시 전용
    ├── /api/execution-data/**
    ├── /api/datasource/**
    └── /health
```

**ZoneConfig 변경:**
| zone | 변경 전 proxyAgentUrl | 변경 후 proxyAgentUrl |
|------|----------------------|----------------------|
| DMZ | http://localhost:8082 | http://localhost:8083 |
| INTERNAL | http://localhost:8092 | http://localhost:8093 |

---

## 3. 프록시 Agent가 필요로 하는 컴포넌트

sync-agent-common에서 이미 제공하는 것:
| 컴포넌트 | 역할 | 위치 |
|----------|------|------|
| `ExecutionDataController` | 실행 데이터 조회/Trace | common |
| `DatasourceController` | 연결 테스트, 스키마 조회 | common |
| `DataSourceProvider` 인터페이스 | JdbcTemplate 동적 제공 | common |
| `Execution`, `SyncLog` 엔티티 | 실행 이력 (JPA) | common |

프록시 Agent에 새로 구현해야 하는 것:
| 컴포넌트 | 역할 |
|----------|------|
| `ProxyApplication.java` | Spring Boot 메인 클래스 |
| `ProxyDataSourceService.java` | `DataSourceProvider` 구현 (파이프라인 없이 프록시 전용) |
| `HealthController.java` | 프록시 전용 헬스체크 |
| `application.yml` | 포트, DB 접속 정보 |

---

## 4. 구현 범위

### Phase 1: DMZ 프록시 Agent 신규 모듈 생성 (`sync-proxy-dmz`)

**4.1 신규 Gradle 모듈 생성**
```
sync-proxy-dmz/
├── build.gradle
├── src/main/java/com/sync/proxy/bojo/
│   ├── ProxyApplication.java
│   ├── config/
│   │   └── ProxyDataSourceService.java    # DataSourceProvider 구현
│   └── controller/
│       └── HealthController.java          # 프록시 전용 헬스체크
└── src/main/resources/
    └── application.yml                    # port: 8083, 로컬 DB 설정
```

- `sync-agent-common` 의존성 추가 → ExecutionDataController, DatasourceController 자동 등록
- 파이프라인 관련 코드 없음 (PipelineRegistry, AgentConfigLoader 등 불필요)
- `ProxyDataSourceService`: Orchestrator에서 credential 받아 동적 DataSource 생성 (기존 `SyncDataSourceService`의 프록시 부분만 추출)

**4.2 기존 Agent에서 프록시 엔드포인트 제거**
- `sync-agent-bojo`의 component-scan에서 `ExecutionDataController`, `DatasourceController` 제외
- 또는 프로파일로 분리 (`@Profile("!proxy-only")`)

> **질문**: 기존 Agent에서 프록시 엔드포인트를 완전히 제거할지, 아니면 남겨두고 ZoneConfig만 변경할지? >> A로 진행함
> - 옵션 A: 완전 제거 → 깔끔하지만 Agent 단독 디버깅 시 불편
> - 옵션 B: 남겨두고 라우팅만 변경 → 안전하지만 중복 존재

### Phase 2: INTERNAL 프록시 Agent 신규 모듈 생성 (`sync-proxy-internal`)

- Phase 1과 동일한 구조
- port: 8093, 로컬 DB: 29002/dev

### Phase 3: Orchestrator 수정

**4.3 ZoneConfig 업데이트**
- `proxyAgentUrl`을 프록시 Agent 주소로 변경
- DDL 스크립트 (`scripts/gims-target-ddl.sql`) 업데이트

**4.4 AgentHealthScheduler 수정 검토**
- 프록시 Agent는 Orchestrator의 `agent` 테이블에 등록하지 않음 (파이프라인 실행 대상이 아니므로)
- ZoneConfig의 proxyAgentUrl 헬스체크는 별도 로직 필요할 수 있음

**4.5 Execution Data 라우팅 확인**
- 현재: executionId → agentCode → agent.endpointUrl → 해당 Agent로 프록시
- 변경 후: executionId → agentCode → agent.zone → ZoneConfig.proxyAgentUrl(프록시) 로 변경?
- **또는**: 실행 데이터 조회는 프록시 Agent가 같은 DB를 바라보므로 ZoneConfig 기반 라우팅으로 통일

> **질문**: 실행 데이터 조회(execution-data) 라우팅을 어떻게 할지? >> 논의 후 A로 확정
> - 옵션 A: 모든 데이터 조회를 ZoneConfig.proxyAgentUrl (프록시)로 라우팅 → 단순 ✅
> - 옵션 B: execution-data는 파이프라인 Agent로, datasource 조회만 프록시로 → 기존과 유사
>
> **A 선택 이유:** 프록시가 같은 zone DB를 바라보므로 데이터 접근 문제 없음.
> 파이프라인 Agent에서 프록시 엔드포인트 완전 제거(질문1 A)와 일관성 유지.
> 실행=파이프라인 Agent, 조회=프록시 Agent로 역할 완전 분리.

---

## 5. 수정 대상 파일 요약

### 신규 파일
| 파일 | 설명 |
|------|------|
| `sync-proxy-dmz/build.gradle` | DMZ 프록시 빌드 설정 |
| `sync-proxy-dmz/.../ProxyApplication.java` | Spring Boot 메인 |
| `sync-proxy-dmz/.../ProxyDataSourceService.java` | DataSourceProvider 구현 |
| `sync-proxy-dmz/.../HealthController.java` | 프록시 헬스체크 |
| `sync-proxy-dmz/.../application.yml` | 설정 |
| `sync-proxy-internal/` (동일 구조) | INTERNAL 프록시 |

### 수정 파일
| 파일 | 변경 |
|------|------|
| `settings.gradle` (루트) | 신규 모듈 include |
| `scripts/gims-target-ddl.sql` | ZoneConfig proxyAgentUrl 변경 |
| `sync-orchestrator/backend/.../execution/ExecutionService.java` | 데이터 조회 라우팅 변경 (결정에 따라) |
| `sync-orchestrator/backend/.../agent/AgentHealthScheduler.java` | 프록시 헬스체크 추가 검토 |

### 제거/변경 검토 파일 (옵션에 따라)
| 파일 | 변경 |
|------|------|
| `sync-agent-bojo`의 component-scan 설정 | 프록시 컨트롤러 제외 (옵션 A 선택 시) |
| `sync-agent-bojo-int`의 component-scan 설정 | 동일 |

---

## 6. 영향 범위

| 영역 | 영향 |
|------|------|
| 파이프라인 실행 | 변경 없음 (Agent → Orchestrator 통신은 그대로) |
| 프론트엔드 | 변경 없음 (Orchestrator API 인터페이스 동일) |
| DB 스키마 | zone_config 테이블 데이터만 UPDATE |
| 배포 | 신규 앱 2개 추가 (프록시 Agent) |
| 포트 | 8083 (DMZ 프록시), 8093 (INTERNAL 프록시) 추가 |

---

## 7. 결정 사항

| # | 질문 | 결정 |
|---|------|------|
| 1 | 기존 Agent의 프록시 엔드포인트 | **A: 완전 제거** |
| 2 | execution-data 라우팅 | **A: 프록시 통합** (논의 후 변경) |
| 3 | 포트 번호 8083/8093 | **미확인 — 답변 필요** |
| 4 | Orchestrator 등록 | **DB_CON_PROXY 타입으로 agent 테이블에 등록** |

---

## 8. 4번 결정(DB_CON_PROXY 등록)에 따른 추가 수정사항

프록시 Agent를 agent 테이블에 등록하면 아래 영역도 함께 수정 필요:

### 8.1 Backend
| 파일 | 변경 |
|------|------|
| `AgentType.java` (enum) | `DB_CON_PROXY` 값 추가 |
| `AgentHealthScheduler.java` | 프록시 Agent 헬스체크 분기 (파이프라인 Agent와 다른 /health 응답 구조) |
| `ExecutionService.java` | execution-data 라우팅: agentCode → zone → 같은 zone의 DB_CON_PROXY agent.endpointUrl 사용 |
| `DatasourceService.java` | proxyAgentUrl 필드 참조 |
| `AgentService.java` | 프록시 Agent는 실행 트리거 대상에서 제외 |

### 8.2 ZoneConfig 변경
- `masterAgentUrl` → `proxyAgentUrl`로 컬럼명 변경 (역할 명확화)
- 값을 프록시 Agent 주소로 변경
- 기존 프록시 라우팅 코드(DatasourceService 등)에서 필드명만 일괄 변경
- agent 테이블의 DB_CON_PROXY는 등록/관리/헬스체크 용도

**수정 대상:**
| 파일 | 변경 |
|------|------|
| `ZoneConfig.java` (엔티티) | `masterAgentUrl` → `proxyAgentUrl` |
| `DatasourceService.java` | 필드 참조명 변경 |
| `ExecutionService.java` | 필드 참조명 변경 |
| `scripts/gims-target-ddl.sql` | 컬럼명 + 값 변경 |

### 8.3 Frontend
| 파일 | 변경 |
|------|------|
| `types/index.ts` | AgentType에 `DB_CON_PROXY` 추가 |
| Agent 목록 페이지 | 프록시 Agent 표시/필터 (실행 버튼 숨김 등) |
| Agent 상세 페이지 | 프록시 Agent는 모니터링/히스토리 탭 불필요, 상태 확인만 |

### 8.4 DDL
| 파일 | 변경 |
|------|------|
| `scripts/gims-target-ddl.sql` | proxy agent INSERT 2건 (DMZ, INTERNAL) |
