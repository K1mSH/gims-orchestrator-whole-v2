# API 관리 — 1차 계획 (외부 API 수집)

## 핵심 설계 원칙: 설정 기반 확장

> **목표: 새 API 수집 경로 추가 시 코드 수정 0, UI 등록만으로 완결**
>
> 실행 엔진은 하나이고, 모든 동작은 DB에 저장된 설정(엔드포인트, 파라미터, 매핑)으로 결정된다.
> 코드에 특정 API명, 테이블명, 컬럼명, 응답 구조가 하드코딩되면 안 된다.

```
┌─────────────────────────────────────────────────┐
│                설정 (DB)                         │
│  ApiEndpoint: URL, method, auth                  │
│  ApiParam: 파라미터 (고정/동적)                   │
│  ApiFieldMapping: 응답경로 → DB컬럼              │
│  ApiSchedule: cron                               │
└──────────────────┬──────────────────────────────┘
                   ↓ 설정 읽기
┌─────────────────────────────────────────────────┐
│            범용 실행 엔진 (코드)                  │
│  1. 설정 로드                                    │
│  2. 동적 파라미터 치환                            │
│  3. HTTP 호출                                    │
│  4. 응답 파싱 (dataRoot 기준)                     │
│  5. 매핑 규칙 적용 → 행 데이터 생성               │
│  6. JdbcTemplate batch INSERT/UPSERT             │
│  7. 이력 기록                                    │
└─────────────────────────────────────────────────┘
```

### 이 원칙이 코드에 미치는 영향

| 영역 | 하면 안 되는 것 | 해야 하는 것 |
|------|---------------|------------|
| 호출 | `if (apiCode == "기상청")` 같은 분기 | 설정에서 URL/method/auth 읽어서 범용 호출 |
| 파싱 | `response.getData().getObsvCode()` | JSONPath로 `$.data[*].obsv_code` 동적 추출 |
| 적재 | `INSERT INTO sec_jewon (obsv_code...)` | 매핑 테이블에서 target_table + columns 조합하여 동적 SQL |
| 변환 | `if (column == "date") format(...)` | 매핑의 data_transform 규칙 해석 (범용 변환기) |
| 스케줄 | API별 스케줄러 클래스 | 범용 Executor가 설정 읽어서 실행 |

---

## 목적
- 외부 API를 호출하여 응답 데이터를 자체 DB에 적재하는 기능
- Agent 관리와 유사한 구조 (등록 → 매핑 → 스케줄 → 실행)
- Orchestrator 프레임 안에서 "API 관리" 메뉴로 운영

## 등록 흐름 — 테스트 호출 선행 방식

> **핵심**: API 응답 구조를 모르는 상태에서 매핑을 설정할 수 없다.
> 따라서 **테스트 호출을 필수 선행**하고, 그 결과로 응답 구조를 파악한 뒤 매핑을 설정한다.

```
1. API 기본정보 등록 (URL, method, auth, 파라미터)
     ↓
2. 테스트 호출 (필수) — 실제 API 호출하여 JSON 응답 수신
     ↓
3. 응답 트리 탐색 — 전체 JSON을 트리 UI로 표시
   - 유저가 데이터 배열 노드를 클릭 → data_root_path 지정
   - API마다 depth가 다른 문제를 유저 선택으로 해결
   - 예: response > body > items > item 클릭 → "response.body.items.item"
     ↓
4. 필드 매핑 — data_root 배열의 첫 번째 요소에서 필드 자동 추출
   - 추출된 필드(이름 + 타입 + 샘플값)를 target DB 컬럼에 매핑
   - PK 지정, 변환 설정
     ↓
5. 매핑 저장 — endpoint + params + data_root_path + mappings 일괄 저장
     ↓
6. 스케줄 등록 (cron) — 매핑 완료 후에야 가능
     ↓
7. 자동/수동 실행 → 이력 기록
```

### 단계별 잠금 (Step Lock)
| 단계 | 선행 조건 |
|------|----------|
| 테스트 호출 | 기본정보(URL, method, params) 저장 완료 |
| data_root 선택 | 테스트 호출 성공 |
| target 테이블 선택 | data_root 선택 완료 |
| 필드 매핑 | target 테이블 선택 완료 (컬럼 목록 로드) |
| 스케줄/수동실행 | 매핑 1개 이상 저장 |

