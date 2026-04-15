# GIMS API Provider — 내부망 데이터 제공 API 시스템

> 작성일: 2026-04-14, 업데이트: 2026-04-15
> 상태: 계획
> 전략 확인: ARCHITECTURE.md, todo/system/05-api-provide.md

## 개요

GIMS 내부망 DB에 적재된 데이터를 외부 시스템에 REST API로 제공하는 독립 모듈.
**코드 변경 없이 UI 등록만으로** 새 API 엔드포인트를 동적 노출한다.

api-collector(수집)의 대칭 모듈로, 동일한 설계 철학을 따른다.

## 설계 원칙

1. **100% 동적** — 복잡한 쿼리는 전처리 테이블로 정제, 제공 시스템은 단순 SELECT만
2. **코드 변경 0** — 새 API 추가 = UI에서 등록만
3. **레거시 호환** — MEGOKR/가뭄119/OPN 3종 API를 이 시스템으로 이식 가능

## 레거시 적용 전략

| 레거시 API | 원본 SQL | 전처리 | 제공 시스템 |
|-----------|---------|--------|-----------|
| 가뭄119 | 단순 SELECT | 불필요 | 바로 등록 |
| MEGOKR NGW_08/09 | 단순 SELECT | 불필요 | 바로 등록 |
| MEGOKR NGW_03 | 서브쿼리 | 전처리 테이블 적재 | 정제 테이블로 등록 |
| MEGOKR NGW_04 | PIVOT 125컬럼 | 전처리 테이블 적재 | 정제 테이블로 등록 |
| OPN observation | DBLINK | 배치로 로컬 적재 | 정제 테이블로 등록 |
| OPN waterQuality | 동적 CASE WHEN | 전처리 테이블 적재 | 정제 테이블로 등록 |

> 전처리 = 기존 Loader/배치 패턴으로 스케줄 실행 → 정제 테이블 적재

## 모듈 구조

```
gims-api-provider/          # 독립 Spring Boot 모듈
├── src/main/java/com/gims/provider/
│   ├── entity/              # 엔티티 (PG orchestrator DB, api_prv_ 프리픽스)
│   │   ├── ApiPrvOperation.java       # 오퍼레이션 (API 단위)
│   │   ├── ApiPrvOperationColumn.java # 제공 컬럼 정의
│   │   ├── ApiPrvOperationParam.java  # WHERE 파라미터 정의
│   │   ├── ApiPrvKeyInfo.java         # API Key 관리
│   │   └── ApiPrvCallHistory.java     # 호출 이력
│   ├── repository/
│   ├── service/
│   │   ├── ApiProviderService.java    # 동적 쿼리 생성 + 실행 (Oracle 직접)
│   │   ├── ApiKeyService.java         # API Key 검증
│   │   └── ApiCallHistoryService.java # 호출 이력 관리
│   ├── controller/
│   │   ├── ApiManageController.java   # 관리 API (CRUD, 테스트)
│   │   └── ApiGatewayController.java  # 외부 노출 API (동적 라우팅)
│   └── config/
│       ├── OracleDataSourceConfig.java  # 기본 DB (내부망 Oracle, 직접)
│       └── PgDataSourceConfig.java      # 관리 DB (내부망 PG orchestrator, 직접)
├── src/main/resources/
│   └── application.yml
└── build.gradle
```

## 위치 & 포트

| 항목 | 값 |
|------|-----|
| 위치 | **내부망** |
| 포트 | 8095 |
| 기본 DB | **내부망 Oracle 29004** — 직접 접근 (제공용 테이블 + 동적 SELECT) |
| 관리 DB | **PG orchestrator** — Proxy 경유 (`api_prv_` 프리픽스) |
| Proxy | 관리 DB(PG) 접근 시 사용 — bojo-int와 동일 패턴 |
| 외부 접근 | 방화벽 허용된 관계자만 호출 가능 |
| 프론트 | 기존 Orchestrator 프론트에 탭 추가 (`/api-provide`) |

> **DB 구조 결정 (4/15)**:
> - api-provider는 내부망에 위치
> - 기본 datasource: Oracle 29004 직접 접근 (제공용 테이블 ddl-auto + 동적 SELECT)
> - 관리 datasource: PG orchestrator Proxy 경유 (관리 테이블) — bojo-int와 동일 패턴
> - 테이블 프리픽스 `api_prv_`로 구분 (collector는 향후 `api_col_`로 변경 예정)

## 엔티티 설계

> 모든 관리 테이블은 orchestrator DB(PG 29001)에 생성.
> 테이블명 프리픽스: `api_prv_`

