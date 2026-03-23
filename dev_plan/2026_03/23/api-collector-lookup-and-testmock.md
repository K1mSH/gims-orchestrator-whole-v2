# API Collector — 필드 가공 확장 + LOOKUP + 테스트 Mock 서비스

## 배경

외부 API 응답에는 없지만, 내부 공통코드 기반으로 파생시켜야 하는 컬럼이 있음.
- 예: `orignl_url` → 도메인 추출 → GIMS 공통코드 API로 언론사명 LOOKUP

핵심 가공 패턴:
> **소스필드 → 정규식 추출 → 공통코드 API(코드 파라미터) 조회 → 매칭(EXACT) → 치환**

이 패턴을 **LOOKUP**이라는 하나의 가공 타입으로 정의하고 범용 제공.
향후 매칭 방식 확장(CONTAINS 등 — 금칙어 같은 케이스) 가능하도록 `lookupMatchType` 필드 예비.

현재 GIMS 본체 API를 직접 호출할 수 없으므로, 테스트용 Mock 서비스도 함께 구성.

---

## 설계 방향: ApiFieldMapping 통합

별도 엔티티(ApiDerivedColumn)를 만들지 않고, **기존 `ApiFieldMapping` 하나로 통합**.
- `transformType`에 LOOKUP 등 추가
- LOOKUP일 때만 사용하는 추가 필드를 ApiFieldMapping에 확장
- 프론트에서도 매핑 테이블 하나에서 1:1 + 파생 컬럼 모두 관리

### 가공 분류

**1. 단순 변환** (기존 1:1 매핑 행에서 사용)
| TransformType | 용도 | transformConfig 예시 |
|---|---|---|
| NONE | 그대로 | - |
| DATE_FORMAT | 날짜 포맷 변환 | `{"from":"yyyy-MM-dd","to":"yyyyMMdd"}` |
| NUMBER | 문자→숫자 | - |
| SUBSTRING | 문자열 자르기 | `{"start":0,"length":5}` |
| TRIM | 공백 제거 | - |
| REPLACE | 문자열 치환 | `{"from":"\n","to":" "}` |
| DEFAULT_VALUE | null 대체 | `{"value":"N/A"}` |

**2. 파생 컬럼** (사용자가 "+"로 추가한 행에서 사용)
| TransformType | 용도 | 추가 필드 사용 |
|---|---|---|
| LOOKUP | 내부 API 공통코드 매핑 | extractPattern, extractGroup, lookupApiUrl, lookupParam, lookupKeyField, lookupValueField, lookupDataRootPath, defaultValue |

---

## Part 1: 테스트 Mock 서비스

### 목적
- api-collector 개발/테스트 시 외부 API + 내부 공통코드 API 역할 대행
- api-collector DB(api_collector)에 테스트용 테이블 추가

### 구현

#### 1-1. Mock 외부 API (뉴스 API 시뮬레이션)
api-collector 자체에 테스트 컨트롤러 추가 (`@Profile("dev")`)

```
GET /mock/news?date=20260323&page=1
→ 응답:
{
  "resultCode": "00",
  "items": [
    {
      "title": "지하수 관측소 신규 설치",
      "orignl_url": "https://www.chosun.com/article/123",
      "reg_date": "2026-03-23"
    },
    ...
  ]
}
```

#### 1-2. Mock 내부 공통코드 API (GIMS 대행)
실제 GIMS API: `GET /api/common/select/{groupCode}`
```
GET /mock/common/select/NGW_0118
→ 응답:
{
  "status": "success",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "common": [
      {"id": 1, "groupId": 15, "detailCode": "chosun.com", "detailCodeName": "조선일보", "shortCode": null, "useYn": "Y"},
      {"id": 2, "groupId": 15, "detailCode": "donga.com", "detailCodeName": "동아일보(신동아)", "shortCode": null, "useYn": "Y"},
      {"id": 3, "groupId": 15, "detailCode": "hani.co.kr", "detailCodeName": "한겨레", "shortCode": null, "useYn": "Y"},
      ...
    ]
  }
}
```

#### 1-3. 테스트용 타겟 테이블
```sql
-- api_collector DB에 생성
CREATE TABLE test_news (
    id SERIAL PRIMARY KEY,
    title VARCHAR(500),
    orignl_url VARCHAR(1000),
    press_name VARCHAR(100),    -- 파생 컬럼 (LOOKUP으로 채워짐)
    reg_date VARCHAR(20)
);
```

---

## Part 2: ApiFieldMapping 확장

### 2-1. 엔티티 필드 추가

