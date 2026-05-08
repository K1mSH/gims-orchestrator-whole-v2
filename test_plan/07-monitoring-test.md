# 07 — 모니터링/운영 (cross-cutting) 기능 테스트 문서

> 작성일: 2026-05-07

---

## 공통 검증 규칙

- claude API 호출 → 1차 확인. **사용자가 직접 프론트(`localhost:3000`)에서 같은 흐름 확인** 후에만 통과.
- 단계마다 사용자 OK 후 다음 진입.
- **사전 의존**: 02 bojo / 04 api-collect 통과 (실 실행 이력이 DB 에 적재되어 있어야 함)

---

## 목차
1. [시스템 구성](#1-시스템-구성)
2. [Agent 관리](#2-agent-관리)
3. [실행 이력 + 페이징 + 필터](#3-실행-이력--페이징--필터)
4. [실행 상세 + Step별 통계](#4-실행-상세--step별-통계)
5. [데이터 추적 (3단계)](#5-데이터-추적-3단계)
6. [Schedule 관리](#6-schedule-관리)
7. [Retention (보존기간)](#7-retention-보존기간)
8. [관리 DB 라우팅 (X-Manage-Datasource-Id)](#8-관리-db-라우팅-x-manage-datasource-id)
9. [대시보드](#9-대시보드)
10. [동시 실행 방지 (runningAgentCodes)](#10-동시-실행-방지-runningagentcodes)
11. [알려진 허점 / 주의사항](#11-알려진-허점--주의사항)

---

## 1. 시스템 구성

### 1-1. 모듈 (cross-cutting)
- 운영자 호출 endpoint = backend (8080) `/api/agents`, `/api/executions`, `/api/schedules`, `/api/dashboard`
- 실행 트리거 = backend → Agent (8082/8085/8092)
- 추적 / 데이터 조회 = backend → Proxy (8083/8093) → Agent

### 1-2. DB
- Orchestrator (29001/orchestrator): `agent`, `agent_table`, `schedule`, `execution_history`, `execution_step_history`, `datasource_table`
- Agent 자체 DB (29001/dev): `sync_log` (Agent 별), Internal 은 Oracle 의 자체 sync_log

### 1-3. 추적 모델 (3단계 분기)
| Source 상태 | 모드 | Agent 패턴 |
|---|---|---|
| source_refs 컬럼 없음 | **PK 파싱** | RCV (외부 DB) |
| source_refs 있고 target 값과 동일 | **source_refs IN** | Loader (IF→Target) |
| source_refs 있지만 target 값과 다름 | **PK 파싱** | SND (PK 기반 새 source_refs) |

---

## 2. Agent 관리

### 2-1. 목록 (`GET /api/agents`)
- [ ] HTTP 200, 배열
- [ ] 각 항목 = `{id, agentCode, agentName, endpointUrl, zone, isActive, agentType, sourceDatasourceId, targetDatasourceId, status, lastExecutionAt}`
- [ ] 사용자 검증: 프론트 `/agents` → Agent 타입별 그룹화 (RCV/SND/Loader/DB Proxy) + 색상 배지

### 2-2. 상태 확인 (`POST /api/agents/{id}/health-check`)
- [ ] backend 가 Agent endpoint 호출 → ONLINE/OFFLINE 결정
- [ ] DB `agent.status` 갱신
- [ ] 사용자 검증: 프론트 "상태확인" 버튼 → 실시간 ONLINE/OFFLINE 토글

### 2-3. auto-discover (`POST /api/agents/discover`)
- [ ] Agent endpointUrl 의 `pipeline/info` 호출 → 가용 agentCode 목록 + zone + agentInfo 수집
- [ ] 응답 = `{endpointUrl, zone, agents: [{agentCode, type, registered}], agentInfo: [{agentCode, ...}]}`
- [ ] 사용자 검증: 프론트 "+ 새 Agent" → URL 입력 → Agent 선택 → ✓/✗ 검증 (YAML vs Datasource 자동 대조)

### 2-4. 등록 / 수정 / 삭제
- [ ] CRUD 모두 cookie 인증 통과 시 정상

### 2-5. 테이블 동기화
- [ ] OFFLINE → ONLINE 시 자동 갱신
- [ ] 수동 "테이블 갱신" 버튼 → `agent_table` 갱신
- [ ] 4/30 fix 회귀: `DatasourceService introspection fallback` (request.columns 비면 자체 introspection)

---

## 3. 실행 이력 + 페이징 + 필터

### 3-1. 페이징 이력 (`GET /api/executions?page=&pageSize=&...`)
```bash
curl -s -b /tmp/cookies.txt \
  "http://localhost:8080/api/executions?page=1&pageSize=20" | jq | head -30
```
- [ ] HTTP 200, `{content: [...], totalElements, totalPages, page, pageSize}`
- [ ] 각 row = `{executionId, agentCode, agentType, zone, status, startedAt, endedAt, durationMs, totalReadCount, totalWriteCount, triggeredBy}`
- [ ] 사용자 검증: 프론트 `/executions` → 같은 목록

### 3-2. 필터 (상태/Zone/Agent Code/타입/날짜/검색)
- [ ] `?status=SUCCESS` → 성공만
- [ ] `?zone=DMZ` → DMZ 만
- [ ] `?agentCode=dmz-bojo-rcv-daejeon`
- [ ] `?startDate=2026-05-01&endDate=2026-05-07`
- [ ] `?search=...` (자유 텍스트)
- [ ] 복합 필터 (여러 조합) 정상 동작
- [ ] 사용자 검증: 프론트 2줄 필터 바 → URL 쿼리 동기화

### 3-3. 최근 50건 (`GET /api/executions/recent`)
- [ ] HTTP 200, 배열 50건 (대시보드용)

### 3-4. 실행 중 목록 (`GET /api/executions/running`)
- [ ] 현재 실행 중인 executions 만 반환
- [ ] 사용자 검증: 프론트 대시보드 "실행 중" 카드 클릭 → 같은 목록

### 3-5. 1970 epoch 정렬 (5/6 fix 회귀)
- [ ] 정렬: 최신 순 (옛 epoch 0 이력 위로 안 옴)

---

## 4. 실행 상세 + Step별 통계

### 4-1. 실행 상세 (`GET /api/executions/{id}`)
```bash
curl -s -b /tmp/cookies.txt http://localhost:8080/api/executions/{id} | jq
```
- [ ] HTTP 200, `{executionId, status, startedAt, endedAt, params, conditions?, steps: [...]}`
- [ ] 각 step = `{stepId, stepName, status, readCount, writeCount, skipCount, errorMessage?, startedAt, endedAt}`

### 4-2. 테이블별 통계 (per-mapping, `GET /api/executions/{id}/stats`)
- [ ] HTTP 200, 배열
- [ ] 각 row = `{mappingName, sourceTable, targetTable, readCount, writeCount, skipCount, failedCount}`
- [ ] 사용자 검증: 프론트 실행 상세 → "테이블별 처리현황" 표

### 4-3. 데이터 조회 (SOURCE/TARGET, `GET /api/executions/{id}/data/...`)
- [ ] `?type=target&tableName=...` → target 테이블의 execution_id 기준 데이터
- [ ] `?type=source&tableName=...` → 역추적 매칭 source 데이터
- [ ] 페이징 + 검색 + 정렬 지원
- [ ] 4/22 통합: target-if 도 `/target` 으로 (별 endpoint X)

### 4-4. 실패 레코드
- [ ] `?failed=true` 또는 별 endpoint
- [ ] 사용자 검증: 프론트 실행 상세 → "실패만 보기" 토글

---

## 5. 데이터 추적 (3단계)

### 5-1. 3단계 자동 분기 (`buildSourceFilter`)
- [ ] 샘플 1건 `SELECT COUNT(*) WHERE source_refs = ?` 체크 → 모드 결정
- [ ] RCV 모드: PK 파싱 (외부 DB)
- [ ] Loader 모드: source_refs IN
- [ ] SND 모드: PK 파싱 (새 source_refs)

### 5-2. 정방향 추적 (Forward, `GET /api/executions/{id}/trace?...`)
- [ ] Source PK → Target 매칭 데이터
- [ ] 사용자 검증: 프론트 데이터 행 클릭 → SOURCE → TARGET

### 5-3. 역추적 (Backward, `GET /api/executions/{id}/trace-source?sourceRefs=...`)
- [ ] target 의 source_refs → source 원본 1건
- [ ] traceStatus = `FOUND` / `SOURCE_NOT_FOUND` / `NOT_TRACKABLE` (5/7 type fix)
- [ ] 사용자 검증: 프론트 TARGET 행 클릭 → Source 원본 표시

### 5-4. 복합 PK 추적
- [ ] 복합 PK 컬럼 (예: `obsv_code,obsv_date,obsv_time`) 파싱 → IN 매칭
- [ ] 테이블명 fallback 검색 (sourceTable 자동 해석)

### 5-5. 추적 비대상 (NOT_TRACKABLE)
- [ ] tm_gd970101 (결과 코드 매핑) 같은 파생 테이블 → 추적 비대상 표시
- [ ] 사용자 검증: 프론트 "추적 비대상 (파생 데이터)" 라벨 표시

### 5-6. Fallback 모드 (실행 덮어쓰기 감지)
- [ ] UPSERT 로 같은 행 재실행 시 execution_id 갱신 → 옛 execution 의 target 0건
- [ ] Fallback 표시 (사용자에게 알림)

### 5-7. False positive fix (4/30 회귀)
- [ ] Object.values.some 대체 → backend `traceBySourcePk` API 호출 (50줄 → 7줄)
- [ ] 정확한 source PK 매칭 검증

---

## 6. Schedule 관리

### 6-1. CRUD
- [ ] `POST /api/schedules` 등록 — `{agentId, cronExpression, isEnabled}`
- [ ] `GET /api/schedules` 목록
- [ ] `GET /api/schedules/{id}` 단건
- [ ] `PUT /api/schedules/{id}` 수정
- [ ] `PUT /api/schedules/{id}/toggle` 활성/비활성
- [ ] `DELETE /api/schedules/{id}` 삭제

### 6-2. 자동 실행
- [ ] cron 시점에 backend `executionService.triggerExecution()` 호출 → Agent POST
- [ ] 실행 이력 `triggeredBy=SCHEDULE` 기록
- [ ] 비활성 스케줄은 실행 X

### 6-3. ScheduleExecutor 패턴
- [ ] 앱 기동 시 `@EventListener(ContextRefreshedEvent)` 가 활성 스케줄 모두 로드
- [ ] `TaskScheduler + CronTrigger` 사용

### 6-4. cron 한글 변환 (Frontend)
- [ ] 사용자 검증: 프론트 Agent 상세 → 스케줄 섹션 → "매일 오전 2시", "30분마다" 같은 한글 표시

---

## 7. Retention (보존기간)

### 7-1. 설정 CRUD (`GET/PUT /api/agents/{id}/retention`)
- [ ] retentionConfig JSON 형식: `{enabled, targetDatasourceId, targets: [{table, dateColumn, retentionDays}]}`
- [ ] 사용자 검증: 프론트 Agent 상세 → Retention 섹션 → 테이블/컬럼/일수 입력 → 저장

### 7-2. Agent 직접 호출 (`POST /api/cleanup/{agentCode}`)
```bash
# DMZ Agent
curl -s -X POST http://localhost:8082/api/cleanup/dmz-bojo-rcv-daejeon \
  -H "X-API-Key: ..." \
  -H "Content-Type: application/json" \
  -d '{"enabled":true,"targets":[{"table":"if_rsv_sec_obsvdata","dateColumn":"obsv_date","retentionDays":1}]}'
```
- [ ] HTTP 200 + `{totalDeleted, results: [...]}`
- [ ] cutoff 이전 데이터 삭제 / 이후 보존
- [ ] enabled=false → 삭제 X
- [ ] targets 빈 배열 → 삭제 X

### 7-3. Internal Agent — `targetDatasourceId` 필수
```bash
curl -s -X POST http://localhost:8092/api/cleanup/internal-bojo-rcv \
  -H "X-API-Key: ..." \
  -d '{"enabled":true,"targetDatasourceId":"internal","targets":[...]}'
```
- [ ] `targetDatasourceId` 누락 → ThreadLocal 비어있어 잘못된 DS 참조
- [ ] 명시 시 정상

### 7-4. 음수 방어 4계층 (3/11 fix 회귀)
- [ ] 프론트: min=1
- [ ] Orchestrator API: 검증
- [ ] Agent Controller: 예외
- [ ] Agent Service: skip

### 7-5. pm_gd970201 의 dateColumn = `obsrvn_dt`
- [ ] obsv_date 아님 (Internal Loader target). 잘못 지정 시 SQL 에러

### 7-6. jewon 테이블 제외
- [ ] obsv_date 컬럼 없음 → Retention 대상 제외

### 7-7. Oracle DELETE 호환
- [ ] Oracle DATE 타입: `DELETE WHERE obsrvn_dt < TO_DATE('2026-03-26', 'YYYY-MM-DD')`
- [ ] VARCHAR2 인 경우 문자열 비교 (형식 일관성)

### 7-8. DataRetentionScheduler
- [ ] Orchestrator 의 매일 새벽 2시 (기본, `retention.cron`) cron
- [ ] Agent 별 retentionConfig 로드 → 각 Agent `/api/cleanup/{code}` 호출

---

## 8. 관리 DB 라우팅 (X-Manage-Datasource-Id)

### 8-1. 헤더 기반 라우팅 (4/22 추가)
- [ ] `X-Manage-Datasource-Id: internal` 헤더 → DataSourceProvider 가 internal datasource 사용
- [ ] 메모리 룰 `project_header_manage_routing` — Proxy 관리 DB 조회 라우팅

### 8-2. ExecutionService → Proxy 호출 시 자동 주입
- [ ] backend ExecutionService 가 Agent.targetDatasourceId 를 헤더에 자동 추가
- [ ] Proxy 가 헤더 받아 적절한 DS 라우팅

### 8-3. ExecutionDataController JdbcTemplate 전환
- [ ] JPA → JdbcTemplate (4/22)
- [ ] ExecutionDataReader 공통 조회 유틸 도입

---

## 9. 대시보드

### 9-1. 통계 카드 (`GET /api/dashboard`)
- [ ] HTTP 200, `{agentTotal, agentOnline, agentOffline, runningExecutions, todayExecutions, todayFailures}`
- [ ] 사용자 검증: 프론트 `/` 대시보드 → 5장 카드

### 9-2. 카드 클릭 → 필터링
- [ ] "실행 중" 클릭 → `/executions?running=true` 이동
- [ ] "오늘 실패" 클릭 → `/executions?status=FAILED&date=today`

### 9-3. Agent 상태 테이블
- [ ] 이름 / Zone / 상태 (배지) / 마지막 실행

### 9-4. 실행 이력 테이블 (최근 N건)
- [ ] Agent명 / 타입 / 상태 / 건수 / 소요시간

### 9-5. 자동 갱신
- [ ] 10초 setInterval 폴링 (`useEffect` cleanup)

---

## 10. 동시 실행 방지 (runningAgentCodes)

### 10-1. Set + finally 패턴
- [ ] backend ExecutionService 또는 Agent PipelineService 가 `runningAgentCodes` Set 보유
- [ ] 실행 시작 = `add(agentCode)`
- [ ] finally = `remove(agentCode)`

### 10-2. 동시 호출 차단
- [ ] 같은 agentCode 재호출 → `409 ALREADY_RUNNING` 또는 `400`
- [ ] 사용자 검증: 프론트 "실행" 버튼 클릭 → 한 번만 실행 (다시 클릭 시 알림)

### 10-3. 4 Agent 일관 (메모리 룰)
- [ ] bojo / bojo-internal / others / provide PipelineService 모두 적용

---

## 11. 알려진 허점 / 주의사항

### 11-1. Link 테이블 SyncLog 제외
- link_ngwis / tm_gd970101 / tm_gd980002 → SyncLog/모니터링/매핑에서 제외 (메모리 정합)
- 추적 검증에서 NOT_TRACKABLE 표시

### 11-2. UPSERT 로 인한 execution_id 덮어쓰기
- 동일 행 재실행 시 옛 execution 의 target 0건. 추적 시점 주의 (5-6 Fallback)

### 11-3. 직접 API 호출 시 source_refs 불완전 (`U:0:0:pk`)
- 운영자 흐름 = 항상 Orchestrator 경유 (`POST /api/executions/{agentId}/run`)
- 직접 Agent API 호출 (`/api/pipeline/execute`) X

### 11-4. Retention dateColumn 별 차이
- pm_gd970201 = `obsrvn_dt`
- if_rsv_sec_obsvdata = `obsv_date`
- jewon = 없음 (제외)
- 등록 시점 정합 점검

### 11-5. Internal Agent ThreadLocal
- Retention 외부 호출 시 ThreadLocal 비어있어 fallback DS 잘못 참조 가능. `targetDatasourceId` body 명시 필수

### 11-6. 1970 epoch 정렬 (5/6 fix)
- 일부 옛 이력의 startedAt = 1970-01-01 → 정렬 시 최상단으로 안 옴 (5/6 fix)

### 11-7. 관리 테이블 네이밍 정리 (별 사이클)
- 현 `agent`, `schedule` 등 → `agt_*` 프리픽스 통일 — todo 영역에 등록만 (미실행)

