# api-collector 외부 Datasource 연동 (Target DB 선택)

## 현재 문제
- MetadataController가 자체 DB(api_collector)의 테이블/컬럼만 조회
- ApiExecutionService가 자체 DataSource로만 INSERT/UPSERT
- targetDatasourceId 필드는 엔티티에 있으나 미사용
- **결과**: API 수집 데이터를 외부 DB에 적재할 수 없음

## 보안 문제 — connection-info 평문 전달
현재 Orchestrator `connection-info` API가 자격증명을 **복호화(평문)해서 응답**하고 있음:
```
[현재 — 위험]
Orchestrator DB: username=ENC(xxx), password=ENC(yyy)
  → DatasourceService.getConnectionInfo()
  → CredentialEncryptor.decrypt() 호출
  → 응답: { username: "k1m", password: "1111" }  ← 평문 노출
  → Agent/Proxy가 평문 받아서 DataSource 생성
```

### 개선: 암호문 전달 + 수신측 복호화
```
[개선 — 안전]
Orchestrator DB: username=ENC(xxx), password=ENC(yyy)
  → DatasourceService.getConnectionInfo()
  → 복호화 하지 않고 암호문 그대로 응답
  → 응답: { username: "ENC(xxx)", password: "ENC(yyy)" }
  → Agent/Proxy/api-collector 각자가 PasswordEncryptor로 복호화
```

- **공통 암호화 클래스**: `sync-agent-common/.../datasource/PasswordEncryptor` (Jasypt, PBEWithHMACSHA512AndAES_256)
- **Orchestrator의 CredentialEncryptor**: 동일 알고리즘 → `PasswordEncryptor`로 통합 후 삭제 대상
- **키 공유**: 모든 서비스가 동일 `jasypt.encryptor.password` 값 사용

## 기존 Agent 방식 (재사용 대상)
```
Orchestrator (DB 관리 화면)
  → Datasource 등록 (host, port, dbName, username, password)
  → 테이블/컬럼 검색 API: GET /api/datasources/{id}/search-tables
  → 컬럼 검색 API: GET /api/datasources/{id}/search-columns?tableName=xxx
  → 연결 정보 API: GET /api/datasources/{id}/connection-info

Agent (ProxyDataSourceService 패턴)
  → Orchestrator에서 connection-info 받아서 HikariCP DataSource 생성
  → ConcurrentHashMap 캐싱 (datasourceId → HikariDataSource)
```

## 기존 코드 재사용 조사 결과

### 프론트엔드 — 이미 완비
- `lib/api.ts`의 `datasourceApi`에 필요한 API 모두 존재:
  - `datasourceApi.getSimple()` — DB 목록 (id, host, port, dbName)
  - `datasourceApi.searchTables(datasourceId, query?)` — 테이블 검색
  - `datasourceApi.searchColumns(datasourceId, tableName)` — 컬럼 검색
- `next.config.js` 프록시 설정: `/api/*` → `localhost:8080` (Orchestrator) 이미 동작
- **MetadataController 백엔드 API 불필요** — 프론트에서 Orchestrator API 직접 호출

### 백엔드 — OrchestratorClient + 동적 DataSource 필요
- 참고 패턴: `sync-proxy-dmz/.../ProxyDataSourceService.java`
  - `fetchFromOrchestrator(datasourceId)` → RestTemplate으로 connection-info 호출
  - `HikariDataSource` 생성 + `ConcurrentHashMap` 캐싱
  - PostgreSQL/MySQL 자동 분기 (dbType 기반)
- 참고 DTO: `sync-agent-common/.../DataSourceInfo.java`
  - host, port, dbName, username, password, dbType → JDBC URL/Driver 자동 생성

## 변경 내용

### Part A. 암호화 통신 전환

#### A-1. Orchestrator — connection-info 암호문 응답으로 변경
```java
// DatasourceService.getConnectionInfo() 수정
// 기존: credentialEncryptor.decrypt() 호출
// 변경: DB에 저장된 암호문 그대로 반환
.username(ds.getUsername())   // ENC(xxx) 그대로
.password(ds.getPassword())   // ENC(yyy) 그대로
```