```java
@Entity
@Table(name = "api_field_mapping")
public class ApiFieldMapping {
    // --- 기존 필드 ---
    Long id;
    ApiEndpoint apiEndpoint;
    String sourceFieldPath;        // 1:1이면 API 필드, LOOKUP이면 키 추출 대상 필드
    String targetColumnName;
    Boolean isConflictKey;
    TransformType transformType;   // NONE, DATE_FORMAT, NUMBER, SUBSTRING, TRIM, REPLACE, DEFAULT_VALUE, LOOKUP
    String transformConfig;        // 단순 변환 설정 JSON
    Integer displayOrder;

    // --- 신규: LOOKUP 전용 필드 ---
    String extractPattern;         // 키 추출 정규식 (예: "https?://(?:www\\.)?([^/]+)")
    Integer extractGroup;          // 정규식 캡처 그룹 번호 (기본 1)
    String lookupApiUrl;           // 내부 API URL (예: "http://host/api/common/select/{groupCode}")
    String lookupParam;            // 공통코드 파라미터 (예: "NGW_0118") — URL {groupCode}에 치환
    String lookupMatchType;        // 매칭 방식: EXACT(기본), 향후 CONTAINS 등 확장 예비
    String lookupKeyField;         // 응답 매칭 키 (예: "detailCode")
    String lookupValueField;       // 응답 반환 값 (예: "detailCodeName")
    String lookupDataRootPath;     // 응답 데이터 루트 (예: "data.common")
    String defaultValue;           // LOOKUP 실패 시 기본값 (예: "기타")
    Boolean isDerived;             // true면 파생 컬럼 (API 응답에 없는 컬럼), false면 1:1 매핑
}
```

### 2-2. TransformType 확장
```java
public enum TransformType {
    NONE,           // 기존
    DATE_FORMAT,    // 기존
    NUMBER,         // 기존
    SUBSTRING,      // 신규 — 문자열 자르기
    TRIM,           // 신규 — 공백 제거
    REPLACE,        // 신규 — 문자열 치환
    DEFAULT_VALUE,  // 신규 — null 대체
    LOOKUP          // 신규 — 내부 API 공통코드 매핑 (파생 컬럼)
}
```

### 2-3. 키 추출 (정규식 기반)

ExtractType enum 없이, `extractPattern` + `extractGroup`으로 범용 처리.

```java
// extractPattern = "https?://(?:www\\.)?([^/]+)", extractGroup = 1
private String extractKey(String value, String pattern, int group) {
    Matcher m = Pattern.compile(pattern).matcher(value);
    return m.find() ? m.group(group) : value;  // 매칭 실패 시 원본 반환
}
```

**프리셋 정규식** (프론트 UI에서 버튼으로 제공):
| 프리셋 | 정규식 | 그룹 | 예시 입력 → 결과 |
|---|---|---|---|
| 도메인 | `https?://(?:www\\.)?([^/]+)` | 1 | `https://www.chosun.com/art/123` → `chosun.com` |
| 숫자만 | `(\d+)` | 1 | `CODE-12345-A` → `12345` |
| 괄호 안 | `\(([^)]+)\)` | 1 | `서울(01)` → `01` |

**미리보기** (프론트에서 실시간 처리):
- 테스트 호출 응답에서 해당 소스필드 값 최대 5건 샘플링
- 입력된 정규식을 JS `RegExp`로 즉시 실행
- 원본값 → 추출 결과를 테이블로 표시 (백엔드 호출 없음)

```
── 추출 미리보기 ──────────────────────────────────────
원본값                                    → 추출 결과
"https://www.chosun.com/art/123"         → "chosun.com"
"https://news.kbs.co.kr/view/1"          → "news.kbs.co.kr"
"https://www.hani.co.kr/arti/3"          → "hani.co.kr"
───────────────────────────────────────────────────────
```

### 2-5. 실행 흐름 변경 (ApiExecutionService.run)

```
기존:
  API 호출 → 레코드 추출 → 1:1 매핑 → DB 적재

변경 후:
  1. 매핑 목록에서 LOOKUP 행 분리
     - isDerived=false → 일반 매핑 (기존)
     - isDerived=true, transformType=LOOKUP → 파생 컬럼

  2. LOOKUP 매핑별 내부 API 호출 → Map<String,String> 캐싱
     - lookupApiUrl + lookupParam으로 호출
     - 응답에서 lookupDataRootPath로 배열 추출
     - lookupKeyField→lookupValueField 쌍으로 Map 구성

  3. 외부 API 호출 → 레코드 추출

  4. 각 레코드 처리:
     a. 일반 매핑: sourceFieldPath → transformType 적용 → targetColumnName
     b. 파생 매핑: sourceFieldPath에서 값 → extractType으로 키 추출 → 캐싱 Map LOOKUP → targetColumnName
        (매칭 실패 시 defaultValue 사용)

  5. DB 적재 (일반 + 파생 컬럼 합쳐서 INSERT/UPSERT)
```

---

## Part 3: 프론트엔드 (MappingTab.tsx)

