# 프로시저 관리 모듈 — sync-orchestrator-procedure (port 8097)

> 작성일: 2026-05-06 (5/6 마지막 트랙) / 갱신: anonymous block 방식 + 의존 객체 책임 정리
> 동반: `todo/system/06-procedure-mgmt.md`

---

## 1. 결정 요약 (사용자 5/6)

- **별 모듈** 신설 — 책임 격리 + 위험 작업 분리. 시스템 패턴 정합 (auth/provider/collector 모두 별 모듈)
- **백그라운드 작업 없음** — 운영자 UI 클릭 시점에만 실행
- **이력 테이블 = Orchestrator PG 29001 공유**
- **Procedure target = 등록된 datasource 들** — Backend `datasource` 테이블의 외부 DB
- **운영자만** 호출 (JWT cookie). 새 procedure 등록은 **개발자 코드 영역**, 운영자는 실행/보기만
- ⭐ **외부 DB 에 procedure 객체 안 만듦 (휘발)** — anonymous block 으로 매 실행마다 동적 컴파일 + 호출
- ⭐ **원문 PL/SQL 100% 보존** — `resources/procedures/{name}.sql` 에 `CREATE OR REPLACE PROCEDURE ...` 그대로. 실행 시 자동으로 anonymous block 으로 wrap
- ⭐ **의존 객체 (테이블/sequence) = 외부 DB 의 기존 인프라** — 우리 시스템 책임 X. procedure 가 사용하는 객체는 운영 DB 에 이미 존재 가정 (DBA 영역)

## 2. 핵심 패턴 — anonymous block + 원문 보존

```java
public interface ProcedureHandler {
    ProcedureMetadata getMetadata();      // name/description/datasourceId/params/sourceText(원문)
    ProcedureResult   execute(Map<String, String> params, String calledBy);
}
```

`@Component` 자동 수집 → `ProcedureHandlerRegistry` (B4 의 CustomHandlerRegistry 패턴).

**기본 구현 = `BaseProcedureHandler`** — `getMetadata()` 만 구현하면 `execute()` 는 anonymous block wrap + JdbcTemplate 호출 자동 처리. 핸들러 클래스는 메타만 정의.

## 3. 모듈 구조

```
sync-orchestrator-procedure/
├── build.gradle              (BOM + auth 검증자 자산 + libs/sync-agent-common JAR)
├── application.yml           (port 8097, jasypt, JWT toggle, orchestrator PG 29001)
├── src/main/
│   ├── java/com/gims/procedure/
│   │   ├── ProcedureApplication.java
│   │   ├── config/SecurityConfig.java
│   │   ├── controller/{ProcedureManageController, ProcedureHistoryController}.java
│   │   ├── handler/
│   │   │   ├── ProcedureHandler.java         (인터페이스)
│   │   │   ├── BaseProcedureHandler.java    (공통 wrap+실행 로직)
│   │   │   ├── ProcedureMetadata.java        (record)
│   │   │   ├── ProcedureResult.java          (record)
│   │   │   ├── ProcedureParamSpec.java
│   │   │   ├── ProcedureHandlerRegistry.java
│   │   │   └── AnonymousBlockTranslator.java ⭐ — 원문 → anonymous block 변환
│   │   ├── service/
│   │   │   ├── ProcedureExecutionService.java
│   │   │   └── ProcedureDataSourceService.java
│   │   ├── entity/ProcedureCallHistory.java
│   │   ├── repository/ProcedureCallHistoryRepository.java
│   │   └── implementations/                  (실제 procedure 핸들러들)
│   │       ├── PcSpMvTodayDataHandler.java
│   │       ├── ProcPickZeroHandler.java
│   │       └── ...
│   └── resources/
│       └── procedures/                       ⭐ — PL/SQL 원문 그대로
│           ├── PC_SP_MV_TODAY_DATA.sql
│           ├── PROC_PICK_ZERO.sql
│           └── ...
```