### ApiPrvOperation (오퍼레이션)
> 테이블: `api_prv_operation`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| operationId | String (UK) | 외부 노출 ID (URL 경로에 사용) |
| operationName | String | 표시명 |
| description | String | 설명 |
| datasourceId | String | 대상 Datasource ID |
| tableName | String | 대상 테이블명 |
| responseFormat | String | JSON / XML (default: JSON) |
| pageSize | Integer | 페이지 크기 (default: 100, max: 1000) |
| orderByColumn | String | 정렬 기준 컬럼 (선택) |
| orderByDirection | String | ASC / DESC (default: ASC) |
| isPublished | Boolean | 게시 여부 (false=미게시, 외부 노출 안 됨) |
| isActive | Boolean | 활성 여부 |
| createdAt | LocalDateTime | 생성일 |
| updatedAt | LocalDateTime | 수정일 |

### ApiPrvOperationColumn (제공 컬럼)
> 테이블: `api_prv_operation_column`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| operation | ApiOperation (FK) | 소속 오퍼레이션 |
| columnName | String | DB 컬럼명 |
| aliasName | String | 응답에 노출할 이름 (선택) |
| displayOrder | Integer | 표시 순서 |

### ApiPrvOperationParam (WHERE 파라미터)
> 테이블: `api_prv_operation_param`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| operation | ApiOperation (FK) | 소속 오퍼레이션 |
| paramName | String | 외부 요청 파라미터명 |
| columnName | String | WHERE 대상 DB 컬럼명 |
| operator | String | EQ / LIKE / GTE / LTE / IN / BETWEEN |
| isRequired | Boolean | 필수 여부 |
| defaultValue | String | 기본값 (선택) |
| dataType | String | STRING / NUMBER / DATE |

### ApiPrvKeyInfo (API Key 관리)
> 테이블: `api_prv_key_info`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| apiKey | String (UK) | API Key 값 |
| clientName | String | 사용자/기관명 |
| allowedIps | String | IP 제한 (콤마 구분, null=제한없음) |
| allowedOperations | String | 허용 오퍼레이션 (콤마 구분, null=전체) |
| isActive | Boolean | 활성 여부 |
| expiresAt | LocalDateTime | 만료일 (선택) |

### ApiPrvCallHistory (호출 이력)
> 테이블: `api_prv_call_history`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| operation | ApiOperation (FK) | 오퍼레이션 |
| apiKey | String | 사용한 API Key |
| clientIp | String | 요청 IP |
| requestParams | String | 요청 파라미터 (JSON) |
| responseCount | Integer | 응답 건수 |
| status | String | SUCCESS / FAILED |
| errorMessage | String | 에러 메시지 |
| durationMs | Long | 처리 시간 |
| calledAt | LocalDateTime | 호출 시각 |

## 페이징

외부 호출 시 `page`(1부터), `pageSize`(오퍼레이션 기본값 사용, 오버라이드 가능) 파라미터 지원.

```
GET /api/provide/{operationId}?param1=value1&page=2&pageSize=50

→ 동적 SQL:
  SELECT col1, col2 FROM table
  WHERE col_a = ?
  ORDER BY col_c ASC
  OFFSET 50 ROWS FETCH NEXT 50 ROWS ONLY

→ 응답:
{
  "data": [...],
  "pagination": {
    "page": 2,
    "pageSize": 50,
    "totalCount": 1234,
    "totalPages": 25
  }
}
```

- `page` 미지정 시 1페이지
- `pageSize` 미지정 시 오퍼레이션의 기본 pageSize 사용
- `pageSize` 오버라이드 가능하되 max(오퍼레이션 설정) 이하로 제한
- totalCount는 COUNT 쿼리 별도 실행 (캐싱 검토)

## 핵심 흐름

### 1. 오퍼레이션 등록 (관리자)

```
[프론트 /api-provide]
  1. Datasource 선택 (Oracle 29004 등)
  2. 테이블 선택 (DB 메타에서 자동 조회)
  3. 제공 컬럼 선택 (체크박스)
  4. WHERE 파라미터 설정 (파라미터명, 컬럼, 연산자, 필수여부)
  5. 정렬/LIMIT 설정
  6. 응답 포맷 선택 (JSON/XML)
  7. operationId 지정 (URL 경로)
```

### 2. 테스트 호출 (관리자)

```
[프론트]
  "테스트" 버튼 → 파라미터 입력 → 미리보기
  - 실제 동적 SELECT 실행
  - 결과 샘플 표시 (최대 10건)
  - SQL 미리보기 (디버그용)
```

### 3. 게시 (관리자)

```
  isPublished = true → 외부 API 노출 시작
  isPublished = false → 외부에서 접근 불가 (404)
```

### 4. 외부 호출 (외부 시스템)

