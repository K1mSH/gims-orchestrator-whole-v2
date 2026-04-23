---
id: VER-004
title: bojo-int target 엔티티 executionId 인덱스 누락 (전무)
status: OPEN
created: 2026-04-23
parts: [P6-bojo-int]
parallel_safe: true
assignee: forward
related: [VER-005]
---

## 증상

`sync-agent-bojo-int/**/entity/target/*.java` 전체에 `@Index(columnList = "execution_id")` 미적용 (**0 건**).

MEMORY `Source 추적 설계 (3/10 확정)` 의 "executionId 인덱스: IF/Target 6개 엔티티에 추가 완료" 기록은 **bojo 에만 한정**. bojo-int 로 확장되지 않음.

## 증거

```
grep "@Index|indexes" sync-agent-bojo-int/**/entity/target/*.java
→ 0 건
```

vs bojo (bojo/entity/target) / common (Execution, SyncLog) / others (IfSnd*) 는 모두 `@Index(columnList = "execution_id")` 적용됨 (대조).

대상 엔티티 (20+):
- TmGd970001, TmGd970002, TmGd970101, TmGd970130, TmGd980002
- TmGd010310, TdGd010310, TmGd014000, TmGd014001
- PmGd111021, PmGd111022, PmGd970201, PmGd970202
- TmGd111010, TmGd111024, TmGd111025, TmGd120001

## 영향

- `/target` 엔드포인트 조회 성능 저하 (execution_id WHERE 절 full scan)
- `/trace-source` 분기 2 / 3 의 source_refs 역조회 속도 저하
- 배포 후 데이터 누적 시 가속도로 악화

## 수정 범위

각 bojo-int target 엔티티에 bojo 선례대로 패턴 적용:
```java
@Table(name = "TM_GD970001",
       indexes = @Index(name = "idx_tm_gd970001_exec_id", columnList = "execution_id"))
```

## 회귀 확인

- 빌드 통과 (`./gradlew -p sync-agent-bojo-int clean build -x test`)
- ddl-auto=`update` 로 인덱스 자동 생성 확인 또는 DDL 스크립트에도 반영
- 실행 후 `/target` / `/trace-source` 응답 시간 측정 (선택)

## 관련 문서
- `verify/_invariants/00-overview.md § 4` (executionId 인덱스 규약)
- VER-005 (provide 엔티티 동일 누락)
- MEMORY: "Source 추적 설계 (3/10 확정)"
