# API Provider 2단계 — DB 전환 + 동적 SELECT 엔진 + 테스트 호출

> 작성일: 2026-04-16
> 상태: 계획
> 기반: dev_plan/2026_04/15/gims-api-provider-plan.md

## 목표

1. **DB 구조 전환**: Oracle 단일 → PG 전용 (관리 + 동적쿼리 모두 PG)
2. **동적 SELECT 엔진**: JdbcTemplate으로 동적 쿼리 생성·실행
3. **테스트 호출**: 관리 API에서 미리보기

## DB 아키텍처 변경

### Before (1단계)
```
api-provider → Oracle 29004 직접 (관리 + 제공 모두, JPA)
```

### After (2단계)
```
api-provider
  ├─ [JPA 직접] PG api_provider (29006)           ← 관리 테이블 (api_prv_*)
  └─ [Proxy 경유 접속정보 → 직접 연결] PG 등      ← 동적 SELECT (datasourceId 기반)
```

### DB 접근 패턴 (bojo-int SyncDataSourceService와 동일)
```
1. api-provider → Proxy(8093) /api/datasources/{datasourceId}/connection-info
2. ← 암호화된 접속정보 응답
3. api-provider → PasswordEncryptor.decrypt() → HikariDataSource 생성 (캐싱)
4. api-provider → JdbcTemplate으로 대상 PG에 직접 쿼리 실행
```

| 용도 | 접근 방식 | DB |
|------|----------|-----|
| 관리 (JPA) | PG 직접 연결 | api_provider (29006, 전용 컨테이너) |
| 동적 SELECT | Proxy 경유 접속정보 → 직접 연결 | datasourceId별 PG |
| DB 메타 조회 | Proxy 기존 API 호출 | search-tables, search-columns |

> **Oracle 불필요** — 제공 대상 = 파이프라인이 정제한 PG 테이블, 레거시 이식도 전처리 후 PG 적재
> 향후 Oracle 필요 시 Proxy datasourceId 추가만 하면 됨

## 현재 상태 (1단계 완료)

- 관리 엔티티 5개 (Operation, Column, Param, KeyInfo, CallHistory) — Oracle JPA
- 제공용 엔티티 4개 (TmGd000203 등) — Oracle JPA
- CRUD API (ManageController + OperationService + KeyService)
- Oracle 단일 datasource

## 수정/신규 파일

### 1. application.yml 수정
> Oracle → PG 전환 + Proxy URL 설정

```yaml
server:
  port: 8095

spring:
  datasource:
    url: jdbc:postgresql://localhost:29006/api_provider
    username: k1m
    password: 1111
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL95Dialect

app:
  proxy:
    url: http://localhost:8093
```

### 2. build.gradle 수정
> ojdbc8 제거 → postgresql 추가

### 3. 제공용 엔티티 4개 삭제
> `entity/provide/` 디렉토리 전체 삭제
> 제공 대상 테이블은 PG에 이미 존재, Proxy 경유 JdbcTemplate으로 읽기

### 4. ProviderDataSourceService.java (신규)
> 위치: `service/ProviderDataSourceService.java`
> 참고: bojo-int SyncDataSourceService 패턴

역할: Proxy 경유 DB 접속정보 해석 → HikariDataSource + JdbcTemplate 캐싱

```java
@Service
public class ProviderDataSourceService {

    @Value("${app.proxy.url}")
    private String proxyUrl;

    private final Map<String, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();

    // datasourceId로 JdbcTemplate 획득 (캐시 → Proxy 호출 → 생성)
    public JdbcTemplate getJdbcTemplate(String datasourceId) { ... }

    // Proxy에서 접속정보 조회 + 복호화 + HikariDataSource 생성
    private JdbcTemplate createJdbcTemplate(String datasourceId) {
        // 1. GET {proxyUrl}/api/datasources/{datasourceId}/connection-info
        // 2. 응답 복호화 (PasswordEncryptor)
        // 3. HikariDataSource 생성 (maxPool=5, timeout=10s)
        // 4. JdbcTemplate 래핑 + 캐시 저장
    }
}
```

### 5. DynamicQueryService.java (신규 — 핵심)
> 위치: `service/DynamicQueryService.java`

역할: 오퍼레이션 설정 → SQL 생성 → ProviderDataSourceService로 JdbcTemplate 획득 → 직접 실행

