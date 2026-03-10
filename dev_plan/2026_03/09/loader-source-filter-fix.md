# Source 데이터 필터링 수정

## 문제
Loader 실행 상세 페이지에서 source 데이터 조회 시,
해당 execution에서 처리된 건수가 아닌 **테이블 전체**가 표시됨.

| Loader | SyncLog 건수 | Source API 건수 | 비고 |
|--------|-------------|----------------|------|
| DMZ Loader | 1,206 | 4,006 | if_rsv_sec_jewon |
| Internal Loader | 14,093 | 33,031 | if_rsv_sec_obsvdata |

### 원인
`getSourcePksFromIfTable()`가 SyncLog에서 **TARGET_IF 타입만** 찾아서 source_refs를 역추출하는 방식.
Loader의 target은 TARGET 타입이라 누락 → return null → 전체 표시 fallback.

### 배경: 추적 구조
- **모든 Agent**가 target row에 `execution_id` + `source_refs`를 기록
- `execution_id`: 실행하면 모든 target row에 박힘
- `source_refs`: DB명, 테이블명, PK가 포함 (예: `["I:internal:if_rsv_sec_obsvdata:41072"]`)
- **RCV/SND**: IF 테이블을 만듦 → target 이름에 규칙성 (`if_rsv_`, `if_snd_`)
- **Loader**: IF를 source 삼아 임의 이름 target에 적재 → 이름 매칭 불가 (pm_gd970201 등)

## 수정 대상 파일
- `sync-agent-common/.../controller/ExecutionDataController.java`
  - `getSourcePksFromIfTable()` 메서드 (254행~)

## 변경 내용

### 1. target 테이블 필터 조건 변경 (258~262행)

TARGET_IF만 찾던 것 → SOURCE/LINK가 아닌 모든 타입으로 변경.

```java
// 기존
.filter(l -> "TARGET_IF".equals(l.getTableType()) || "IF".equals(l.getTableType()))
// 수정
.filter(l -> !"SOURCE".equals(l.getTableType()) && !"LINK".equals(l.getTableType()))
```

### 2. 이름 매칭 → source_refs 기반 매칭으로 변경 (272~281행)

기존: IF prefix 제거 후 코어명 비교 → Loader target(pm_gd970201)은 매칭 불가.

수정: 이름 매칭 실패 시, 각 target 테이블에서 `source_refs LIMIT 1` 샘플을 조회하여
source_refs 안에 **sourceTable명이 포함**된 target을 찾는다.

```
예: sourceTable = if_rsv_sec_obsvdata, target 후보 = [pm_gd970201]
  1. 이름 매칭 → pm_gd970201에 "sec_obsvdata" 미포함 → 실패
  2. pm_gd970201에서 source_refs 샘플: ["I:internal:if_rsv_sec_obsvdata:41072"]
     → "if_rsv_sec_obsvdata" 포함 → 매칭 성공
```

### 3. 218행 fallback 제거

```java
// 기존: sourcePks == null → 전체 데이터 표시
// 수정: sourcePks == null → 빈 결과 + "필터링 불가" 메시지
```

### 4. source_refs N:M 관계 대응

source_refs는 JSON 배열이므로 1:1, 1:N, N:1 모두 커버 가능해야 함.

| 관계 | 예시 | 처리 |
|------|------|------|
| 1:1 | source 1건 → target 1건 | 기본 |
| 1:N | source 1건 → target 3건 (EAV) | 같은 PK 중복 → DISTINCT |
| N:1 | source 2건 → target 1건 | source_refs 배열에 복수 원소 `["...:401","...:402"]` → 전부 파싱 |

- `parseSourceRefsPks()`는 이미 JSON 배열 전체를 파싱하므로 N:1 커버됨
- target에서 추출한 PK 목록은 **DISTINCT** 처리 후 source IN 절에 사용

## 영향 범위
- source 데이터 조회 API(`/{executionId}/source`)만 변경
- forward trace, reverse trace, target 데이터 조회 등은 변경 없음
- 모든 Agent(RCV, Loader, SND)에서 동일 로직으로 통합
