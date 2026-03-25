# sync-orchestrator/frontend (프론트엔드)

## 프록시 아키텍처 반영
- [x] AgentType DB_CON_PROXY 타입/레이블/색상
- [x] agents/[id] 프록시 제외 (실행/테스트 숨김)
- [x] executions/page 타입 레이블
- [x] 대시보드 타입 레이블

## 테이블 alias 표시
- [x] datasources/page remarks → alias
- [x] InfoTab alias 4곳
- [x] agents/page alias 2곳
- [x] executions/[id]/page alias 2곳 + alias-map API

## SyncLog 매핑 단위 UI
- [x] types/index TableStats 재설계
- [x] executions/[id]/page 매핑별 처리현황
- [x] flatTableStats 변환 로직

## 동적 WHERE 조건
- [x] types/index ExecutionCondition + CONDITION_OPERATORS
- [x] agents/[id]/page WHERE 조건 UI (컬럼/연산자/값)
- [x] lib/api trigger() conditions 파라미터
- [x] agents/[id]/page condTableSelections (tableName 바인딩)

## execution-modes 제거
- [x] types/index ExecutionModeResponse 제거
- [x] lib/api getExecutionModes/refreshExecutionModes 제거
- [x] agents/[id]/page 실행옵션 UI 제거

## Retention
- [x] InfoTab retentionDays input min=1 검증

## 미완료
- [ ] Orchestrator 프론트 테이블 comment 표시 일부 미적용 (API Collector 쪽만 완료)

**진행도: 18/19 = 95%**
