# GIMS 내부망 Agent 개발 방향 문서

> 이 문서는 내부망 Agent(`sync-agent-bojo-int`)의 설계와 구현을 정리한 문서입니다.
> DMZ Agent(`sync-agent-bojo`)와의 차이점 위주로 서술합니다.
> 공통 개념(파이프라인 구조, IF 테이블 설계, 컬럼 규칙 등)은 [ARCHITECTURE.md](ARCHITECTURE.md) 참조.

---

## 목차

1. [개요](#1-개요)
2. [DMZ vs 내부망 비교](#2-dmz-vs-내부망-비교)
3. [데이터 흐름](#3-데이터-흐름)
4. [프로젝트 구조](#4-프로젝트-구조)
5. [파이프라인 설정](#5-파이프라인-설정)
6. [인프라 구성](#6-인프라-구성)
7. [향후 계획](#7-향후-계획)

---

# 1. 개요

## 1.1 목적

DMZ에서 SND가 준비한 IF_SND 데이터를 **내부망으로 가져오는** 역할.

| 항목 | 값 |
|------|-----|
| 프로젝트명 | `sync-agent-bojo-int` |
| 패키지 | `com.sync.agent.bojoint` |
| 포트 | 8092 |
| Zone | INTERNAL |
| 로컬 DB | PostgreSQL 29002/dev |

## 1.2 왜 별도 프로젝트인가

| 이유 | 설명 |
|------|------|
| 네트워크 분리 | DMZ와 내부망은 물리적으로 분리됨 (실제 운영 환경) |
| 관심사 분리 | DMZ는 외부 수집+내부 전송, 내부망은 수신+내부 적재 |
| 독립 배포 | 내부망 Agent만 별도 배포/재시작 가능 |
| 설정 단순화 | Link 테이블, MySQL 드라이버, Loader/SND 불필요 |

---

# 2. DMZ vs 내부망 비교

## 2.1 아키텍처 차이

| 항목 | DMZ (`sync-agent-bojo`) | 내부망 (`sync-agent-bojo-int`) |
|------|-------------------------|-------------------------------|
| 포트 | 8082 | 8092 |
| Zone | DMZ | INTERNAL |
| DB | 29001/dev | 29002/dev |
| Agent 수 | 12개 (RCV 10 + Loader 1 + SND 1) | 1개 (RCV 1) → 나중에 Loader 추가 |
| RCV 방식 | Link 테이블 기반 증분 / SIMPLE_COPY 혼용 | **SIMPLE_COPY only** |
| Link 테이블 | 사용 (link_ngwis) | **미사용** |
| MySQL 지원 | O (외부 MySQL DB 연결) | **X** (PostgreSQL only) |
| SND | O (Target → IF_SND) | **X** (내부망에서 외부 전송 없음) |

## 2.2 코드 차이

| 파일 | DMZ | 내부망 | 차이 |
|------|-----|--------|------|
| `AgentDefinition` | LinkConfig, StepConfig, ifTable, targetTable 포함 | **RCV 관련만** (TableConfig 2개) | Loader/SND 필드 제거 |
| `AgentConfigLoader` | Link, ifTable, targetTable, step 파싱 | **jewon, obsvdata만** 파싱 | 불필요한 파싱 제거 |
| `RcvPipelineConfig` | SIMPLE_COPY + CUSTOM_STAGING (Link 기반) | **SIMPLE_COPY only** | Link 로직 전체 제거 |
| `SyncDataSourceService` | 로그 `[Bojo]`, 풀명 `BojoPool-` | 로그 `[BojoInt]`, 풀명 `BojoIntPool-` | prefix만 변경 |
| `AsyncConfig` | core=6, max=15, prefix `Bojo-Pipeline-` | core=4, max=8, prefix `BojoInt-Pipeline-` | 스레드풀 축소 |
| `HealthController` | RCV/Loader/SND 모두 표시 | **RCV만** 표시 | |
| `build.gradle` | MySQL 드라이버 포함 | **MySQL 드라이버 제거** | |
| `application.yml` | Jasypt 암호화 DB 계정 | 평문 DB 계정 (k1m/1111) | 내부망이라 암호화 불필요 |

## 2.3 YAML 설정 차이

**DMZ RCV (예: rcv-bojo-01):**
```yaml
agent-code: rcv-bojo-01
type: RCV
jewon:
  source-table: sec_jewon_view      # 외부 DB의 VIEW
  target-table: if_rsv_sec_jewon
  primary-key: obsv_code
  full-copy: true
  skip-source-status-update: true   # 외부 VIEW라 상태 업데이트 불가
link:
  use-link-table: true              # Link 테이블로 증분 추적
  table-name: link_ngwis
```

**내부망 RCV (internal-bojo-rcv):**
```yaml
agent-code: internal-bojo-rcv
type: RCV
jewon:
  source-table: if_snd_sec_jewon    # DMZ IF_SND 테이블 (직접 읽기)
  target-table: if_rsv_sec_jewon
  primary-key: id
  conflict-key: source_refs         # UPSERT 충돌 기준
  full-copy: true
  skip-source-status-update: false  # IF_SND는 TABLE이라 상태 업데이트 가능
# link 없음 - SIMPLE_COPY 방식
```

**핵심 차이:**
- DMZ: 외부 VIEW → Link 테이블로 증분 추적 필요
- 내부망: IF_SND TABLE → `link_status` 기반으로 PENDING 조회하므로 Link 불필요

---

# 3. 데이터 흐름

## 3.1 전체 파이프라인에서의 위치

```
[외부 시스템]        [DMZ - bojo:8082]                [내부망 - bojo-int:8092]
Source DB    →(RCV)→ IF_RSV
                     ↓(Loader)
                     Target(sec_jewon/obsvdata)
                     ↓(SND)
                     IF_SND_sec_jewon    →(int-RCV)→  IF_RSV_sec_jewon
                     IF_SND_sec_obsvdata →(int-RCV)→  IF_RSV_sec_obsvdata
                                                       ↓(Loader, 향후)
                                                       Target
```

## 3.2 내부망 RCV 상세

```
[DMZ 29001/dev]                        [내부망 29002/dev]
┌─────────────────────┐                ┌──────────────────────┐
│ if_snd_sec_jewon    │ ──(SOURCE)──→  │ if_rsv_sec_jewon     │
│ (link_status=PENDING)│               │ (link_status=PENDING)│
└─────────────────────┘                └──────────────────────┘
┌─────────────────────┐                ┌──────────────────────┐
│ if_snd_sec_obsvdata │ ──(SOURCE)──→  │ if_rsv_sec_obsvdata  │
│ (link_status=PENDING)│               │ (link_status=PENDING)│
└─────────────────────┘                └──────────────────────┘
```

**처리 과정:**
1. DMZ IF_SND에서 `link_status = PENDING` 데이터 조회 (Source)
2. 내부망 IF_RSV에 UPSERT (Target, `ON CONFLICT (source_refs)`)
3. DMZ IF_SND의 `link_status`를 `SUCCESS`로 업데이트

## 3.3 source_refs 전파

```
[원본]  E:8:26:DJ-DJC-G1-0001
  ↓ (DMZ RCV → IF_RSV)
  ↓ (DMZ Loader → Target)
  ↓ (DMZ SND → IF_SND)
[DMZ IF_SND]  source_refs = ["E:8:26:DJ-DJC-G1-0001"]
  ↓ (내부망 RCV → IF_RSV)
[내부 IF_RSV]  source_refs = ["E:8:26:DJ-DJC-G1-0001"]  ← 그대로 복사
```

- source_refs는 원본 출처 정보이므로 **덮어쓰지 않고 그대로 전달**
- 내부망에서도 원본이 어느 외부 시스템에서 왔는지 추적 가능

---

# 4. 프로젝트 구조

```
sync-agent-bojo-int/
├── build.gradle                    # MySQL 드라이버 없음
├── settings.gradle
├── libs/
│   └── sync-agent-common-*.jar     # bojo와 동일 공통 모듈
├── src/main/java/com/sync/agent/bojoint/
│   ├── BojoIntAgentApplication.java
│   ├── config/
│   │   ├── AgentConfigLoader.java      # jewon/obsvdata만 파싱
│   │   ├── AgentDefinition.java        # RCV 필드만
│   │   ├── PipelineRegistry.java
│   │   ├── RcvPipelineConfig.java      # SIMPLE_COPY only
│   │   ├── SyncDataSourceService.java  # [BojoInt] prefix
│   │   └── AsyncConfig.java            # BojoInt-Pipeline- prefix
│   ├── controller/
│   │   ├── PipelineController.java
│   │   └── HealthController.java       # appName: sync-agent-bojo-int
│   └── pipeline/
│       ├── PipelineService.java
│       └── CompositeStepCallback.java
└── src/main/resources/
    ├── application.yml                 # port 8092, 29002/dev
    └── config/agents/
        └── internal-bojo-rcv.yml       # RCV 1개
```

**DMZ bojo 대비 없는 것들:**
- `rcv/fetcher/` - LinkTableObsvDataFetcher (Link 기반 증분 조회)
- `rcv/step/` - LinkTableUpdateStep (link_ngwis 업데이트)
- `loader/` - DaejeonLoadStep, TargetRepositoryService
- `dto/`, `entity/` - JPA 엔티티 (Loader용)
- `config/LoaderPipelineConfig.java`
- `config/SndPipelineConfig.java`
- `config/DynamicEntityManagerService.java`
- `config/CaseAwareNamingStrategy.java`

---

# 5. 파이프라인 설정

## 5.1 RCV 파이프라인 (SIMPLE_COPY)

내부망 RCV는 DMZ IF_SND 테이블을 **직접 읽는** 단순 복사 방식.

| Step | Source (DMZ) | Target (내부) | 방식 |
|------|-------------|--------------|------|
| jewon-extract | if_snd_sec_jewon | if_rsv_sec_jewon | UPSERT (fullCopy) |
| obsvdata-extract | if_snd_sec_obsvdata | if_rsv_sec_obsvdata | UPSERT (conflict-key) |

**UPSERT 충돌 기준:**

| 테이블 | conflict-key | 이유 |
|--------|-------------|------|
| jewon | `source_refs` | 외부 DB obsv_code 중복 문제 동일 |
| obsvdata | `source_refs` | 복합키 대신 source_refs로 유일성 보장 |

## 5.2 왜 Link 테이블이 불필요한가

DMZ RCV가 Link 테이블을 쓰는 이유:
- 외부 DB는 **VIEW**라 `link_status` 컬럼이 없음
- obsv_code별 "어디까지 가져왔는지" 별도 추적 필요

내부망 RCV가 Link 테이블이 필요 없는 이유:
- DMZ IF_SND는 **TABLE**이라 `link_status` 컬럼이 있음
- `link_status = PENDING` 조회 → 처리 → `SUCCESS` 업데이트로 충분
- `SourceToIfStep`의 기본 동작(SIMPLE_COPY)이 이를 처리

---

# 6. 인프라 구성

## 6.1 서버 포트

| 서비스 | 포트 | Zone |
|--------|------|------|
| Orchestrator Backend | 8080 | - |
| DMZ Agent (bojo) | 8082 | DMZ |
| **내부망 Agent (bojo-int)** | **8092** | **INTERNAL** |
| Frontend (Next.js) | 3000 | - |

## 6.2 DB 구성

| 용도 | 포트 | DB명 | 사용자 |
|------|------|------|--------|
| DMZ Agent 로컬 + IF | 29001 | dev | k1m/1111 |
| **내부망 Agent 로컬 + IF** | **29002** | **dev** | **k1m/1111** |

## 6.3 Orchestrator 연결

내부망 Agent도 DMZ와 동일한 Orchestrator(8080)에 연결.

```yaml
agent:
  orchestrator-url: http://localhost:8080
  zone: INTERNAL
```

- Orchestrator에 `internal-bojo-rcv` Agent 레코드 등록 필요
- endpointUrl: `http://localhost:8092`
- Source datasource: DMZ 29001/dev (IF_SND 읽기)
- Target datasource: 내부망 29002/dev (IF_RSV 쓰기)

---

# 7. 향후 계획

## 7.1 내부망 Loader 추가

```
[내부망 29002/dev]
IF_RSV_sec_jewon     →(Loader)→  sec_jewon (Target)
IF_RSV_sec_obsvdata  →(Loader)→  sec_obsvdata (Target)
```

- `config/LoaderPipelineConfig.java` 추가
- `config/agents/internal-loader.yml` 추가
- Target 엔티티/DTO 필요 시 `entity/`, `dto/` 패키지 추가
- DaejeonLoadStep 또는 신규 LoadStep 구현

## 7.2 SND는 없음

내부망에서 외부로 데이터를 보낼 필요가 없으므로 SND 파이프라인은 구축하지 않음.

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-24 | 최초 작성 - sync-agent-bojo-int 프로젝트 생성, RCV 파이프라인 |