```java
@Service
public class DynamicQueryService {

    private final ProviderDataSourceService dataSourceService;

    public DynamicQueryResult execute(ApiPrvOperation op, Map<String, String> requestParams, int page, int pageSize) {
        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(op.getDatasourceId());
        // SQL 생성 → jdbc.queryForList() → 결과 반환
    }

    public String buildSqlPreview(ApiPrvOperation op, Map<String, String> requestParams) { ... }
}
```

SQL 생성 흐름 (PG 기준):
```
1. SELECT: columns → "col1, col2 AS alias1, ..." (비어있으면 SELECT *)
2. FROM: operation.tableName
3. WHERE: params + requestParams
   - 필수 누락 시 예외
   - 선택 파라미터는 값이 있을 때만 추가
   - defaultValue: 요청에 값 없으면 기본값 사용
   - 연산자:
     - EQ:      col = ?
     - LIKE:    col LIKE '%' || ? || '%'
     - GTE:     col >= ?
     - LTE:     col <= ?
     - IN:      col IN (?, ?, ...)  (콤마 분리)
     - BETWEEN: col BETWEEN ? AND ? (콤마 분리)
   - dataType별 처리:
     - STRING: 그대로
     - NUMBER: 바인딩 시 Long/Double 변환
     - DATE:   TO_DATE(?, 'YYYY-MM-DD') 또는 ?::date
4. ORDER BY: orderByColumn + orderByDirection (null이면 생략)
5. 페이징 (PG 표준): LIMIT ? OFFSET ?
6. COUNT: SELECT COUNT(*) FROM table WHERE ... (별도 쿼리)
```

보안:
- **테이블명/컬럼명**: `[A-Za-z0-9_]` 정규식 화이트리스트
- **파라미터 값**: PreparedStatement 바인딩만 사용
- **ORDER BY 방향**: ASC/DESC만 허용

### 6. DynamicQueryResult.java (신규 — DTO)
> 위치: `dto/DynamicQueryResult.java`

```java
@Data @Builder
public class DynamicQueryResult {
    private List<Map<String, Object>> data;
    private PaginationInfo pagination;
    private String executedSql;       // 테스트 모드에서만
    private long durationMs;

    @Data @Builder
    public static class PaginationInfo {
        private int page;
        private int pageSize;
        private long totalCount;
        private int totalPages;
    }
}
```

### 7. ApiPrvManageController.java 수정
> 테스트 호출 + DB 메타 조회 API 추가

```
POST /api/manage/operations/{id}/test
Body: { "params": { "key": "value" }, "page": 1, "pageSize": 10 }
→ DynamicQueryService.execute() (pageSize 최대 10건 강제)
→ 응답: { data, pagination, executedSql, durationMs }

GET /api/manage/meta/tables?datasourceId=xxx
→ Proxy /api/datasource/search-tables 호출

GET /api/manage/meta/tables/{tableName}/columns?datasourceId=xxx
→ Proxy /api/datasource/search-columns 호출
```

## 수정 파일 요약

| 파일 | 변경 |
|------|------|
| application.yml | **수정** — PG 29006 + Proxy URL |
| build.gradle | **수정** — ojdbc8 제거, postgresql 추가 |
| entity/provide/*.java (4개) | **삭제** |
| ProviderDataSourceService.java | **신규** — Proxy 경유 접속정보 → JdbcTemplate 캐싱 |
| DynamicQueryService.java | **신규** — 동적 SQL 생성 + 직접 실행 |
| DynamicQueryResult.java | **신규** — 결과 DTO |
| ApiPrvManageController.java | **수정** — 테스트 호출 + 메타 API |

## 의존성

- PasswordEncryptor: bojo-int에서 사용하는 것과 동일 로직 필요
  - sync-agent-common JAR 의존 또는 로직 복사 (모듈 독립성 고려)

## 테스트 시나리오

1. **기동 테스트** — PG 29006 연결 + ddl-auto 관리 테이블 5개 생성
2. **DB 메타 조회** — Proxy 경유 PG 테이블/컬럼 목록 조회
3. **오퍼레이션 등록** — CRUD로 PG 테이블 대상 등록
4. **테스트 호출** — 동적 SELECT 실행 + 결과 + SQL 미리보기
5. **파라미터 조건** — WHERE 조건 적용 확인
6. **페이징** — LIMIT/OFFSET + totalCount
7. **보안** — 잘못된 테이블명/컬럼명 거부

## 영향 범위

- gims-api-provider 모듈 내부 변경
- sync-proxy-internal: 변경 없음 (기존 API 그대로 사용)
- 다른 모듈 영향 없음
