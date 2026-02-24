# GIMS Orchestrator v2 - 기능 동작 명세

> 파이프라인 실행 동작과 테스트 검증 기준을 정리한 문서.
> 구조/설계는 `ARCHITECTURE.md`, 화면은 `UI_GUIDE.md` 참조.

---

## 1. 파이프라인 동작 요약

### 1.1 전체 흐름

```
외부 DB ──[RCV]──> IF_RSV ──[Loader]──> Target ──[SND]──> IF_SND
          수집           적재             송신 준비
```

- 하나의 물리적 앱(`sync-agent-bojo:8082`)에서 `agentCode`로 12개 논리적 Agent 라우팅
- Orchestrator(`8080`)가 Agent에 HTTP로 실행 요청, Agent가 콜백으로 결과 보고

### 1.2 RCV (수집)

외부 Source DB에서 데이터를 추출하여 IF_RSV 테이블에 적재.

| 항목 | 동작 |
|------|------|
| **Step 1: 제원 (jewon)** | Source `sec_jewon_view` → IF `if_rsv_sec_jewon` |
| 모드 | `full-copy: true` → 매 실행마다 전체 UPSERT |
| 충돌 기준 | `conflict-key: source_refs` |
| Source 상태 업데이트 | `skip-source-status-update: true` → 안 함 |
| **Step 2: 관측데이터 (obsvdata)** | Source `sec_obsvdata_view` → IF `if_rsv_sec_obsvdata` |
| 모드 | Link 테이블 기반 증분 동기화 |
| 동작 | `link_ngwis`에서 obsv_code별 마지막 동기화 시점 조회 → 이후 데이터만 추출 |
| 충돌 기준 | `conflict-key: source_refs` |
| **Step 3: Link 업데이트** | IF obsvdata에서 max(date, time) 계산 → `link_ngwis` UPSERT |

**증분 동기화 흐름 (obsvdata):**
```
1. link_ngwis에서 obsv_code별 last_date/last_time 조회
2. Source에서 해당 시점 이후 데이터만 SELECT
3. IF 테이블에 INSERT (ON CONFLICT → 경량 UPDATE: updated_at, execution_id만)
4. link_ngwis에 새 max 시점 기록
```

**기간 지정 실행 (재동기화):**
```
1. link_ngwis 무시 (시점 체크 건너뜀)
2. 지정 범위 전체 SELECT
3. IF 테이블에 UPSERT (ON CONFLICT → 전체 컬럼 UPDATE)
4. link_status = RESYNC으로 설정 (하류 파이프라인에 전파)
```

### 1.3 Loader (적재)

IF_RSV 테이블에서 Target 테이블로 데이터 이동.

| 항목 | 동작 |
|------|------|
| 입력 | `if_rsv_sec_jewon`, `if_rsv_sec_obsvdata` (link_status가 PENDING/RESYNC인 것) |
| 출력 | `sec_jewon`, `sec_obsvdata` (Target DB) |
| 방식 | JDBC batch UPSERT (ON CONFLICT) |
| 완료 후 | IF 테이블의 link_status를 SUCCESS/FAILED로 업데이트 |

### 1.4 SND (송신 준비)

Target 테이블에서 IF_SND 테이블로 추출 (외부 전송 대비).

| 항목 | 동작 |
|------|------|
| 입력 | `sec_jewon`, `sec_obsvdata` (Target 테이블) |
| 출력 | `if_snd_sec_jewon`, `if_snd_sec_obsvdata` |
| 방식 | `SourceToIfStep` SIMPLE_COPY (1:1 매핑) |

---

## 2. 실행 모드

### 2.1 수동 실행 (MANUAL)

```
POST /api/executions/{agentId}/run
Body: 없음 (증분) 또는 {"startTime":"...", "endTime":"..."}
```

- Body 없음: 증분 동기화 (link_status 기반)
- Body 있음: 기간 지정 재동기화

### 2.2 스케줄 실행 (SCHEDULE)

```
POST /api/schedules
Body: {"agentId": 7, "cronExpression": "0 */5 * * * *", "isEnabled": true}
```

- Spring `TaskScheduler` + `CronTrigger`로 주기 실행
- 앱 시작 시 활성 스케줄 자동 로드
- **중복 방지**: Agent가 RUNNING 상태면 해당 스케줄 실행 건너뜀 (예외 로그만 남김)
- `triggeredBy: SCHEDULE`로 기록

### 2.3 체인 실행 (CHAIN)

- Agent 체인 정의 후 순차 실행 (SEQUENTIAL) 또는 개별 실행 (INDIVIDUAL)
- 현재: 엔티티 정의 완료, 실행기 미구현

---

## 3. 건수 계산 규칙

### 3.1 SyncLog 건수 의미