### 테스트 호출 시 동적 파라미터 처리
- 동적 파라미터(TODAY 등)는 **현재 시점 기준 자동 치환**하여 호출
- 테스트 UI에서 치환 결과를 미리보기로 표시 (예: `date = 20260312`)
- 필요 시 유저가 테스트용 값을 **오버라이드** 가능

### 응답 트리 UI 동작 예시

API 응답이 아래와 같을 때:
```json
{
  "response": {
    "header": { "resultCode": "00", "resultMsg": "OK" },
    "body": {
      "items": {
        "item": [
          { "obscd": "G001", "wl": 12.5, "obs_date": "20260313" },
          { "obscd": "G002", "wl": 8.3, "obs_date": "20260313" }
        ]
      },
      "totalCount": 2,
      "pageNo": 1
    }
  }
}
```

트리 표시:
```
▸ response                         (object)
  ▸ header                         (object)
    - resultCode: "00"             (string)
    - resultMsg: "OK"              (string)
  ▸ body                           (object)
    ▸ items                        (object)
      ▸ item                       (array, 2건) ← [데이터 루트로 선택]
        - obscd: "G001"            (string)
        - wl: 12.5                 (number)
        - obs_date: "20260313"     (string)
    - totalCount: 2                (number)
    - pageNo: 1                    (number)
```

유저가 `item` 노드 클릭 → `data_root_path = "response.body.items.item"` 자동 설정
→ 하위 필드 3개(obscd, wl, obs_date)가 매핑 후보로 나열됨

## 1차 스코프 (이번에 만드는 것)

### Backend — 엔티티

#### ApiEndpoint (외부 API 정의)
```
api_endpoint
├── id (PK)
├── api_name (표시명)
├── api_code (고유코드, unique)
├── url (호출 URL 템플릿 — 예: "https://api.example.com/data/{type}")
├── http_method (GET / POST)
├── content_type (application/json 등 — POST body 형식)
├── headers (JSON — 추가 헤더)
├── auth_type (NONE / BASIC / BEARER)
├── auth_config (JSON — BASIC: {username, password}, BEARER: {token})
├── data_root_path (응답에서 데이터 배열 위치 — 테스트 호출 후 트리에서 지정)
├── target_datasource_id (적재 대상 datasource — 매핑 단계에서 설정)
├── target_table_name (적재 대상 테이블명 — 매핑 단계에서 설정)
├── upsert_enabled (true: UPSERT, false: INSERT)
├── description
├── is_active
├── zone (DMZ / INTERNAL)
├── created_at
└── updated_at
```

> **url**: PATH 파라미터는 `{paramName}` 플레이스홀더 — ApiParam에서 치환.
> **auth_type**: 헤더 기반 인증만 관리 (BASIC, BEARER). API Key는 ApiParam에서 QUERY/HEADER 파라미터로 등록.
> **data_root_path**: 최초 등록 시 비어있음 → 테스트 호출 후 트리 UI에서 지정.
> **target_datasource/table**: 최초 등록 시 비어있음 → 매핑 단계에서 Orchestrator의 기존 테이블 목록에서 선택.
> 하나의 API = 하나의 적재 테이블. 심플하게.

#### ApiParam (호출 파라미터)
```
api_param
├── id (PK)
├── api_endpoint_id (FK)
├── param_name (파라미터명)
├── param_type (QUERY / BODY / PATH)
├── value_type (STATIC / DYNAMIC)
├── static_value (고정값 — value_type=STATIC일 때)
├── dynamic_type (TODAY / NOW / CUSTOM)
├── dynamic_format (날짜 포맷 — 예: "yyyyMMdd", "yyyy-MM-dd")
├── dynamic_offset (오프셋 — 예: -1 = 어제, -7 = 일주일 전)
├── description
└── display_order
```

