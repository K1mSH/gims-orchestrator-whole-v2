# 동적 데이터소스와 암호화 (Dynamic DataSource & Encryption)

> 이 문서는 GIMS 동기화 시스템이 **여러 종류의 외부 DB에 동적으로 연결하는 방법**과
> **비밀번호를 안전하게 관리하는 암호화 방식**을 설명합니다.

---

## 전체 그림

이 시스템은 10개 이상의 외부 DB에 연결한다. 각 DB는 종류(PostgreSQL, MySQL), 주소, 포트, 계정이 모두 다르다. 앱이 시작될 때 모든 DB 연결을 미리 만들어두는 것이 아니라, **필요할 때 동적으로 생성**한다.

```
Orchestrator (DB 연결 정보 보관소)
     ↓  "daejeon DB 정보 알려줘"
     ↓  응답: {host, port, dbName, username(암호화), password(암호화)}
     ↓
Agent / API Collector
     ↓  비밀번호 복호화
     ↓  HikariCP 커넥션 풀 생성
     ↓  JDBC로 DB 작업 수행
외부 DB (daejeon, bytek, chungnam, ...)
```

---

## 핵심 개념

### 1. 커넥션 풀 (Connection Pool) — HikariCP

DB에 쿼리를 보내려면 먼저 **연결(Connection)**을 맺어야 한다. 연결을 맺는 과정은 느리다 (TCP 핸드셰이크, 인증, 세션 설정 등). 매번 연결했다 끊으면 성능이 나쁘다.

**커넥션 풀**은 연결을 미리 몇 개 만들어놓고 **빌려쓰고 반납하는** 방식이다. `HikariCP`는 오픈소스 커넥션 풀 라이브러리이며, Spring Boot 2.x부터 기본 내장되어 있다:

```
커넥션 풀 (HikariCP)
┌──────────────────────────────────────┐
│  [연결1: 사용중]  [연결2: 대기]       │
│  [연결3: 대기]    [연결4: 사용중]     │
│  [연결5: 대기]                        │
└──────────────────────────────────────┘

작업 요청 → 대기 중인 연결 빌림 → 쿼리 실행 → 연결 반납 (끊지 않음)
```

**HikariCP**는 Java에서 가장 빠른 커넥션 풀 라이브러리다. Spring Boot의 기본 풀이기도 하다.

우리 시스템의 풀 설정:

| 설정 | API Collector | Agent/Proxy | 의미 |
|------|--------------|-------------|------|
| maxPoolSize | 5 | 10 | 최대 동시 연결 수 |
| minIdle | 1 | 2 | 최소 유지 연결 수 |
| connectionTimeout | 10초 | 10초 | 연결 대기 최대 시간 |
| maxLifetime | 10분 | 10분 | 연결 하나의 최대 수명 |
| leakDetectionThreshold | - | 60초 | 60초 이상 반납 안 하면 경고 |
| keepaliveTime | - | 120초 | 2분마다 연결 살아있는지 확인 |
| connectionTestQuery | SELECT 1 | SELECT 1 | 연결 유효성 검사 쿼리 |

**maxLifetime이 필요한 이유**: DB 서버 쪽에서 오래된 연결을 끊을 수 있다. 그래서 풀에서도 일정 시간이 지난 연결을 스스로 갱신한다.

**leakDetectionThreshold**: 코드에서 연결을 빌려가고 반납을 안 하면(=누수) 로그에 경고를 남긴다. 디버깅에 유용하다.

> **연관 파일 (HikariCP 풀 생성)**
> - `infolink-api-collector/.../config/DynamicDataSourceService.java` — maxPoolSize=5
> - `infolink-agent-bojo-dmz/.../config/SyncDataSourceService.java` — maxPoolSize=10, leakDetection=60s
> - `infolink-agent-bojo-internal/.../config/SyncDataSourceService.java` — 동일
> - `infolink-proxy-dmz/.../config/ProxyDataSourceService.java` — maxPoolSize=10
> - `infolink-proxy-internal/.../config/ProxyDataSourceService.java` — 동일

---

### 2. 동적 DataSource — 필요할 때 풀을 만든다 [각 모듈 자체 구현]

일반적인 Spring 앱은 application.yml에 DB 연결 정보를 적어두고, 앱 시작 시 한 번만 풀을 만든다. 하지만 우리 시스템은 **외부 DB가 10개 이상**이고, 설정이 UI에서 동적으로 바뀔 수 있다.

그래서 **Lazy Loading** 방식을 쓴다:

