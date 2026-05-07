# 체크리스트 — provide Agent (Internal, 8096)

> **대상**: infolink-agent-provide-dmz (Oracle 29004 → PG 29006)
> **최신 리스크** (4/22): 헤더 기반 관리 DB 라우팅, /target 통합, trace-source 분기 3 개선, TM_GD130001 파일럿, 16개 엔티티 @Comment 추가
> **필수 의존**: P1 (common), P3 (proxy-internal), P10 (Orchestrator backend), P9 (api-provider)
> **사용법**: 체크 항목은 **재실행 가능한 절차** 기준. 실패 시 `runs/YYYY-MM-DD.md` + `issues/OPEN/VER-*.md` 로 기록.

---

## 0. 환경 준비

- [ ] Docker: `gims_api_provider_pg` (29006), `gims_internal_oracle` (29004), `gims_orchestrator_pg` (29001) 기동 확인
- [ ] Orchestrator backend (8080) 기동 + `/api/health`
- [ ] Proxy Internal (8093) 기동
- [ ] provide Agent (8096) 기동 + `/actuator/health`
- [ ] api-provider (8095) 기동 (최종 API 경로 검증용)
- [ ] Frontend (3000) 기동

### 기동 로그 확인
- [ ] provide Agent 로그에 에러 없음 (JPA 매핑, ddl-auto 경고 확인)
- [ ] Proxy Internal 로그에 `X-Manage-Datasource-Id` 관련 에러 없음

---

## 1. 헤더 기반 관리 DB 라우팅 (P1 + P3 + P10 공유 규약)

- [ ] Orchestrator → Proxy → provide Agent 호출 시 요청 헤더에 `X-Manage-Datasource-Id: {agent.targetDatasourceId}` 포함
  - Proxy Internal 로그에서 헤더 수신 확인
- [ ] Agent 의 Execution/SyncLog 가 **PG 29006 (api_provider DB)** 에 기록됨
  - `psql` 로 `executions`, `sync_logs` 테이블 count 확인
- [ ] Orchestrator 모니터링 화면에서 provide Agent 실행 이력 정상 조회
- [ ] **회귀**: bojo-internal 실행도 정상 — 29001 Internal DB 에 기록되어야 함 (헤더가 다른 값)

---

## 2. /target 통합 및 trace-source 분기 3 (P1)

> `/target-if` → `/target` 로 통합됨. `/trace-source` 분기 3 에 `target_tables` 1순위 매칭 추가.

### 2.1 정방향 (Source → Target 추적)
- [ ] Frontend 실행 상세 화면에서 Source 테이블 행 클릭 → Target 데이터 정상 노출
- [ ] 백엔드 `/source` 응답: source PK 포함
- [ ] `/target` 응답: execution_id 기반 정상 조회 (JdbcTemplate 기반)

### 2.2 역방향 (Target → Source 추적)
- [ ] Frontend 실행 상세 화면에서 Target 테이블 행 클릭 → Source 데이터 정상 노출
- [ ] `/trace-source` 응답: 분기 3 에서 `target_tables` 우선 매칭 확인
  - 로그에서 분기 번호/매칭 근거 출력 확인

### 2.3 레거시 제거 검증
- [ ] 프론트엔드 어디에도 `TARGET_IF` 문자열 남아 있지 않음 (grep)
- [ ] 프론트에 `getTargetIfData` 호출 없음

---

## 3. TM_GD000203 (A3 파일럿) E2E 재현

- [ ] Orchestrator 에서 `provide-tm-gd000203` 실행
- [ ] Oracle 29004 `NGW.TM_GD000203` → PG 29006 `api_prv_tm_gd000203` 로 적재됨
- [ ] 원본 테이블 `LINK_STATUS` PENDING → 처리 후 상태 전이 확인
- [ ] `EXTRACTED_AT`, `UPDATED_AT` 갱신 확인
- [ ] SyncLog 에 `source_pk_column=merge-key` 저장됨
- [ ] 테이블별 처리현황에서 read/write count 일치
- [ ] 정방향 추적 / 역방향 추적 모두 동작

---

## 4. TM_GD130001 (A8 영향조사) E2E 재현

- [ ] 4/22 기준 6건 PENDING 처리됨 — 재현 가능 여부 확인 (샘플 데이터 재투입 필요 시 PENDING 복구)
- [ ] Oracle 29004 `TM_GD130001` → PG 29006 `api_prv_tm_gd130001`
- [ ] 엔티티 `@Comment` 가 PG 테이블/컬럼 주석으로 반영 (drop 후 재생성 기준)
  - `SELECT obj_description('api_prv_tm_gd130001'::regclass)` — 한글 코멘트 확인
- [ ] 정방향/역방향 추적 정상

---

## 5. 16 개 엔티티 @Comment 일괄 반영 (회귀)

- [ ] 모든 provide 엔티티(`infolink-agent-provide-dmz/src/.../entity/target/*.java`) 에 `@org.hibernate.annotations.Table(comment=)` + 컬럼 `@Comment` 있음
- [ ] 임의 샘플 엔티티 3개: PG 에 테이블/컬럼 코멘트 실제 반영됨
- [ ] 아직 생성되지 않은 테이블(DDL 없음) 은 엔티티만 존재 — 기동 시 ddl-auto update 실패/경고 없음

---

## 6. 새 YAML 추가 시 스모크 (P13)

> forward 세션이 새 Type A YAML 추가 시 추가 검증.

- [ ] 새 YAML 로드 정상 (기동 로그)
- [ ] Orchestrator 에서 Agent 목록에 노출
- [ ] 실행 시 PG 29006 적재 정상
- [ ] 추적 컬럼 업데이트 정상
- [ ] trace 정/역방향 정상

---

## 7. api-provider(8095) 연동 (미검증 항목)

> 아직 E2E 검증 안 됨. 향후 확장.

- [ ] api-provider 가 PG 29006 `api_prv_*` 테이블 읽어 외부 응답 구성
- [ ] 인증/페이징 동작
- [ ] 응답 JSON 스키마 일치 (레거시 대비)

---

## 8. 회귀 위험 포인트 (기억용)

- **ConditionOperator NoClassDefFoundError**: bojo-internal/provide JVM 이 구 common JAR 을 로드 중일 때 발생. 재배포 후 반드시 **재기동**.
- **Retention 음수 방어**: 4계층 다중 방어 적용됨 — 프론트 min=1 / Orchestrator API 검증 / Agent Controller 예외 / Agent Service skip.
- **source_refs vs PK 분기**: buildSourceFilter 샘플 1건 비교 로직이 3종 (RCV/Loader/SND) 모두 커버하는지 샘플링 확인.
- **target_tables 1순위 매칭**: provide 네이밍 방향이 기존 RCV/Loader 와 반대였음 → 새 Agent 네이밍 추가 시 동일 이슈 재발 가능성.
- **link_status 컬럼 없는 테이블**: 과거 잠재 버그. 새 테이블 추가 시 스키마 일관성 확인.

---

## 체크리스트 갱신 규칙

- 새 이슈가 완료되어 DONE 이동 시, 재발 방지를 위해 **여기에 케이스 추가**.
- 새 Type A/B 테이블이 추가될 때마다 섹션 확장 또는 파일 분리.
