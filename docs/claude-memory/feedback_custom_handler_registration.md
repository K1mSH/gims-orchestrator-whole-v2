---
name: Custom Handler 등록 = custom-handlers/register endpoint (직접 operations POST 금지)
description: Type B 커스텀 핸들러는 메타 자동 sync endpoint 로 등록해야 columns/params/operationType 정합
type: feedback
originSessionId: 6a53f35d-a928-4fc2-a0fa-6199aa746193
---
Type B 커스텀 핸들러 (api-provider 의 `CustomOperationHandler`) 새로 등록 시 반드시 **`POST /api/manage/custom-handlers/register`** 사용.

```
POST /api/manage/custom-handlers/register
{ "operationId": "<핸들러 metadata 의 operationId>",
  "customOperationId": null,    // 옵션 — 운영자가 변경한 ID
  "customOperationName": null }  // 옵션
```

**Why**: `CustomHandlerCatalogService.register()` 가 핸들러의 `getMetadata()` 를 보고 `ApiPrvOperation` 생성:
- operationType = `CUSTOM`
- isLocked = `true`
- columns/params 자동 sync (메타에 박힌 13개 컬럼 + 2 파라미터 등)
- handlerKey = metadata.operationId

**❌ 직접 `POST /api/manage/operations` 로 등록하면**:
- operationType default = `META`
- columns/params 0개
- 호출 시 `dynamicQueryService` 가 호출되어 잘못된 SQL 실행 (`SELECT * FROM <table> LIMIT 20` 같은) → ORA-00933 또는 syntax error
- Frontend 화면에서 컬럼/파라미터 메타 표시 안 됨

**How to apply**: 새 Type B 핸들러 만든 후 등록할 때 register endpoint 만 사용. operations CRUD endpoint 는 운영자 화면 수정용 (operationName 변경 등) 으로만.
