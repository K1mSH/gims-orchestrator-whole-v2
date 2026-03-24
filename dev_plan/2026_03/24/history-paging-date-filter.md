# 실행 이력 페이징 개선 + 날짜 검색

## 목적
- 실행 이력 페이징 UI 개선 (이전/다음 → 페이지 번호 표시)
- 날짜 범위 검색 필터 추가

## 수정 대상

### 백엔드 (infolink-api-collector)

| 파일 | 변경 내용 |
|------|----------|
| `repository/ApiExecutionHistoryRepository.java` | 날짜 범위 조건 쿼리 메서드 추가 |
| `controller/ApiHistoryController.java` | `startDate`, `endDate` 쿼리 파라미터 추가, 분기 처리 |

### 프론트엔드 (sync-orchestrator/frontend)

| 파일 | 변경 내용 |
|------|----------|
| `lib/collectorApi.ts` | historyApi에 startDate/endDate 파라미터 추가 |
| `components/api-collect/HistoryTab.tsx` | 날짜 검색 UI + 페이지 번호 UI |

## 변경 상세

### 백엔드 — Repository
```java
// 날짜 범위 조회
Page<ApiExecutionHistory> findByApiEndpointIdAndStartedAtBetweenOrderByStartedAtDesc(
    Long endpointId, LocalDateTime from, LocalDateTime to, Pageable pageable);
```

### 백엔드 — Controller
```java
@GetMapping
public Page<Response> getHistory(
    @PathVariable Long endpointId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(required = false) String startDate,  // yyyy-MM-dd
    @RequestParam(required = false) String endDate) {   // yyyy-MM-dd
    // startDate/endDate 있으면 범위 쿼리, 없으면 기존 전체 쿼리
}
```

### 프론트 — HistoryTab
- 헤더 영역에 시작일/종료일 date input 2개 + 검색 버튼
- 페이징: 페이지 번호 버튼 (최대 5개 표시, 현재 페이지 하이라이트)
- 전체 건수 표시

## 영향 범위
- api_collector DB: 변경 없음 (기존 started_at 컬럼 활용)
- 다른 모듈: 영향 없음
