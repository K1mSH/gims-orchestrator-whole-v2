---
id: TASK-001
title: Datasource 삭제 시 Agent 참조 무결성 검증 로직 추가
status: OPEN
created: 2026-04-23
parts: [P10-orchestrator-backend]
parallel_safe: true
depends_on: []
blocks: []
assignee: forward
---

## 목적

`DatasourceService.deleteDatasource()` 에 TODO 로 남아있는 "Agent 에서 참조 중인지 확인" 로직 구현.

실배포 환경에서 **참조 중인 datasource 가 삭제되면 Agent 기동/실행 실패** → 운영 장애로 직결.
운영자 UI 에서 잘못된 클릭 한 번으로 파이프라인 전체가 멈출 수 있음.

## 수정 대상 파일
- [ ] `infolink-orchestrator-backend/src/main/java/com/sync/orchestrator/service/DatasourceService.java` (line 226 부근)

## 변경 내용
- [ ] 삭제 대상 `datasourceId` 를 Agent 엔티티의 `sourceDatasourceId` / `targetDatasourceId` 컬럼에서 참조 중인지 쿼리
- [ ] 참조 중이면 삭제 거부 (409 Conflict 등) + 참조 중인 Agent 목록 응답에 포함
- [ ] (선택) 프론트 삭제 확인 다이얼로그에 참조 정보 노출

## 완료 조건 (Definition of Done)
- [ ] 빌드 통과 (`./gradlew clean build -x test`)
- [ ] 참조 중인 datasource 삭제 시도 → 적절한 HTTP 에러(409 등) + 참조 Agent 목록 반환
- [ ] 참조 없는 datasource 삭제는 기존대로 정상 동작
- [ ] 회귀: 기존 datasource CRUD (GET / POST / PUT) 동작 정상
- [ ] TODO 주석 제거

## 금지 사항 (scope creep 방지)
- datasource 엔티티 / Agent 엔티티 자체 스키마 변경 금지
- 본 참조 검증 외 리팩토링 / UI 대규모 개편 금지
- 참조 종류 확장(예: ApiEndpoint 등 다른 곳 참조 탐지) 은 별도 TASK 로 분리

## 배경 / 발견 경위
- `verify/runs/2026-04-23.md` 의 공통 규약 스캔에서 `DatasourceService.java:226 // TODO: Agent에서 참조 중인지 확인` 발견
- invariant 위반은 아니지만 **운영 안정성(invariant 11C 장애 격리/복원력)** 관점에서 중요 — 실배포 전 해소 권장

## 관련 문서
- `verify/_invariants/00-overview.md` § 11C (장애 격리 / 복원력 — 운영자 조작 오류 방지)
- `verify/runs/2026-04-23.md` 후속 작업
