# 실행 엔진과 트랜잭션 (Execution Engine & Transaction)

> 이 문서는 GIMS 동기화 시스템에서 **데이터를 실제로 읽고 쓰는 핵심 엔진**의 기술을 설명합니다.
> "한 건이 실패해도 나머지는 살리는" 안전한 데이터 적재 방식을 이해할 수 있도록 작성했습니다.

---

## 전체 그림

```
데이터 소스 (외부 API / 외부 DB)
    ↓  데이터 읽기
실행 엔진
    ↓  변환 + 적재
타겟 DB (INSERT 또는 UPSERT)
    ↓  결과 기록
실행 이력 (성공 N건, 실패 M건, 소요시간)
```

시스템에는 **두 종류의 실행 엔진**이 있다:

| 엔진 | 데이터 소스 | 적재 방식 |
|------|-----------|----------|
| Agent 파이프라인 (SourceToIfStep, LoaderStep) | 외부 DB | 배치 UPSERT (수백~수만 건) |
| API Collector (ApiExecutionService) | 외부 REST API | 행 단위 INSERT/UPSERT |

---

## 핵심 개념

### 1. 트랜잭션 (Transaction)

DB에 여러 건을 쓸 때, "전부 성공하거나 전부 취소하거나" 보장하는 장치다.

```
트랜잭션 시작
  INSERT 1건  ← 성공
  INSERT 2건  ← 성공
  INSERT 3건  ← 실패!
트랜잭션 롤백 → 1건, 2건도 취소됨 (DB에 아무 변화 없음)
```

이 동작이 기본인데, 우리 시스템에서는 **한 건 실패 때문에 나머지가 다 취소되면 곤란하다**. 외부에서 100건 받아왔는데 3건만 형식이 잘못됐다고 97건까지 버리면 낭비다.

그래서 두 가지 해법을 쓴다: **Savepoint**와 **Batch Fallback**.

---

### 2. Savepoint — 행 단위 안전망

Savepoint는 우리가 만든 기능이 아니라 **SQL 표준(SQL:1999)**에 정의된 기능이다. Java에서는 `java.sql.Connection`이 API로 제공한다(`setSavepoint()`, `rollback(sp)`, `releaseSavepoint(sp)`). 대부분의 RDBMS(PostgreSQL, MySQL, Oracle 등)가 지원한다.

트랜잭션 안에 **중간 저장점**을 설정하여, 실패 시 트랜잭션 전체가 아니라 그 저장점까지만 되돌릴 수 있다.

**API Collector**가 이 방식을 쓴다:

```
트랜잭션 시작
  │
  ├─ Savepoint 설정 ──── INSERT 1건 ── 성공 → Savepoint 해제, insertCount++
  │
  ├─ Savepoint 설정 ──── INSERT 2건 ── 성공 → Savepoint 해제, insertCount++
  │
  ├─ Savepoint 설정 ──── INSERT 3건 ── 실패! → Savepoint까지만 롤백, skipCount++
  │                                              (1건, 2건은 살아있음)
  │
  ├─ Savepoint 설정 ──── INSERT 4건 ── 성공 → Savepoint 해제, insertCount++
  │
  커밋 → 1건, 2건, 4건 저장 완료 (3건만 건너뜀)
```

**왜 PostgreSQL에서 특히 중요한가**: PostgreSQL은 트랜잭션 안에서 에러가 한 번이라도 나면, 그 트랜잭션 전체가 "중단 상태(aborted)"가 되어 이후 모든 쿼리가 거부된다. Savepoint 없이는 한 건 실패 = 전체 실패다.

```
PostgreSQL 기본 동작 (Savepoint 없이):
  INSERT 1건 ← 성공
  INSERT 2건 ← 실패!
  INSERT 3건 ← 에러: "current transaction is aborted" (실행 자체 불가)
  → 전체 롤백
```

Savepoint를 쓰면 실패한 행만 롤백하고 트랜잭션을 계속 이어갈 수 있다.

> **연관 파일**: `infolink-api-collector/.../service/ApiExecutionService.java` — Savepoint 패턴 사용

---

### 3. Batch UPSERT + Fallback — 대량 데이터의 안전 적재

Agent 파이프라인은 **배치(batch)** 방식을 쓴다. 수백 건을 한 번에 묶어서 보내면 DB 왕복 횟수가 줄어 훨씬 빠르다.