```
GET /api/provide/{operationId}?param1=value1&param2=value2
Header: X-API-Key: {key}

→ ApiKeyService.validate(key, operationId, clientIp)
→ ApiProviderService.execute(operationId, params)
  1. ApiOperation 조회 (isPublished=true 체크)
  2. 동적 SELECT SQL 생성
     SELECT col1, col2, col3
     FROM target_table
     WHERE col_a = ? AND col_b >= ?
     ORDER BY col_c ASC
     FETCH FIRST 1000 ROWS ONLY
  3. 파라미터 바인딩 + 실행
  4. 응답 변환 (JSON or XML)
→ ApiCallHistoryService.save(history)
→ 응답 반환
```

## 동적 SQL 생성 규칙

```java
// SELECT
String select = columns.stream()
    .map(c -> c.getAliasName() != null 
        ? c.getColumnName() + " AS " + c.getAliasName()
        : c.getColumnName())
    .collect(joining(", "));

// WHERE
String where = params.stream()
    .filter(p -> requestParams.containsKey(p.getParamName()) || p.getDefaultValue() != null)
    .map(p -> buildCondition(p))  // "COL = ?", "COL >= ?", "COL LIKE ?", "COL IN (?,...)"
    .collect(joining(" AND "));

// ORDER BY
String orderBy = operation.getOrderByColumn() != null
    ? "ORDER BY " + operation.getOrderByColumn() + " " + operation.getOrderByDirection()
    : "";

// 페이징 (Oracle 12c+)
int offset = (page - 1) * pageSize;
String paging = "OFFSET " + offset + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";

// 최종 SQL
String sql = "SELECT " + select + " FROM " + tableName
           + (where.isEmpty() ? "" : " WHERE " + where)
           + " " + orderBy + " " + paging;

// totalCount (별도)
String countSql = "SELECT COUNT(*) FROM " + tableName
               + (where.isEmpty() ? "" : " WHERE " + where);
```

## 관리 API

| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/manage/operations | 오퍼레이션 목록 |
| GET | /api/manage/operations/{id} | 상세 조회 |
| POST | /api/manage/operations | 등록 |
| PUT | /api/manage/operations/{id} | 수정 |
| DELETE | /api/manage/operations/{id} | 삭제 |
| POST | /api/manage/operations/{id}/test | 테스트 호출 (미리보기) |
| PUT | /api/manage/operations/{id}/publish | 게시/비게시 토글 |
| GET | /api/manage/operations/{id}/history | 호출 이력 |
| GET | /api/manage/api-keys | API Key 목록 |
| POST | /api/manage/api-keys | API Key 발급 |
| DELETE | /api/manage/api-keys/{id} | API Key 삭제 |

## 외부 제공 API

| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/provide/{operationId} | 데이터 조회 (API Key 필수) |

## 프론트 UI

기존 Orchestrator 프론트에 탭 추가:

```
/api-provide
├── /api-provide                    # 오퍼레이션 목록
├── /api-provide/new                # 등록
├── /api-provide/[id]               # 상세 (컬럼/파라미터/이력 탭)
└── /api-provide/api-keys           # API Key 관리
```

## 개발 단계

| 단계 | 작업 | 비고 |
|------|------|------|
| 1 | 모듈 생성 + 엔티티 + CRUD API | 프로젝트 골격 |
| 2 | 동적 SELECT 엔진 + 테스트 호출 | 핵심 기능 |
| 3 | API Gateway (외부 노출) + API Key 인증 | 외부 접근 |
| 4 | 프론트 관리 UI | 오퍼레이션 등록/테스트/게시 |
| 5 | 호출 이력 + 통계 | 모니터링 |
| 6 | 레거시 API 3종 이식 (전처리 테이블 + 등록) | 마이그레이션 |

## api-collector와의 대칭 비교

| 항목 | api-collector (수집) | api-provider (제공) |
|------|---------------------|---------------------|
| 방향 | 외부 → GIMS | GIMS → 외부 |
| 위치 | DMZ (8084) | 내부망 (8095) |
| 등록 단위 | ApiEndpoint | ApiOperation |
| 매핑 | 응답필드 → DB컬럼 | DB컬럼 → 응답필드 |
| 실행 | HTTP 호출 → DB INSERT | DB SELECT → HTTP 응답 |
| 테스트 | API 호출 미리보기 | 쿼리 결과 미리보기 |
| 인증 | 없음 (수집) | API Key + IP 제한 |
| 이력 | ApiExecutionHistory | ApiCallHistory |
| 스케줄 | 수집 스케줄 (cron) | 없음 (요청 기반) |
| 동적 범위 | 100% | 100% (전처리 전제) |

## 레거시 API 전처리 — 정제 테이블 적재 계획