| 필드 | SOURCE 테이블 | IF 테이블 |
|------|--------------|----------|
| `successCount` | IF에 실제 적재된 건수 (=writeCount) | 동일 |
| `failedCount` | 적재 실패 건수 | 동일 |
| `skipCount` | 이미 존재하여 건너뛴 건수 | 동일 |
| `totalCount` | **successCount + failedCount** (skip 미포함) | 동일 |

- **SOURCE와 IF의 건수는 항상 일치**
- `totalCount`에 skip은 포함하지 않음 (실제 처리 건수만 반영)
- `readCount`(Source에서 읽은 건수)는 SyncLog에 별도 저장하지 않음

### 3.2 시나리오별 예상 건수

#### 첫 실행 (데이터 400건 기준)

| 테이블 | 건수 | 성공 | 실패 | skip |
|--------|------|------|------|------|
| SOURCE jewon_view | 400 | 400 | 0 | 0 |
| IF if_rsv_sec_jewon | 400 | 400 | 0 | 0 |
| SOURCE obsvdata_view | N | N | 0 | 0 |
| IF if_rsv_sec_obsvdata | N | N | 0 | 0 |

#### 반복 실행 (신규 데이터 없음)

| 테이블 | 건수 | 성공 | 실패 | skip |
|--------|------|------|------|------|
| SOURCE jewon_view | 400 | 400 | 0 | 0 |
| IF if_rsv_sec_jewon | 400 | 400 | 0 | 0 |
| SOURCE obsvdata_view | **0** | 0 | 0 | M |
| IF if_rsv_sec_obsvdata | **0** | 0 | 0 | M |

- 제원: `full-copy`이므로 매번 전체 UPSERT → 항상 성공
- 관측데이터: 증분이므로 신규 없으면 skip만 발생, 건수=0

#### 반복 실행 (신규 6건 추가)

| 테이블 | 건수 | 성공 | 실패 | skip |
|--------|------|------|------|------|
| SOURCE jewon_view | 400 | 400 | 0 | 0 |
| IF if_rsv_sec_jewon | 400 | 400 | 0 | 0 |
| SOURCE obsvdata_view | **6+** | 6+ | 0 | M |
| IF if_rsv_sec_obsvdata | **6+** | 6+ | 0 | M |

- link 테이블 기반이므로 obsv_code별 최신 이후 데이터가 있으면 추출
- 기존 데이터는 ON CONFLICT으로 skip 처리

#### 데이터 없는 실행 (records=0)

| 테이블 | 건수 | 성공 | 실패 | skip |
|--------|------|------|------|------|
| SOURCE | 0 | 0 | 0 | 0 |
| IF | 0 | 0 | 0 | 0 |

- Source에서 0건 조회 시 조기 반환 (skip도 0)

---

## 4. 실행 상태 흐름

### 4.1 Agent 상태

```
ONLINE ──(실행 트리거)──> RUNNING ──(완료 콜백)──> ONLINE
                                  ──(실패 콜백)──> ONLINE
```

- RUNNING 상태에서 추가 실행 요청 → `IllegalStateException` (거부)
- 콜백 실패 시 RUNNING 상태로 남을 수 있음 (수동 리셋 필요)

### 4.2 ExecutionHistory 상태

```
(started 콜백) RUNNING ──(finished 콜백)──> SUCCESS / FAILED
```

### 4.3 executionId 형식

```
{agentCode}_{UUID}
예: dmz-bojo-rcv-daejeon_550e8400-e29b-41d4-a716-446655440000
```

---

## 5. API 엔드포인트

### 5.1 Orchestrator (8080)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/agents` | Agent 목록 |
| GET | `/api/agents/{id}` | Agent 상세 |
| POST | `/api/executions/{agentId}/run` | 실행 트리거 |
| GET | `/api/executions/history` | 실행 이력 (page, size) |
| GET | `/api/executions/{executionId}/detail` | 실행 상세 (Agent DB 프록시) |
| GET | `/api/executions/{executionId}/tables` | 테이블별 통계 (Agent DB 프록시) |
| GET | `/api/schedules` | 스케줄 목록 |
| POST | `/api/schedules` | 스케줄 생성 |
| PUT | `/api/schedules/{id}` | 스케줄 수정 |
| PUT | `/api/schedules/{id}/toggle` | 스케줄 활성/비활성 토글 |
| GET | `/api/datasources` | DB 목록 |
| POST | `/api/datasources/{id}/test` | DB 연결 테스트 |

### 5.2 Agent (8082)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/pipeline/execute` | 파이프라인 실행 (Orchestrator가 호출) |
| GET | `/api/execution-data/{execId}/summary` | 실행 요약 (성공/실패/skip 합계) |
| GET | `/api/execution-data/{execId}/tables` | 테이블별 SyncLog 통계 |
| GET | `/api/execution-data/{execId}/source` | Source 테이블 데이터 조회 (페이징) |
| GET | `/api/execution-data/{execId}/if` | IF 테이블 데이터 조회 (페이징) |
| GET | `/api/execution-data/{execId}/target` | Target 테이블 데이터 조회 (페이징) |
| GET | `/api/execution-data/{execId}/trace` | PK 기반 데이터 추적 (Source→IF→Target) |

