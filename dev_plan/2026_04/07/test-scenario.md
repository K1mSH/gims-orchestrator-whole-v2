# 2026-04-07 개발 점검 및 테스트 시나리오

## 오늘 개발 내용

| # | 항목 | 모듈 | 테스트 상태 |
|---|------|------|------------|
| 1 | ApiKeyFilter `/api/pipeline/info` 인증 예외 | sync-agent-common | ✓ curl 검증됨 |
| 2 | StepFactory 다중 factory-key 지원 (getFactoryKeys) | sync-agent-common | ✗ bojo/others 빌드 미확인 |
| 3 | StepFactoryRegistry 다중 키 등록 | sync-agent-common | ✗ 런타임 미확인 |
| 4 | I2 JejuObsvdataLoadStep | sync-agent-bojo-int | ✓ E2E 통과 |
| 5 | I5 UseLoadStep + UseLoadStepFactory | sync-agent-bojo-int | ✗ 빌드만, E2E 미테스트 |
| 6 | internal-use-loader.yml (신규 Agent) | sync-agent-bojo-int | ✗ pipeline/info 확인만 |
| 7 | PM_GD970202 DDL | Oracle 29004 | ✓ 생성됨 |
| 8 | 이용량 타겟 5개 DDL | Oracle 29004 | ✓ 생성됨 |

## 4/6 개발 — 미테스트 잔여 (commit 42e8d5d)

| # | 항목 | 모듈 | 테스트 상태 |
|---|------|------|------------|
| A | Orchestrator discoverAgents() — pipeline/info 호출 | backend | ✗ 인증 이슈로 미테스트 → 이제 해결됨 |
| B | AgentService.create() — sourceTableNames로 자동 등록 | backend | ✗ |
| C | AgentService.resolveTableIds() — datasource_table 자동 INSERT | backend | ✗ |
| D | 프론트 Agent 등록 UI — 조회/선택/검증/등록 전체 흐름 | frontend | ✗ |
| E | DiscoverResponse 타입에 agentInfo 필드 누락 | frontend | ✗ 타입 미수정 (런타임은 동작) |
| F | 테이블 존재 검증 UI (pass/fail 표시) | frontend | ✗ |

## 점검 필요 사항

