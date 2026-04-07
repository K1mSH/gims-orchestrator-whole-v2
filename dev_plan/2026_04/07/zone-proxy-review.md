# Zone/Proxy 구조 재검토

> 작성일: 2026-04-07
> 상태: 논의 중 (결론 미정)

## 발단

Agent 등록 화면에서 zone(망구분) 선택 UI가 없었고, health 응답의 zone 값(`INTERNAL`)과 DB 값(`INTERNAL_COMMON`)이 불일치하는 문제 발견. zone의 존재 이유와 활용 방안을 재검토하게 됨.

## 현황 조사

### zone이 쓰이는 곳

| 용도 | zone 출처 | 파일 |
|------|----------|------|
| **Proxy 찾기** (실행 데이터 조회) | `agent.zone` → ZoneConfig → proxyAgentUrl | ExecutionService.getProxyUrlForAgent() |
| **DB 연결 테스트** | `datasource.zone` → ZoneConfig → proxyAgentUrl | DatasourceService.testConnectionViaAgent() |
| **UI 표시** | `agent.zone` | Agent 목록 뱃지 |
| **실행 이력 필터** | `agent.zone` | ExecutionService.getHistoryPaged() |

### 실행 시 datasource 접속에 zone이 관여하는가?

**아니오.** 실행 시 DB 접속 정보는 다음 경로로 전달:

```
Agent 실행 요청 (buildExecutionRequest):
  sourceDatasourceId → Datasource 테이블에서 조회 → datasource.zone (Agent zone 아님)
  sourceZoneShortCode → datasource.zone으로 ZoneConfig 조회 (source_refs 접두사용)
  targetDatasourceId → 동일
```

**agent.zone은 실행 로직에서 사용되지 않음.** datasource 자체에 zone 필드가 있어서 거기서 가져감.

### Proxy의 실제 역할

Proxy 모듈(sync-proxy-dmz, sync-proxy-internal)의 컨트롤러:
- `ConnectionInfoController` — DB 연결정보 제공
- `HealthController` — 상태 확인
- `ExecutionDataController` (common에서 상속) — 실행 데이터 조회 API

Proxy는 **해당 망의 DB에 직접 접근**하여 execution-data API를 제공. Orchestrator는 Proxy를 통해 각 망의 DB 데이터를 조회.

### 교차 망 케이스

SND(DMZ) → RCV(내부망) 흐름에서:
- Internal RCV Agent의 source = DMZ DB (IF_SND)
- Internal RCV Agent의 zone = INTERNAL_COMMON
- **그런데 문제없이 동작함**

이유: Agent가 실행할 때 source DB 접속 정보는 `datasource.zone`에서 오고, Agent가 직접 해당 DB에 접속함. agent.zone은 관여하지 않음.

## 논점

### 1. zone의 원래 탄생 배경
- Agent가 특정 망에 배치됨 (DMZ, 내부망)
- 그 망에는 Proxy가 하나 고정
- Orchestrator가 해당 망 DB를 조회하려면 Proxy 경유 필요
- agent.zone으로 "어느 망 → 어느 Proxy" 결정

### 2. 현재 agent.zone의 실질적 역할
- **오직 Proxy URL 찾기 + UI 표시**에만 사용
- 실행 로직, DB 접속과는 무관
- datasource에도 zone이 있어서 중복

### 3. 혼동 가능성
- zone = Agent 위치? DB 위치? → Agent 위치
- Agent endpoint URL로 이미 위치를 알 수 있음
- "다른 DB로 밀어넣는" Agent가 생기면 zone 정의가 혼동될 수 있음

### 4. Proxy가 필요한가?
- 실서버 망 분리 환경에서 Orchestrator → 다른 망 DB 직접 접근 불가 시 필요
- 로컬에서는 불필요 (전부 직접 접근 가능)
- Proxy 없이 Agent에 execution-data API를 직접 호출하는 방식도 가능?

## 대안 후보 (결론 미정)

### A안: 현행 유지
- agent.zone으로 Proxy 찾기
- health 응답 zone 값 매핑 문제만 수정
- 등록 화면에 zone 드롭다운 추가

### B안: zone 제거, Proxy를 datasource 기반으로
- datasource에 proxyUrl 필드 추가
- agent.zone 폐기
- Proxy 찾기: datasource.proxyUrl 직접 사용

### C안: zone 제거, Proxy 폐기
- Orchestrator가 Agent의 execution-data API를 직접 호출
- Agent endpoint URL로 직접 접근 (agent.endpointUrl + /api/execution-data)
- 망 분리 환경에서 가능한지 검토 필요

### D안: zone을 UI 표시용으로만 유지
- Proxy 찾기는 다른 방식으로 (datasource 기반 등)
- zone은 순수 분류/필터 용도

## 다음 단계

- 실서버 망 분리 환경에서 Proxy가 반드시 필요한 케이스 확인
- C안(Agent 직접 호출) 가능 여부 검토
- 결정 후 구현
