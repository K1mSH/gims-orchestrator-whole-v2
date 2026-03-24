# API키 참조 판별 방식 리팩토링

## 문제
- 현재 API키 참조 판별: `param.description.startsWith("🔑")` (이모지 기반)
- 인라인 테스트(등록 단계)에서 `description`이 전달 안 됨 → API키 resolve 안 됨 → 401
- 이모지 기반 분기는 불안정하고 의도가 불명확

## 해결 방안
`ApiParam`에 `isApiKeyRef` boolean 컬럼 추가, 이걸로 분기

## 수정 대상

### 백엔드 (infolink-api-collector)

| 파일 | 변경 내용 |
|------|----------|
| `entity/ApiParam.java` | `isApiKeyRef` boolean 필드 추가 (default false) |
| `service/ApiCallService.java` | 분기 조건 `🔑` → `param.isApiKeyRef()` 로 변경 |
| `dto/TestCallDto.java` | `InlineParam`에 `isApiKeyRef` 필드 추가 |
| `service/ApiTestService.java` | `testCallInline()`에서 `.isApiKeyRef()` 세팅 |
| `service/ApiEndpointService.java` | param 저장 시 `isApiKeyRef` 반영 |
| `dto/ApiEndpointDto.java` | CreateRequest의 param DTO에 `isApiKeyRef` 추가 |

### 프론트엔드 (sync-orchestrator/frontend)

| 파일 | 변경 내용 |
|------|----------|
| `types/api-collect.ts` | `ApiParam` 타입에 `isApiKeyRef` 추가 |
| `lib/collectorApi.ts` | `InlineTestRequest.params`에 `isApiKeyRef` 추가 |
| `app/api-collect/page.tsx` | API키 선택 분기: `description.startsWith('🔑')` → `isApiKeyRef` 로 변경, 인라인 테스트 시 `isApiKeyRef` 전달 |
| `components/api-collect/InfoTab.tsx` | 동일하게 `isApiKeyRef` 기반 분기로 변경 |

## 변경 로직 상세

### ApiCallService 분기 (Before → After)
```java
// Before
if (param.getDescription() != null && param.getDescription().startsWith("🔑")) {
    value = resolveApiKey(param.getStaticValue());
}

// After
if (Boolean.TRUE.equals(param.getIsApiKeyRef())) {
    value = resolveApiKey(param.getStaticValue());
}
```

### 프론트 분기 (Before → After)
```tsx
// Before
p.description?.startsWith('🔑') ? 'APIKEY' : 'STATIC'

// After
p.isApiKeyRef ? 'APIKEY' : p.valueType
```

### 인라인 테스트 요청 (page.tsx)
```tsx
// Before — isApiKeyRef 전달 안 됨
params: params.map(p => ({
  paramName: p.paramName,
  paramType: p.paramType,
  valueType: p.valueType,
  staticValue: p.staticValue,
  ...
}))

// After — isApiKeyRef 포함
params: params.map(p => ({
  paramName: p.paramName,
  paramType: p.paramType,
  valueType: p.valueType,
  staticValue: p.staticValue,
  isApiKeyRef: p.isApiKeyRef || false,
  ...
}))
```

## description 필드 처리
- `description`에서 `🔑` 마커 역할 제거
- description은 순수 설명용으로만 사용 (API키 서비스명 등 자유 입력)
- 기존 `🔑 서비스명` 저장 패턴은 유지해도 무방 (표시용), 분기에는 사용 안 함

## 영향 범위
- DB: `api_param` 테이블에 `is_api_key_ref` boolean 컬럼 추가 (DDL 자동, default false)
- 기존 데이터: 🔑 description이 있는 기존 row는 마이그레이션 필요 없음 (JPA auto-ddl로 컬럼 추가, 기존 row는 false → 사용자가 다시 설정하거나, 한번 UPDATE 쿼리로 일괄 처리)
- 다른 모듈 영향: 없음 (api-collector 내부 + 프론트만)