| # | 항목 | 확인 방법 |
|---|------|----------|
| 1 | common JAR 전파 | bojo, others, proxy-dmz, proxy-internal에 최신 JAR 복사 확인 |
| 2 | bojo/others 빌드 | StepFactory.getFactoryKeys() default 메서드 호환 |
| 3 | 기존 Agent 정상 동작 | bojo-loader, bojo-rcv 등 기존 파이프라인 영향 없는지 |
| 4 | I2 중복 실행 멱등성 | IF_RSV_TB_JEJU가 이미 SUCCESS — 0건 처리 |
| 5 | 인증 예외 범위 | /api/pipeline/info만 예외, 다른 /api/** 경로는 인증 유지 |
| 6 | DiscoverResponse 타입 | agentInfo 필드 추가 필요 여부 판단 |

## 테스트 시나리오

### T1. 기존 모듈 빌드 검증

```
1. sync-agent-bojo → ./gradlew clean build -x test
2. sync-agent-others → ./gradlew clean build -x test
   → StepFactory.getFactoryKeys() default 메서드 호환 확인
3. 실패 시: 해당 모듈의 StepFactory 구현체 확인
```

### T2. 인증 예외 범위 확인

```
이미 완료:
  curl http://localhost:8082/api/pipeline/info → 200 ✓
  curl http://localhost:8085/api/pipeline/info → 200 ✓
  curl http://localhost:8092/api/pipeline/info → 200 ✓

추가 확인 필요:
  curl http://localhost:8082/api/pipeline/run → 401 (인증 유지)
  → /api/pipeline/info만 예외이고 다른 API는 보호되는지
```

### T3. I2 JejuObsvdataLoadStep 재실행 (멱등성)

```
1. POST http://localhost:8080/api/executions/32/run
2. 기대결과:
   - read=0 (IF_RSV_TB_JEJU 전부 SUCCESS 상태)
   - write=0
   - status=SUCCESS
```

### T4. auto-discover UI 전체 흐름 (★ 핵심 — 4/6 + 오늘 작업 통합 검증)

```
사전: bojo-int 재기동

Phase 1: Orchestrator → Agent 조회 (4/6 백엔드 A 검증)
  1. http://localhost:3000/agents 접속
  2. "에이전트 등록" 클릭
  3. endpoint: http://localhost:8092 입력 → "에이전트 조회" 클릭
  4. 확인사항 (4/6 프론트 D 검증):
     a. Agent 목록 표시 — 등록됨/미등록 구분
        - internal-bojo-loader: 등록됨 (비활성/회색)
        - internal-bojo-rcv: 등록됨
        - internal-jeju-loader: 등록됨
        - internal-jeju-rcv: 등록됨
        - internal-saeol-loader: 등록됨
        - internal-saeol-rcv: 등록됨
        - internal-use-rcv: 등록됨
        - internal-use-loader: ★미등록 (선택 가능)
     b. RCV/LOADER/SND 타입별 그룹 표시
     c. 에러 없이 목록 렌더링 (DiscoverResponse 타입 이슈 E 확인)

Phase 2: pipeline info 표시 (4/6 프론트 D + 오늘 pipeline/info 인증 수정)
  5. internal-use-loader 선택
  6. 확인사항:
     a. 파이프라인 구성 (읽기 전용) 표시
        - step: use-load / 이용량 적재
        - source: IF_RSV_USE_LEGACY_DATA, IF_RSV_USE_STATUS_DATA
        - target: PM_GD111021, PM_GD111022, TM_GD111024, TM_GD111025
     b. 표시 안 되면 → 브라우저 콘솔 에러 확인 (타입 이슈 가능성)

Phase 3: datasource 선택 + 테이블 존재 검증 (4/6 프론트 F 검증)
  7. zone: INTERNAL_COMMON 선택
  8. source datasource: internal 선택
     → IF_RSV_USE_LEGACY_DATA: ✓ (존재)
     → IF_RSV_USE_STATUS_DATA: ✓ (존재)
  9. target datasource: internal 선택
     → PM_GD111021: ✓
     → PM_GD111022: ✓
     → TM_GD111024: ✓
     → TM_GD111025: ✓

Phase 4: 등록 (4/6 백엔드 B, C 검증)
  10. "등록" 클릭
  11. 확인사항:
      a. agent 목록에 internal-use-loader 등장
      b. agent_table에 source/target 테이블 자동 연결 (resolveTableIds)
      c. datasource_table에 미등록 테이블 자동 INSERT
      d. 스케줄 자동 생성 (cron)

Phase 5: E2E 실행 (오늘 I5 검증)
  12. Agent 상세 → "실행" 클릭
  13. 기대결과:
     - Legacy: read=10, write=10, skip=0
     - Status: read=5, write=5
     - 총: read=15, write=15+α (일집계/수신현황 포함)
     - status=SUCCESS

Phase 6: 데이터 검증 (Oracle)
  SELECT COUNT(*) FROM PM_GD111021;   -- 시간자료 (10건 예상)
  SELECT COUNT(*) FROM PM_GD111022;   -- 일자료 (일별 집계)
  SELECT COUNT(*) FROM TM_GD111024;   -- 최근수신현황
  SELECT COUNT(*) FROM TM_GD111025;   -- 관측데이터 (5건 예상)
  SELECT LINK_STATUS, COUNT(*) FROM IF_RSV_USE_LEGACY_DATA GROUP BY LINK_STATUS;
  SELECT LINK_STATUS, COUNT(*) FROM IF_RSV_USE_STATUS_DATA GROUP BY LINK_STATUS;
```

### T5. I5 멱등성 검증

```
1. T4 완료 후 재실행
2. 기대결과:
   - read=0 (전부 SUCCESS 상태)
   - write=0
   - status=SUCCESS
```

### T6. 기존 파이프라인 영향 확인

```
1. internal-bojo-loader (ID:20) 실행
2. 기대결과: 기존과 동일하게 SUCCESS (StepFactory 변경 영향 없음)
```

## 실행 순서

```
1. T1 (빌드 검증) — 선행 필수
2. bojo-int 재기동
3. T2 (인증 범위 추가 확인)
4. T3 (I2 멱등성)
5. T4 (auto-discover UI + I5 등록 + E2E) ★ 핵심
6. T5 (I5 멱등성)
7. T6 (기존 파이프라인)
```

## 알려진 이슈 (테스트 중 발견 시 수정)

| # | 이슈 | 심각도 | 대응 |
|---|------|--------|------|
| 1 | DiscoverResponse 타입에 agentInfo 필드 누락 | 낮음 | 런타임 동작은 함, 타입 안전성만 |
| 2 | 테이블 존재 검증 API 미구현 가능성 | 중간 | verify-tables 엔드포인트 검색 결과 없음 — UI에서 직접 조회할 수도 |
| 3 | TM_GD111010 최소 컬럼만 생성 | 낮음 | I3 구현 시 ALTER TABLE로 확장 필요 |