```
getDataSource("daejeon")
  │
  ├─ 캐시에 있나? → 있으면 바로 반환
  │
  └─ 없으면:
       ├─ Orchestrator API 호출 → 연결 정보 받기
       ├─ 비밀번호 복호화
       ├─ HikariCP 풀 생성
       ├─ 캐시에 저장
       └─ 반환
```

캐시는 `ConcurrentHashMap<datasourceId, HikariDataSource>`로 관리한다. 한 번 만든 풀은 계속 재사용.

> **연관 파일**
> - `infolink-api-collector/.../config/DynamicDataSourceService.java` — computeIfAbsent() 패턴
> - `infolink-api-collector/.../config/OrchestratorClient.java` — Orchestrator에서 연결정보 조회
> - `infolink-agent-common/.../datasource/DataSourceInfo.java` — 연결정보 DTO (JDBC URL 자동 생성)

**JDBC URL 자동 생성**: DB 종류에 따라 URL 형식이 다르다. 시스템이 `dbType`을 보고 자동으로 만든다:

```
PostgreSQL → jdbc:postgresql://host:port/dbName
MySQL      → jdbc:mysql://host:port/dbName?useSSL=false&serverTimezone=Asia/Seoul
Oracle     → jdbc:oracle:thin:@host:port:dbName
```

---

### 3. 4단계 DataSource Fallback (Agent)

> 이 Fallback 구조는 우리가 설계한 것이다. `ThreadLocal`은 Java 표준(`java.lang`)이 제공하는 스레드별 저장소다.

Agent는 가장 복잡한 DataSource 해석 로직을 갖고 있다. 파이프라인 실행 시 소스/타겟 DB가 실행마다 다를 수 있기 때문이다.

```
datasourceId로 DataSource를 찾는 순서:

1단계: ThreadLocal (현재 실행의 전용 DataSource)
   └→ 파이프라인 시작 시 Orchestrator가 알려준 소스/타겟 정보가 세팅됨
   └→ 같은 스레드 내에서만 유효

2단계: 메모리 캐시 (이전에 만들어둔 풀)
   └→ ConcurrentHashMap에 저장된 것

3단계: Proxy API 호출 (캐시에 없으면)
   └→ Proxy → Orchestrator 경유로 연결 정보 받기

4단계: Spring 기본 DataSource (위 모두 실패 시)
   └→ Agent 자체 DB (IF/Target 로컬 DB)
```

**ThreadLocal이란?**: 같은 변수인데 **스레드마다 독립적인 값**을 갖는 저장소다. 스레드 A가 "daejeon DB" 정보를 넣으면, 스레드 B에서는 보이지 않는다. 동시에 여러 파이프라인이 실행될 때 서로의 DB 연결 정보가 섞이지 않도록 보장한다.

> **연관 파일 (4단계 Fallback)**
> - `infolink-agent-bojo-dmz/.../config/SyncDataSourceService.java` — ThreadLocal + 캐시 + Proxy + 기본DB
> - `infolink-agent-bojo-internal/.../config/SyncDataSourceService.java` — 동일 패턴

---

### 4. Jasypt 암호화 — 비밀번호 안전 관리

> Jasypt(Java Simplified Encryption)는 오픈소스 암호화 라이브러리다. `jasypt-spring-boot-starter`를 통해 Spring Boot와 통합되며, `ENC(...)` 래퍼를 자동 인식하여 복호화한다.

DB 비밀번호를 코드나 설정 파일에 평문으로 적어두면 위험하다. Git에 올라가거나 로그에 찍힐 수 있다. **Jasypt**는 설정 파일의 값을 암호화하는 라이브러리다.

```yaml
# application.yml
spring:
  datasource:
    password: ENC(A3bC9dE2fG1hI4jK5lM6nO7pQ8rS9tU0)
```

`ENC(...)` 로 감싸진 값은 앱 시작 시 자동으로 복호화된다. 복호화에 필요한 **마스터 키**는 환경 변수로 주입한다:

```
앱 시작 시:
  환경변수 JASYPT_PASSWORD → 마스터 키
  ENC(...) 값 → 마스터 키로 복호화 → 실제 비밀번호
```

### PasswordEncryptor [infolink-agent-common] — 공통 암호화/복호화 클래스

실제 암호화·복호화를 수행하는 클래스는 **`PasswordEncryptor`**다. common 모듈에 있고, 전 모듈이 공통으로 사용한다.