> **동적 파라미터 치환 규칙**:
> - `STATIC`: static_value 그대로 사용
> - `DYNAMIC + TODAY`: LocalDate.now() + offset → format 적용
> - `DYNAMIC + NOW`: LocalDateTime.now() + offset → format 적용
> - `DYNAMIC + CUSTOM`: 향후 확장용 (1차에서는 미구현)
>
> 예시: `param_name=date, dynamic_type=TODAY, dynamic_format=yyyyMMdd, dynamic_offset=-1` → 어제 날짜 "20260312"

#### ApiFieldMapping (응답 필드 → DB 컬럼 매핑)
```
api_field_mapping
├── id (PK)
├── api_endpoint_id (FK)
├── source_field_path (응답 필드명 — data_root 기준 상대경로. 예: "obsv_code", "data.value")
├── target_column_name (DB 컬럼명)
├── is_pk (UPSERT conflict 기준 여부)
├── transform_type (NONE / DATE_FORMAT / NUMBER / SUBSTRING — 확장 가능)
├── transform_config (JSON — 변환 설정. 예: {"from":"yyyyMMdd","to":"yyyy-MM-dd"})
└── display_order
```

> **source_field_path**: data_root_path 아래의 상대 경로. 중첩 객체는 dot notation (예: "location.lat").
> **transform_type**: 범용 enum으로 설계. 1차는 NONE + DATE_FORMAT만 구현.
>   - `NONE`: 값 그대로
>   - `DATE_FORMAT`: transform_config의 from→to 포맷 변환
>   - `NUMBER`: 문자열→숫자 변환 (추후)
>   - `SUBSTRING`: 부분 문자열 (추후)

#### ApiSchedule (스케줄)
```
api_schedule
├── id (PK)
├── api_endpoint_id (FK)
├── cron_expression (Spring cron 6자리)
├── is_enabled
├── created_at
```

#### ApiExecutionHistory (실행 이력)
```
api_execution_history
├── id (PK)
├── api_endpoint_id (FK)
├── execution_id (UUID)
├── status (RUNNING / SUCCESS / FAILED)
├── http_status_code
├── response_count (파싱된 레코드 수)
├── insert_count (적재 성공 수)
├── skip_count (스킵 수 — PK 중복 등)
├── error_message
├── started_at
├── finished_at
├── duration_ms
├── triggered_by (MANUAL / SCHEDULE)
```

### Backend — API 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| **CRUD** | | |
| GET | /api/endpoints | 목록 조회 |
| GET | /api/endpoints/{id} | 단건 조회 (params, mappings 포함) |
| POST | /api/endpoints | 등록 |
| PUT | /api/endpoints/{id} | 수정 |
| DELETE | /api/endpoints/{id} | 삭제 |
| **파라미터** | | |
| PUT | /api/endpoints/{id}/params | 파라미터 일괄 저장 |
| **테스트/실행** | | |
| POST | /api/endpoints/{id}/test | 테스트 호출 → 응답 트리 + 필드 후보 반환 |
| POST | /api/endpoints/{id}/run | 수동 실행 (호출 → 파싱 → 적재) |
| **매핑** | | |
| GET | /api/endpoints/{id}/mappings | 매핑 목록 |
| PUT | /api/endpoints/{id}/mappings | 매핑 일괄 저장 |
| **스케줄** | | |
| GET | /api/endpoints/{id}/schedules | 스케줄 목록 |
| POST | /api/endpoints/{id}/schedules | 스케줄 등록 |
| PUT | /api/schedules/{id} | 스케줄 수정 |
| PUT | /api/schedules/{id}/toggle | 토글 |
| DELETE | /api/schedules/{id} | 삭제 |
| **이력** | | |
| GET | /api/endpoints/{id}/history | 실행 이력 (Pageable) |
| **메타데이터** | | |
| GET | /api/metadata/tables | 자체 DB 테이블 목록 |
| GET | /api/metadata/tables/{name}/columns | 테이블 컬럼 목록 (PK 포함) |

> **프론트 프록시**: Next.js `/collector-api/*` → `localhost:8084/api/*`

### Backend — 핵심 서비스 (범용 엔진)

