# API Collector 로직 설명서

> **모듈**: `infolink-api-collector`
> **목적**: 코드 변경 없이 UI 등록만으로 외부 API 데이터를 수집·적재하는 범용 엔진
> **작성일**: 2026-03-24

---

## 목차

1. [개요](#1-개요)
2. [전체 데이터 플로우](#2-전체-데이터-플로우)
3. [엔티티 & 테이블 구조](#3-엔티티--테이블-구조)
4. [서비스 계층 상세](#4-서비스-계층-상세)
5. [LOOKUP 파생 컬럼](#5-lookup-파생-컬럼)
6. [스케줄링](#6-스케줄링)
7. [에러 처리 전략](#7-에러-처리-전략)
8. [REST API 목록](#8-rest-api-목록)
9. [설정 파일](#9-설정-파일)

---

## 1. 개요

### 설계 원칙

외부 API가 추가될 때마다 코드를 수정하지 않고, **UI에서 등록·설정만으로** 수집이 가능하도록 설계되었다. >> 테이블 추가는 필요함

```
[사용자가 UI에서 하는 일]
  API 기본정보 등록 → 파라미터 설정 → 테스트 호출 → 데이터 루트 선택
  → 필드 매핑 설정 → 타겟 테이블 지정 → 수동 실행 or 스케줄 등록
```

### 기술 스택

| 항목 | 기술 |
|------|------|
| Framework | Spring Boot 2.7.12 |
| Java | 17 |
| ORM | Spring Data JPA (Hibernate) |
| DB | PostgreSQL (api_collector, port 29001) |
| Build | Gradle |
| HTTP Client | RestTemplate (connect 10s, read 30s) |
| Scheduler | Spring TaskScheduler (ThreadPool 4) |
| 암호화 | Jasypt |

### 포트

| 환경 | 포트 |
|------|------|
| DMZ | 8084 |
| Internal | 8094 |

---

## 2. 전체 데이터 플로우

### 2.1 등록 ~ 실행 흐름 (End-to-End)

```
① API 등록
   POST /api/endpoints {apiName, url, httpMethod, authType, ...}
   └→ ApiEndpoint 생성 (params/mappings 비어있음)

② 파라미터 설정
   PUT /api/endpoints/{id}/params [{paramName, paramType, valueType, ...}, ...]
   └→ ApiParam 일괄 교체 저장

③ 테스트 호출
   POST /api/endpoints/{id}/test {paramOverrides: {...}}
   └→ API 호출 → JSON 응답 → TreeNode 변환 (UI에서 트리 시각화)
   └→ dataRootPath 지정 시 해당 경로의 필드 목록 추출

④ 데이터 루트 지정
   PUT /api/endpoints/{id} {dataRootPath: "data.items"}
   └→ JSON 응답에서 실제 데이터 배열의 위치

⑤ 필드 매핑 설정
   PUT /api/endpoints/{id}/mappings [{sourceFieldPath, targetColumnName, transformType, ...}]
   └→ JSON 필드 → DB 컬럼 매핑 (1:1 + LOOKUP 파생)

⑥ 타겟 테이블 지정
   PUT /api/endpoints/{id} {targetDatasourceId, targetTableName, upsertEnabled}

⑦ 실행 (수동 or 스케줄)
   POST /api/endpoints/{id}/run
   └→ ApiExecutionService.run()
       └→ API 호출 → JSON 파싱 → 변환 → INSERT/UPSERT → 이력 저장
```

### 2.2 실행 엔진 내부 흐름 (ApiExecutionService.run)

```
run(endpointId, triggeredBy)
  │
  ├─ 1. 검증: dataRootPath, fieldMappings, targetTableName 확인
  │
  ├─ 2. 실행 이력 생성 (Status=RUNNING)
  │
  ├─ 3. API 호출: ApiCallService.call() → HTTP 응답
  │     └→ httpStatusCode 기록
  │
  ├─ 4. JSON 파싱: ResponseParser.extractRecords() → List<Map>
  │     └→ responseCount 기록
  │
  ├─ 5. 매핑 분리
  │     ├─ normalMappings: isDerived=false (1:1 매핑)
  │     └─ derivedMappings: isDerived=true (LOOKUP)
  │
  ├─ 6. LOOKUP 사전 로드
  │     └→ 각 derived 매핑의 lookupParam으로 공통코드 API 호출 → Map 캐싱
  │
  ├─ 7. SQL 생성
  │     ├─ INSERT INTO table (col1, col2, ...) VALUES (?, ?, ...)
  │     └─ upsertEnabled=true 시: ON CONFLICT (pk) DO UPDATE SET ...
  │
  ├─ 8. Row 단위 INSERT (Savepoint 방식)
  │     for each record:
  │       ├─ Savepoint 설정
  │       ├─ 값 추출 + 변환(DataTransformer) + LOOKUP(LookupService)
  │       ├─ PreparedStatement.executeUpdate()
  │       ├─ 성공 → Savepoint 해제, insertCount++
  │       └─ 실패 → Savepoint 롤백, skipCount++ (다음 행 계속)
  │     Commit
  │
  └─ 9. 이력 업데이트: SUCCESS/FAILED, insertCount, skipCount, durationMs
```

---

## 3. 엔티티 & 테이블 구조

### 3.1 ER 관계

```
ApiEndpoint (1) ──── (N) ApiParam
     │
     ├──── (N) ApiFieldMapping
     │
     ├──── (N) ApiSchedule
     │
     └──── (N) ApiExecutionHistory
```

### 3.2 ApiEndpoint (api_endpoint)

API 정의의 중심 엔티티.

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 생성 |
| apiName | String | API 이름 |
| url | String | URL 템플릿 (`{param}` 플레이스홀더 지원) |
| httpMethod | String | GET / POST |
| contentType | String | POST body 형식 |
| authType | String | NONE / BASIC / BEARER |
| authConfig | String | 인증 설정 (암호화) |
| headers | String | 추가 헤더 JSON |
| dataRootPath | String | JSON 응답에서 데이터 배열 경로 (예: `$.data.items`) |
| targetDatasourceId | String | 외부 DB 연결 ID (Orchestrator에서 조회) |
| targetTableName | String | 적재 대상 테이블명 |
| upsertEnabled | Boolean | PostgreSQL ON CONFLICT 사용 여부 |
| zone | String | DMZ / INTERNAL |
| isActive | Boolean | 활성 상태 |

### 3.3 ApiParam (api_param)

API 호출 시 사용할 파라미터.

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | |
| paramName | String | 파라미터 이름 |
| paramType | String | QUERY / BODY / PATH / HEADER |
| valueType | String | STATIC / DYNAMIC |
| staticValue | String | 고정값 또는 API Key ID (isApiKeyRef=true 시) |
| isApiKeyRef | Boolean | true면 staticValue를 키 ID로 해석 → Orchestrator에서 실제 키 조회 |
| dynamicType | String | TODAY / NOW / CUSTOM |
| dynamicFormat | String | DateTimeFormatter 패턴 (예: `yyyyMMdd`) |
| dynamicOffset | Integer | TODAY: 일 수, NOW: 시간 수 오프셋 |
| displayOrder | Integer | UI 정렬 순서 |

**Dynamic 파라미터 예시**:
- `valueType=DYNAMIC, dynamicType=TODAY, dynamicFormat=yyyyMMdd, dynamicOffset=-1`
- → 어제 날짜를 `20260323` 형태로 치환

### 3.4 ApiFieldMapping (api_field_mapping)

JSON 응답 필드 → DB 컬럼 매핑. 일반 매핑과 LOOKUP 파생 매핑 두 가지 모드.

#### 일반 매핑 (isDerived=false)

| 필드 | 설명 |
|------|------|
| sourceFieldPath | JSON 필드 경로 (dot 표기, 예: `author.name`) |
| targetColumnName | DB 컬럼명 |
| transformType | NONE / DATE_FORMAT / NUMBER / SUBSTRING / TRIM / REPLACE / DEFAULT_VALUE |
| transformConfig | 변환 설정 JSON |
| isConflictKey | true면 UPSERT의 PK 컬럼으로 사용 |

#### LOOKUP 파생 매핑 (isDerived=true, transformType=LOOKUP)

| 필드 | 설명 |
|------|------|
| sourceFieldPath | 키를 추출할 원본 필드 |
| targetColumnName | 결과를 저장할 DB 컬럼 |
| extractPattern | 정규식 (키 추출용) |
| extractGroup | 캡처 그룹 번호 (기본 1) |
| lookupParam | 공통코드 그룹 (예: `NGW_0118`) |
| lookupKeyField | 공통코드 응답에서 매칭할 키 필드 |
| lookupValueField | 공통코드 응답에서 가져올 값 필드 |
| lookupDataRootPath | 공통코드 응답의 데이터 경로 |
| lookupMatchType | EXACT (향후 CONTAINS 등 확장 예정) |
| defaultValue | 매칭 실패 시 기본값 |

### 3.5 ApiSchedule (api_schedule)

| 필드 | 설명 |
|------|------|
| cronExpression | Spring 6필드 cron (초 분 시 일 월 요일) |
| isEnabled | 활성/비활성 |

### 3.6 ApiExecutionHistory (api_execution_history)

| 필드 | 설명 |
|------|------|
| executionId | UUID (요청 추적용) |
| status | RUNNING / SUCCESS / FAILED |
| httpStatusCode | HTTP 응답 코드 |
| responseCount | API에서 받은 전체 레코드 수 |
| insertCount | 성공 적재 수 |
| skipCount | 변환/적재 오류로 건너뛴 수 |
| errorMessage | 에러 메시지 |
| durationMs | 실행 소요 시간 (ms) |
| triggeredBy | MANUAL / SCHEDULE |
| startedAt / finishedAt | 시작/종료 시각 |

---

## 4. 서비스 계층 상세

### 4.1 ApiCallService — HTTP 호출

범용 HTTP 클라이언트. 모든 API 호출을 처리한다.

```
call(endpoint, params) 흐름:

1. URL 빌드
   - PATH 파라미터 치환: url 내 {paramName} → 값으로 교체

2. 파라미터 해석
   - isApiKeyRef=true → apiKeyCache에서 실제 키 로드 (Orchestrator API)
   - 그 외 → DynamicParamResolver.resolve()

3. 파라미터 적용
   - QUERY → URL 쿼리스트링
   - BODY → JSON/FormData body
   - HEADER → HTTP 헤더
   - PATH → 이미 1단계에서 치환됨

4. 인증 적용
   - BASIC → Base64(username:password) → Authorization 헤더
   - BEARER → token → Authorization: Bearer {token}

5. RestTemplate 호출 → CallResult(statusCode, body, error)
```

**API Key 캐싱**: 첫 사용 시 Orchestrator에서 로드, 이후 인메모리 캐시.

### 4.2 ResponseParser — JSON 파싱

두 가지 파싱 모드를 제공한다.

#### parseToTree (테스트 UI용)

```json
// 입력 JSON
{"data": {"items": [{"title": "뉴스1", "author": {"name": "홍길동"}}]}}

// 출력 TreeNode
TreeNode(name="root", type="OBJECT", children=[
  TreeNode(name="data", type="OBJECT", children=[
    TreeNode(name="items", type="ARRAY", arraySize=1, children=[
      TreeNode(name="[0]", type="OBJECT", children=[
        TreeNode(name="title", type="STRING", sampleValue="뉴스1"),
        TreeNode(name="author", type="OBJECT", children=[
          TreeNode(name="name", type="STRING", sampleValue="홍길동")
        ])
      ])
    ])
  ])
])
```

#### extractRecords (실행 엔진용)

```
dataRootPath="data.items" 로 탐색 → 배열의 각 요소를 flat Map으로 변환

입력: {"data":{"items":[{"title":"뉴스1","author":{"name":"홍길동"}}]}}
출력: [{"title":"뉴스1", "author.name":"홍길동"}]

→ 중첩 객체는 dot 표기로 평탄화
```

#### extractFields (매핑 UI용)

```
dataRootPath의 첫 번째 배열 요소에서 필드 목록 추출
출력: [FieldInfo(path="title", type="STRING", sampleValue="뉴스1"), ...]
```

### 4.3 DynamicParamResolver — 파라미터 치환

```
resolve(param, override) 로직:

1. override가 있으면 (테스트 호출) → override 값 반환
2. STATIC → staticValue 반환
3. DYNAMIC:
   - TODAY + offset → LocalDate.now().plusDays(offset).format(dynamicFormat)
   - NOW + offset   → LocalDateTime.now().plusHours(offset).format(dynamicFormat)
   - CUSTOM         → (미구현)
```

### 4.4 DataTransformer — 값 변환

1:1 매핑의 transformType에 따른 변환 처리.

| transformType | 동작 | transformConfig 예시 |
|---------------|------|---------------------|
| NONE | 그대로 전달 | — |
| DATE_FORMAT | 날짜 형식 변환 | `{"from":"yyyyMMdd","to":"yyyy-MM-dd"}` |
| NUMBER | 문자열→숫자 파싱 | — |
| SUBSTRING | 부분 문자열 추출 | `{"start":0,"length":10}` |
| TRIM | 공백 제거 | — |
| REPLACE | 문자열 치환 | `{"find":"old","replace":"new"}` |
| DEFAULT_VALUE | null이면 기본값 | `{"value":"N/A"}` |

> LOOKUP은 DataTransformer가 아닌 LookupService가 처리한다.

### 4.5 ApiEndpointService — CRUD

- 등록/수정/삭제/조회 기본 CRUD
- params, mappings는 **일괄 교체 방식** (기존 전체 삭제 → 새로 저장)
- 삭제 시 cascade: params → mappings → schedules → history 전부 삭제

### 4.6 ApiTestService — 테스트 호출

```
testCall(endpointId, paramOverrides)
  └→ DB에서 endpoint + params 로드
  └→ ApiCallService.call() (override 적용)
  └→ ResponseParser.parseToTree() → 트리 구조 반환
  └→ dataRootPath 있으면 extractFields() → 필드 목록 반환

testCallInline(InlineRequest)
  └→ DB 저장 없이 임시 Endpoint/Params 구성
  └→ 동일 흐름 (등록 전 사전 검증용)
```

---

## 5. LOOKUP 파생 컬럼

### 5.1 개념

API 응답의 원본 값에서 **정규식으로 키를 추출**하고, **공통코드 API를 호출**하여 해당 키에 대응하는 값을 가져오는 파생 컬럼 기능.

### 5.2 처리 흐름 (LookupService)

```
예시: 뉴스 URL → 언론사명 변환

원본 데이터: orgnlUrl = "https://www.chosun.com/national/court/2026/03/23/..."

① 키 추출 (extractPattern)
   패턴: https?://(?:www\.)?([^/]+)
   그룹 1 → "chosun.com"

② 공통코드 API 호출 (1회, 캐싱)
   GET {lookup.common-code-url}/NGW_0118
   응답: {"data":{"common":[
     {"detailCode":"chosun.com","detailCodeName":"조선일보"},
     {"detailCode":"donga.com","detailCodeName":"동아일보"}, ...
   ]}}

③ Map 구성 (lookupKeyField → lookupValueField)
   {"chosun.com":"조선일보", "donga.com":"동아일보", ...}

④ 매칭
   "chosun.com" → "조선일보"

⑤ 결과
   press_nm = "조선일보"
   (매칭 실패 시 defaultValue 사용)
```

### 5.3 캐싱

- 같은 실행 내에서 동일 `lookupParam`은 1회만 API 호출
- `lookupMaps: Map<lookupParam, Map<key, value>>` 로 캐싱
- 실행 단위로 캐시 생성·폐기 (실행 간 공유 없음)

### 5.4 설정 필드 요약

| 필드 | 예시 | 용도 |
|------|------|------|
| sourceFieldPath | `orgnlUrl` | 키 추출 대상 원본 필드 |
| extractPattern | `https?://(?:www\.)?([^/]+)` | 정규식 |
| extractGroup | `1` | 캡처 그룹 번호 |
| lookupParam | `NGW_0118` | 공통코드 그룹 |
| lookupKeyField | `detailCode` | 코드 Map의 key 필드 |
| lookupValueField | `detailCodeName` | 코드 Map의 value 필드 |
| lookupDataRootPath | `data.common` | 공통코드 응답 데이터 경로 |
| defaultValue | `(기타)` | 매칭 실패 시 기본값 |

---

## 6. 스케줄링

### 6.1 구조

```
ApiScheduleService (CRUD)
  └→ ApiScheduleExecutor (등록/해제/실행)
       └→ Spring TaskScheduler (ThreadPoolTaskScheduler, poolSize=4)
            └→ CronTrigger로 주기 실행
```

### 6.2 생명주기

```
생성:  DB 저장 → register() → TaskScheduler에 CronTrigger 등록
수정:  DB 업데이트 → unregister() → register() (재등록)
토글:  isEnabled 변경 → enabled면 register(), disabled면 unregister()
삭제:  unregister() → DB 삭제
앱 시작: ContextRefreshedEvent → 모든 enabled 스케줄 register()
```

### 6.3 동시성

- `ScheduledFuture`를 `ConcurrentHashMap<scheduleId, future>`에 보관
- unregister 시 `future.cancel(false)` 호출
- 스레드 풀 4개 → 최대 4개 스케줄 동시 실행 가능

---

## 7. 에러 처리 전략

### 7.1 계층별 전략

| 계층 | 전략 | 설명 |
|------|------|------|
| API 호출 | 예외 미전파 | `CallResult(error)` 반환, throw 안 함 |
| JSON 파싱 | 빈 결과 반환 | 파싱 실패 시 빈 List/null, throw 안 함 |
| Row 적재 | **Savepoint 개별 롤백** | 한 행 실패해도 나머지 행 계속 처리 |
| 실행 이력 | 전부 기록 | 성공/실패 무관하게 이력 테이블에 기록 |
| 스케줄러 | catch & continue | 실행 실패해도 스케줄러 자체는 계속 동작 |

### 7.2 Row 단위 Savepoint (핵심)

```java
for (record : records) {
    Savepoint sp = connection.setSavepoint();
    try {
        // 값 추출 + 변환 + INSERT
        stmt.executeUpdate();
        connection.releaseSavepoint(sp);
        insertCount++;
    } catch (Exception e) {
        connection.rollback(sp);  // 이 행만 롤백
        skipCount++;              // 다음 행 계속
    }
}
connection.commit();  // 성공한 행들만 커밋
```

한 행의 데이터 오류가 전체 배치를 실패시키지 않는다.

---

## 8. REST API 목록

### 8.1 Endpoint 관리

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/endpoints` | 목록 조회 |
| GET | `/api/endpoints/{id}` | 상세 조회 (params, mappings 포함) |
| POST | `/api/endpoints` | 등록 |
| PUT | `/api/endpoints/{id}` | 수정 |
| DELETE | `/api/endpoints/{id}` | 삭제 (cascade) |

### 8.2 파라미터 & 매핑

| Method | URL | 설명 |
|--------|-----|------|
| PUT | `/api/endpoints/{id}/params` | 파라미터 일괄 저장 |
| PUT | `/api/endpoints/{id}/mappings` | 매핑 일괄 저장 |

### 8.3 테스트 & 실행

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/endpoints/{id}/test` | 테스트 호출 (저장 없음) |
| POST | `/api/endpoints/test-inline` | 인라인 테스트 (DB 미저장 상태) |
| POST | `/api/endpoints/{id}/run` | 수동 실행 |

### 8.4 스케줄

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/endpoints/{eid}/schedules` | 스케줄 목록 |
| POST | `/api/endpoints/{eid}/schedules` | 스케줄 등록 |
| PUT | `/api/schedules/{id}` | 스케줄 수정 |
| PUT | `/api/schedules/{id}/toggle` | 활성/비활성 토글 |
| DELETE | `/api/schedules/{id}` | 스케줄 삭제 |

### 8.5 이력

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/endpoints/{eid}/history?page=0&size=20` | 실행 이력 (페이징) |

### 8.6 기타

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/endpoints/api-keys` | API Key 목록 (Orchestrator 프록시) |

---

## 9. 설정 파일

### application.yml 주요 항목

```yaml
server:
  port: 8084

spring:
  datasource:
    url: ENC(...)          # PostgreSQL api_collector DB (29001)
    username: ENC(...)
    password: ENC(...)
  jpa:
    hibernate:
      ddl-auto: update     # 엔티티 기반 자동 스키마 관리

orchestrator:
  url: http://localhost:8080   # 외부 데이터소스 정보 조회용

lookup:
  common-code-url: http://localhost:8084/mock/common/select/{groupCode}
  api-key-url: http://localhost:8084/mock/api-keys
```

### 외부 연동

| 대상 | 용도 | 설정 |
|------|------|------|
| Orchestrator (8080) | 외부 데이터소스 연결정보 조회 | `orchestrator.url` |
| 공통코드 API | LOOKUP 파생 컬럼용 | `lookup.common-code-url` |
| Frontend (3000) | Next.js CORS 허용 | WebConfig |

### Dynamic DataSource

- `DynamicDataSourceService`가 외부 DB 연결을 동적으로 관리
- Orchestrator에서 연결정보 조회 → HikariCP DataSource 생성 → 캐싱
- PostgreSQL / MySQL 자동 분기 지원

---

## 부록: 파일 위치 요약

```
infolink-api-collector/src/main/java/com/infolink/collector/
├── controller/
│   ├── ApiEndpointController.java     # Endpoint CRUD + test + run
│   ├── ApiScheduleController.java     # Schedule CRUD + toggle
│   ├── ApiHistoryController.java      # 실행 이력 조회
│   └── MockApiController.java         # 개발용 Mock API
├── service/
│   ├── ApiEndpointService.java        # Endpoint CRUD
│   ├── ApiTestService.java            # 테스트 호출
│   ├── ApiExecutionService.java       # 실행 엔진 (핵심)
│   ├── ApiCallService.java            # HTTP 호출
│   ├── ResponseParser.java            # JSON 파싱 (Tree/Records/Fields)
│   ├── DynamicParamResolver.java      # 동적 파라미터 치환
│   ├── DataTransformer.java           # 값 변환 (DATE_FORMAT 등)
│   ├── LookupService.java            # LOOKUP 파생 컬럼
│   ├── ApiScheduleService.java        # 스케줄 CRUD
│   └── ApiScheduleExecutor.java       # Cron 스케줄 실행기
├── entity/
│   ├── ApiEndpoint.java               # API 정의
│   ├── ApiParam.java                  # 파라미터
│   ├── ApiFieldMapping.java           # 필드 매핑
│   ├── ApiSchedule.java              # 스케줄
│   └── ApiExecutionHistory.java       # 실행 이력
├── dto/
│   ├── ApiEndpointDto.java            # Endpoint 요청/응답 DTO
│   ├── TestCallDto.java               # 테스트 호출 DTO
│   ├── ApiScheduleDto.java            # 스케줄 DTO
│   └── ApiExecutionHistoryDto.java    # 이력 DTO
├── repository/                        # JPA Repository 인터페이스들
└── config/
    ├── RestTemplateConfig.java        # HTTP 클라이언트 설정
    ├── SchedulerConfig.java           # TaskScheduler 설정
    ├── DynamicDataSourceService.java  # 동적 DataSource 관리
    ├── OrchestratorClient.java        # Orchestrator API 클라이언트
    └── WebConfig.java                 # CORS 설정
```
