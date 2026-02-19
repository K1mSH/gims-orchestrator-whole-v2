# GIMS Orchestrator 개발 방향 문서

> 이 문서는 프로젝트의 설계 원칙과 구현 방법을 정리한 공유 문서입니다.
> 작업 시작 전 이 문서를 참조하여 일관된 개발 방향을 유지합니다.

---

## 목차

1. [Agent 관리](#1-agent-관리)
2. [API 관리](#2-api-관리)
3. [프로시저 관리](#3-프로시저-관리)

---

# 1. Agent 관리

## 1.1 개요

### 1.1.1 목적

**사용자에게 제공하는 가치:**

| 가치 | 설명 |
|------|------|
| 자동화된 데이터 연계 | 외부 시스템 데이터를 수동 작업 없이 자동으로 수집 |
| 외부 전송 준비 | 내부 데이터를 IF_SND에 준비하여 외부 전송 대기 상태로 관리 |
| 데이터 추적성 | 각 row가 어디서 왔고, 언제 처리되었고, 현재 어느 단계인지 추적 가능 |
| 장애 대응 용이 | 중간 테이블(IF)에서 문제 데이터 확인 및 재처리 가능 |
| 유연한 재동기화 | 특정 기간 데이터만 선택적으로 다시 동기화 가능 |
| 중앙 집중 관리 | 여러 Agent를 웹 UI 한 곳에서 실행/모니터링 |

### 1.1.2 전체 데이터 흐름

```
[외부 시스템 ENT1]                    [IF 테이블]                    [내부 시스템]
     Source DB          →          PostgreSQL IF           →         Target DB
  (sec_jewon 등)       RCV        (if_rsv_sec_jewon)      Loader    (sec_jewon)
                                                                         ↓
                                  (if_snd_sec_jewon)       ←          SND
```

**흐름 요약:**
```
1. RCV:     Source(외부) → IF_RSV(중간)
2. Loader:  IF_RSV(중간) → Target(내부)
3. SND:     Target(내부) → IF_SND(중간, 전송 대기)
```

---

## 1.2 모듈 구성

> **v2 통합 아키텍처 (2026-02-12~)**
>
> v1에서는 RSV Relay, Loader, SND Relay가 각각 별도의 Spring Boot 앱이었으나,
> v2에서는 **`sync-agent-bojo` 하나의 물리적 앱**에 **12개 논리적 Agent**(RCV 10 + Loader 1 + SND 1)를 통합.
> - 명칭 변경: RSV Relay → **RCV** (Receive), SND Relay → **SND**
> - `PipelineRegistry`: agentId → PipelineRunner 라우팅으로 논리적 Agent 구분
> - `config/agents/*.yml`: 파일 기반 Agent 설정 (Agent 추가/제거 = 파일 추가/제거)
> - Orchestrator에는 12개 Agent 레코드가 모두 같은 `endpointUrl`을 가리킴
> - 실행 요청 시 `agentId` 필드로 어떤 파이프라인을 실행할지 라우팅

### 1.2.0 통합 Agent (`sync-agent-bojo`)

**역할:** RCV, Loader, SND 3종 파이프라인을 하나의 Spring Boot 앱에서 관리

**핵심 아키텍처:**

| 컴포넌트 | 역할 |
|----------|------|
| `PipelineRegistry` | agentId → PipelineRunner 매핑, 논리적 Agent 라우팅 |
| `AgentConfigLoader` | `config/agents/*.yml` 스캔 → AgentDefinition 파싱 |
| `RcvPipelineConfig` | RCV 파이프라인 등록 (10개) |
| `LoaderPipelineConfig` | Loader 파이프라인 등록 (1개) |
| `SndPipelineConfig` | SND 파이프라인 등록 (1개) |
| `PipelineService` | 실행 요청 수신 → agentId로 라우팅 → 파이프라인 실행 |
| `SyncDataSourceService` | ThreadLocal 기반 DataSource 격리 (RCV/SND JDBC + Loader JPA 공존) |

**Agent YAML 설정 (파일 기반):**
```
sync-agent-bojo/src/main/resources/config/agents/
├── rcv-bojo-01.yml ~ rcv-bojo-10.yml   # RCV 10개
├── loader-bojo.yml                      # Loader 1개
└── snd-bojo.yml                         # SND 1개
```

**YAML 예시 (RCV):**
```yaml
agent-id: rcv-bojo-01
type: RCV
jewon:
  source-table: sec_jewon_view
  target-table: if_rsv_sec_jewon
  primary-key: obsv_code
  full-copy: true
  skip-source-status-update: true
obsvdata:
  source-table: sec_obsvdata_view
  target-table: if_rsv_sec_obsvdata
  primary-key: obsv_code,obsv_date,obsv_time
  date-column: obsv_date
  time-column: obsv_time
link:
  use-link-table: true
  table-name: link_ngwis
```

**OrchestratorClient 동적 생성:**
- v1: 앱별 싱글톤 (`@PostConstruct`에서 1회 생성)
- v2: 실행마다 해당 agentId로 새 인스턴스 생성 (OrchestratorClient는 stateless)

**프로젝트 구조:**
```
sync-agent-bojo/
├── src/main/java/com/sync/agent/bojo/
│   ├── BojoAgentApplication.java
│   ├── config/
│   │   ├── PipelineRegistry.java           # agentId → Pipeline 라우팅
│   │   ├── AgentDefinition.java            # YAML 파싱 결과 POJO
│   │   ├── AgentConfigLoader.java          # config/agents/*.yml 스캔
│   │   ├── RcvPipelineConfig.java          # RCV 파이프라인 등록
│   │   ├── SndPipelineConfig.java          # SND 파이프라인 등록
│   │   ├── LoaderPipelineConfig.java       # Loader 파이프라인 등록
│   │   ├── AsyncConfig.java               # 비동기 스레드 풀
│   │   ├── SyncDataSourceService.java      # RSV+Loader 통합 DataSource
│   │   ├── DynamicEntityManagerService.java # Loader JPA EntityManager
│   │   └── CaseAwareNamingStrategy.java    # JPA 네이밍
│   ├── controller/
│   │   ├── PipelineController.java         # 통합 API (agentId 라우팅)
│   │   └── HealthController.java           # 모든 Agent 상태 표시
│   ├── pipeline/
│   │   ├── PipelineService.java            # 통합 서비스
│   │   └── CompositeStepCallback.java
│   ├── rcv/
│   │   ├── fetcher/LinkTableObsvDataFetcher.java
│   │   └── step/LinkTableUpdateStep.java
│   ├── loader/
│   │   ├── step/DaejeonLoadStep.java
│   │   └── repository/TargetRepositoryService.java
│   ├── dto/
│   │   ├── JewonDto.java, LinkDto.java, ObsvDataDto.java
│   └── entity/
│       ├── source/ (SecJewonView, SecObsvdataView, ...)
│       ├── iftable/rsv/ (IfRsvSecJewon, IfRsvSecObsvdata)
│       ├── iftable/snd/ (IfSndSecJewon, IfSndSecObsvdata)
│       ├── target/ (SecJewon, SecObsvdata, LinkNgwis)
│       └── local/DataSourceConfig.java
└── src/main/resources/
    ├── application.yml
    └── config/agents/ (12개 YAML)
```

---

### 1.2.1 RCV 파이프라인

**역할:** 외부 Source DB에서 데이터를 추출하여 IF_RSV 테이블에 적재

**제공 기능:**
| 기능 | 사용자 가치 |
|------|-------------|
| 자동 데이터 수집 | 외부 시스템 데이터를 수동 작업 없이 자동 수집 |
| 증분 동기화 | 이미 수집한 데이터는 건너뛰어 중복 없이 효율적 수집 |
| 기간 지정 재수집 | 특정 기간 데이터에 문제가 있을 때 해당 기간만 다시 수집 가능 |
| 출처 정보 기록 | 각 row가 어느 시스템의 어떤 테이블에서 왔는지 추적 가능 (`source_refs`) |
| 10개 독립 Agent | 외부 시스템별로 독립적인 RCV Agent 운용 (rcv-bojo-01 ~ 10) |

**구현 방법:**
- `SourceToIfExtractStep`: Source → IF_RSV 적재
  - jewon: UPSERT (`fullCopy=true`, `ON CONFLICT DO UPDATE`)
  - obsvdata: INSERT (`ON CONFLICT DO NOTHING`)
- `LinkTableObsvDataFetcher`: link_ngwis 기반 증분 조회 또는 기간 지정 조회
- `LinkTableUpdateStep`: 동기화 완료 후 link_ngwis 업데이트 (PG: `ON CONFLICT`, MySQL: `ON DUPLICATE KEY UPDATE`)
- `skipSourceStatusUpdate=true`: 외부 DB(VIEW)라 Source 상태 업데이트 안함
- **Multi-DB 지원**: PostgreSQL, MySQL/MariaDB 모두 동작 (SQL 방언 자동 분기)

**핵심 파일:**
```
sync-agent-bojo/src/main/java/com/sync/agent/bojo/
├── rcv/fetcher/LinkTableObsvDataFetcher.java  # 데이터 조회 (증분/기간지정)
├── rcv/step/LinkTableUpdateStep.java          # link_ngwis 업데이트
└── config/RcvPipelineConfig.java              # RCV 파이프라인 등록 (YAML → PipelineRunner)
```

---

### 1.2.2 Loader 파이프라인

**역할:** IF_RSV 테이블에서 데이터를 읽어 Target DB에 적재

**제공 기능:**
| 기능 | 사용자 가치 |
|------|-------------|
| 자동 적재 | 수집된 데이터를 내부 시스템에 자동 반영 |
| 처리 상태 추적 | 각 데이터의 적재 성공/실패 여부 확인 가능 |
| 실행 이력 추적 | 어떤 실행에서 이 데이터가 적재되었는지 확인 가능 (`execution_id`) |
| 기간 지정 재적재 | 특정 기간 데이터를 강제로 다시 적재 가능 |
| 출처 정보 유지 | RCV에서 기록한 원본 출처 정보를 Target까지 전달 |

**구현 방법:**
- `DaejeonLoadStep`: IF_RSV → Target 적재 로직 (JPA 기반)
- `TargetRepositoryService.convertToSecJewon/Obsvdata()`: 엔티티 변환 시 상태 설정
- `IfTableService.markAsProcessed()`: IF_RSV 상태 업데이트
- `DynamicEntityManagerService`: 동적 JPA EntityManager 생성

**핵심 파일:**
```
sync-agent-bojo/src/main/java/com/sync/agent/bojo/
├── loader/step/DaejeonLoadStep.java               # 메인 적재 로직 (JPA)
├── loader/repository/TargetRepositoryService.java  # Target DB 접근
└── config/LoaderPipelineConfig.java                # Loader 파이프라인 등록
```

---

### 1.2.3 SND 파이프라인

**역할:** Target DB의 데이터를 IF_SND 테이블로 복사 (외부 전송 준비)

**제공 기능:**
| 기능 | 사용자 가치 |
|------|-------------|
| 외부 전송 준비 | 내부에서 처리된 데이터를 IF_SND에 준비 (외부 전송 대기) |
| 내부 처리 완료 표시 | Target 데이터를 `SYNCED`로 표시하여 처리 완료 관리 |
| 전송 대기 상태 추적 | 어떤 데이터가 IF_SND에서 대기 중인지 확인 가능 |
| 중복 처리 방지 | 이미 IF_SND로 복사된 데이터는 `SYNCED` 상태로 표시하여 재처리 방지 |

**구현 방법:**
- `SourceToIfExtractStep` (공통 모듈): Target → IF_SND 적재
  - jewon: UPSERT (`fullCopy=true`, `ON CONFLICT DO UPDATE`)
  - obsvdata: INSERT (`ON CONFLICT DO NOTHING`)
- Target 테이블의 `link_status` 기반 PENDING 데이터 조회
- Source(Target DB) 처리 완료 후 `link_status=SUCCESS`로 업데이트

**핵심 파일:**
```
sync-agent-bojo/src/main/java/com/sync/agent/bojo/
└── config/SndPipelineConfig.java  # SND 파이프라인 등록
```

---

### 1.2.4 Orchestrator (`sync-orchestrator`)

**역할:** Agent 실행 관리, 모니터링, 대시보드 제공

**제공 기능:**
| 기능 | 사용자 가치 |
|------|-------------|
| 중앙 집중 관리 | 여러 Agent를 한 곳에서 등록/관리 |
| 실행 트리거 | 웹 UI에서 클릭 한 번으로 동기화 실행 |
| 실시간 모니터링 | 실행 중인 작업의 진행 상황 확인 |
| 실행 이력 조회 | 과거 실행 기록과 성공/실패 통계 확인 |
| 데이터 추적 (Trace) | 특정 데이터가 어디서 왔고 어디까지 처리되었는지 추적 (단일/복합 PK 지원) |
| 기간 지정 실행 | UI에서 날짜 범위를 지정하여 재동기화 실행 |
| **스케줄러** | Agent별 자동 실행 주기를 cron 표현식으로 커스텀 설정 |
| **소스 테이블 자동 등록** | 실행 트리거 시 Agent API로 테이블 정보 조회 → `datasource_table`, `agent_table` 자동 생성 |

**스케줄러 기능 상세:**
| 기능 | 설명 |
|------|------|
| Cron 표현식 설정 | Agent별로 실행 주기를 자유롭게 설정 (예: `0 0 * * * *` = 매시 정각) |
| 스케줄 활성화/비활성화 | 스케줄을 삭제하지 않고 일시 중지 가능 |
| Agent별 개별 스케줄 | 각 Agent마다 다른 주기로 설정 가능 |
| UI에서 관리 | 웹 대시보드에서 스케줄 생성/수정/삭제 |

**테이블/컬럼 설정 기능:**
| 기능 | 설명 |
|------|------|
| Source/Target Datasource 설정 | Agent가 연결할 DB 지정 |
| 테이블 선택 | 동기화 대상 테이블 선택/해제 |
| 컬럼 목록 확인 | 각 테이블의 컬럼 구조 확인 |
| 테이블 구조 비교 | Source와 Target의 컬럼 매핑 검토 |

**구성:**
```
sync-orchestrator/
├── backend/   # Spring Boot (포트 8080)
└── frontend/  # Next.js
```

---

### 1.2.5 공통 모듈 (`sync-agent-common`)

**역할:** 모든 Agent가 공유하는 공통 로직

**핵심 컴포넌트:**
- `SourceToIfStep`: Source → IF 추출 공통 로직 (Multi-DB 지원: `isMysql()`/`qi()` 방언 분기)
- `ExtractStepConfig`: Step 설정 (아래 옵션 참조)
- `IfTableService`: IF 테이블 상태 관리
- `IfTableUtils`: IF 테이블 유틸리티
- `ExecutionDataController`: 실행 데이터 조회 API (Tracing, Multi-DB 지원)
  - PK 자동 감지: JDBC `DatabaseMetaData.getPrimaryKeys()` (단일/복합 PK)
  - 유니크 키 감지: JDBC `getIndexInfo()` (SND Business Key fallback)
  - SQL 방언 분기: 식별자 인용(`"` vs `` ` ``), 검색(`ILIKE` vs `LIKE`), 스키마(`public` vs `DATABASE()`)
  - `detectDbType()`: JDBC URL에서 DB 종류 자동 감지

**ExtractStepConfig 주요 옵션:**
| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `fullCopy` | false | true: 전체 복사 + UPSERT (jewon용) |
| `skipSourceStatusUpdate` | false | true: Source link_status 업데이트 안함 (RCV 외부 DB용) |
| `extractType` | SIMPLE_COPY | CUSTOM_STAGING: 커스텀 DataFetcher 사용 |

**Agent별 설정:**
| Agent | 테이블 | fullCopy | skipSourceStatusUpdate |
|-------|--------|----------|------------------------|
| RCV | jewon | true | true (외부 VIEW) |
| RCV | obsvdata | false | - (CUSTOM_STAGING) |
| SND | jewon | true | false (내부 TABLE) |
| SND | obsvdata | false | false |

**JAR 배포:**
```
빌드: cd sync-agent-common && ./gradlew clean build -x test
배포 위치:
  - sync-agent-bojo/libs/    # 통합 Agent (유일한 배포 대상)
```

---

## 1.3 테이블 설계 원칙

### 1.3.1 IF 테이블 (if_rsv_*, if_snd_*)

**목적:** Source와 Target 사이의 중간 버퍼 역할

**왜 중간 테이블이 필요한가:**
- 외부 시스템 장애 시에도 이미 수집한 데이터는 보존됨
- 데이터 검증/변환 후 Target에 적재 가능
- 문제 발생 시 IF 테이블에서 데이터 확인 후 재처리 가능
- 외부 시스템과 내부 시스템의 스키마 차이 흡수

**공통 메타 컬럼:**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| `execution_id` | VARCHAR | 데이터를 **쓴** Agent의 실행 ID |
| `link_status` | VARCHAR | 연계 상태 (PENDING → SUCCESS/FAILED) |
| `source_refs` | JSON | Source 추적 정보 `{"ZONE":["ds:table:pk"]}` |
| `extracted_at` | TIMESTAMP | 최초 추출 시각 |
| `updated_at` | TIMESTAMP | 마지막 수정 시각 |

**상태 전이:**
```
IF_RSV: PENDING (RCV 적재) → SUCCESS/FAILED (Loader 처리 후)
IF_SND: PENDING (SND 적재) → SUCCESS/FAILED (후속 처리 후)
```

---

### 1.3.2 Target 테이블 (sec_jewon, sec_obsvdata)

**목적:** 내부 시스템의 실제 데이터 저장소

**왜 메타 컬럼이 필요한가:**
- 외부로 전송해야 할 데이터(PENDING)와 이미 전송한 데이터(SYNCED) 구분
- 데이터가 어떤 실행에서 적재되었는지 이력 관리
- 문제 발생 시 원본 출처까지 역추적 가능

**메타 컬럼:**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| `execution_id` | VARCHAR | Loader 실행 ID |
| `link_status` | VARCHAR | SND 처리 상태 (PENDING → SYNCED) |
| `source_refs` | JSON | IF_RSV로부터 복사된 Source 추적 정보 |

**상태 전이:**
```
Target: PENDING (Loader 적재) → SYNCED (SND 처리 후)
```

---

### 1.3.3 Link 테이블 (link_ngwis)

**목적:** RCV의 증분 동기화 시점 추적

**왜 Link 테이블이 필요한가:**
- 관측소(obsv_code)별로 "어디까지 가져왔는지" 기록
- 다음 실행 시 이미 가져온 데이터는 건너뛰어 효율적 동기화
- 기간 지정 실행 시에는 이 테이블을 무시하고 전체 재수집 가능

**컬럼:**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| `obsv_code` | VARCHAR | PK, 관측소 코드 |
| `obsv_date` | DATE | 마지막 동기화 날짜 |
| `obsv_time` | VARCHAR | 마지막 동기화 시간 (HHMMSS) |
| `update_time` | TIMESTAMP | 업데이트 시각 |

**사용 방식:**
- RCV가 각 obsv_code별로 마지막 동기화 시점 조회
- 해당 시점 이후 데이터만 Source에서 추출
- 동기화 완료 후 link_ngwis 업데이트 (**더 최신 데이터일 때만**)

**업데이트 조건 (더 최신 데이터일 때만):**

PostgreSQL:
```sql
INSERT INTO link_ngwis (...) VALUES (...)
ON CONFLICT (obsv_code) DO UPDATE SET ...
WHERE link_ngwis.obsv_date < EXCLUDED.obsv_date
   OR (link_ngwis.obsv_date = EXCLUDED.obsv_date
       AND link_ngwis.obsv_time < EXCLUDED.obsv_time)
```

MySQL:
```sql
INSERT INTO link_ngwis (...) VALUES (...)
ON DUPLICATE KEY UPDATE
  obsv_date = IF(obsv_date IS NULL OR obsv_date < VALUES(obsv_date)
    OR (obsv_date = VALUES(obsv_date) AND obsv_time < VALUES(obsv_time)),
    VALUES(obsv_date), obsv_date), ...
```

---

### 1.3.4 이력 테이블 (sync_record_history)

**목적:** UPSERT 시 덮어씌워지는 execution_id/source_refs의 이전 이력을 레코드 단위로 보존

**왜 이력 테이블이 필요한가:**
- UPSERT 시 `execution_id`와 `source_refs`가 최신 값으로 덮어씌워져 이전 실행 이력이 소실됨
- 특정 레코드가 "이전에 어떤 실행들에서 처리되었는가?" 추적 불가 문제 해결
- 각 Agent의 자체 DB에 저장 (기존 SyncLog, StepLog 패턴과 동일)

**컬럼:**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK, AUTO) | |
| `execution_id` | VARCHAR(100) | 이 작업을 수행한 실행 ID |
| `step_id` | VARCHAR(50) | Step ID |
| `table_name` | VARCHAR(100) | 대상 테이블 (if_rsv_sec_jewon, sec_jewon 등) |
| `record_key` | VARCHAR(500) | 레코드 비즈니스 키 (obsv_code 또는 복합키 `obsv_code\|date\|time`) |
| `action` | VARCHAR(20) | INSERT, UPDATE, UPSERT |
| `source_refs` | TEXT | 이 시점의 source_refs 값 |
| `processed_at` | TIMESTAMP | 처리 시각 |

**인덱스:**
- `idx_srh_execution` → `execution_id` (특정 실행이 처리한 레코드 조회)
- `idx_srh_record` → `table_name, record_key` (특정 레코드의 이력 조회)

**사용 시나리오:**
```sql
-- 특정 레코드의 전체 처리 이력 조회
SELECT * FROM sync_record_history
WHERE table_name = 'if_rsv_sec_jewon' AND record_key = 'GPM-3050-001'
ORDER BY processed_at;

-- 특정 실행에서 처리된 레코드 목록 조회
SELECT * FROM sync_record_history
WHERE execution_id = 'exec-xxx-yyy';
```

**저장 위치:** 각 Agent의 자체 DB (ddl-auto: update로 자동 생성)

---

## 1.4 컬럼 설계 원칙

### 1.4.1 execution_id - 실행 이력 추적

**목적:** "이 데이터가 언제, 어떤 실행에서 처리되었는가?"를 추적

**사용자 가치:**
- 문제 발생 시 해당 데이터가 처리된 실행을 특정할 수 있음
- 특정 실행에서 처리된 데이터만 필터링하여 조회 가능
- 실행 단위로 롤백/재처리 판단 가능

**설정 규칙:** 각 Agent는 자신이 데이터를 "쓰는" 테이블에만 execution_id 설정

| Agent | 읽는 테이블 | 쓰는 테이블 | execution_id 설정 |
|-------|-------------|-------------|-------------------|
| RCV | Source | IF_RSV | IF_RSV에 설정 |
| Loader | IF_RSV | Target | Target에 설정 (IF_RSV는 변경 안 함) |
| SND | Target | IF_SND | IF_SND에 설정 |

**왜 이렇게 하는가:**
- 데이터를 "넣은" 주체가 명확해야 추적이 가능
- 읽는 쪽에서 덮어쓰면 "누가 원래 넣었는지" 정보 손실

---

### 1.4.2 link_status - 처리 상태 추적

**목적:** "이 데이터가 다음 단계로 넘어갔는가?"를 추적

**사용자 가치:**
- 데이터 파이프라인의 어디서 막혔는지 한눈에 파악
- PENDING이 쌓이면 다음 단계 Agent에 문제가 있음을 알 수 있음
- 처리 완료된 데이터와 대기 중인 데이터 구분 가능

**전체 흐름:**
```
┌─────────────────────────────────────────────────────────────────┐
│ RCV                                                       │
│   Source → IF_RSV (link_status: PENDING)                        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Loader                                                          │
│   IF_RSV (PENDING→SUCCESS) → Target (link_status: PENDING)      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ SND                                                       │
│   Target (PENDING→SYNCED) → IF_SND (link_status: PENDING)       │
└─────────────────────────────────────────────────────────────────┘
```

---

### 1.4.3 source_refs - 원본 출처 추적

**목적:** "이 데이터가 원래 어디서 왔는가?"를 추적

**사용자 가치:**
- 데이터 오류 발생 시 원본 시스템의 어떤 테이블, 어떤 row인지 특정 가능
- 외부 시스템 담당자에게 "이 row에 문제가 있다"고 정확히 전달 가능
- 데이터 정합성 검증 시 원본과 비교 가능

**형식:** JSON 배열 (v2)
```json
["E:8:26:DJ-DJC-G1-0001"]
["E:8:27:DJ-DJC-G1-0001|2026-02-19|01:00:00"]
```

**구성:** `zone:datasourceId:tableId:primaryKey`
- zone: E(External), D(DMZ), I(Internal)
- datasourceId: Orchestrator `datasource` 테이블의 ID (숫자)
- tableId: Orchestrator `datasource_table` 테이블의 ID (숫자, 자동 등록)
- primaryKey: 레코드의 PK값 (복합 PK는 `|` 구분)

**복합 PK 예시:**
- 단일 PK (제원): `E:8:26:DJ-DJC-G1-0001` → `obsv_code`
- 복합 PK (관측데이터): `E:8:27:DJ-DJC-G1-0001|2026-02-19|01:00:00` → `obsv_code|obsv_date|obsv_time`

**데이터 흐름:** Source → IF_RSV → Target까지 그대로 전달되어 어디서든 원본 추적 가능

**자동 테이블 등록:** Orchestrator가 실행 트리거 시 `sourceTableIds`가 비어있으면:
1. Agent API `GET /api/pipeline/{agentCode}/tables` 호출
2. `datasource_table` 자동 생성 (tableId 부여)
3. `agent_table` SOURCE 매핑 자동 생성
4. 이후 source_refs에 정상 tableId 기록 (tbId=0 방지)

---

### 1.4.4 데이터 검색 및 추적 기능

**목적:** 대량의 데이터 중에서 원하는 데이터를 빠르게 찾고 추적

**제공 기능:**
| 기능 | 사용자 가치 |
|------|-------------|
| 키워드 검색 | 관측소 코드, 이름, 지역 등으로 데이터 검색 |
| 컬럼 지정 검색 | 특정 컬럼만 대상으로 정확한 검색 |
| 상태 필터링 | PENDING/SUCCESS/FAILED 상태별 필터 |
| 정렬 | 원하는 컬럼 기준 정렬 |
| source_refs 검색 | 특정 Source에서 온 데이터만 검색 |

**검색 가능 컬럼 (제원):**
`id`, `obsvCode`, `obsvName`, `sido`, `sigungu`, `sourceRefs`

**검색 가능 컬럼 (관측데이터):**
`id`, `obsvCode`, `remark`, `sourceRefs`

**활용 시나리오:**
| 상황 | 검색 방법 |
|------|-----------|
| 특정 관측소 데이터 확인 | obsvCode로 검색 |
| 특정 지역 데이터 확인 | sido/sigungu로 검색 |
| 문제 데이터만 확인 | status=FAILED 필터 |
| 특정 Source 데이터 확인 | sourceRefs로 검색 |
| 원본까지 역추적 | Trace 기능으로 Source → IF → Target 전체 흐름 확인 |

---

## 1.5 실행 모드별 동작

### 1.5.0 UPSERT 정책

> **핵심 원칙: 모든 실행에서 UPSERT 사용 (ON CONFLICT DO UPDATE)**

데이터 적재 시 **항상 UPSERT** 방식을 사용한다.

| 상황 | SQL 방식 | 동작 |
|------|----------|------|
| 새 데이터 | UPSERT | INSERT |
| 기존 데이터 | UPSERT | UPDATE (변경사항 반영) |

**왜 UPSERT를 기본으로 사용하는가:**
- 외부 DB가 여러 개 연결될 수 있어 데이터 충돌 가능성 있음
- Source 데이터 변경 시 항상 Target에 반영되어야 함
- INSERT ONLY 방식은 변경된 데이터를 놓칠 수 있음

**구현 (`SourceToIfStep.java`):**
```java
// 항상 UPSERT 사용
// PostgreSQL: ON CONFLICT DO UPDATE
// MySQL: ON DUPLICATE KEY UPDATE
String insertSql = buildUpsertSql(actualTargetIfTable, columns);
```

---

### 1.5.0.1 link_status 상태값

> **핵심 원칙: RESYNC 상태로 파이프라인 전체에 UPSERT 요구사항 전파**

| 상태 | 의미 | 처리 방식 | 다음 에이전트 동작 |
|------|------|----------|------------------|
| `PENDING` | 새 데이터 (일반 실행) | INSERT | INSERT |
| `RESYNC` | 재동기화 데이터 (기간 지정) | UPSERT | UPSERT |
| `SUCCESS` | 처리 완료 | - | 조회 대상 아님 |
| `FAILED` | 처리 실패 | - | 재시도 대상 |

**RESYNC 상태의 역할:**
- 기간 지정 실행은 맨 앞 에이전트(RCV)에서만 설정
- 이후 에이전트(Loader, SND)는 일반 실행으로도 RESYNC 레코드 처리 가능
- RESYNC 레코드는 UPSERT 방식으로 처리되어 데이터 변경 반영
- RESYNC 상태는 파이프라인 전체에 전파됨

**구현:**
```java
// 기간 지정 실행이면 RESYNC, 그 외 PENDING
String linkStatus = isTimeRangeExecution ? "RESYNC" : "PENDING";

// Source가 RESYNC면 전파
if ("RESYNC".equals(sourceLinkStatus)) {
    targetLinkStatus = "RESYNC";
}
```

---

### 1.5.1 일반 실행 (증분 동기화)

**RCV:**
1. `link_ngwis`에서 obsv_code별 마지막 동기화 시점 조회
2. 해당 시점 이후 데이터만 Source에서 추출
3. IF_RSV에 적재 (link_status: PENDING)
   - jewon: **UPSERT** (fullCopy=true)
   - obsvdata: **INSERT** (중복 시 무시)
4. `link_ngwis` 업데이트

**Loader:**
1. IF_RSV에서 `link_status IN ('PENDING', 'RESYNC')` 데이터 조회
2. Target에 적재 (link_status: Source에서 전파)
   - PENDING → PENDING
   - RESYNC → RESYNC (UPSERT 방식)
3. IF_RSV 상태를 SUCCESS/FAILED로 업데이트

**SND:**
1. Target에서 `link_status IN ('PENDING', 'RESYNC')` 데이터 조회
2. IF_SND에 적재 (link_status: Source에서 전파)
   - PENDING → PENDING (INSERT)
   - RESYNC → RESYNC (UPSERT)
3. Target 상태를 SUCCESS로 업데이트

---

### 1.5.2 기간 지정 실행 (재동기화)

**목적:** 특정 기간의 데이터를 강제로 재동기화하고, **변경된 데이터를 반영**

**RCV (기간 지정 실행):**
1. `link_ngwis` **무시** (조회하지 않음)
2. 지정된 기간의 데이터를 Source에서 전체 추출
3. IF_RSV에 **UPSERT** (기존 데이터 업데이트)
   - jewon: UPSERT
   - obsvdata: **UPSERT** (기간 지정이므로)
4. **link_status = 'RESYNC'** 설정 (파이프라인 전체에 UPSERT 요구)
5. `link_ngwis`는 현재 실행에서 처리된 데이터 기준으로 업데이트

**Loader (일반 실행으로도 RESYNC 처리):**
1. IF_RSV에서 `link_status IN ('PENDING', 'RESYNC')` 조회
2. RESYNC 레코드는 UPSERT 방식으로 Target에 적재
3. Target link_status에 RESYNC 전파
4. IF_RSV 상태 업데이트

**SND (일반 실행으로도 RESYNC 처리):**
1. Target에서 `link_status IN ('PENDING', 'RESYNC')` 조회
2. RESYNC 레코드는 UPSERT 방식으로 IF_SND에 적재
3. IF_SND link_status에 RESYNC 전파
4. Target 상태 업데이트

**RESYNC 전파 흐름:**
```
RCV 기간지정 → IF_RSV(RESYNC) → Loader 일반 → Target(RESYNC) → SND 일반 → IF_SND(RESYNC)
```

**주의사항:**
- 기간 지정 실행 시 기존 데이터가 **업데이트**됨 (덮어쓰기)
- Source에서 데이터가 수정된 경우 Target에도 반영됨
- `link_ngwis`는 **더 최신 데이터일 때만 업데이트**됨 (과거 시점으로 돌아가지 않음)
- **RESYNC 상태는 파이프라인 전체에 전파**되어 모든 에이전트가 UPSERT 사용

---

## 1.6 서버 포트 구성

| 서버 | 포트 | 설명 |
|------|------|------|
| Orchestrator Backend | 8080 | 중앙 관리 서버 |
| 통합 Agent (sync-agent-bojo) | 8082 | RCV/Loader/SND 전체 (12개 논리적 Agent) |

> v1에서는 RSV Relay(18081), Loader(8082), SND Relay(18082)로 3개 포트였으나,
> v2에서는 하나의 앱(8082)으로 통합. Orchestrator의 12개 Agent 레코드가 모두 같은 endpointUrl을 가리킴.

---

# 2. API 관리

> (미개발 - 추후 작성 예정)

---

# 3. 프로시저 관리

> (미개발 - 추후 작성 예정)

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-05 | 최초 작성 |
| 2026-02-05 | ExtractStepConfig 옵션 추가 (fullCopy, skipSourceStatusUpdate) |
| 2026-02-06 | INSERT vs UPSERT 정책 명시 (기간 지정 실행 시 UPSERT) |
| 2026-02-06 | link_ngwis 업데이트 조건 추가 (더 최신일 때만) |
| 2026-02-06 | RESYNC 상태 추가 (파이프라인 전체 UPSERT 전파) |
| 2026-02-09 | sync_record_history 이력 테이블 추가 (UPSERT 이력 보존) |
| 2026-02-12 | v2 통합 아키텍처 반영: 3개 분리 모듈 → sync-agent-bojo 1개 통합, RSV→RCV 명칭 변경, PipelineRegistry/AgentConfigLoader 추가, 파일 기반 Agent 설정 |
| 2026-02-19 | Tracing 기능 강화: 복합 PK 지원, JDBC 메타데이터 기반 PK 자동 감지, source_refs 형식 업데이트 (JSON배열), 소스 테이블 자동 등록, MySQL/PG 호환성 (SQL 방언 분기) |