#### ApiCallService (HTTP 호출)
- 설정에서 URL + method + headers + auth 읽기
- 동적 파라미터 치환 (DynamicParamResolver)
- PATH 파라미터: URL 템플릿 `{name}` 치환
- QUERY 파라미터: URL ?key=value 조립
- BODY 파라미터: JSON body 조립
- Auth 처리: BASIC(Base64 header), BEARER(token header). API Key는 param으로 처리됨
- **결과**: HTTP 응답 String + status code

#### DynamicParamResolver (파라미터 치환기)
```java
// 범용 치환 — ApiParam 설정만으로 동작
String resolve(ApiParam param) {
    if (param.valueType == STATIC) return param.staticValue;
    LocalDate base = LocalDate.now().plusDays(param.dynamicOffset);
    return base.format(DateTimeFormatter.ofPattern(param.dynamicFormat));
}
```

#### ResponseParser (응답 파싱)
- JSON 응답 + data_root_path → List<Map<String, Object>> 추출
- 중첩 객체 flatten (dot notation)
- 테스트 모드: 필드 트리 반환 (fieldName + dataType추론 + 샘플값)
- **코드에 응답 구조 하드코딩 없음** — 모든 것은 JSONPath + 매핑으로 해결

#### DataTransformer (값 변환기)
```java
// 매핑의 transform_type + transform_config로 범용 변환
Object transform(Object value, TransformType type, Map<String,String> config) {
    return switch (type) {
        case NONE -> value;
        case DATE_FORMAT -> convertDateFormat(value, config.get("from"), config.get("to"));
        // 추후 NUMBER, SUBSTRING 등 추가 — enum만 확장하면 됨
    };
}
```

#### ApiExecutionService (실행 엔진)
```
1. ApiEndpoint 설정 로드
2. ApiCallService.call(endpoint, params) → 응답
3. ResponseParser.parse(응답, data_root_path) → List<Map>
4. 각 행에 대해:
   - ApiFieldMapping 순회 → source_field_path로 값 추출
   - DataTransformer.transform() 적용
   - target_column_name에 매핑
5. JdbcTemplate batch INSERT 또는 UPSERT (is_pk 기준 conflict key)
6. ApiExecutionHistory 기록
```

> **핵심**: 이 엔진은 어떤 API든 동일하게 동작한다.
> 새 API 추가 = DB에 설정 행 추가 (UI에서 등록) → 끝.

#### ApiScheduleExecutor
- 기존 ScheduleExecutor 패턴 재활용
- 앱 기동 시 활성 ApiSchedule 로드 → TaskScheduler 등록
- 트리거 시 ApiExecutionService.run(endpointId) 호출

### Frontend — 화면 구성

#### 메뉴 구조
```
사이드바 (Sidebar.tsx)
├── ... (기존)
├── API 관리
│   ├── 외부 API 수집   ← 1차
│   └── 내부 API 제공   ← 추후 (비활성 표시)
```

#### 페이지
```
app/
├── api-collect/
│   ├── page.tsx          — API 목록 + 등록
│   └── [id]/page.tsx     — API 상세
│       ├── InfoTab       — 기본정보 (URL, method, auth) + 파라미터 CRUD
│       ├── MappingTab    — 테스트 호출 + 필드 매핑 UI
│       ├── ScheduleTab   — 스케줄 CRUD
│       └── HistoryTab    — 실행 이력
```

#### 핵심 UI: MappingTab (2단계 구성)

**상태 1: 테스트 호출 전 (매핑 잠김)**
```
┌──────────────────────────────────────────────────────────┐
│ ⚠ 매핑을 설정하려면 먼저 테스트 호출을 실행하세요.       │
│                                                          │
│ [테스트 호출]                                             │
└──────────────────────────────────────────────────────────┘
```