```
위치: infolink-agent-common/.../datasource/PasswordEncryptor.java

사용하는 모듈:
  ├─ infolink-orchestrator-backend   → WebConfig, DatasourceService, ExecutionService, AgentService
  ├─ infolink-agent-bojo-dmz             → SyncDataSourceService
  ├─ infolink-agent-bojo-internal         → SyncDataSourceService
  ├─ infolink-proxy-dmz              → ProxyDataSourceService
  ├─ infolink-proxy-internal         → ProxyDataSourceService
  ├─ infolink-api-collector      → OrchestratorClient
  └─ infolink-agent-common 자체      → SyncDataSourceManager, LazyDataSource
```

각 모듈이 Jasypt 마스터 키로 `PasswordEncryptor`를 초기화하고, `ENC(...)` 값을 만나면 이 클래스의 `decrypt()`로 복호화한다. 주로 **DB 연결정보를 받아서 실제 커넥션 풀을 만들기 직전**에 사용된다.

```
Orchestrator가 DB 연결정보 응답
  → username: "ENC(abc...)", password: "ENC(xyz...)"
  → Agent/Proxy/Collector가 PasswordEncryptor.decrypt()로 평문 변환
  → 평문 credentials로 HikariCP 풀 생성
```

**암호화 알고리즘**: `PBEWithHMACSHA512AndAES_256`
- PBE: Password-Based Encryption (마스터 키 기반)
- HMAC-SHA512: 무결성 검증
- AES-256: 실제 암호화 (군사 등급)
- RandomIvGenerator: 같은 평문이라도 매번 다른 암호문 생성

**암호화 도구**: `scripts/encrypt.java`로 직접 암호화 가능:
```bash
java encrypt.java encrypt "내비밀번호"
# 출력: ENC(A3bC9dE2fG1...)
```

**암호화되는 항목들**:
- DB 접속 URL, username, password
- Orchestrator/Proxy 주소
- API Key
- Proxy 인증 키

### 마스터 키는 어디에 저장되나?

마스터 키(`JASYPT_PASSWORD`)는 **코드에 직접 넣지 않는다**. 운영 환경에서는 Docker 환경변수로 주입한다.

#### application.yml (모든 모듈 공통)

```yaml
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
```

`${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` 의미:
- 환경변수 `JASYPT_PASSWORD`가 있으면 → 그 값 사용 (운영)
- 없으면 → `sync-pipeline-secret-key-2024` 기본값 사용 (로컬 개발)

#### Docker 배포 시 키 주입 경로

docker-compose.yaml의 `environment`에 직접 마스터 키를 설정한다. 이 파일은 **서버 로컬에만 존재하고 Git에 올라가지 않으므로** 보안상 문제없다.

```
docker-compose.yaml (서버 로컬, Git 미포함)
  └→ environment:
       - JASYPT_PASSWORD=실제운영키값

컨테이너 내부
  └→ 환경변수 JASYPT_PASSWORD=실제운영키값
  └→ Spring이 application.yml의 ${JASYPT_PASSWORD}를 치환
  └→ Jasypt가 ENC(...) 값들을 복호화
```

docker-compose.yaml 예시 (infolink-api-collector):
```yaml
services:
  infolink-api-collector:
    image: ${DOCKER_REGISTRY}/infolink-api-collector:${IMAGE_TAG}
    environment:
      - SERVER_PORT=8084
      - SPRING_PROFILES_ACTIVE=prod
      - JASYPT_PASSWORD=실제운영키값       # 서버 로컬 파일에 직접 기재
```

#### 키가 적용되는 모듈 (전부 동일한 패턴)

| 모듈 | application.yml 위치 |
|------|---------------------|
| infolink-orchestrator-backend | `src/main/resources/application.yml` |
| infolink-agent-bojo-dmz | `src/main/resources/application.yml` |
| infolink-agent-bojo-internal | `src/main/resources/application.yml` |
| infolink-proxy-dmz | `src/main/resources/application.yml` |
| infolink-proxy-internal | `src/main/resources/application.yml` |
| infolink-api-collector | `src/main/resources/application.yml` |

6개 모듈 모두 **같은 마스터 키**를 사용한다. 하나의 키로 시스템 전체의 암호화/복호화가 통일된다.

#### 보안 주의사항

- docker-compose.yaml은 서버 로컬에만 존재, Git에 올리지 않음
- 로컬 개발 시 기본값(`sync-pipeline-secret-key-2024`)이 쓰이므로 별도 설정 불필요
- 운영 배포 시 docker-compose.yaml에 강력한 키 설정 필요
- 마스터 키가 노출되면 모든 ENC() 값이 복호화 가능하므로 관리 철저

---

## 모듈 간 통신 경로

