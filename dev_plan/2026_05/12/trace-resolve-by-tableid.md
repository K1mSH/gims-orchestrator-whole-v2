# 추적(trace) 테이블 해석을 source_refs 의 tableId 기반으로 — 이름/접두사 추측 제거 (계획)

작성일: 2026-05-12
발견 경위: 04-others — `internal-jeju-rcv` 실행 후 `IF_RSV_TB_JEJU` 행 → 역추적(trace-source) "원본 없음". 추적이 `if_snd_tb_jeju` 대신 `if_snd_tb_jeju_jewon`(접두사 충돌) 을 조회.

## 원칙

source_refs 형식 = `["{zone}:{datasourceId}:{tableId}:{pk}"]` (`zone` = D/E/I, `tableId` = `agent_table.id` — 그 (agent, 테이블) 을 *정확히* 특정). **추적 로직은 이 tableId 로 테이블을 특정해야 함.** IF 테이블명에서 접두사 떼서 `contains` 매칭 같은 추측은 금지 — `if_snd_tb_jeju` ⊂ `if_snd_tb_jeju_jewon` 같은 충돌이 언제든 또 남.

> 보조 자료: `parseSourceRefsTableName` 은 `I:dsId:tableName:pk` (3번째가 *이름*) 만 처리하고 `D:`/`E:` (3번째가 숫자 tableId) 는 null 반환 — 그래서 tableId→이름 레지스트리 조회가 없어 이름 추측으로 빠졌음.

## 버그 2개

### A. 역방향 — `traceToSource` (Target → Source)
`sourceTable` 이 `if_rsv_*`/`if_snd_*` → `baseName = strip prefix` → `allSourceTables.filter(t -> t.equals(baseName)).findFirst().or(() -> ...filter(t -> t.contains(baseName))...)` → `contains` 매칭에서 `if_snd_tb_jeju_jewon` 이 `tb_jeju` 포함해서 stream 순서상 먼저 잡힘. (Loader-TARGET 분기, 마지막 fallback 도 이름/`contains` 기반)

### B. 정방향 — `traceBySourcePk` (Source PK → Target)
`patternA = "%:" + sourceTable + ":" + pkValue + "\"]"` — source_refs 안에 테이블 *이름* 이 있다고 가정하고 LIKE. 근데 `D:`/`E:` 포맷은 3번째가 tableId(숫자) → Pattern A 가 안 맞아서 느슨한 Pattern B(`%:{pk}"]` — pk만) 로 떨어짐 → 같은 pk 값 쓰는 다른 소스 테이블 있으면 오매칭 가능. (`I:` 포맷만 Pattern A 가 맞음)

## 수정

### 공통 헬퍼 (ExecutionDataReader 또는 ExecutionDataController private)
- `findTableNameById(JdbcTemplate mgmtJdbc, long agentTableId)` → `SELECT dt.table_name FROM agent_table at JOIN datasource_table dt ON at.datasource_table_id = dt.id WHERE at.id = ?` → 테이블명 (없으면 null)
  - (코딩 시 `datasource_table` 의 실제 컬럼명 확인 — `table_name` 가정)
- `findAgentTableIdByName(JdbcTemplate mgmtJdbc, String tableName, Long agentId)` → agent_id 우선 + 이름 매칭으로 `agent_table.id` (정방향 패턴 구성용; agent_id 없거나 못 찾으면 이름만으로 first)
- `parseSourceRefsTableId(String sourceRefs)` → `D:`/`E:` 면 `parts[2]` 를 Long 으로 (숫자 아니면 null = `I:` 포맷 → `parseSourceRefsTableName` 이 처리)

### A. `traceToSource` — upstream 테이블 해석 순서 정리
1. **1순위 (exact)**: `parseSourceRefsTableId(sourceRefs)` → null 아니면 `findTableNameById(...)` → 테이블명. 이게 잡히면 그걸로 확정. (`if_snd_tb_jeju` 정확히 나옴)
2. **2순위 (I: 포맷)**: `parseSourceRefsTableName(sourceRefs)` (3번째가 이름)
3. **3순위 (fallback)**: 기존 SyncLog source_tables 매칭 — 단 **정확 매칭만** (`equals`), `contains`/substring/prefix 매칭 제거. + IF↔IF 대응이 필요하면 정확히 `if_snd_`+baseName / `if_rsv_`+baseName 같은 *정확한 변환* 한 번만 (substring 금지).
4. (Loader-TARGET 분기의 "양방향 contains" 도 동일하게 — target_tables 정확 매칭 → 그 매핑의 source_tables, 안 되면 위 1~3)

### B. `traceBySourcePk` — 검색 패턴을 tableId 로
1. `sourceTable` + (execution 의 agent_id) → `findAgentTableIdByName(...)` → tableId
2. tableId 있으면 **Pattern A' = `%:{tableId}:{pkValue}"]`** 로 (precise). `I:` 포맷 호환 위해 기존 이름 패턴 (`%:{sourceTable}:{pkValue}"]`) 도 OR 로 같이 검색.
3. 둘 다 못 맞으면 기존 Pattern B (`%:{pkValue}"]`, 느슨) fallback 유지.

## 영향 / 회귀

- `infolink-agent-common/.../controller/ExecutionDataController.java` (+ 헬퍼는 `ExecutionDataReader` 에) → common JAR → 9개 모듈 복사 + 영향 agent 재빌드 (bojo-dmz/bojo-internal/others-dmz/provide-dmz/api-collector — RCV/SND/Loader trace-source 쓰는 모듈).
- 회귀: exact 매칭이 prefix/contains 매칭보다 *더* 정확 — 기존에 우연히 맞던 케이스(이름이 안 겹치던)는 그대로, 겹쳐서 틀리던 케이스(jeju)는 고쳐짐. tableId=0 (소스 미등록) / `I:` 포맷 / 레지스트리 조회 실패 시엔 기존 fallback 으로 그대로 동작.
- `manageDsId`/`X-Manage-Datasource-Id` 헤더로 받는 mgmtJdbc 가 `agent_table`/`datasource_table` 를 가진 관리 DB — 이미 SyncLog/Execution 조회에 쓰고 있으므로 추가 의존 없음.

## 테스트

1. 수정 후 — `internal-jeju-rcv` 의 `IF_RSV_TB_JEJU` 행 → trace-source → `sourceTableName=if_snd_tb_jeju`, 1건 FOUND ✅ (버그 케이스)
2. `IF_RSV_TB_JEJU_JEWON` → `if_snd_tb_jeju_jewon` FOUND (회귀)
3. DMZ side: `if_snd_tb_jeju` 행 → trace-source → `tb_jeju` FOUND (회귀 — 03-bojo/04-others §4-5 동작 유지)
4. 정방향: `tb_jeju` 의 rid=145 → `/trace` → targetTableName=`if_snd_tb_jeju` (jewon 아님), 1건. `tb_jeju_jewon` 의 SC001 → `if_snd_tb_jeju_jewon`
5. 03-bojo trace (sec_jewon/sec_obsvdata) 회귀 — 이름 안 겹치는 케이스 그대로

## 작업 순서

헬퍼 추가(ExecutionDataReader/Controller) → `traceToSource` 해석 순서 교체 → `traceBySourcePk` 패턴 교체 → common build → JAR 9 모듈 복사 → others-dmz/bojo-internal/bojo-dmz/provide-dmz 재빌드(가동 중인 것만 재기동: others-dmz 8085, bojo-internal 8092, bojo-dmz 8082) → 테스트 → dev_log/커밋
