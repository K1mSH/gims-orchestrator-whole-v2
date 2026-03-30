# API Collector 실행 카운트 리팩토링 + UPSERT 기본화

## 목적
1. UPSERT 시 신규(INSERT)와 갱신(UPDATE)을 구분하여 카운트
2. UPSERT 토글 제거 → conflict key가 있으면 자동 UPSERT, 없으면 단순 INSERT

## 변경 사항

### 카운트 변경
- 현재: `insertCount`(성공) / `skipCount`(실패)
- 변경: `insertCount`(신규) / `updateCount`(갱신) / `skipCount`(실패)

### UPSERT 기본화
- `upsertEnabled` 필드/토글 제거
- conflict key 설정 여부로 자동 판단:
  - conflict key 있음 → ON CONFLICT DO UPDATE (UPSERT)
  - conflict key 없음 → 단순 INSERT

### 카운트 구현 방식
- 적재 전 기존 PK Set 조회: `SELECT pk_cols FROM target_table`
- 각 행의 PK가 Set에 있으면 updateCount++, 없으면 insertCount++
- conflict key 없을 때(단순 INSERT)는 PK 조회 스킵, 전부 insertCount
- DB 비의존적 (PG/Oracle 모두 동작)

## 수정 대상 파일

### 백엔드
| 파일 | 작업 |
|------|------|
| `ApiExecutionHistory.java` | `updateCount` 컬럼 추가 |
| `ApiExecutionService.java` | PK Set 조회 + insert/update 분기 + `upsertEnabled` 참조 제거 → conflict key 기반 |
| `ApiExecutionHistoryDto.java` | `updateCount` 필드 추가 |
| `CustomExecutionResult.java` | `updateCount` 필드 추가 |
| `AnyangUsageExecutor.java` | updateCount 반환 추가 |
| `ApiEndpoint.java` | `upsertEnabled` 필드 제거 |
| `ApiEndpointDto.java` | `upsertEnabled` 필드 제거 |

### 프론트엔드
| 파일 | 작업 |
|------|------|
| `types/api-collect.ts` | `updateCount` 추가, `upsertEnabled` 제거 |
| `HistoryTab.tsx` | "갱신" 컬럼 추가 |
| `MappingTab.tsx` | UPSERT 체크박스 제거, `localUpsert`/`baseUpdateFields` 정리 |
| `InfoTab.tsx` | `upsertEnabled` 참조 제거 |
| `app/api-collect/[id]/page.tsx` | 실행 완료 alert에 갱신 건수 추가 |

## 영향 범위
- api_execution_history 테이블: updateCount 컬럼 추가 (Hibernate ddl-auto)
- api_endpoint 테이블: upsert_enabled 컬럼 미사용 (삭제는 나중에)
- 기존 이력 데이터: updateCount = null (프론트에서 `-` 표시)