Agent와 Orchestrator 사이의 통신이 **모두 Proxy를 경유하는 것은 아니다**. 용도에 따라 직접 통신과 Proxy 경유가 나뉜다.

### 직접 통신 (Orchestrator ↔ Agent)

| 통신 | 방향 | 용도 |
|------|------|------|
| 실행 명령 | Orchestrator → Agent | `POST /api/pipeline/execute` |
| 실행 결과 콜백 | Agent → Orchestrator | `POST /api/callback/finished` |
| 헬스체크 | Orchestrator → Agent | `GET /actuator/health` (30초마다) |
| Retention 정리 | Orchestrator → Agent | `POST /api/cleanup/{agentCode}` |

이들은 **제어/상태 관련 통신**으로, Proxy 경유 없이 직접 주고받는다.

### Proxy 경유 (DataSource 연결정보 조회)

파이프라인 실행 명령을 받은 직후, Agent가 실제 DB에 연결하기 위해 접속 정보를 조회하는 단계에서 Proxy를 경유한다.

```
① Orchestrator → Agent: "daejeon 동기화 실행해라"
   (datasourceId만 전달, 실제 접속 정보는 안 줌)

② Agent: "ds-daejeon-001의 접속 정보가 필요하다"
   → Proxy에 GET /api/datasources/ds-daejeon-001/connection-info
              (X-API-Key 헤더 포함)

③ Proxy: API Key 검증 통과 → Orchestrator에 그대로 패스스루

④ Orchestrator → Proxy → Agent: {host, port, username(암호화), password(암호화)}

⑤ Agent: PasswordEncryptor로 복호화 → HikariCP 풀 생성 → 외부 DB 연결

⑥ 이제 데이터 읽기/쓰기 시작
```

즉 Orchestrator가 실행 명령을 보낼 때는 "어떤 DB를 쓸지(datasourceId)"만 알려주고, **실제 host/port/password 같은 민감한 접속 정보**는 Agent가 필요한 시점에 Proxy를 통해 별도 조회한다.

### API Collector → Orchestrator 직접

API Collector는 Orchestrator와 같은 네트워크에 있으므로 DataSource 정보도 직접 조회한다:

```
API Collector → Orchestrator → 연결 정보 응답
```

---

## 모듈별 DataSource 서비스 비교

| 모듈 | 클래스 | 캐시 | 연결정보 출처 | ThreadLocal |
|------|--------|------|-------------|-------------|
| API Collector | DynamicDataSourceService | ConcurrentHashMap | Orchestrator 직접 | 없음 |
| Agent (DMZ/INT) | SyncDataSourceService | ConcurrentHashMap | Proxy 경유 | 있음 |
| Proxy (DMZ/INT) | ProxyDataSourceService | ConcurrentHashMap | Orchestrator 직접 | 없음 |

---

## Multi-DB 지원

시스템은 PostgreSQL과 MySQL을 동시에 지원한다. DB 종류에 따라 달라지는 것들:

| 항목 | PostgreSQL | MySQL |
|------|-----------|-------|
| JDBC URL | `jdbc:postgresql://...` | `jdbc:mysql://...?useSSL=false&...` |
| 드라이버 | `org.postgresql.Driver` | `com.mysql.cj.jdbc.Driver` |
| UPSERT | `ON CONFLICT ... DO UPDATE SET col = EXCLUDED.col` | `ON DUPLICATE KEY UPDATE col = VALUES(col)` |
| 식별자 인용 | `"컬럼명"` (큰따옴표) | `` `컬럼명` `` (백틱) |

코드에서는 `dbType`을 확인하는 헬퍼 메서드로 분기한다:
- `isMysql(dbType)`: MySQL이면 true
- `qi(columnName, dbType)`: DB 종류에 맞는 인용 부호로 컬럼명 감싸기

---

## 풀 관리 생명주기

```
1. 최초 요청 시 풀 생성 (Lazy)
   getDataSource("daejeon") → 캐시 miss → 풀 생성 → 캐시 저장

2. 이후 요청에서 재사용
   getDataSource("daejeon") → 캐시 hit → 기존 풀 반환

3. 설정 변경 시 폐기 + 재생성
   evict("daejeon") → 기존 풀 close → 캐시 제거
   다음 요청 시 새 풀 생성

4. 앱 종료 시 전체 정리
   @PreDestroy → 모든 풀 close
```

**풀 건강 체크** (Agent에서만): 파이프라인 실행 전 `checkPoolHealth()`로 현재 풀 상태를 확인한다. 활성 연결 수, 대기 중인 요청 수 등을 로그에 남겨 문제를 사전에 감지한다.
