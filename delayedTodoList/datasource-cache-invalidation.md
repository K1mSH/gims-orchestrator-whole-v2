# Datasource 캐시 자동 만료 (TODO)

## 상태: 보류

## 배경
Agent의 SyncDataSourceService가 Proxy에서 받은 connection-info를 메모리 캐시(ConcurrentHashMap)에 저장.
datasourceId 기준으로 캐시 히트하면 Proxy 호출을 생략하므로,
Orchestrator에서 datasource 정보(host/port/password 등)를 변경해도 Agent 재시작 전까지 반영 안 됨.

## 방안: Orchestrator → Agent 캐시 클리어 요청

Orchestrator에서 datasource 수정 시 해당 datasourceId를 사용하는 Agent에 캐시 클리어 요청을 보낸다.

- Orchestrator DatasourceService.update() 시점에 해당 datasourceId를 사용하는 Agent 목록 조회
- 각 Agent(또는 Proxy)에 캐시 클리어 API 호출: DELETE /api/datasource-cache/{datasourceId}
- Agent SyncDataSourceService에 evict(datasourceId) 메서드 추가
- 수동 캐시 클리어 API는 불필요 (사용자가 내부 구현을 몰라도 되게)

## 영향 범위
- sync-orchestrator: DatasourceService.update()에 캐시 클리어 로직 추가
- sync-agent-bojo: SyncDataSourceService에 evict() + REST 엔드포인트
- sync-agent-bojo-int: 동일
- sync-proxy-dmz: ProxyDataSourceService에 evict() + REST 엔드포인트 (Proxy도 자체 캐시 있음)
- sync-proxy-internal: 동일
