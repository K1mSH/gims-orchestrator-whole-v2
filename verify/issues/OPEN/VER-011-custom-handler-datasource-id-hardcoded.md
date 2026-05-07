---
id: VER-011
title: Type B 핸들러 datasource ID "internal" 7곳 하드코딩 (운영자 변경 인터페이스 미제공)
status: OPEN
created: 2026-04-28
parts: [P9-api-provider]
parallel_safe: true
assignee: forward
related: []
---

## 증상 요약

Type B 커스텀 핸들러 7개 클래스가 각자 `private static final String DATASOURCE_ID = "internal"` 를 들고 있고, `CustomOperationMetadata.java:32` 의 default 값에도 `"internal"` 박혀있음. 합계 **8지점 동일 상수 산재**.

`CustomHandlerCatalogService.register()` 인터페이스는 운영자가 `customOperationId` / `customOperationName` 만 변경 허용 — **datasourceId 는 운영자가 코드 수정 없이 변경 불가**.

## 재현 절차

```bash
grep -rn "DATASOURCE_ID = \"internal\"" infolink-api-provider/src/main/java/com/gims/provider/custom/
# → 7 핸들러 + CustomOperationMetadata 의 default 1
```

## 기대 vs 실제

### 기대 (invariant 3 정신 — Orchestrator DB 등록이 단일 진실원)
- 핸들러 metadata 의 `datasourceId` 는 default 만 코드에 두고, **운영자가 register 시 다른 datasource 를 선택 가능**
- 또는 모듈 단일 상수(예: `CustomDataSourceIds.GIMS_INTERNAL_ORACLE`)로 모이고 운영자 변경 인터페이스 보강

### 실제
- 핸들러 7개 각자 자기 상수 박음 → 등록명 변경 시 7곳 동시 수정 필요
- register 시 운영자 변경 불가
- 새 핸들러 추가될 때마다 누적 (현재 10종 미구현 — 17곳까지 늘어날 예정)

## 증거

```
infolink-api-provider/.../custom/handler/ActualUseDetailDjHandler.java:31:    private static final String DATASOURCE_ID = "internal";
infolink-api-provider/.../custom/handler/ActualUseDetailKbHandler.java:28:    private static final String DATASOURCE_ID = "internal";
infolink-api-provider/.../custom/handler/GroundwaterQualityHandler.java:40:    private static final String DATASOURCE_ID = "internal";
infolink-api-provider/.../custom/handler/InspectionDistinctHandler.java:37:    private static final String DATASOURCE_ID = "internal";
infolink-api-provider/.../custom/handler/InspectionListHandler.java:38:    private static final String DATASOURCE_ID = "internal";
infolink-api-provider/.../custom/handler/SupplementaryGroundwaterHandler.java:47:    private static final String DATASOURCE_ID = "internal";
infolink-api-provider/.../custom/handler/UnregitsFclySmrizeHandler.java:32:    private static final String DATASOURCE_ID = "internal";
infolink-api-provider/.../custom/CustomOperationMetadata.java:32:    private String datasourceId = "internal";
```

## 영향

- **invariant 3 직접 위반은 아님** (yml 이 아닌 Java 상수). 그러나 정신 위반 — Orchestrator 등록명을 코드 다수 지점이 참조
- 실배포 환경이 dev 와 datasource 등록명을 다르게 쓸 경우 17개 핸들러 동시 수정 (B7~B18 까지 완성 시)
- "운영자가 datasource 만 바꿔서 다른 Oracle 가리키게" 하는 것 불가능 — 코드 수정 필수

## 수정 범위 제안

옵션 A — **단일 상수 + register 시 운영자 변경 허용**
- 모듈 단일 클래스(예: `CustomHandlerDefaults`)에 `DEFAULT_DATASOURCE_ID = "internal"` 1지점만 보존
- `CustomHandlerCatalogService.register()` 에 `customDatasourceId` 파라미터 추가, 미지정 시 metadata default 사용

옵션 B — **추상 클래스로 공통 상수 + protected getter**
- `AbstractInternalOracleHandler` 같은 베이스 클래스에 `DATASOURCE_ID` 1곳만 두고 7 핸들러 상속
- 운영자 변경은 미보강 (현행 유지) — 단순화 우선

옵션 C — **현행 유지 + invariant 3 명시적 예외**
- "Type B 핸들러는 GIMS Oracle 직결 전용으로 datasourceId 코드 박음을 허용" 을 invariant 에 명시
- 새 핸들러 추가 시 컨벤션 준수만 강제

## 회귀 확인

- 기존 7 핸들러 호출 동작 동일 (외부 API 응답 무변화)
- 빌드 통과 + Bootstrap 의존 없음 (운영자 register 흐름 유지)

## 관련 문서

- `verify/_invariants/00-overview.md` § 3 (yml 하드코딩 금지 — 정신상 확장)
- MEMORY: `feedback_config_vs_registration` ("Orchestrator DB 등록이 단일 진실원")
- `dev_plan/2026_04/27/type-b-builtin-handler.md` (1:1 원칙 — SQL 중복 허용 컨벤션과 충돌 검토 필요)
- 2026-04-28 검증 실행 기록 (`verify/runs/2026-04-28.md`)