### UI 구조 (테이블 하나로 통합)
```
API필드(소스)  →  Target컬럼  | 가공방식         | 설정              | 중복키 | 제외
─────────────────────────────────────────────────────────────────────────────────
title          →  title       | 없음             |                   |        |
orignl_url     →  orignl_url  | 없음             |                   |        |
reg_date       →  reg_date    | 날짜변환         | yyyy-MM-dd→…      |        |
─── 구분선 ──────────────────────────────────────────────────────────────────────
orignl_url  ▼  →  press_name  | LOOKUP(공통코드)  | [설정 펼치기 ▾]    |   ✓   |  ✕
                               ┌──────────────────────────────────────────────────────┐
                               │ 추출 정규식: [https?://(?:www\.)?([^/]+)            ] │
                               │ 추출 그룹:   [1]                                     │
                               │ [프리셋: 도메인 | 숫자만 | 괄호 안]                    │
                               │                                                      │
                               │ ── 추출 미리보기 ──────────────────────────────────── │
                               │ "https://www.chosun.com/art/123"  → "chosun.com"     │
                               │ "https://news.kbs.co.kr/view/1"   → "news.kbs.co.kr" │
                               │ "https://www.hani.co.kr/arti/3"   → "hani.co.kr"     │
                               │ ───────────────────────────────────────────────────── │
                               │                                                      │
                               │ LOOKUP API: [http://.../{groupCode}                ] │
                               │ 코드 파라미터: [NGW_0118                           ] │
                               │ 데이터 루트: [data.common                          ] │
                               │ 키 필드: [detailCode                               ] │
                               │ 값 필드: [detailCodeName                           ] │
                               │ 기본값: [기타                                       ] │
                               └──────────────────────────────────────────────────────┘
─────────────────────────────────────────────────────────────────────────────────
                              [+ 파생 컬럼 추가]
```

- 위쪽: API 응답에서 자동 생성된 1:1 행 (isDerived=false)
- 아래 구분선 뒤: 사용자가 "+"로 추가한 파생 행 (isDerived=true)
- 파생 행의 소스필드는 드롭다운 (1:1 행의 API 필드 목록에서 선택)
- 가공방식이 LOOKUP이면 설정 패널이 인라인 확장
- 단순 변환(DATE_FORMAT 등)도 가공방식 드롭다운에서 선택 가능
- 파생 행은 ✕ 버튼으로 삭제 가능
- 저장 시 모든 행(1:1 + 파생) 한번에 전송

---

## 수정 대상 파일

| 파일 | 작업 |
|------|------|
| `entity/ApiFieldMapping.java` | **수정** — LOOKUP 필드 추가 (extractPattern, extractGroup, lookup*, defaultValue, isDerived) |
| `entity/TransformType.java` | **수정** — SUBSTRING, TRIM, REPLACE, DEFAULT_VALUE, LOOKUP 추가 |
| `service/DataTransformer.java` | **수정** — SUBSTRING, TRIM, REPLACE, DEFAULT_VALUE 구현 |
| `service/LookupService.java` | **신규** — 내부 API 호출 + Map 캐싱 + 정규식 키 추출 + LOOKUP |
| `service/ApiExecutionService.java` | **수정** — 파생 컬럼 처리 통합 |
| `service/ApiEndpointService.java` | **수정** — saveMappings에 isDerived 처리 |
| `dto/ApiFieldMappingDto.java` (또는 기존 DTO) | **수정** — LOOKUP 필드 추가 |
| `controller/MockApiController.java` | **신규** — `@Profile("dev")` 테스트 API |
| `frontend/components/api-collect/MappingTab.tsx` | **수정** — 파생 컬럼 행 추가/삭제, LOOKUP 설정 패널 |
| `frontend/types/api-collect.ts` | **수정** — 타입 확장 |

---

## 작업 순서

### Step 1: Mock 서비스 + 테스트 테이블
- MockApiController (뉴스 + 공통코드)
- test_news 테이블 DDL

### Step 2: 백엔드 — 엔티티/enum 확장
- ApiFieldMapping에 LOOKUP 필드 추가 (extractPattern, extractGroup, lookup*, defaultValue, isDerived)
- TransformType 확장 (SUBSTRING, TRIM, REPLACE, DEFAULT_VALUE, LOOKUP)

### Step 3: 백엔드 — 단순 변환 구현
- DataTransformer에 SUBSTRING, TRIM, REPLACE, DEFAULT_VALUE 케이스 추가

### Step 4: 백엔드 — LOOKUP 로직
- LookupService (정규식 키 추출 + 내부 API 호출 → Map 캐싱)
- ApiExecutionService 통합

### Step 5: 프론트엔드
- MappingTab.tsx — 파생 컬럼 행 추가/삭제, LOOKUP 설정 패널
- 가공방식 드롭다운 확장
- types 수정

### Step 6: 통합 테스트
- Mock 뉴스 API 등록 → 1:1 매핑 + LOOKUP 파생 컬럼 설정 → 실행 → test_news 확인

---

## 영향 범위
- **infolink-api-collector 모듈만** — 다른 모듈 변경 없음
- DB: `api_field_mapping` 테이블에 컬럼 추가 (JPA auto-ddl) + `test_news` 테이블
- 기존 1:1 매핑은 isDerived=false, transformType 기존값 그대로 → 하위호환 유지