#### A-2. Orchestrator — CredentialEncryptor → PasswordEncryptor 교체
- `build.gradle`에 `sync-agent-common` JAR 의존성 추가: `implementation files('libs/sync-agent-common-1.0.0-SNAPSHOT.jar')`
- `CredentialEncryptor.java` **삭제**
- 모든 사용처를 `PasswordEncryptor` (sync-agent-common)로 교체:
  - `DatasourceService.java`: encrypt/decrypt 모두 PasswordEncryptor 사용
  - `AgentService.java`, `ExecutionService.java`: CredentialEncryptor import 제거 → PasswordEncryptor
- PasswordEncryptor는 `@Component`가 아닌 일반 클래스 → Bean 등록 필요:
  ```java
  @Bean
  public PasswordEncryptor passwordEncryptor(@Value("${jasypt.encryptor.password}") String key) {
      return new PasswordEncryptor(key);
  }
  ```

#### A-3. Agent/Proxy — PasswordEncryptor로 복호화 추가
```
기존: 평문 username/password 받아서 바로 DataSource 생성
변경: 암호문 수신 → PasswordEncryptor.decrypt() → DataSource 생성

수정 대상:
  - sync-proxy-dmz/.../ProxyDataSourceService.java
  - sync-proxy-internal/.../ProxyDataSourceService.java
  - sync-agent-bojo/.../SyncDataSourceService.java
  - sync-agent-bojo-int/.../SyncDataSourceService.java
```

### Part B. api-collector 외부 Datasource 연동

#### B-1. application.yml — Orchestrator URL 설정 추가
```yaml
orchestrator:
  url: ${ORCHESTRATOR_URL:http://localhost:8080}
```

#### B-2. OrchestratorClient 신규 — 백엔드 connection-info 호출
```java
@Component
public class OrchestratorClient {
    private final RestTemplate restTemplate;
    private final String orchestratorUrl;

    // 연결 정보 (암호문 자격증명) — 테이블/컬럼은 프론트에서 직접 호출
    ConnectionInfo getConnectionInfo(String datasourceId);
}
```
- ProxyDataSourceService.fetchFromOrchestrator() 패턴 참고
- 반환 DTO: host, port, dbName, username(암호문), password(암호문), dbType

#### B-3. DynamicDataSourceService 신규 — HikariCP 동적 DataSource
```java
@Component
public class DynamicDataSourceService {
    private final OrchestratorClient orchestratorClient;
    private final PasswordEncryptor passwordEncryptor;  // 복호화
    private final ConcurrentHashMap<String, HikariDataSource> cache = new ConcurrentHashMap<>();

    // datasourceId → HikariDataSource (캐싱)
    // 내부에서 PasswordEncryptor.decrypt()로 복호화 후 DataSource 생성
    DataSource getDataSource(String datasourceId);

    void evict(String datasourceId);
}
```
- PasswordEncryptor: sync-agent-common JAR 의존성으로 사용 (libs/ 폴더에 JAR 복사)

#### B-4. MetadataController 삭제
- 자체 DB 메타데이터 조회 역할 → 불필요 (프론트에서 Orchestrator API 직접)
- 기존 `/collector-api/metadata/*` 엔드포인트 제거

#### B-5. ApiExecutionService 수정 — 동적 DataSource로 적재
```
기존: this.dataSource (자체 DB) 사용
변경:
  1. endpoint.targetDatasourceId 존재 시 → DynamicDataSourceService.getDataSource(id)
  2. 해당 DataSource의 Connection으로 INSERT/UPSERT
  3. targetDatasourceId 미설정 시 → 자체 DB fallback
```

#### B-6. 프론트 MappingTab 수정
```
기존: metadataApi (자체 DB)로 테이블/컬럼 목록
변경:
  1. Datasource 드롭다운 추가 (datasourceApi.getSimple() — Orchestrator API)
  2. Datasource 선택 → datasourceApi.searchTables(id) → 테이블 드롭다운
  3. 테이블 선택 → datasourceApi.searchColumns(id, tableName) → 컬럼 드롭다운
  4. 선택한 datasourceId를 endpoint에 저장 (targetDatasourceId)
```

