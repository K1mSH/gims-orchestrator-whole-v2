---
id: VER-005
title: provide target 엔티티 executionId 인덱스 누락 (전무)
status: OPEN
created: 2026-04-23
parts: [P7-provide]
parallel_safe: true
assignee: forward
related: [VER-004]
---

## 증상

`sync-agent-provide/**/entity/**/*.java` 전체에 `@Index(columnList = "execution_id")` 미적용 (**0 건**).

4/22 작업에서 16 개 엔티티에 `@Comment` 는 추가했으나 **인덱스는 빠짐**.

## 증거

```
grep "@Index|indexes" sync-agent-provide/**/entity/**/*.java
→ 0 건
```

대상 엔티티 (16):
- ApiPrvTmGd000203 (A3 파일럿)
- ApiPrvTmGd110301, ApiPrvTmGd110302
- ApiPrvTmGd112002, ApiPrvTmGd120001, ApiPrvTmGd130001
- ApiPrvNgw04, ApiPrvPermwell, ApiPrvGeneral105
- ApiPrvLinkageChart, ApiPrvWaterQuality, ApiPrvWaterQualityMfds
- ApiPrvInspection, ApiPrvActualUseDj, ApiPrvUnregitsFcly, ApiPrvWqInputStatusDj

## 영향

- `/target` 엔드포인트 조회 성능 저하
- `/trace-source` 분기 2/3 에서 source_refs 역조회 속도 저하
- api-provider 가 provide 테이블 조회 시 부가 부담
- 배포 후 데이터 누적 시 악화

## 수정 범위

각 `ApiPrv*` 엔티티에 패턴 적용:
```java
@Table(name = "api_prv_tm_gd000203",
       indexes = @Index(name = "idx_api_prv_tm_gd000203_exec_id", columnList = "execution_id"))
```

## 주의

- @Comment 추가 시 학습한 함정: ddl-auto update 가 기존 테이블 스키마 일부를 반영 안 함
- 인덱스는 일반적으로 `update` 가 생성하지만, 기존 ddl-auto 동작 확인 필요. 필요 시 DDL 직접 실행 또는 drop/recreate.

## 회귀 확인

- 빌드 통과
- PG 29006 에서 `\d api_prv_tm_gd000203` 으로 인덱스 존재 확인
- 샘플 실행 후 조회 성능 측정 (선택)

## 관련 문서
- `verify/_invariants/00-overview.md § 4`
- VER-004 (bojo-int 동일 누락)
- MEMORY: "Source 추적 설계 (3/10 확정)", `feedback_provide_layer_upsert`
