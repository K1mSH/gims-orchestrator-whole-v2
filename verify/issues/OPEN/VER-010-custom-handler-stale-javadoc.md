---
id: VER-010
title: CustomOperationHandler Javadoc 의 CustomHandlerBootstrap 참조 (실재 X)
status: OPEN
created: 2026-04-28
parts: [P9-api-provider]
parallel_safe: true
assignee: any
related: []
---

## 증상 요약

`infolink-api-provider/src/main/java/com/gims/provider/custom/CustomOperationHandler.java:14` Javadoc 이 `CustomHandlerBootstrap` 클래스를 참조하지만 **실재하지 않음**.

설계 초기안(부팅 자동 등록)에서 카탈로그 패턴(`CustomHandlerCatalogService` — 운영자 의도 기반 등록)으로 전환되며 주석이 잔존한 것으로 추정.

## 재현 절차

1. `CustomOperationHandler.java:14` 의 `{@link CustomHandlerBootstrap}` Javadoc 라인 확인
2. `grep CustomHandlerBootstrap` → 1건 매치(자기 자신만)
3. `CustomHandlerCatalogService.java:21` 주석에 "부팅 자동 등록은 X (운영자 의도 반영)" 명시 — 정책 전환 흔적

## 기대 vs 실제

- 기대: Javadoc 이 실재 클래스만 참조하거나, 카탈로그 등록 흐름으로 갱신됨
- 실제:
  ```java
  // CustomOperationHandler.java:13-14
  * - operationId 1개 = 핸들러 1개 (1:1)
  * - 부팅 시 {@link CustomHandlerBootstrap} 가 metadata 를 ApiPrvOperation 으로 변환·등록
  ```

## 영향

- 코드 동작 정상 (Javadoc 만 영향)
- 신규 핸들러 추가하는 개발자가 존재하지 않는 부트스트랩 클래스를 찾으며 혼란
- IDE 의 Javadoc 링크 깨짐

## 수정 범위 제안

`CustomOperationHandler.java:7-17` Javadoc 갱신:
- `CustomHandlerBootstrap` 참조 → `CustomHandlerCatalogService` 로 교체
- 흐름을 "부팅 자동 등록" → "운영자 등록(카탈로그→preview→register)" 으로 정정
- "is_locked=true 로 운영자 수정/삭제 차단" 표현은 유지 (실제 동작과 일치)

## 회귀 확인

- IDE 에서 `{@link ...}` 깨진 링크 0건
- `grep CustomHandlerBootstrap` 0건

## 관련 문서

- 2026-04-28 검증 실행 기록 (`verify/runs/2026-04-28.md`)
