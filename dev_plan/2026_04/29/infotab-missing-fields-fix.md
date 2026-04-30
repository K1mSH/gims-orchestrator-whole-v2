# API Collector 상세 페이지 InfoTab — 누락 필드 추가

> **작성일**: 2026-04-29
> **목적**: 상세 페이지에서 사후 수정 불가했던 4 필드를 InfoTab 에 추가
> **트리거**: 약수터 작업 중 UPSERT 사후 수정 불가 발견 ("upsert 설정 유무나 뭐 그런것도 확인이 안되는거 같으니까")

## 현황 — 4 필드 사후 수정 불가

| 필드 | DB | backend DTO | frontend type | InfoTab UI |
|------|:--:|:----------:|:-------------:|:----------:|
| `upsertEnabled` | ✅ | ✅ Update 받음 | ✅ | ❌ |
| `dataRootPath` | ✅ | ✅ | ✅ | ❌ |
| `targetTableName` | ✅ | ✅ | ✅ | ❌ |
| `isActive` | ✅ | ✅ | ✅ | ❌ (form state 만, 위젯 없음) |

backend / DTO / type 모두 받을 준비 됨 — **frontend UI 만 추가**.

## 변경 대상

`sync-orchestrator/frontend/components/api-collect/InfoTab.tsx`

## 추가 내용 — "적재 설정" 섹션 신설 (기존 "요청" 섹션 다음)

```
┌─ 적재 설정 ──────────────────────────────────────────────┐
│  Target Datasource: [select]  Target Table: [select]    │
│  dataRootPath:      [input]                             │
│  ☐ UPSERT (중복 시 갱신)        ☐ 활성 (실행 가능)       │
└─────────────────────────────────────────────────────────┘
```

## 작업 범위

1. form state 에 `upsertEnabled` / `dataRootPath` / `targetTableName` / `isActive` 추가 (initial = endpoint.* 값)
2. Target Table 변경 시 datasourceApi.searchTables 호출해서 옵션 채우기 (이미 datasource select 있음 — 동일 패턴)
3. UI 위젯 추가 (위 mockup)
4. handleSaveInfo 에 4 필드 전송 — backend update 요청에 포함

## 영향 범위

| 파일 | 변경 |
|------|------|
| `InfoTab.tsx` | state 4개 + UI 위젯 + handleSaveInfo 4 필드 전송 |
| 다른 파일 | 영향 없음 (backend / DTO / type 이미 수용 상태) |

## 검증

- `/api-collect/26` (약수터-제원) 진입 → 적재 설정 섹션 표시
- UPSERT 토글 / dataRootPath / Target Table / 활성 모두 보여지고 수정/저장 동작
- 약수터 본 작업과 자연스럽게 합류 (UPSERT 다시 켜기, isConflictKey 매핑 탭에서 처리)