```
일반 INSERT:
  DB에 1건 보내고 응답 받기 × 1000회 = 1000번 왕복

배치 INSERT:
  DB에 500건 묶어서 보내기 × 2회 = 2번 왕복
```

그런데 배치는 **한 건이라도 실패하면 묶음 전체가 실패**한다. 이를 해결하기 위해 **Fallback 전략**을 쓴다:

```
Phase 1: 배치로 시도 (500건 묶음)
  ├→ 성공 → 500건 한 번에 적재 완료
  └→ 실패 → Phase 2로 전환

Phase 2: 개별로 재시도 (1건씩)
  ├→ 1건: 성공 → writeCount++
  ├→ 2건: 실패 → skipCount++ (이 건만 건너뜀)
  ├→ 3건: 성공 → writeCount++
  └→ ... 계속
```

이렇게 하면 **정상 상황에서는 배치의 속도**를, **오류 상황에서는 개별 처리의 안전성**을 모두 확보한다.

> **연관 파일**: `sync-agent-common/.../step/SourceToIfStep.java` — batchUpdate() + catch 블록에서 개별 fallback

---

### 4. INSERT vs UPSERT — 동기화 전략에 따른 선택

#### 기본 전략: INSERT

우리 시스템의 정상적인 동기화 흐름에서는 **INSERT만 발생한다**. 중복이 애초에 일어나지 않도록 설계되어 있기 때문이다.

핵심은 `link_status` 컬럼이다. 모든 동기화 대상 테이블에 이 컬럼이 있고, 데이터를 조회할 때 **PENDING 또는 RESYNC 상태인 것만** 가져온다. 한 번 처리된 데이터는 SUCCESS로 바뀌어 다음 동기화에서 조회 대상에서 빠진다.

```
자동 동기화 흐름:
  SELECT * FROM source WHERE link_status IN ('PENDING', 'RESYNC')
  → 이미 처리된 건(SUCCESS)은 안 가져옴
  → 중복 발생할 일이 없음
  → INSERT로 충분
```

#### UPSERT가 필요한 경우 (두 가지)

**1. 수동 실행에서 사용자가 조건을 직접 지정할 때**

UI에서 시간 범위나 obsv_code 등을 직접 지정하여 수동 실행하면, link_status 필터를 우회하게 된다. 이 경우 이미 처리된 데이터가 다시 들어올 수 있으므로 UPSERT가 필요하다.

```
수동 실행 (조건 지정):
  SELECT * FROM source WHERE obsv_date BETWEEN '2026-03-01' AND '2026-03-24'
  → link_status와 무관하게 조회
  → 이미 적재된 데이터가 포함될 수 있음
  → UPSERT로 중복 안전 처리
```

**2. 제원(jewon) 데이터 — 예외적 케이스**

관측소 기본정보(이름, 위치, 심도 등)는 새로운 행이 추가되는 것이 아니라 **같은 관측소 정보가 매번 반복**해서 들어온다. 관측소가 새로 생기는 일은 드물고, 기존 관측소의 정보가 갱신되는 것이 일반적이다. 따라서 항상 UPSERT로 처리한다.

#### UPSERT SQL 문법 (DB별)

DB 종류에 따라 문법이 다르다:

**PostgreSQL**:
```sql
INSERT INTO 테이블 (id, name, value) VALUES (1, '홍길동', 100)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  value = EXCLUDED.value;
```
- `ON CONFLICT (id)`: id가 중복되면
- `EXCLUDED`: 새로 넣으려던 값을 가리킴

**MySQL**:
```sql
INSERT INTO 테이블 (id, name, value) VALUES (1, '홍길동', 100)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  value = VALUES(value);
```
- `ON DUPLICATE KEY`: PK/UK 중복되면
- `VALUES(name)`: 새로 넣으려던 값을 가리킴

우리 시스템은 PostgreSQL과 MySQL을 모두 지원하므로, SQL 생성 시 DB 종류를 확인하고 적절한 문법을 선택한다.

### UPSERT의 두 가지 모드

| 모드 | 동작 | 용도 |
|------|------|------|
| Full UPSERT | 모든 데이터 컬럼 갱신 | 데이터를 최신으로 덮어쓰기 |
| 경량 UPDATE | 메타 컬럼(updated_at, execution_id)만 갱신 | 이미 있는 데이터는 보존, 실행 추적만 |

