# Agent 테이블 자동 발견(Auto-Discover) 계획

> 작성일: 2026-04-06
> 목적: Agent 등록 시 YAML 기반 테이블 자동 발견, 수동 SQL 등록 제거

## 현재 문제

- Agent의 source/target 테이블을 SQL 스크립트로 수동 등록
- datasource_table 등록 → agent_table 연결 → 2단계 수동
- YAML에 이미 정의된 정보를 다시 등록하는 중복 작업
- UI에서 "선택"을 제공하지만 테이블은 선택의 여지 없음 (답이 정해져있음)
- 테이블 등록 UI 자체가 없어서 SQL 직접 실행 필요

## 변경 후 흐름

```
1. UI에서 Agent 등록
   a. endpoint URL 입력
   b. Orchestrator → Agent에 info 조회 (GET /api/pipeline/info)
   c. Agent가 YAML 기반으로 응답:
      - agentCode, type
      - steps[]: stepId, stepName, sourceTables[], targetTables[]
   d. UI에 파이프라인 구성 표시 (읽기 전용):
      ┌──────────────────────────────────────┐
      │ Agent: internal-jeju-loader [LOADER] │
      │                                      │
      │ [jeju-jewon-load] 제주 제원 적재      │
      │   Source: IF_RSV_TB_JEJU_JEWON       │
      │   Target: TM_GD970001, TM_GD120001,  │
      │           TM_GD970130, ...            │
      └──────────────────────────────────────┘
   e. 사용자가 zone, source/target datasource 선택
   f. datasource 선택 시 테이블 존재 여부 검증:
      Source Datasource: 내부망(Oracle) 선택
        ✓ IF_RSV_TB_JEJU_JEWON — 확인됨
      Target Datasource: 내부망(Oracle) 선택
        ✓ TM_GD970001 — 확인됨
        ✓ TM_GD120001 — 확인됨
        ✗ TM_GD999999 — 미발견 (경고)
   g. 사용자 확인 후 "등록" 클릭

2. Orchestrator 등록 처리
   a. Agent 기본정보 저장 (agent 테이블)
   b. source_datasource에서 source 테이블:
      - datasource_table에 있으면 → id 사용
      - 없으면 → 자동 INSERT (테이블명 + COMMENT)
   c. target_datasource에서 동일 처리
   d. agent_table 연결 (SOURCE/TARGET 구분)
   e. 컬럼 정보 없으면 자동 수집

3. UI 상세보기
   - 현재와 동일하게 agent_table 기반 표시
   - 읽기 전용 (수동 테이블 연결 UI 없음)

4. Agent YAML 변경 시
   - Agent 재기동 → UI에서 "갱신" 버튼으로 info 재조회 가능
   - 기존 데이터 보존, 변경분만 반영
```

## 수정 대상

### 1. Agent 측 (3개 모듈 공통)

**신규 API: GET /api/pipeline/info**

PipelineController에 추가. AgentConfigLoader에서 정보 수집:
```json
[
  {
    "agentCode": "internal-jeju-loader",
    "type": "LOADER",
    "steps": [
      {
        "stepId": "jeju-jewon-load",
        "stepName": "제주 제원 적재",
        "sourceTables": ["IF_RSV_TB_JEJU_JEWON"],
        "targetTables": ["TM_GD970001", "TM_GD120001", "TM_GD970130", "TM_GD970002", "TM_GD970101"]
      }
    ]
  }
]
```

### 2. Orchestrator 측

**Agent 등록 API 확장**

기존 등록 + 테이블 자동 처리:
1. Agent 기본정보 저장
2. steps 정보에서 source/target 테이블명 추출
3. source_datasource의 datasource_table에서 매칭 (없으면 INSERT)
4. target_datasource 동일 처리
5. agent_table 연결 (SOURCE/TARGET)

**테이블 존재 검증 API: GET /api/agents/verify-tables**

요청: datasourceId + 테이블명 목록
응답: 각 테이블의 pass/fail

### 3. UI 측

**Agent 등록 화면 (신규 또는 기존 개편):**
1. endpoint 입력 + 연결 확인
2. Agent info 자동 조회 → 파이프라인 구성 표시 (읽기 전용)
3. zone 선택
4. source datasource 선택 → 테이블 pass/fail 표시
5. target datasource 선택 → 테이블 pass/fail 표시
6. 등록 버튼

**Agent 상세:**
- 테이블 목록: 읽기 전용 (현재와 동일)
- 수동 테이블 연결 UI: 제거

## 실행 순서

```
Phase 1: Agent API
  - GET /api/pipeline/info — 3개 모듈 PipelineController에 추가
  - AgentDefinition에서 step별 source/target 테이블 반환

Phase 2: Orchestrator 백엔드
  - 테이블 존재 검증 API
  - Agent 등록 시 테이블 자동 등록/연결 로직
  - datasource_table 자동 INSERT

Phase 3: UI
  - Agent 등록 화면: endpoint → info 조회 → datasource 선택 → 검증 → 등록
  - Agent 상세: 수동 테이블 연결 제거

Phase 4: 정리
  - orchestrator-*-register.sql에서 테이블 등록 부분 제거
  - 기존 agent_table 데이터는 유지 (호환)
```

## 주의사항

- Agent가 떠있어야 info 조회 가능 — endpoint 입력 후 연결 실패 시 안내 메시지
- 기존 수동 등록 데이터 유지 — 자동 발견이 덮어쓰지 않고 없는 것만 추가
- datasource 선택은 사용자 판단 — 환경마다 다를 수 있음
- 테이블 미발견(fail)은 경고만 — DDL 미생성 상태일 수 있으므로 등록 차단하지 않음
- 실행 로직은 변경 없음 — 등록 과정만 개선
