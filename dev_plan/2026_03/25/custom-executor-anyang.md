# API Collector 커스텀 실행기 + 안양 이용량 연동

## 목적
- api-collector에 **커스텀 실행기** 구조 추가 (범용 매핑으로 처리 불가능한 특수 로직 대응)
- 첫 번째 케이스: 안양시 이용량 API (FAC + DATA + USE_LEGACY_DATA)

## 배경
- 안양시 API는 2개 호출 결과를 테이블에 넣고, 추가로 JOIN 기반 파생 테이블(USE_LEGACY_DATA)에 기록
- DATA INSERT 시 `USGQTY`를 서브쿼리로 산출 (건별 계산)
- 범용 매핑(1:1 필드매핑 + INSERT)으로는 불가능
- 외부 접근 불가 → Mock API로 시뮬레이션

## 설계

### 1. 커스텀 실행기 구조

#### 인터페이스
```java
public interface CustomExecutor {
    /** 실행기 ID (엔드포인트에 저장되는 키) */
    String getId();

    /** 표시명 (UI 드롭다운용) */
    String getDisplayName();

    /** 실행 */
    CustomExecutionResult execute(ApiEndpoint endpoint, List<ApiParam> params, String triggeredBy);
}

public record CustomExecutionResult(
    int responseCount, int insertCount, int skipCount,
    String errorMessage
) {}
```

#### 레지스트리
```java
@Component
public class CustomExecutorRegistry {
    private final Map<String, CustomExecutor> executors;

    // Spring이 모든 CustomExecutor Bean을 자동 주입
    public CustomExecutorRegistry(List<CustomExecutor> executorList) {
        this.executors = executorList.stream()
            .collect(Collectors.toMap(CustomExecutor::getId, e -> e));
    }

    public Optional<CustomExecutor> get(String id) { ... }
    public List<CustomExecutor> getAll() { ... }  // UI 목록용
}
```

#### ApiEndpoint 엔티티 변경
```java
// 추가 필드
private String executorType;  // null = 범용, "anyang-usage" = 커스텀
```

#### 실행 분기 (ApiExecutionService 또는 Controller)
```
if (endpoint.executorType != null) {
    CustomExecutor executor = registry.get(endpoint.executorType);
    result = executor.execute(endpoint, params, triggeredBy);
} else {
    result = 기존 범용 매핑 실행;
}
// 이후 ApiExecutionHistory 이력 저장은 동일
```

### 2. 안양 이용량 커스텀 실행기

#### 클래스
```java
@Component
public class AnyangUsageExecutor implements CustomExecutor {

    @Override public String getId() { return "anyang-usage"; }
    @Override public String getDisplayName() { return "안양시 이용량 (FAC+DATA+LEGACY)"; }
}
```

#### 실행 플로우
```
1. API 1 호출 → FAC 데이터 수신
2. ANYANG_API_FAC 테이블 전체 DELETE + INSERT (시설정보 갱신)
3. API 2 호출 → DATA 데이터 수신
4. for (각 DATA 건) {
     a. ANYANG_API_DATA INSERT
        - USGQTY = VALUE - (SELECT MAX(last_meter_value) FROM anyang_api_data WHERE account_no=? AND meter_dtm < ?)
     b. USE_LEGACY_DATA INSERT
        - FAC JOIN DATA → telno, obsr_dt, last_measure_value, usgqty, last_change_dt
        - 중복 제외 (sn NOT IN use_legacy_data)
   }
5. 결과 리턴 (총 건수, INSERT 건수)
```

#### 필요한 것
- `ApiCallService.call()` 재사용 (HTTP 호출)
- `DynamicDataSourceService` 재사용 (외부 DB 연결)
- JdbcTemplate으로 커스텀 SQL 실행

### 3. Mock API (안양시 시뮬레이션)

#### `/mock/anyang/fac` — 시설정보
```json
{
  "data": [
    {
      "company_cd": "1",
      "company_nm": "하이텍",
      "account_no": "1041-001-040-0800-90-2",
      "account_nm": "삼원프라자호텔",
      "status_device": "2",
      "connect_dtm": "2026-03-20 10:00:00",
      "device_sn": "NL1122800284",
      "gps_latitude": "37.398216",
      "gps_longitude": "126.922561",
      "meter_sn": "22-330073",
      "caliber_cd": "32",
      "full_addr": "안양 1동 장내로 139번길 7",
      "cdma_no": "450-06-1239072110"
    },
    ... (5건 정도)
  ]
}
```