---

## 6. DB별 특이사항

### 6.1 큰산 (ext_keunsan) - PostgreSQL 대문자

| 항목 | 값 |
|------|-----|
| 테이블명 | `SEC_JEWON_VIEW`, `SEC_OBSVDATA_VIEW` (대문자) |
| 컬럼명 | `ID`, `OBSV_CODE`, `OBSV_DATE` 등 (대문자) |
| 주의 | PostgreSQL에서 `"SEC_JEWON_VIEW"` (따옴표 필수), 따옴표 없으면 소문자로 정규화 |

코드 대응:
- `JdbcTableNameResolver`: 대소문자 변형 시도하여 실제 테이블명 해석
- `findActualColumnName`: 대소문자 무시 비교로 실제 컬럼명 반환
- Source 상세조회: `findActualColumnName(jdbc, table, "id")` → `"ID"` 자동 해석

### 6.2 MySQL Agent (인포월드, 하이드로넷)

| 항목 | 값 |
|------|-----|
| 포트 | 29010 (Docker) |
| 인용 부호 | 백틱 `` ` `` (PG는 `"`) |
| 시간 결합 | `TIMESTAMP(date, time)` (PG는 `date + time`) |

코드 대응:
- `isMysql()` 헬퍼로 DB 타입 분기
- `qi(name, dbType)` 헬퍼로 인용 부호 자동 선택

### 6.3 IF/Target DB (if_dmz) - PostgreSQL 소문자

| 항목 | 값 |
|------|-----|
| 포트 | 29001 (dev DB) |
| 테이블명 | 모두 소문자 (`if_rsv_sec_jewon`, `link_ngwis` 등) |
| 특이사항 | 없음 (표준 PostgreSQL) |

---

## 7. 테스트 검증 체크리스트

### 7.1 RCV 기본 실행

- [ ] 제원: SOURCE/IF 모두 `성공=N, 실패=0`
- [ ] 관측데이터: 신규 데이터만 추출 (link 테이블 시점 이후)
- [ ] SOURCE와 IF 건수 일치
- [ ] `totalCount`에 skip 미포함 (skip은 별도 필드)
- [ ] link_ngwis에 최신 date/time 갱신 확인

### 7.2 반복 실행 (신규 데이터 없음)

- [ ] 제원: 매번 전체 UPSERT → `성공=400`
- [ ] 관측데이터: `건수=0, 성공=0, skip=M`
- [ ] SOURCE obsvdata도 `건수=0` (IF와 일치)

### 7.3 신규 데이터 추가 후 실행

- [ ] 추가한 건수만큼 obsvdata `성공` 증가
- [ ] IF 테이블에 신규 데이터 존재 확인
- [ ] link_ngwis 시점 업데이트 확인

### 7.4 스케줄 실행

- [ ] 설정한 cron 주기에 자동 실행
- [ ] `triggeredBy: SCHEDULE` 기록
- [ ] Agent RUNNING 중 스케줄 → 실행 건너뜀 (에러 아님)
- [ ] 스케줄 비활성화 후 실행 중단

### 7.5 큰산 (대문자 DB)

- [ ] SOURCE 상세조회: `column "id" does not exist` 에러 없음
- [ ] 대문자 테이블/컬럼 정상 조회
- [ ] IF 테이블 적재 정상 (소문자 IF 테이블에 정상 매핑)

### 7.6 기간 지정 실행 (RESYNC)

- [x] 지정 범위의 데이터만 추출
- [x] `ON CONFLICT (source_refs)` UPSERT로 기존 데이터 전체 컬럼 갱신
- [x] link_status = RESYNC으로 설정
- [x] SOURCE/IF 건수 일치 (대전 6/6, 인포로컬 6/6)
- [x] Loader → Target, SND → IF_SND까지 업데이트 값 전파 확인

**주의**: obsvdata의 UPSERT conflict-key는 반드시 `source_refs`여야 함.
`id`(auto-increment)로 하면 새 ID가 기존 행과 충돌하지 않아 UK 위반 발생.

### 7.7 데이터 추적 (Trace)

- [x] RCV: Source PK → IF 테이블 매칭 (source_refs 기반)
- [x] Loader: IF_RSV PK → Target 매칭 (순방향 trace)
- [x] Loader: Target → IF_RSV 역추적 (source_refs fallback으로 매칭)
- [x] SND: Target PK → IF_SND 매칭 (순방향 trace)
- [x] SND: IF_SND → Target 역추적 (source_refs PK 직접 매칭)

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-24 | 초안 작성 |
| 2026-02-24 | 기간 지정 실행, Trace 검증 결과 반영 |