> 의존 객체 (TMP_*, TT_*, SEQ_*) DDL 은 본 모듈에 두지 않음. 외부 DB 의 기존 인프라.

## 4. ⭐ Anonymous Block 변환 패턴

### 4.1 원문 보존 정책

`resources/procedures/{NAME}.sql` 에 PL/SQL 원문 그대로:
```sql
CREATE OR REPLACE PROCEDURE NGW.PC_SP_MV_TODAY_DATA IS
BEGIN
    DELETE FROM TMP_PM_GD970201_TODAY;
    COMMIT;
    INSERT INTO TMP_PM_GD970201_TODAY
    SELECT * FROM PM_GD970201 WHERE OBSR_DT >= ...;
    COMMIT;
END;
```

→ 사용자가 다른 procedure 마이그레이션 시 **표준화 컬럼명만 변환** + 그대로 복사. procedure 의 단위 보존.

### 4.2 변환 규칙 (실행 시)

`AnonymousBlockTranslator.wrap(sourceText, args)` 동작:

```
[원문]
CREATE OR REPLACE PROCEDURE [NGW.]PROC_NAME(p1, p2) IS
    <BODY>
END[ PROC_NAME];

      ↓ 변환

[anonymous block]
DECLARE
    PROCEDURE PROC_NAME(p1, p2) IS
        <BODY>
    END;
BEGIN
    PROC_NAME(?, ?);    -- bind: args 순서대로
END;
```

#### 변환 단계
1. `CREATE OR REPLACE PROCEDURE` → `PROCEDURE` (DECLARE 블록 시작 추가)
2. 스키마 prefix (`NGW.`) 제거
3. 마지막 `END[ name];` 뒤에 `BEGIN <name>(?, ?, ...); END;` 추가
4. 파라미터 bind = handler 의 `getMetadata().params` 순서대로

#### 정규식 / 파싱 규칙
```java
// 1. 헤더 매칭 + 캡처
Pattern.compile(
  "^\\s*CREATE\\s+OR\\s+REPLACE\\s+PROCEDURE\\s+(?:\\w+\\.)?(\\w+)\\s*",
  Pattern.CASE_INSENSITIVE
)
// 2. 헤더 → "PROCEDURE <name> "
// 3. 끝의 "END;" 또는 "END <name>;" 발견
// 4. CALL 부 추가
```

복잡도 — procedure 안에 nested SQL 의 `END` (`CASE WHEN ... END`, `LOOP ... END LOOP`) 와 procedure 종료 `END` 구분 필요. 가장 마지막 `END[ name];` 만 매칭 (마지막 줄 + 세미콜론 기준).

### 4.3 파라미터 처리

핸들러 메타에 박힌 `ProcedureParamSpec` 순서대로 prepared statement bind:

```java
ProcedureMetadata.builder()
    .params(List.of(
        new ProcedureParamSpec("P_GENNUM",  "VARCHAR2", true),
        new ProcedureParamSpec("P_SDATE",   "VARCHAR2", false),
        new ProcedureParamSpec("P_EDATE",   "VARCHAR2", false)
    ))
    .build();
```

호출 측 (운영자) → `{ "P_GENNUM": "GW-001", "P_SDATE": "20240101", "P_EDATE": "20240131" }` body. 핸들러가 `params` 순서대로 binding.

### 4.4 결과 + DBMS_OUTPUT 캡처

Anonymous block 안의 `DBMS_OUTPUT.PUT_LINE(...)` 캡처:

```java
// 실행 전 활성화
jdbc.execute("BEGIN DBMS_OUTPUT.ENABLE(1000000); END;");

// procedure 실행 (anonymous block)
jdbc.update(wrappedSql, args.toArray());

// 출력 라인 가져오기
List<String> outputLines = jdbc.execute(
    "DECLARE l_line VARCHAR2(32767); l_status NUMBER; ... END;",
    callableExtractor);
```

