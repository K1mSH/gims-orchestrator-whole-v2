# 미해결 이슈: provide Agent 관리 테이블 DB 위치

> 작성일: 2026-04-21
> 상태: 내일 결정

## 문제

Orchestrator 프론트에서 provide Agent의 테이블별 처리현황이 안 보임.

### 원인

- Proxy(8093)가 `execution_id` 기반으로 execution/sync_log를 조회
- Proxy는 자기 기본 datasource(Oracle 29004)에서 조회
- provide Agent의 execution/sync_log는 PG 29006에 있음 → Proxy가 못 읽음

### DMZ에서는 왜 되나

DMZ의 Proxy/bojo/others가 **같은 DB(PG 29001 dev)**를 기본 datasource로 공유.
같은 execution/sync_log 테이블에 agent_id로 구분.

### 내부망 현황

```
Oracle 29004 (기본 datasource)
  ├── Proxy(8093) — 여기서 읽음
  ├── bojo-int(8092) — 여기에 쓰고 ✓
  └── provide(8096) — 여기에 안 씀 ✗ → PG 29006에 쓰고 있음
```

## 검토 중인 방향

provide Agent의 기본 datasource를 Oracle 29004로 변경.

```
provide Agent:
  기본 datasource → Oracle 29004 (execution, sync_log → common 로직 변경 없음)
  제공 테이블     → PG 29006 (DynamicEntityManagerService로 별도 관리)
```

### 장점
- common 로직 변경 없음 (JPA 기본 datasource에 자동 저장)
- Proxy가 Oracle에서 모든 Agent의 이력을 조회 가능
- DMZ 패턴과 동일 (같은 DB 공유)

### 고려사항
- api_prv_* 엔티티의 ddl-auto가 Oracle에 생기지 않도록 EntityScan 분리 필요
- DynamicEntityManagerService로 PG 29006에 api_prv_* 테이블 생성 — bojo-int 기존 패턴 활용
- 기본 datasource가 bojo-int과 같아지는 것 — 문제 없음 (DMZ도 같은 구조)