> **연관 파일**
> - `sync-agent-common/.../step/SourceToIfStep.java` — buildUpsertSql() (PG ON CONFLICT / MySQL ON DUPLICATE KEY 분기)
> - `infolink-api-collector/.../service/ApiExecutionService.java` — PG ON CONFLICT SQL 생성
> - `sync-agent-bojo/.../loader/repository/TargetRepositoryService.java` — batchUpsertJewon(), batchUpsertObsvData()

---

### 5. PreparedStatement — SQL 주입 방지 + 성능

> `PreparedStatement`는 JDBC 표준(`java.sql`)이 제공하는 인터페이스다. 모든 RDBMS 드라이버가 구현한다.

DB에 쿼리를 보낼 때 값을 직접 문자열로 넣으면 위험하다:

```sql
-- 위험: 사용자 입력이 SQL로 실행될 수 있음 (SQL Injection)
"INSERT INTO users VALUES ('" + userName + "')"
-- userName에 "'; DROP TABLE users;--" 가 들어오면?
```

PreparedStatement는 **SQL 구조와 값을 분리**한다:

```sql
-- 안전: ?는 항상 "값"으로만 처리됨
PreparedStatement ps = conn.prepareStatement("INSERT INTO users VALUES (?)");
ps.setObject(1, userName);  // userName이 뭐든 문자열 값으로만 들어감
```

성능 이점도 있다: DB가 SQL 구조를 한 번만 파싱하고, 이후에는 값만 바꿔서 반복 실행할 수 있다. 수천 건을 넣을 때 큰 차이가 난다.

> **연관 파일**
> - `infolink-api-collector/.../service/ApiExecutionService.java` — ps.setObject() 패턴
> - `sync-agent-common/.../step/SourceToIfStep.java` — JdbcTemplate 내부에서 PreparedStatement 사용

---

### 6. @Transactional — Spring의 선언적 트랜잭션

> `@Transactional`은 Spring Framework가 제공하는 애노테이션이다. 내부적으로 JDBC의 `commit()`/`rollback()`을 AOP 프록시로 자동 호출한다.

메서드에 `@Transactional` 애노테이션을 붙이면 Spring이 자동으로 트랜잭션을 관리한다:

```java
@Transactional
public void createSchedule(Schedule schedule) {
    scheduleRepository.save(schedule);    // ← 이 사이가 하나의 트랜잭션
    scheduleExecutor.register(schedule);  // ← 여기서 에러나면 save도 롤백
}
```

개발자가 직접 `connection.commit()`, `connection.rollback()`을 쓸 필요가 없다.

**읽기 전용 최적화**:
```java
@Transactional(readOnly = true)
public List<Schedule> getAll() { ... }
```
`readOnly = true`는 DB에게 "이 트랜잭션은 읽기만 한다"고 알려서 내부 최적화를 유도한다.

**우리 시스템에서의 사용 분류**:

| 상황 | 방식 | 이유 |
|------|------|------|
| 일반 CRUD (스케줄 생성, 이력 저장) | `@Transactional` | 간편하고 안전 |
| 대량 데이터 적재 (Agent, API Collector) | 수동 Connection 관리 | Savepoint 제어, 외부 DB 연결 등 세밀한 제어 필요 |

> **연관 파일 (@Transactional 사용)**
> - `sync-orchestrator/backend/.../schedule/ScheduleService.java` — 스케줄 CRUD
> - `sync-orchestrator/backend/.../execution/ExecutionService.java` — 실행 트리거
> - `sync-agent-common/.../service/ExecutionService.java` — 실행 이력 기록
> - `infolink-api-collector/.../service/ApiEndpointService.java` — Endpoint CRUD
> - `infolink-api-collector/.../service/ApiScheduleService.java` — 스케줄 CRUD

---

## 실행 엔진별 상세

### API Collector 실행 엔진 [infolink-api-collector]