또는 OracleJDBC 의 `OracleConnection.getCallableOutput()` 사용. JdbcTemplate 의 `execute(ConnectionCallback)` 안에서 처리.

→ 운영자 화면 결과 영역에 `[DBMS_OUTPUT]` 섹션으로 표시 (procedures.txt 의 `DBMS_OUTPUT.PUT_LINE('정상적으로 처리되었습니다.')` 같은 출력).

## 5. DB — Orchestrator PG 29001 공유

### `procedure_call_history` 테이블 (신규, JPA 엔티티)
| 컬럼 | 타입 | 의미 |
|---|---|---|
| id | BIGSERIAL PK | |
| handler_key | VARCHAR(100) | procedure 핸들러 식별 |
| handler_name | VARCHAR(200) | 표시 이름 (snapshot) |
| datasource_id | VARCHAR(50) | 대상 datasource |
| called_by | VARCHAR(50) | authUsersId (실행자) |
| called_at | TIMESTAMP | 시작 시각 |
| params_json | TEXT | 입력 파라미터 (JSON) |
| status | VARCHAR(20) | SUCCESS / FAILED |
| duration_ms | BIGINT | |
| error_message | TEXT | 실패 시 |
| result_summary | TEXT | 영향 row 수 + DBMS_OUTPUT 요약 |
| dbms_output | TEXT | DBMS_OUTPUT.PUT_LINE 캡처 (긴 출력) |
| INDEX (handler_key, called_at), (called_by) |

ddl-auto=update 자동 생성.

## 6. Datasource 접근 패턴 (재사용)

api-provider 의 `ProviderDataSourceService` 동일:
1. `procedureDataSourceService.getJdbcTemplate(datasourceId)`
2. 캐시 miss → Internal Proxy(8093) `/api/datasources/{id}/connection-info` (X-API-Key)
3. ENC 자격증명 복호화 → HikariDataSource → JdbcTemplate
4. 캐시 hit 재사용

→ **자격증명 단일 진실원 = Backend 의 datasource 테이블** (메모리 룰 정합).

## 7. 보안 흐름

| 호출 | 인증 |
|---|---|
| 운영자 → procedure 모듈 `/api/procedures/...` | JWT cookie (Phase 2~5 패턴) |
| procedure 모듈 → Internal Proxy `/api/datasources/...` | X-API-Key (시스템 간) |
| Proxy → Backend connection-info | X-API-Key (5/6 ApiKeyFilter soft-mode 통과) |

procedure 모듈의 SecurityConfig:
- `/actuator/**` permitAll
- `/api/procedures/**` authenticated (JWT cookie)
- 그 외 denyAll

## 8. Frontend

### Sidebar 메뉴 추가
`Sidebar.tsx`:
```tsx
{ href: '/procedures', label: '프로시저', icon: '⚙️' },
```

### Route Group 안 신규 — `app/(main)/procedures/`
- `page.tsx` — 목록 (handler_key, name, datasource, 마지막 실행, 실행 횟수)
- `[handlerKey]/page.tsx` — 상세 + 실행 + 이력
  - 본문 보기 (PL/SQL 원문, syntax highlight 또는 단순 `<pre>`)
  - 파라미터 입력 폼 (메타의 ProcedureParamSpec 자동 생성)
  - [실행] 버튼
  - 결과 영역 (영향 row 수 + DBMS_OUTPUT 캡처)
  - 이력 (최근 20건 — 실행자/시각/duration/status)

### `lib/procedureApi.ts`
- `listProcedures()`, `getProcedure(handlerKey)`, `executeProcedure(handlerKey, params)`, `getHistory(handlerKey)`, `getAllHistory(page)`

### `next.config.js`
```js
{ source: '/procedure-api/:path*', destination: 'http://localhost:8097/api/:path*' }
```

## 9. Endpoint 매트릭스

