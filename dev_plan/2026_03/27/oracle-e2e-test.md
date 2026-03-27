# Oracle 전환 E2E 테스트 계획

## 목적
- Internal Agent의 IF/Target DB를 Oracle(29004/XEPDB1)로 전환한 상태에서 test_plan/bojo-test.md 기준 E2E 검증
- 어제(3/26) 코드 수정 완료, 오늘은 **테스트만 진행**

## 현재 상태
- 코드 수정: ConditionBuilder, SourceToIfStep, InternalBojoLoadStep, GimsTargetRepository, DbType 등 Oracle 분기 완료
- DDL: scripts/oracle-init.sql (4테이블), oracle-init-if.sql (2테이블) 준비 완료
- 빌드: 4개 모듈 BUILD SUCCESSFUL
- 어제 이슈: Proxy 경유 연결 실패 (Proxy에 Oracle 드라이버 필요 여부 확인 필요)

## 변경점 vs test_plan
| test_plan 기준 | 기존 (PG) | Oracle 전환 후 |
|---------------|-----------|---------------|
| Internal IF/Target DB | PG 29002/dev | Oracle 29004/XEPDB1 |
| Internal IF 테이블 | if_rsv_sec_jewon, if_rsv_sec_obsvdata | 동일 (Oracle DDL) |
| GIMS Target | pm_gd970201 | 동일 (Oracle DDL) |
| Link 테이블 | tm_gd980002 | 동일 (Oracle DDL) |

## 테스트 단계

### Phase 0: 사전 점검
1. Oracle 컨테이너(29004) 기동 확인
2. DDL 실행 확인 (6테이블 존재 여부)
3. Orchestrator에 internal datasource가 Oracle(29004)로 등록되어 있는지 확인
4. 5개 서비스 빌드 + 기동 (8080, 8082, 8083, 8092, 8093)

### Phase 1: 연결 테스트 (test_plan 3장)
- Agent(8092) → Proxy(8093) → Orchestrator(8080) → connection-info → Oracle 연결
- Proxy 패스스루 로그 확인
- Agent 복호화 + JDBC 연결 성공 확인

### Phase 2: E2E 파이프라인 (test_plan 4장 - Internal 구간만)
- DMZ 구간(RCV→Loader→SND)은 PG 그대로이므로 기존과 동일
- **핵심**: Internal RCV + Internal Loader가 Oracle에 정상 적재하는지

| 순서 | Agent | 검증 |
|------|-------|------|
| 1 | DMZ RCV (대전 1개만) | IF_RSV 적재 확인 |
| 2 | DMZ Loader | Target 적재 확인 |
| 3 | DMZ SND | IF_SND 적재 확인 |
| 4 | Internal RCV | **Oracle** IF_RSV 적재 확인 |
| 5 | Internal Loader | **Oracle** pm_gd970201 적재 확인 (1:3 EAV) |

### Phase 3: 추적 검증 (test_plan 6장)
- 각 Step 실행 후 Summary API 호출
- Target/Source 데이터 조회 → 건수 일치 확인
- Internal Loader: source_refs 매칭 모드 확인

### Phase 4: Conditions 검증 (test_plan 5장 - Internal만)
- Internal Loader에 조건실행 → Oracle WHERE 절 생성 확인
- ConditionBuilder Oracle 문법 (인용 없음) 정상 동작

### Phase 5: Retention 검증 (test_plan 8장 - Internal만)
- Internal Agent cleanup → Oracle DELETE 정상
- targetDatasourceId: "internal" 필수 확인
- pm_gd970201의 obsrvn_dt 컬럼 사용

## 수정 대상 파일 (예상)
- 코드 수정 없음 (테스트만)
- Proxy Oracle 드라이버 이슈 발생 시: sync-proxy-internal/build.gradle

## 영향 범위
- Internal Agent (sync-agent-bojo-int) 관련만
- DMZ 구간은 변경 없음