복잡한 SQL(PIVOT, 서브쿼리, DBLINK, 동적 CASE WHEN)의 결과를
미리 정제 테이블에 적재하여 API Provider가 단순 SELECT만 하도록 한다.

### 전처리 대상

| 레거시 API | 원본 SQL 복잡도 | 정제 테이블 (안) | 전처리 방식 |
|-----------|---------------|-----------------|-----------|
| MEGOKR NGW_03 | 2단계 서브쿼리 | TM_PROVIDE_NGW03 | Loader Step: 서브쿼리 결과 flat 적재 |
| MEGOKR NGW_04 | PIVOT 125컬럼 | TM_PROVIDE_NGW04 | Loader Step: PIVOT 결과 그대로 적재 |
| OPN waterQuality | 동적 CASE WHEN | TM_PROVIDE_WATER_QUALITY | Loader Step: 전 항목 PIVOT 적재 |
| OPN observation_station | DBLINK | TM_PROVIDE_OBSERVATION | 배치: 외부 DB → 로컬 적재 |
| OPN info_general_105 | 5개 LEFT JOIN | TM_PROVIDE_GENERAL_105 | Loader Step: JOIN 결과 flat 적재 |

### 전처리 불필요 (바로 등록 가능)

| 레거시 API | 소스 테이블 | 비고 |
|-----------|-----------|------|
| 가뭄119 | WT_DREAM_PERMWELL_PUBLIC_21033 | 단순 SELECT, 바로 오퍼레이션 등록 |
| MEGOKR NGW_08 | TM_GD00203 | 단순 SELECT, 바로 등록 |
| MEGOKR NGW_09 | WT_DREAM_PERMWELL_PUBLIC | 단순 SELECT, 바로 등록 |
| OPN info_general | 기존 테이블 | 단순 SELECT, 바로 등록 |

### 전처리 실행 구조

```
[Orchestrator 스케줄]
  → internal-api-provide-preprocessor (신규 Agent or Loader Step)
  → 복잡 SQL 실행 → 정제 테이블 MERGE
  → 주기: 일 1회 or 원본 데이터 갱신 시

[정제 테이블]
  TM_PROVIDE_* — 단순 flat 구조, 인덱스 포함
  API Provider는 여기서 SELECT만

[API Provider]
  오퍼레이션 등록: TM_PROVIDE_NGW03 → 컬럼 선택 → 조건 설정 → 게시
```

### 전처리 Loader 구현 방식

2가지 선택지:

**Option A: bojo-int 기존 Loader에 Step 추가**
- 기존 internal-jeju-loader 등과 동일 패턴
- `PreprocessLoadStep` + Factory 추가
- 장점: 기존 인프라(SyncLog, ConditionBuilder 등) 재사용
- 단점: bojo-int가 점점 비대해짐

**Option B: api-provider 모듈 내 자체 배치**
- api-provider가 자체 스케줄로 전처리 실행
- 장점: 독립적, api-provider 단일 모듈로 완결
- 단점: 배치 인프라 중복 구현

**권장: Option A** — 전처리도 "IF_RSV → Target 적재"와 동일 패턴이므로
기존 bojo-int Loader 인프라를 재사용하는 게 효율적.
전처리 결과가 TM_PROVIDE_* 테이블에 들어가면, api-provider는 읽기만 하면 됨.

### 전처리 개발 순서

| 순번 | 작업 | 비고 |
|------|------|------|
| 1 | 정제 테이블 DDL 설계 (TM_PROVIDE_*) | 레거시 SQL 결과 컬럼 분석 |
| 2 | 정제 테이블 엔티티 생성 (bojo-int) | ddl-auto로 Oracle 자동 생성 |
| 3 | PreprocessLoadStep 구현 | 복잡 SQL 실행 → 정제 테이블 MERGE |
| 4 | YAML + Factory 등록 | internal-provide-preprocessor.yml |
| 5 | 스케줄 등록 + 테스트 | 전처리 결과 확인 |
| 6 | api-provider에서 오퍼레이션 등록 | 정제 테이블 대상 |

> 전처리는 api-provider 시스템 구축 후, 레거시 이식 단계(6단계)에서 진행.
> 먼저 바로 등록 가능한 4종(가뭄119, NGW_08/09, info_general)으로 시스템 검증 후 착수.

## 리스크

- **SQL Injection**: 동적 SQL 생성이므로 반드시 PreparedStatement 파라미터 바인딩 사용. 컬럼명/테이블명은 화이트리스트 검증.
- **대량 응답**: maxRows로 제한하되, 무제한 요청 방어 필요
- **Oracle 부하**: 내부망 Oracle에서 직접 SELECT — 인덱스 확인 필요
- **XML 응답**: JSON 우선, XML은 2차로 추가 (라이브러리 선택 필요)