## 수정 대상 파일
| 구분 | 파일 | 작업 |
|------|------|------|
| Part A | `orchestrator/backend/build.gradle` | sync-agent-common JAR 의존성 추가 |
| Part A | `orchestrator/backend/libs/` | sync-agent-common JAR 복사 |
| Part A | `orchestrator/.../CredentialEncryptor.java` | **삭제** |
| Part A | `orchestrator/.../DatasourceService.java` | CredentialEncryptor → PasswordEncryptor, getConnectionInfo() decrypt 제거 |
| Part A | `orchestrator/.../AgentService.java` | CredentialEncryptor → PasswordEncryptor |
| Part A | `orchestrator/.../ExecutionService.java` | CredentialEncryptor → PasswordEncryptor |
| Part A | `orchestrator에 PasswordEncryptor Bean 등록` | @Configuration 클래스에 @Bean 추가 |
| Part A | `sync-proxy-dmz/.../ProxyDataSourceService.java` | PasswordEncryptor 복호화 추가 |
| Part A | `sync-proxy-internal/.../ProxyDataSourceService.java` | PasswordEncryptor 복호화 추가 |
| Part A | `sync-agent-bojo/.../SyncDataSourceService.java` | PasswordEncryptor 복호화 추가 |
| Part A | `sync-agent-bojo-int/.../SyncDataSourceService.java` | PasswordEncryptor 복호화 추가 |
| Part B | `infolink-api-collector/application.yml` | orchestrator.url 추가 |
| Part B | `infolink-api-collector/build.gradle` | sync-agent-common JAR 의존성 추가 |
| Part B | `infolink-api-collector/libs/` | sync-agent-common JAR 복사 |
| Part B | `infolink-api-collector/OrchestratorClient.java` (신규) | connection-info 호출 |
| Part B | `infolink-api-collector/DynamicDataSourceService.java` (신규) | HikariCP 동적 DataSource |
| Part B | `infolink-api-collector/MetadataController.java` | **삭제** |
| Part B | `infolink-api-collector/ApiExecutionService.java` | 동적 DataSource로 적재 |
| Part B | `frontend/components/api-collect/MappingTab.tsx` | datasource 드롭다운 추가 |

## 플로우
```
[MappingTab]
  1. 테스트 호출 → JSON 트리 → 데이터 루트 선택
  2. Target Datasource 선택 (드롭다운: datasourceApi.getSimple())
  3. Target 테이블 선택 (드롭다운: datasourceApi.searchTables(dsId))
  4. 테이블 선택 시 컬럼 로드 (datasourceApi.searchColumns(dsId, table))
  5. 필드 매핑 (API 응답 필드 → 선택한 테이블의 컬럼)
  6. 매핑 저장 (targetDatasourceId, targetTableName, fieldMappings)
  7. 수동 실행 → connection-info(암호문) → PasswordEncryptor.decrypt() → HikariCP → INSERT/UPSERT

[ApiExecutionService — 실행 시]
  endpoint.targetDatasourceId 확인
    ├─ 있음 → DynamicDataSourceService.getDataSource(id)
    │          → OrchestratorClient.getConnectionInfo(id)  (암호문)
    │          → PasswordEncryptor.decrypt()
    │          → HikariCP DataSource 생성 → 외부 DB 적재
    └─ 없음 → 자체 DataSource fallback (기존 동작)
```

## 작업 순서
1. **Part A 먼저** — 암호문 통신 전환 (Orchestrator + Agent/Proxy)
2. **Part B** — api-collector 외부 datasource 연동 (백엔드 → 프론트)

## 주의사항
- Orchestrator가 기동 중이어야 메타데이터 조회 + 실행 가능
- 모든 서비스가 동일한 jasypt.encryptor.password 키 사용 필수
- Part A 배포 시 Orchestrator + Agent/Proxy 동시 배포 필요 (한쪽만 바꾸면 복호화 실패)
- DataSource 캐싱: HikariCP pool → ConcurrentHashMap (ProxyDataSourceService 패턴)
- PostgreSQL/MySQL 분기 지원 (기존 Agent와 동일)
