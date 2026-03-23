# API Collector — API 키 바인딩 (GIMS 본체 연동)

## 배경

외부 API 호출 시 인증 키가 필요한데, 키를 GIMS 본체에서 별도 관리하고 있음.
- 네이버: `X-Naver-Client-Id` + `X-Naver-Client-Secret` (HEADER)
- 공공데이터포털: `serviceKey` (QUERY 파라미터)
- 기타: 각 API마다 키 이름/위치가 다름

현재는 헤더 JSON 직접 입력 + 파라미터 수동 입력으로 분산 관리 → 키 관리 불편, API키 서비스명 추적 불가.

## 설계

### 핵심 방향

1. **데이터 통합**: 헤더/파라미터 구분 없이 `ApiParam` 하나로 관리. `paramType=HEADER`이면 헤더로 주입, `paramType=QUERY`이면 쿼리로 주입.
2. **기존 헤더 JSON 입력 제거**: 별도 헤더 섹션 없이, 파라미터에서 `paramType=HEADER`로 등록.
3. **API키 연동**: 값 유형을 `직접입력 / 🔑 API키 / 동적` 3가지로 구분. API키 선택 시 GIMS 본체에서 키 조회 → 서비스명 표시, 실제 값은 `staticValue`에 저장.
4. **서비스명 보존**: `description` 필드에 `🔑 서비스명` 저장 → 저장 후 다시 열어도 API키 출처 식별 가능.

### 데이터 구조 (기존 ApiParam 그대로)

```
- paramName: "X-Naver-Client-Id"
- paramType: HEADER              ← 헤더로 주입
- valueType: STATIC
- staticValue: "6ZMOvG6W..."     ← 실제 키 값 (API키 선택 시 자동 채움)
- description: "🔑 네이버 검색 Client-ID"  ← 서비스명 표시용
```

### UI 표시: paramType으로 섹션 분리

데이터는 같은 `params` 배열이지만, UI에서는 `paramType`으로 나눠서 표시:

```
헤더 (paramType=HEADER인 것만 필터)
┌──────────────────────────────────────────────────────────────┐
│ 이름                     값 유형        값/서비스            │
│ [X-Naver-Client-Id    ] [🔑 API키 ▼]  [네이버 검색 Client-ID ▼] │
│ [X-Naver-Client-Secret] [🔑 API키 ▼]  [네이버 검색 Client-Secret ▼] │
│                                              [+ 헤더 추가]  │
└──────────────────────────────────────────────────────────────┘

호출 파라미터 (QUERY/BODY/PATH인 것만 필터)
┌──────────────────────────────────────────────────────────────┐
│ 이름       위치        값 유형      값                       │
│ [query   ] [QUERY ▼]  [직접입력 ▼] [지하수              ]   │
│ [display ] [QUERY ▼]  [직접입력 ▼] [100                 ]   │
│ [start   ] [QUERY ▼]  [직접입력 ▼] [1                   ]   │
│                                              [+ 파라미터 추가] │
└──────────────────────────────────────────────────────────────┘
```

- 헤더 섹션: `paramType` 고정(HEADER), 위치 컬럼 불필요
- 파라미터 섹션: QUERY/BODY/PATH 선택 가능
- 둘 다 같은 `params` 배열에 저장, 저장/조회 로직 변경 없음
- 🔑 API키 선택 시 드롭다운에 서비스명 + 만료일 표시

### 기존 헤더 JSON 마이그레이션

기존 `endpoint.headers` JSON에 저장된 값 → `ApiParam(paramType=HEADER)`로 이전 필요.
- 수동 이전 (기존 데이터 소량)
- 또는 최초 로드 시 자동 변환

### GIMS 본체 API 스펙
```
GET /api/???/api-keys
→ 응답:
{
  "status": "success",
  "data": {
    "apis": {
      "content": [
        {
          "id": 1,
          "serviceName": "브이월드",
          "apiKey": "532E2418-...",
          "useAt": "Y",
          "expiryDate": "2026-04-22",
          "expiryType": "정상",
          "dday": 30
        }
      ]
    }
  }
}
```

### application.yml 설정

```yaml
lookup:
  common-code-url: http://localhost:8084/mock/common/select/{groupCode}
  api-key-url: http://localhost:8084/mock/api-keys
```

---

## 현재 진행 상태 (Step 1~2 완료)

### 완료
- MockApiController에 API 키 목록 API 추가
- ApiEndpointController에 키 목록 조회 프록시 엔드포인트
- application.yml에 api-key-url 설정
- collectorApi.ts에 apiKeyApi 조회 함수
- 등록 폼(page.tsx) 파라미터: 값 유형 `직접입력/🔑 API키/동적` 3분기 + API키 드롭다운
- 상세 수정(InfoTab.tsx) 파라미터: 동일
- 헤더 섹션: 🔑 API키 선택 시 서비스명 배지 표시
- InfoTab 기본정보 저장 시 dataRootPath/targetTableName 유지 버그 수정

### 남은 작업
- **UI 통합**: 헤더 JSON 입력 → 파라미터(paramType=HEADER) 통합, 섹션 분리 표시
- **기존 데이터 마이그레이션**: endpoint.headers JSON → ApiParam(HEADER) 변환
- **ApiCallService**: 기존 headers JSON 읽는 로직 → ApiParam(HEADER) 기반으로 변경
- **테스트**: API키 선택 → 등록 → 실행 → 인증 성공 확인

---

## 수정 대상 파일

| 파일 | 작업 | 상태 |
|------|------|------|
| `controller/MockApiController.java` | Mock API 키 목록 추가 | ✅ |
| `controller/ApiEndpointController.java` | API 키 목록 조회 프록시 | ✅ |
| `application.yml` | api-key-url 설정 | ✅ |
| `lib/collectorApi.ts` | apiKeyApi 조회 함수 | ✅ |
| 프론트 `page.tsx` | 파라미터 API키 선택 UI | ✅ (헤더 통합 미완) |
| 프론트 `InfoTab.tsx` | 상세 수정 API키 선택 UI | ✅ (헤더 통합 미완) |
| 프론트 `page.tsx` | 헤더 섹션 → 파라미터 HEADER 통합 | 미완 |
| 프론트 `InfoTab.tsx` | 헤더 섹션 → 파라미터 HEADER 통합 | 미완 |
| `service/ApiCallService.java` | headers JSON → ApiParam(HEADER) 전환 | 미완 |

---

## 영향 범위
- **infolink-api-collector 모듈만**
- DB 변경 없음 (기존 ApiParam 활용)
- 기존 수동 입력 방식 그대로 유지