**상태 2: 테스트 호출 후 (응답 트리 + 매핑)**
```
┌──────────────────────────────────────────────────────────┐
│ ✓ 테스트 호출 성공: 200 OK                               │
│ [다시 호출]                                               │
│                                                          │
│ ── 응답 구조 ──────────────────────────────────────────  │
│ ▸ response                                               │
│   ▸ header                                               │
│   ▸ body                                                 │
│     ▸ items                                              │
│       ▸ item (array, 15건)  [← 데이터 루트로 선택]       │
│                                                          │
│ data_root_path: response.body.items.item                 │
│                                                          │
│ ── 필드 매핑 ──────────────────────────────────────────  │
│ ┌──────────────────┐     ┌───────────────────────────┐   │
│ │ API 응답 필드     │     │ DB 컬럼 매핑              │   │
│ │──────────────────│     │───────────────────────────│   │
│ │ obscd (str)      │ →   │ [obsv_code ▾] [PK☑]      │   │
│ │  샘플: "G001"    │     │                           │   │
│ │ wl (num)         │ →   │ [water_level ▾] [  ]      │   │
│ │  샘플: 12.5      │     │                           │   │
│ │ obs_date (str)   │ →   │ [obsv_date ▾] [  ]        │   │
│ │  샘플: "20260313"│     │  변환: [DATE ▾] yyyyMMdd→yyyy-MM-dd │
│ │ station_nm (str) │ →   │ [— 제외 —]                │   │
│ └──────────────────┘     └───────────────────────────┘   │
│                                                          │
│ [매핑 저장]  [수동 실행]                                  │
└──────────────────────────────────────────────────────────┘
```

- **트리 영역**: JSON 응답 전체를 펼칠 수 있는 트리 — array 노드에 "데이터 루트로 선택" 버튼
- **target 선택**: data_root 선택 후 → Orchestrator Datasource API에서 datasource/테이블 드롭다운 제공
- **매핑 영역**: target 테이블 선택 후 활성화 — 좌측은 API 필드, 우측은 선택한 테이블의 컬럼
- 좌측: 필드명 + 타입추론 + 샘플값 (테스트 응답에서 자동 추출)
- 우측: Orchestrator 기존 테이블 컬럼 드롭다운 (또는 "제외"), PK 체크, 변환 옵션
- 컬럼 목록은 **Orchestrator의 기존 Datasource/Table API 재활용** → 이름 정합성 보장

## 모듈 구성

> 기존 Agent 파이프라인과 성격이 다르므로 **별도 모듈**로 분리.
> DMZ/내부망 양쪽에 배포 가능.

```
orchestrator_v2/
├── infolink-api-collector/           ← 신규 모듈 (구현 완료)
│   ├── build.gradle                  (Spring Boot 2.7.12, JPA, PostgreSQL, json-path, Jasypt)
│   ├── settings.gradle
│   ├── src/main/java/com/infolink/collector/
│   │   ├── ApiCollectorApplication.java
│   │   ├── domain/              (5개 엔티티 + Repository)
│   │   ├── dto/                 (ApiEndpointDto, ApiScheduleDto, ApiExecutionHistoryDto, TestCallDto)
│   │   ├── controller/          (ApiEndpointController, ApiScheduleController, ApiHistoryController, MetadataController)
│   │   ├── service/             (9개: Endpoint, Schedule, ScheduleExecutor, Call, Test, Execution, Parser, Transformer, Resolver)
│   │   └── config/              (WebConfig, RestTemplateConfig, SchedulerConfig)
│   └── src/main/resources/
│       └── application.yml       (port 8084, PG api_collector DB)
```

> **배포 분리**: 1차는 단일 모듈에 profile로 구분 예정.
> DMZ: `--spring.profiles.active=dmz` (port 8084), 내부망: `--spring.profiles.active=int` (port 8094)

| 배포 | 모듈 | 포트 (예시) | 역할 |
|------|------|------------|------|
| DMZ | infolink-api-collector-dmz | 8084 | 외부 공공 API 수집 |
| 내부망 | infolink-api-collector-int | 8094 | 내부망 API 수집 |

> **1차 개발**: 공통 코드(`infolink-api-collector`)를 먼저 만들고 DMZ 설정으로 테스트.
> 내부망 배포 설정은 구조만 잡아두고 실제 배포는 추후.

### API Key 관리 (1차)
- API Key는 **ApiParam으로 등록** (STATIC, QUERY 또는 HEADER 타입)
  - 예: `param_name=serviceKey, param_type=QUERY, value_type=STATIC, static_value="abc123..."`
