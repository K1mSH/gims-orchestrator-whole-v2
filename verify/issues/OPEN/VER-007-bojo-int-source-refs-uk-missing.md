---
id: VER-007
title: bojo-int 엔티티 source_refs UK 누락
status: OPEN
created: 2026-04-23
parts: [P6-bojo-int]
parallel_safe: true
assignee: forward
related: [VER-004, VER-006]
---

## 증상

bojo-int 의 target / iftable 엔티티에 `source_refs` 컬럼은 존재(92 파일 중 일부 포함)하지만 **`@UniqueConstraint(columnNames = {"source_refs"})` 는 전무**.

다른 모든 Agent (bojo, others, provide — 32 엔티티)는 `uk_*_source_refs` UK 일관 적용.

## 증거

```
grep "@UniqueConstraint.*source_refs" --glob "**/entity/**/*.java"
→ 매치 없음: bojo-int
→ 매치 있음: bojo (6), others (10), provide (16)
```

대조 사례:
```java
// sync-agent-bojo/entity/target/SecJewon.java:22-24
uniqueConstraints = @UniqueConstraint(
    name = "uk_sec_jewon_source_refs",
    columnNames = {"source_refs"}
)

// sync-agent-provide/entity/target/ApiPrvTmGd000203.java
@UniqueConstraint(name = "uk_api_prv_tm_gd000203_source_refs", columnNames = {"source_refs"})
```

## 영향

- **중복 레코드 방지 미작동** — 동일 source_refs 로 2 회 들어오면 둘 다 저장
- **trace-source 분기 2 (source_refs IN 매칭) 효율 저하** — UK 가 없으면 인덱스도 없음
- MEMORY `Source 추적 설계` 의 "제원 UK = source_refs" 규약 위반
- VER-004 (executionId 인덱스 누락) 와 함께 bojo-int 엔티티의 인덱스/제약 전반 재정비 필요

## 수정 범위

bojo-int 의 source_refs 컬럼 보유 엔티티에 UK 추가:
```java
@Table(name = "TM_GD970001",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_tm_gd970001_source_refs",
           columnNames = {"source_refs"}),
       indexes = @Index(name = "idx_tm_gd970001_exec_id", columnList = "execution_id"))  // VER-004 와 함께
```

대상 엔티티 목록은 VER-004 와 동일 (bojo-int/entity/target/** + iftable/**).

## 주의

- UK 추가는 기존 중복 데이터가 있으면 DDL 실패 → **배포 전 중복 레코드 정리 필요**
- ddl-auto=`update` 로 UK 자동 추가되지만 기존 데이터 상황에 따라 DDL 실패 가능
- 권장: 먼저 중복 탐지 쿼리 → 정리 → UK 추가

## 회귀 확인

- 빌드 통과
- 기존 데이터에 대한 UK 추가 DDL 성공
- 신규 레코드 중복 삽입 시 DataIntegrityViolationException
- trace-source 분기 2 동작 확인 (제원 역조회)

## 관련 문서
- `verify/_invariants/00-overview.md § 2` (source_refs UK 규약)
- MEMORY: "Source 추적 설계 (3/10 확정)"
- VER-004, VER-006 (bojo-int 엔티티 재정비 묶음)