#### `/mock/anyang/data` — 이용량
```json
{
  "data": [
    {
      "account_no": "1041-001-040-0800-90-2",
      "meter_dtm": "2026-03-24 08:00:00",
      "value": 4869,
      "digits": 0,
      "leak_state": "0",
      "term_batt": 35,
      "m_low_batt": "0",
      "m_leak": "0",
      "m_over_load": "0",
      "m_reverse": "0",
      "m_not_use": "0",
      "db_in_dtm": "2026-03-24 08:05:00",
      "db_in_seq": 141707364,
      "last_meter_value": 4869,
      "useqty": 2
    },
    ... (10건 정도)
  ]
}
```

### 4. DB 테이블 (api_collector DB에 생성)

```sql
-- 시설정보
CREATE TABLE anyang_api_fac (
    company_cd VARCHAR(10),
    company_nm VARCHAR(100),
    account_no VARCHAR(50),  -- UK
    account_nm VARCHAR(100),
    status_device VARCHAR(10),
    connect_dtm TIMESTAMP,
    state_display VARCHAR(10),
    device_sn VARCHAR(50),
    gps_latitude VARCHAR(20),
    gps_longitude VARCHAR(20),
    meter_sn VARCHAR(50),
    caliber_cd VARCHAR(10),
    mt_down VARCHAR(10),
    mt_down_dtm TIMESTAMP,
    mt_last_dtm TIMESTAMP,
    full_addr VARCHAR(200),
    cdma_no VARCHAR(50),
    nwk VARCHAR(50)
);

-- 이용량 데이터
CREATE TABLE anyang_api_data (
    account_no VARCHAR(50),
    meter_dtm TIMESTAMP,
    value NUMERIC,
    digits NUMERIC,
    leak_state VARCHAR(10),
    term_batt NUMERIC,
    m_low_batt VARCHAR(10),
    m_leak VARCHAR(10),
    m_over_load VARCHAR(10),
    m_reverse VARCHAR(10),
    m_not_use VARCHAR(10),
    db_in_dtm TIMESTAMP,
    db_in_seq NUMERIC,
    last_meter_value NUMERIC,
    usgqty NUMERIC
);

-- 결과 기록 (파생)
CREATE TABLE use_legacy_data (
    sn NUMERIC,
    telno VARCHAR(20),
    obsr_dt TIMESTAMP,
    last_measure_value NUMERIC,
    usgqty NUMERIC,
    last_change_dt TIMESTAMP
);
```

## 수정 대상 파일

### 백엔드 (infolink-api-collector)
| 파일 | 작업 |
|------|------|
| `CustomExecutor.java` | **신규** — 인터페이스 |
| `CustomExecutionResult.java` | **신규** — 결과 record |
| `CustomExecutorRegistry.java` | **신규** — Bean 레지스트리 |
| `AnyangUsageExecutor.java` | **신규** — 안양 이용량 실행기 |
| `ApiEndpoint.java` | `executorType` 필드 추가 |
| `ApiEndpointDto.java` | Create/Update/Detail에 `executorType` 추가 |
| `ApiEndpointService.java` | 저장 시 executorType 반영 |
| `ApiExecutionService.java` | 실행 시 커스텀/범용 분기 |
| `ApiEndpointController.java` | 커스텀 실행기 목록 API 추가 |
| `MockApiController.java` | 안양 Mock API 2개 추가 |

### 프론트엔드
| 파일 | 작업 |
|------|------|
| `types/api-collect.ts` | `executorType` 필드 추가 |
| `lib/collectorApi.ts` | 커스텀 실행기 목록 API 추가 |
| `app/api-collect/page.tsx` | 등록 시 실행 방식 선택 (범용/커스텀) |
| `components/api-collect/InfoTab.tsx` | 수정 시 실행 방식 표시/변경 |
| `components/api-collect/MappingTab.tsx` | 커스텀 선택 시 매핑 섹션 비활성화 또는 숨김 |

### DB
| 작업 |
|------|
| `anyang_api_fac`, `anyang_api_data`, `use_legacy_data` 테이블 생성 (api_collector DB) |

## 영향 범위
- api-collector 백엔드/프론트만 수정
- 기존 범용 매핑 실행에는 영향 없음 (executorType=null이면 기존 로직)
- 다른 모듈(agent, orchestrator, proxy) 변경 없음

## UI 동작

### 등록 화면
```
실행 방식:  ○ 범용 (매핑 기반)    ○ 커스텀
           [안양시 이용량 (FAC+DATA+LEGACY) ▼]

※ 커스텀 선택 시: 파라미터/테스트 호출은 사용 가능, 매핑 설정은 불필요
```

### 상세 화면
- 커스텀 엔드포인트: 매핑 탭 대신 "커스텀 실행기 정보" 표시
- 테스트 호출, 수동 실행, 스케줄, 이력은 동일하게 사용 가능