```
ApiExecutionService.run(endpointId)
  │
  ├─ 1. 검증: dataRootPath, fieldMappings, targetTableName 확인
  │
  ├─ 2. 실행 이력 생성 (RUNNING)
  │
  ├─ 3. HTTP로 외부 API 호출 → JSON 응답
  │
  ├─ 4. JSON 파싱 → List<Map> (레코드 목록)
  │
  ├─ 5. 매핑 분리
  │     ├─ 일반 매핑: JSON 필드 → DB 컬럼 (1:1 변환)
  │     └─ 파생 매핑: LOOKUP으로 코드 치환
  │
  ├─ 6. SQL 생성 (INSERT 또는 UPSERT)
  │
  ├─ 7. 행 단위 INSERT (Savepoint 방식)
  │     ├─ 성공 → insertCount++
  │     └─ 실패 → skipCount++ (다음 행 계속)
  │
  └─ 8. 이력 업데이트 (SUCCESS/FAILED, 소요시간)
```

**특징**: 행 단위 Savepoint로 개별 실패 격리. API 응답이 보통 수십~수백 건이라 이 방식으로 충분.

> **연관 파일**
> - `infolink-api-collector/.../service/ApiExecutionService.java` — run() 메서드
> - `infolink-api-collector/.../service/ApiCallService.java` — HTTP 호출
> - `infolink-api-collector/.../service/ResponseParser.java` — JSON 파싱
> - `infolink-api-collector/.../service/DataTransformer.java` — 값 변환
> - `infolink-api-collector/.../service/LookupService.java` — LOOKUP 파생

### Agent 파이프라인 실행 엔진 [sync-agent-common의 SourceToIfStep]

```
SourceToIfStep.execute()
  │
  ├─ 1. 소스 DB에서 데이터 조회 (JPA/JDBC)
  │
  ├─ 2. 파라미터 준비 (source_refs, link_status, execution_id 부착)
  │
  ├─ 3. 배치 UPSERT 실행
  │     ├─ 성공 → writeCount += 배치 크기
  │     └─ 실패 → 개별 INSERT Fallback
  │           ├─ 성공 → writeCount++
  │           └─ 실패 → skipCount++, failedKeys에 기록
  │
  ├─ 4. 소스 테이블 link_status 업데이트
  │     ├─ 성공 건 → "SUCCESS"
  │     └─ 실패 건 → "FAILED"
  │
  └─ 5. SyncLog 기록 (매핑별 read/write/skip 통계)
```

**특징**: 배치 우선 + 개별 Fallback. 수천~수만 건 처리에 최적화.

> **연관 파일**
> - `sync-agent-common/.../step/SourceToIfStep.java` — execute(), batchUpdate + fallback
> - `sync-agent-bojo/.../loader/step/DmzBojoLoadStep.java` — DMZ Loader Step
> - `sync-agent-bojo/.../loader/step/LoaderStepHelper.java` — processJewon(), processObsvdata()
> - `sync-agent-bojo/.../loader/repository/TargetRepositoryService.java` — batch UPSERT SQL

---

## link_status와 source_refs — 데이터 추적 장치

동기화 데이터가 어디서 왔고, 어디까지 처리됐는지 추적하는 장치다.

### link_status (처리 상태)

```
PENDING  → 새로 수집됨, 아직 다음 단계로 안 넘어감
RESYNC   → 수동 재동기화 요청됨
SUCCESS  → 다음 단계로 정상 전달 완료
FAILED   → 전달 실패 (변환 오류 등)
```

### source_refs (출처 추적)

데이터가 어느 DB의 어느 테이블의 어느 행에서 왔는지를 JSON으로 기록한다.

```json
["dmz:ds001:if_rsv_obsvdata:12345"]
 ───  ────  ──────────────  ─────
 zone  DB ID    테이블명      PK값
```

이 정보로 **역추적**이 가능하다: Target 데이터 → source_refs 확인 → 원본 소스 조회.

---

## 트랜잭션 패턴 요약

| 패턴 | 사용처 | 기술 | 에러 시 동작 |
|------|--------|------|-------------|
| Savepoint 개별 롤백 | API Collector | 수동 Connection + Savepoint | 해당 행만 롤백, 나머지 계속 |
| 배치 + Fallback | Agent 파이프라인 | JdbcTemplate.batchUpdate() | 배치 실패 → 개별 재시도 |
| @Transactional | CRUD 서비스 | Spring AOP | 메서드 전체 롤백 |
| ON CONFLICT (PG) | UPSERT | PostgreSQL 네이티브 | PK 충돌 → UPDATE |
| ON DUPLICATE KEY (MySQL) | UPSERT | MySQL 네이티브 | PK 충돌 → UPDATE |