| Method | Path | 동작 |
|---|---|---|
| GET | `/api/procedures` | 등록된 핸들러 목록 (메타) |
| GET | `/api/procedures/{handlerKey}` | 핸들러 상세 (본문 + 메타) |
| POST | `/api/procedures/{handlerKey}/execute` | 실행 (params body) → ProcedureResult + history 기록 |
| GET | `/api/procedures/{handlerKey}/history` | 핸들러별 최근 이력 |
| GET | `/api/procedures/history` | 전체 이력 (페이징) |

## 10. 작업 단위 + 추정

| Step | 내용 | 시간 |
|---|---|:--:|
| 1 | 모듈 신설 + build.gradle + yml + Application | 30분 |
| 2 | SecurityConfig + ApiKeyFilter (5/6 패턴 재사용) | 20분 |
| 3 | ProcedureHandler 인프라 (인터페이스 + Registry + Metadata/Result/ParamSpec) | 30분 |
| 4 | ⭐ **AnonymousBlockTranslator** (원문 → wrap, 정규식) + 단위 테스트 | 45분 |
| 5 | BaseProcedureHandler — 공통 실행 로직 (`.sql` resource 로드 + translate + jdbc.update + DBMS_OUTPUT 캡처) | 30분 |
| 6 | ProcedureCallHistory 엔티티 + Repository | 20분 |
| 7 | ProcedureDataSourceService (api-provider 패턴 복사) | 30분 |
| 8 | ProcedureExecutionService (실행 + 이력 기록 + 예외) | 30분 |
| 9 | ProcedureManageController + HistoryController | 30분 |
| 10 | 첫 핸들러 1~2개 (procedures.txt 의 PC_SP_MV_TODAY_DATA + PROC_PICK_ZERO 표준화 컬럼 변환) | 1h |
| 11 | Frontend procedureApi.ts + sidebar + 화면 | 1.5h |
| 12 | next.config.js rewrite | 5분 |
| 13 | E2E 검증 (운영자 cookie + 실행 + 이력 + DBMS_OUTPUT 표시) | 30분 |
| **합계** | — | **~6h** |

## 11. 제약 / 한계

1. **의존 객체 외부 DB 에 사전 존재 필수** — TMP_*, TT_*, SEQ_* 등 procedure 가 사용하는 객체는 외부 DB (운영 GIMS DB) 의 기존 인프라. 우리 dev 환경에서 검증하려면 internal-oracle 에 별도로 박혀있어야 함 (DBA 영역, 본 모듈 책임 X)
2. **Oracle 우선** — anonymous block 패턴은 Oracle 의 PL/SQL inner procedure 정의 활용. PG 의 `DO $$ ... $$;` 도 비슷하게 가능. MySQL 은 inner procedure 정의 X (별 사이클)
3. **성능** — 매 실행마다 PL/SQL parse + compile. 자주 호출 안 하는 운영 작업이라 무시 가능
4. **버전/변경 이력** — procedure 본문 변경 = 코드 git 커밋. 별 history 테이블 없음 (실행 이력만)

## 12. 결정 필요 항목

1. **모듈 이름 / port** — `sync-orchestrator-procedure` / `8097` OK?
2. **DBMS_OUTPUT 캡처** — Oracle PL/SQL 의 `DBMS_OUTPUT.PUT_LINE` 결과 캡처해서 운영자 화면 표시?
3. **첫 박을 procedure** — `PC_SP_MV_TODAY_DATA` (단순) + `PROC_PICK_ZERO` (파라미터 3개) 두 개? 또는 1개만?
4. **표준화 컬럼 변환** — procedures.txt 의 본문 컬럼명을 표준화 자료대로 변환해서 `.sql` 작성. 변환 작업 ~30~60분 추가
5. **타임아웃** — 긴 procedure 위해 JdbcTemplate timeout 박을지 (기본 60s 권장 + 핸들러 메타 override)
