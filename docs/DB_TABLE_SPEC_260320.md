# DB 테이블 명세서

> Sync Orchestrator 화면에서 사용하는 데이터베이스 테이블 및 컬럼 정의
> (API 수집 관리 화면 제외)
> 최종 갱신: 2026-03-20

---

## 목차

1. [화면-테이블 매핑 요약](#1-화면-테이블-매핑-요약)
2. [테이블 상세 명세](#2-테이블-상세-명세)
   - [agent](#21-agent)
   - [agent_table](#22-agent_table)
   - [execution_history](#23-execution_history)
   - [execution_step_history](#24-execution_step_history)
   - [schedule](#25-schedule)
   - [datasource](#26-datasource)
   - [datasource_table](#27-datasource_table)
   - [datasource_column](#28-datasource_column)
   - [zone_config](#29-zone_config)
3. [ENUM 정의](#3-enum-정의)
4. [테이블 관계도](#4-테이블-관계도)

---

## 1. 화면-테이블 매핑 요약

| 화면 | 경로 | 사용 테이블 |
|------|------|-------------|
| 대시보드 | `/` | `agent`, `execution_history` |
| Agent 관리 | `/agents` | `agent`, `agent_table`, `datasource`, `datasource_table`, `schedule` |
| Agent 상세 | `/agents/[id]` | `agent`, `agent_table`, `schedule`, `execution_history` |
| 실행 이력 | `/executions` | `execution_history`, `agent` |
| 실행 상세 | `/executions/[id]` | `execution_history`, `execution_step_history`, `datasource`, `datasource_table` |
| DB 관리 | `/datasources` | `datasource`, `datasource_table`, `datasource_column` |

---

## 2. 테이블 상세 명세

### 2.1 agent

**용도**: 동기화 에이전트 등록 정보
**사용 화면**: 대시보드, Agent 관리, Agent 상세, 실행 이력 필터

| 컬럼명 | 타입 | NULL | UK | 설명 |
|--------|------|------|----|------|
| `id` | BIGINT | NO | - | PK (자동생성) |
| `agent_code` | VARCHAR(50) | NO | YES | 에이전트 고유 코드 |
| `agent_name` | VARCHAR(100) | NO | - | 에이전트 표시명 |
| `endpoint_url` | VARCHAR(255) | NO | - | REST 엔드포인트 URL |
| `zone` | VARCHAR(50) | NO | - | 네트워크 존 (DMZ/INTERNAL 등) |
| `agent_type` | VARCHAR(20) | NO | - | 에이전트 유형 (RCV/LOADER/SND/DB_CON_PROXY) |
| `status` | VARCHAR(20) | YES | - | 상태 (ONLINE/OFFLINE/RUNNING, 기본: OFFLINE) |
| `is_active` | BOOLEAN | YES | - | 활성화 여부 (기본: true) |
| `datasource_tag` | VARCHAR(50) | YES | - | 데이터소스 태그 |
| `source_datasource_id` | VARCHAR(50) | YES | - | 소스 데이터소스 ID |
| `target_datasource_id` | VARCHAR(50) | YES | - | 타겟 데이터소스 ID |
| `description` | TEXT | YES | - | 설명 |
| `last_executed_at` | TIMESTAMP | YES | - | 마지막 실행 시각 |
| `last_execution_status` | VARCHAR(20) | YES | - | 마지막 실행 결과 |
| `retention_config` | TEXT | YES | - | 데이터 보존(Retention) 설정 JSON |
| `created_at` | TIMESTAMP | YES | - | 생성 시각 (자동, 불변) |

**인덱스**: `agent_code` (UNIQUE)

---

### 2.2 agent_table

**용도**: 에이전트-테이블 매핑 (에이전트가 처리하는 SOURCE/TARGET 테이블)
**사용 화면**: Agent 관리 (등록 시), Agent 상세

| 컬럼명 | 타입 | NULL | 설명 |
|--------|------|------|------|
| `id` | BIGINT | NO | PK (자동생성) |
| `agent_id` | BIGINT | NO | Agent FK |
| `datasource_table_id` | BIGINT | NO | 데이터소스 테이블 FK |
| `table_type` | VARCHAR(20) | NO | 테이블 유형 (SOURCE/TARGET) |

**유니크 제약**: (`agent_id`, `datasource_table_id`)

---

### 2.3 execution_history

**용도**: 실행 이력 (Agent 실행 결과 기록)
**사용 화면**: 대시보드, Agent 상세(이력 탭), 실행 이력, 실행 상세

| 컬럼명 | 타입 | NULL | 설명 |
|--------|------|------|------|
| `execution_id` | VARCHAR(100) | NO | PK (실행 ID) |
| `agent_code` | VARCHAR(50) | NO | 에이전트 코드 |
| `agent_name` | VARCHAR(100) | YES | 에이전트명 |
| `agent_type` | VARCHAR(30) | YES | 에이전트 유형 |
| `status` | VARCHAR(20) | YES | 실행 상태 (RUNNING/SUCCESS/FAILED) |
| `total_read_count` | BIGINT | YES | 총 읽기 건수 |
| `total_write_count` | BIGINT | YES | 총 쓰기 건수 |
| `total_skip_count` | BIGINT | YES | 총 스킵 건수 |
| `duration_ms` | BIGINT | YES | 소요 시간 (밀리초) |
| `error_message` | TEXT | YES | 오류 메시지 |
| `started_at` | TIMESTAMP | YES | 실행 시작 시각 |
| `finished_at` | TIMESTAMP | YES | 실행 종료 시각 |
| `triggered_by` | VARCHAR(20) | YES | 트리거 유형 (MANUAL/SCHEDULE/CHAIN, 기본: MANUAL) |

**인덱스**:
- `idx_execution_history_agent`: `agent_code`
- `idx_execution_history_status`: `status`
- `idx_execution_history_started`: `started_at` DESC

---

### 2.4 execution_step_history

**용도**: 실행 스텝 이력 (파이프라인 스텝별 상세 결과)
**사용 화면**: 실행 상세

| 컬럼명 | 타입 | NULL | 설명 |
|--------|------|------|------|
| `id` | BIGINT | NO | PK (자동생성) |
| `execution_id` | VARCHAR(100) | NO | 실행 이력 FK |
| `step_id` | VARCHAR(100) | NO | 스텝 ID |
| `status` | VARCHAR(20) | YES | 스텝 상태 (SUCCESS/FAILED/SKIPPED) |
| `read_count` | INTEGER | YES | 읽기 건수 |
| `write_count` | INTEGER | YES | 쓰기 건수 |
| `skip_count` | INTEGER | YES | 스킵 건수 |
| `duration_ms` | BIGINT | YES | 소요 시간 (밀리초) |
| `error_message` | TEXT | YES | 오류 메시지 |
| `step_order` | INTEGER | YES | 스텝 순서 |

**인덱스**: `idx_step_history_execution`: `execution_id`

---

### 2.5 schedule

**용도**: 스케줄 설정 (Agent 자동 실행 cron 스케줄)
**사용 화면**: Agent 관리 (등록 시), Agent 상세

| 컬럼명 | 타입 | NULL | 설명 |
|--------|------|------|------|
| `schedule_id` | BIGINT | NO | PK (자동생성) |
| `agent_id` | BIGINT | NO | Agent FK |
| `cron_expression` | VARCHAR(50) | NO | Cron 표현식 |
| `is_enabled` | BOOLEAN | YES | 활성화 여부 (기본: true) |
| `execution_options` | TEXT | YES | 조건실행 옵션 JSON |
| `created_at` | TIMESTAMP | YES | 생성 시각 (자동, 불변) |

---

### 2.6 datasource

**용도**: 데이터소스 연결정보 (DB 접속 설정)
**사용 화면**: DB 관리, Agent 관리 (등록 시), 실행 상세

| 컬럼명 | 타입 | NULL | 설명 |
|--------|------|------|------|
| `datasource_id` | VARCHAR(50) | NO | PK (데이터소스 고유 ID) |
| `id` | BIGINT | YES | 자동생성 시퀀스 (insertable/updatable=false) |
| `datasource_name` | VARCHAR(100) | NO | 데이터소스명 |
| `db_type` | VARCHAR(20) | NO | DB 유형 (POSTGRESQL/MYSQL/ORACLE/MARIADB/MSSQL) |
| `host` | VARCHAR(255) | NO | 호스트 주소 |
| `port` | INTEGER | NO | 포트 번호 |
| `database_name` | VARCHAR(100) | NO | 데이터베이스명 |
| `username` | VARCHAR(512) | NO | 접속 계정 (암호화 저장) |
| `password` | VARCHAR(1024) | NO | 접속 비밀번호 (암호화 저장) |
| `description` | TEXT | YES | 설명 |
| `zone` | VARCHAR(50) | YES | 네트워크 존 |
| `is_active` | BOOLEAN | YES | 활성화 여부 (기본: true) |
| `created_at` | TIMESTAMP | YES | 생성 시각 (자동, 불변) |
| `updated_at` | TIMESTAMP | YES | 수정 시각 (자동 갱신) |

---

### 2.7 datasource_table

**용도**: 데이터소스에 등록된 테이블 메타데이터
**사용 화면**: DB 관리 (테이블 관리), Agent 관리 (테이블 선택)

| 컬럼명 | 타입 | NULL | 설명 |
|--------|------|------|------|
| `id` | BIGINT | NO | PK (자동생성) |
| `datasource_id` | VARCHAR(50) | NO | 데이터소스 ID |
| `table_name` | VARCHAR(100) | NO | 테이블명 |
| `table_alias` | VARCHAR(100) | YES | 테이블 별칭 (화면 표시용) |
| `description` | VARCHAR(500) | YES | 설명 |
| `created_at` | TIMESTAMP | YES | 생성 시각 (자동, 불변) |

---

### 2.8 datasource_column

**용도**: 데이터소스 테이블의 컬럼 메타데이터
**사용 화면**: DB 관리 (테이블 관리 모달)

| 컬럼명 | 타입 | NULL | 설명 |
|--------|------|------|------|
| `id` | BIGINT | NO | PK (자동생성) |
| `datasource_table_id` | BIGINT | NO | DatasourceTable FK |
| `column_name` | VARCHAR(100) | NO | 컬럼명 |
| `column_alias` | VARCHAR(100) | YES | 컬럼 별칭 |
| `data_type` | VARCHAR(50) | YES | 데이터 타입 |
| `is_primary_key` | BOOLEAN | YES | PK 여부 (기본: false) |
| `is_nullable` | BOOLEAN | YES | NULL 허용 여부 (기본: true) |
| `description` | VARCHAR(500) | YES | 설명 |

---

### 2.9 zone_config

**용도**: 네트워크 존 설정 (Proxy URL 등)
**사용 화면**: 직접 표시되지 않으나 Agent 실행 시 내부적으로 참조

| 컬럼명 | 타입 | NULL | 설명 |
|--------|------|------|------|
| `zone` | VARCHAR(50) | NO | PK (존 이름) |
| `short_code` | VARCHAR(5) | NO | 존 약어 (E/D/IC/IS) |
| `proxy_agent_url` | VARCHAR(500) | NO | 프록시 에이전트 URL |
| `description` | VARCHAR(500) | YES | 설명 |
| `is_active` | BOOLEAN | YES | 활성화 여부 (기본: true) |
| `created_at` | TIMESTAMP | YES | 생성 시각 (자동, 불변) |
| `updated_at` | TIMESTAMP | YES | 수정 시각 (자동 갱신) |

---

## 3. ENUM 정의

### AgentStatus
| 값 | 설명 |
|----|------|
| `ONLINE` | 정상 가동 중 |
| `OFFLINE` | 미가동 |
| `RUNNING` | 실행 중 |

### AgentType
| 값 | 설명 |
|----|------|
| `RCV` | 수신 (외부 → IF) |
| `LOADER` | 적재 (IF → Target) |
| `SND` | 송신 (Target → IF) |
| `DB_CON_PROXY` | DB 연결 프록시 |

### ExecutionStatus
| 값 | 설명 |
|----|------|
| `RUNNING` | 실행 중 |
| `SUCCESS` | 성공 |
| `FAILED` | 실패 |

### DbType
| 값 | 기본 포트 | 설명 |
|----|-----------|------|
| `POSTGRESQL` | 5432 | PostgreSQL |
| `MYSQL` | 3306 | MySQL |
| `ORACLE` | 1521 | Oracle |
| `MARIADB` | 3306 | MariaDB |
| `MSSQL` | 1433 | MS SQL Server |

### TableType (AgentTable)
| 값 | 설명 |
|----|------|
| `SOURCE` | 소스 테이블 |
| `TARGET` | 타겟 테이블 |

---

## 4. 테이블 관계도

```
agent (1) ──────┬──── (*) agent_table
                │           └── datasource_table_id → datasource_table.id
                │
                └──── (*) schedule


datasource (1) ─── 참조 ── agent.source_datasource_id
                         ── agent.target_datasource_id


datasource_table (*) ──── (1) datasource
                              (datasource_id → datasource.datasource_id)

datasource_column (*) ──── (1) datasource_table
                                (datasource_table_id → datasource_table.id)


execution_history ──── 참조 ── agent.agent_code
     │
     └──── (*) execution_step_history
                (execution_id → execution_history.execution_id)


zone_config ──── 참조 ── agent.zone
```

### FK 관계 정리

| 자식 테이블 | FK 컬럼 | 부모 테이블 | 부모 PK | Cascade |
|-------------|---------|-------------|---------|---------|
| `agent_table` | `agent_id` | `agent` | `id` | DELETE |
| `schedule` | `agent_id` | `agent` | `id` | DELETE |
| `datasource_column` | `datasource_table_id` | `datasource_table` | `id` | DELETE + ORPHAN |
| `execution_step_history` | `execution_id` | `execution_history` | `execution_id` | - |

### JSON 필드 상세

| 테이블 | 컬럼 | 저장 내용 |
|--------|------|-----------|
| `agent` | `retention_config` | `{ "enabled": bool, "retentionDays": int, "targetTables": [...] }` |
| `schedule` | `execution_options` | `{ "conditions": [{ "tableName": str, "column": str, "operator": str, "value": str }] }` |