- auth_type(BASIC/BEARER)은 HTTP 헤더 인증이 필요한 경우에만 사용
- **추후**: 내부망 API key 테이블 연동 시 value_type에 `CREDENTIAL_REF` 추가하여 확장

---

## 기존 인프라 재활용

| 기존 | 재활용 방식 |
|------|-----------|
| Datasource/Table/Column API (Orchestrator) | 프론트에서 target 테이블/컬럼 드롭다운 조회 — 이름 정합성 보장 |
| Schedule 구조 | ApiSchedule에 동일 패턴 적용 |
| ExecutionHistory 패턴 | ApiExecutionHistory로 변형 |
| Sidebar/TabButton 컴포넌트 | UI 공통 컴포넌트 재사용 |
| RestTemplate (bean) | API 호출에 재사용 |
| JdbcTemplate | 동적 SQL INSERT/UPSERT |

## 1차에서 제외 (추후)
- XML 응답 파싱 (1차는 JSON만)
- DMZ → 내부망 데이터 이관 (Agent SND 패턴 재활용 예정)
- 내부 API 제공 기능
- 복잡한 데이터 변환 (1차는 NONE + DATE_FORMAT만)
- 페이지네이션 처리 (다중 페이지 API 호출)
- OAuth2 인증
- 응답 캐싱 / 중복 호출 방지

## 작업 순서 및 진행 상태

### Step 1: 엔티티 + CRUD + 기본 UI ✅ 완료 (3/13)
- 백엔드: 5개 엔티티 (ApiEndpoint, ApiParam, ApiFieldMapping, ApiSchedule, ApiExecutionHistory)
- 백엔드: CRUD API + DTO
- 프론트: 목록 페이지 + InfoTab (기본정보 + 파라미터 등록)
- Sidebar 메뉴 추가

### Step 2: 테스트 호출 + 응답 트리 탐색 ✅ 완료 (3/13)
- 백엔드: ApiCallService + DynamicParamResolver
- 백엔드: ResponseParser — JSON → 트리 구조 변환 (노드별 타입/샘플값 포함)
- 백엔드: POST /test → 응답 트리 + 필드 후보 반환
- 프론트: MappingTab 상단 — 테스트 호출 버튼 + JSON 트리 표시
- 프론트: 트리에서 array 노드 클릭 → data_root_path 지정

### Step 3: 필드 매핑 + 실행 ✅ 완료 (3/13)
- 백엔드: ApiExecutionService + DataTransformer + 동적 SQL 생성
- 프론트: MappingTab 하단 — data_root 선택 후 필드 매핑 UI 활성화
- 프론트: 매핑 저장 + 수동 실행 + HistoryTab
- **E2E 검증 완료**: INSERT 100건, 중복 skip 100건, UPSERT 100건

#### 구현 중 수정사항
- `$` 경로 파싱: `navigateTo()`에서 `$` = root 처리 추가 (flat array 응답 대응)
- PG 트랜잭션 aborted 방지: Savepoint 패턴 적용 (행 단위 에러 격리)
- `@Transactional` + 별도 DataSource Connection 조합 (JPA 세션 유지 + INSERT 독립 커밋)

### Step 4: 스케줄 ✅ 완료 (3/13)
- 백엔드: ApiScheduleExecutor (TaskScheduler + CronTrigger)
- 백엔드: SchedulerConfig (ThreadPoolTaskScheduler, 4스레드)
- 프론트: ScheduleTab (cron 프리셋, 인라인 편집, 토글, 삭제)
- **스케줄 자동 실행 검증 완료**: triggeredBy=SCHEDULE로 이력 기록 확인

#### 구현 중 수정사항
- LazyInitializationException: 스케줄 스레드에서 JPA 세션 없음 → `@Transactional` 재적용으로 해결

### Step 5: 통합 테스트 ⬜ 미완료
- E2E 테스트 (공공 API 등 실제 대상으로)
- 다양한 depth의 API 응답으로 트리 탐색 검증
- 프론트엔드 브라우저에서 전체 UI 플로우 테스트
