# agent_table 자동 동기화 계획

> 작성일: 2026-04-07
> 선행: auto-discover API 완료, pipeline/info 인증 예외 완료

## 목적

Agent YAML 변경(step 추가/제거) 시 Orchestrator의 agent_table을 자동으로 동기화한다.
사용자가 수동으로 "갱신" 버튼을 누르지 않아도 반영되도록 한다.

## 현재 문제

- YAML에 step 추가 → Agent 재기동 → Orchestrator agent_table은 그대로
- 상세보기에서 실제 YAML과 DB 등록 상태 불일치를 알 수 없음
- 예: internal-jeju-loader에 I2 추가했지만 agent_table에는 I1 테이블만

## 변경 후 흐름

```
1. Agent YAML 수정 → Agent 재기동
2. Health Scheduler(30초)에서 OFFLINE → ONLINE 감지
3. pipeline/info 조회 → YAML 기반 source/target 테이블명 수집
4. agent_table 동기화:
   - datasource_table에 있으면 → agent_table 연결
   - datasource_table에 없으면 → skip (연결 불가)
   - YAML에서 제거된 테이블 → 기존 연결 유지 (안전)
5. 상세보기 진입 시 pipeline/info 조회 → pass/fail 표시
```

## 수정 대상

### 1. AgentHealthScheduler.java (백엔드)

OFFLINE → ONLINE 전환 시 pipeline/info 조회 + agent_table 동기화 추가.

```java
// 기존: ONLINE 전환만
agent.setStatus(AgentStatus.ONLINE);
agentRepository.save(agent);

// 추가: pipeline/info 조회 → agent_table 동기화
syncAgentTables(agent);
```

syncAgentTables 로직:
1. GET {endpointUrl}/api/pipeline/info
2. 해당 agentCode의 steps에서 sourceTables/targetTables 수집
3. agent.sourceDatasourceId의 datasource_table에서 매칭
4. agent.targetDatasourceId의 datasource_table에서 매칭
5. 기존 agent_table에 없는 것만 INSERT (추가만, 삭제 안 함)

### 2. AgentService.java (백엔드)

syncAgentTables 메서드 추가 (스케줄러 + 상세보기에서 공용):
- pipeline/info 조회
- datasource_table 매칭
- agent_table 동기화

### 3. InfoTab.tsx (프론트 — 상세보기 읽기 모드)

현재: agent_table 기반 테이블 목록만 표시
변경: pipeline/info 조회 → YAML 기반 테이블 목록 + pass/fail 표시

```
읽기 모드:
  Source Datasource: 내부망 (Oracle)
    ✓ IF_RSV_USE_LEGACY_DATA
    ✓ IF_RSV_USE_STATUS_DATA

  Target Datasource: 내부망 (Oracle)
    ✓ PM_GD111021  이용량시간자료
    ✓ PM_GD111022  이용량일자료
    ✗ TM_GD111099  (미발견)
```

Agent OFFLINE 시: pipeline/info 조회 불가 → 기존 agent_table 목록만 표시 (pass/fail 없음)

### 4. InfoTab.tsx (프론트 — 수동 갱신 버튼)

상세보기 읽기 모드에 "파이프라인 동기화" 버튼 추가.
클릭 시: pipeline/info 재조회 → agent_table 동기화 API 호출 → 화면 새로고침.

용도: 30초 안에 Agent 재기동되어 OFFLINE→ONLINE 감지 못한 경우 수동 트리거.

### 5. InfoTab.tsx (프론트 — 수정 모드)

이미 구현됨:
- datasource select 활성화
- pass/fail 검증
- fail 시 저장 차단

## 구현 순서

```
1. AgentService에 syncAgentTables 메서드 추가
2. AgentHealthScheduler에서 OFFLINE→ONLINE 시 호출
3. InfoTab 읽기 모드에 pass/fail 표시 추가
4. 테스트: Agent 재기동 → 자동 동기화 확인
```

## 주의사항

- 추가만, 삭제 안 함 — YAML에서 제거된 테이블의 기존 연결은 유지 (이력 보존)
- datasource_table에 미등록 테이블은 agent_table 연결 불가 — skip + 로그
- Agent OFFLINE 시 pipeline/info 조회 실패 → 기존 데이터 유지, 에러 무시
- 동일 endpointUrl에 여러 Agent → pipeline/info 1회 조회, agentCode별 분배
