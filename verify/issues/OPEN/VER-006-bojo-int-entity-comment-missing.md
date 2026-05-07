---
id: VER-006
title: bojo-internal 엔티티 @Comment 전무
status: OPEN
created: 2026-04-23
parts: [P6-bojo-internal]
parallel_safe: true
assignee: forward
related: []
---

## 증상

`infolink-agent-bojo-internal/**/entity/**/*.java` 전체에 `@Comment` / `@org.hibernate.annotations.Table(comment=...)` 미적용 (**0 건**).

다른 모든 Agent / 모듈은 엔티티에 `@Comment` 적용 상태 (bojo / others / provide / orchestrator / collector). **bojo-internal 만 예외**.

## 증거

```
grep "@Comment(|@org.hibernate.annotations.Table" infolink-agent-bojo-internal/**/entity/**/*.java
→ 0 건
```

대조:
- bojo 엔티티 (SecObsvdata 11, SecJewon 20, LinkNgwis 4, IfRsv/IfSnd sec* 각 13~22 등): 적용
- provide 16 엔티티 (4/22 작업): 전면 적용
- others 11 엔티티 IfSnd*: 적용
- orchestrator 엔티티 (Agent, Datasource, Schedule 등): 적용

## 영향

- bojo-internal 관리 DB (Internal Oracle XEPDB1) 의 테이블/컬럼 **주석 부재**
- 운영자가 DB 직접 조회 시 스키마 이해도 저하
- 표준화 매핑 문서(`docs/Standardizedtable/_converted/`) 와 일관성 ↓
- invariant 7 (@Comment ↔ DB 주석 동기화) 위반

## 수정 범위

bojo-internal target 엔티티 (20+) + IF_RSV 엔티티 (14 saeol + 7 일반) 에 `@Comment` 적용:

```java
@Table(name = "TM_GD970101")
@org.hibernate.annotations.Table(appliesTo = "TM_GD970101", comment = "ODM결과 (관측소 측정 데이터)")
public class TmGd970101 {
    @Comment("관측소 식별자 (BRNCH_ID)")
    @Column(name = "BRNCH_ID")
    private String brnchId;
    ...
}
```

## 주의 — ddl-auto 함정 (4/22 선례)

- `ddl-auto=update` 는 **기존 테이블의 주석 변경을 반영하지 않음**
- 4/22 provide 작업에서 동일 함정 발생 → 16 개 테이블 drop 후 재생성으로 해결
- bojo-internal 는 Internal Oracle 의 실 데이터 테이블 — drop 영향 큼. 대안:
  - (a) DDL 직접 실행 (`COMMENT ON TABLE ...` / `COMMENT ON COLUMN ...`)
  - (b) 데이터 백업 후 drop / recreate
  - 권장: (a)

## 회귀 확인

- 빌드 통과
- `SELECT * FROM USER_TAB_COMMENTS WHERE TABLE_NAME = 'TM_GD970101'` (Oracle) 에서 주석 확인
- 컬럼 주석: `USER_COL_COMMENTS`

## 관련 문서
- `verify/_invariants/00-overview.md § 7` (@Comment ↔ DB 주석 동기화)
- 4/22 provide 엔티티 @Comment 작업 선례 (`dev_logs/2026_04/2026-04-22.md`)
- 표준화 매핑: `docs/Standardizedtable/_converted/`
