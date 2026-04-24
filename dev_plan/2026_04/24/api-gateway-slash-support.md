# ApiGateway — 슬래시 포함 operationId 지원

> 작성일: 2026-04-24
> 범위: `gims-api-provider` 의 `ApiGatewayController` 경로 매핑 수정
> 목적: 레거시 URL (`/megokrApi/ngw08`, `/drought119Api/selectDrought119`, `/api/data/{service}/{operation}`) 을 **그대로 재현** 가능하도록 `operationId` 에 슬래시 허용

## 1. 현재 제약

```java
@RequestMapping("/api/provide")
@GetMapping("/{operationId}")
public ResponseEntity<?> provide(@PathVariable String operationId, ...)
```

- Spring `@PathVariable` 의 `{operationId}` 는 **단일 세그먼트** 만 캡처 → 슬래시 포함 시 Spring 이 핸들러 매칭 실패 → 404
- 예: `/api/provide/megokrApi/ngw08` 요청 시 `operationId=megokrApi` 로 매칭 시도하고 뒤의 `/ngw08` 이 매핑 없음 → 404

## 2. 변경 내용

### 2.1 경로 매핑

```java
@GetMapping("/**")                            // 모든 하위 경로 허용
public ResponseEntity<?> provide(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(value = "apiKey", required = false) String apiKey,
        HttpServletRequest request) {
    String operationId = extractOperationId(request);
    if (operationId == null || operationId.isEmpty()) {
        return ResponseEntity.status(404).body(Map.of("error", "operationId 가 필요합니다"));
    }
    // 이후 로직은 기존과 동일 (operation 조회 → isPublished → API Key → 파라미터 추출 → 실행)
    ...
}

private String extractOperationId(HttpServletRequest request) {
    String uri = request.getRequestURI();                    // "/api/provide/megokrApi/ngw08"
    String prefix = "/api/provide/";
    if (!uri.startsWith(prefix) || uri.length() <= prefix.length()) return null;
    String raw = uri.substring(prefix.length());
    return URLDecoder.decode(raw, StandardCharsets.UTF_8);   // 한글/인코딩 대비
}
```

### 2.2 기존 로직 유지

- operation 조회 (`operationRepository.findByOperationId`)
- `isPublished=true` 체크 (false → 404)
- API Key 검증
- `pageSize` 계산, requestParams 추출, 실행
- 응답 처리

변경은 **진입부의 `operationId` 획득 방식** 만.

## 3. 하위호환

- 기존 하이픈 operationId (`megokrapi-ngw08`) → 여전히 단일 세그먼트 → 그대로 작동 (`extractOperationId` 가 동일하게 추출)
- 신규 슬래시 operationId (`megokrApi/ngw08`) → 이번 수정으로 추출 가능
- 즉 **기존 등록된 operation 재등록 불필요**

## 4. 엣지 케이스

| 입력 | 결과 |
|---|---|
| `/api/provide/` | 404 (`operationId 가 필요합니다`) |
| `/api/provide/abc` | `operationId = "abc"` |
| `/api/provide/a/b/c` | `operationId = "a/b/c"` |
| `/api/provide/megokrApi/ngw08?x=1` | `operationId = "megokrApi/ngw08"` (쿼리스트링 제외 — `getRequestURI()` 가 이미 제거) |
| `/api/provide/한글경로` | `operationId = "한글경로"` (URLDecoder 처리) |

## 5. 리스크

- **경로 충돌**: `/api/provide/**` 가 `MockApiKeyController` 같은 다른 컨트롤러의 `/api/provide/*` 하위 경로와 겹치면 안 됨. 현재 `MockApiKeyController` 는 `/api/provide` prefix 없음 확인 필요 → 검증 후 진행
- **Spring Security 등 필터 체인**: `/api/provide/**` 에 인증/권한 필터 있으면 확인. 현재 API Key 검증은 컨트롤러 내부에서 처리하므로 별 영향 없을 것

## 6. 테스트 시나리오

1. 기존 하이픈 등록된 operation (있다면) → `/api/provide/megokrapi-ngw08` 호출 → 정상 (하위호환)
2. 신규 슬래시 operation (사용자 등록: `megokrApi/ngw08`) → `/api/provide/megokrApi/ngw08?ctpv_nm=대전광역시` 호출 → 정상 (이번 수정 목적)
3. `/api/provide/` (operationId 없음) → 404 + `operationId 가 필요합니다`
4. 미등록 operationId → 404 + `오퍼레이션을 찾을 수 없습니다`

## 7. 배포

1. `ApiGatewayController.java` 수정
2. 기존 실행 중인 `gims-api-provider` 프로세스 종료
3. `./gradlew bootRun` 재기동
4. 위 §6 테스트 4건 검증

## 8. 문서 반영

- `PROVIDE_OPERATION_SPEC.md` §2 공통 설정 또는 §8 Postman 팁에 "operationId 에 슬래시 포함 가능" 문구 추가

## 9. 일정

작업 분량 약 10분. 즉시 진행.

---

## 개정 이력

- 2026-04-24: 최초 작성. 레거시 URL 그대로 재현 위한 엔진 확장 계획.
