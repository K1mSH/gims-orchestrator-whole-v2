# SyncLog table-mapping 기반 재설계

## 배경

### 현재 문제
1. **Source 데이터 필터링 실패**: Loader 실행 상세에서 source 데이터가 전체 표시됨
2. **Source↔Target 관계 추론 불가**: SyncLog에 매핑 정보 없음, 이름 추론에 의존
3. **Loader target 이름 규칙성 없음**: pm_gd970201, tm_gd980002 등 → 이름으로 source 유추 불가

### 근본 원인
Source→Target 관계가 어디에도 명시되지 않음.
- YAML: 암묵적 키(jewon, obsvdata)로 연결되지만, N:M 표현 불가
- SyncLog: per-table 구조 (table_name 하나), 상대편 테이블 모름
- 추적 코드: 이름 매칭/source_refs 샘플링으로 때움 (fragile)

## 설계

### 1. YAML에 table-mappings 추가

Source→Target 관계를 명시적으로 정의. 양쪽 모두 배열 (N:M 대응).

**RCV (dmz-bojo-rcv-daejeon.yml):**
```yaml
# 기존 jewon/obsvdata/link 설정은 유지
table-mappings:
  - name: jewon
    source: [sec_jewon_view]
    target: [if_rsv_sec_jewon]
  - name: obsvdata
    source: [sec_obsvdata_view]
    target: [if_rsv_sec_obsvdata]
```

**DMZ Loader (dmz-bojo-loader.yml):**
```yaml
table-mappings:
  - name: jewon
    source: [if_rsv_sec_jewon]
    target: [sec_jewon]
  - name: obsvdata
    source: [if_rsv_sec_obsvdata]
    target: [sec_obsvdata]
```

**SND (dmz-bojo-snd.yml):**
```yaml
table-mappings:
  - name: jewon
    source: [sec_jewon]
    target: [if_snd_sec_jewon]
  - name: obsvdata
    source: [sec_obsvdata]
    target: [if_snd_sec_obsvdata]
```

**Internal RCV (internal-bojo-rcv.yml):**
```yaml
table-mappings:
  - name: jewon
    source: [if_snd_sec_jewon]
    target: [if_rsv_sec_jewon]
  - name: obsvdata
    source: [if_snd_sec_obsvdata]
    target: [if_rsv_sec_obsvdata]
```

**Internal Loader (internal-bojo-loader.yml):**
```yaml
table-mappings:
  - name: obsvdata
    source: [if_rsv_sec_obsvdata]
    target: [pm_gd970201, tm_gd970101]
```
→ link 테이블(tm_gd980002)은 동기화 시점 추적용이므로 매핑에서 제외.
  link는 별도 설정(기존 `target-table.link`)으로 유지하되 매핑/SyncLog/추적 대상이 아님.

### 2. SyncLog 구조 변경 (per-table → per-mapping)

**현재:**
```
id | execution_id | table_name          | table_type | success_count | failed_count | ...
1  | xxx          | if_rsv_sec_obsvdata | SOURCE     | 14093         | 0            |
2  | xxx          | pm_gd970201         | TARGET     | 14093         | 0            |
3  | xxx          | tm_gd980002         | LINK       | 100           | 0            |
```

**변경 후:**
```
id | execution_id | mapping_name | source_tables              | target_tables                   | read_count | write_count | skip_count | failed_count | ...
1  | xxx          | obsvdata     | ["if_rsv_sec_obsvdata"]    | ["pm_gd970201","tm_gd970101"]   | 14093      | 14093       | 0          | 0            |
```
→ link 테이블(tm_gd980002)은 매핑/SyncLog 대상에서 제외

컬럼 변경:
- 삭제: `table_name`, `table_type`
- 추가: `mapping_name` (YAML의 name), `source_tables` (JSON 배열), `target_tables` (JSON 배열)
- 변경: `success_count` → `read_count` + `write_count` 분리 (source 읽은 수 ≠ target 쓴 수)

### 3. Step 코드 수정 — SyncLog 기록 로직

**현재**: Step이 테이블마다 개별 saveSyncLogSummary() 호출
```java
saveSyncLogSummary(executionId, ifObsvdataTable, "SOURCE", readCount, ...);
saveSyncLogSummary(executionId, targetObsvdataTable, "TARGET", writeCount, ...);
saveSyncLogSummary(executionId, targetLinkTable, "LINK", linkCount, ...);
```

**변경**: 매핑 단위로 한 번 호출 (link 테이블 제외)
```java
saveSyncLogMapping(executionId, "obsvdata",
    List.of(ifObsvdataTable),
    List.of(targetObsvdataTable, targetResultTable),
    readCount, writeCount, skipCount, failedCount, ...);
```

수정 대상 Step:
- `sync-agent-common/.../step/SourceToIfStep.java` (RCV, SND 공통)
- `sync-agent-bojo/.../loader/step/DaejeonLoadStep.java` (DMZ Loader)
- `sync-agent-bojo-int/.../loader/step/InternalLoadStep.java` (Internal Loader)

### 4. YAML 파싱 — AgentConfig에 mappings 로드

`AgentConfigLoader`가 `table-mappings` 섹션을 파싱하여 Step에 전달.

```java
public class TableMapping {
    private String name;
    private List<String> source;
    private List<String> target;
}
```

Step은 SyncLog 기록 시 이 매핑 정보를 사용.

### 5. ExecutionDataController 수정 — 매핑 기반 source 필터링

**현재 `getSourcePksFromIfTable()`:**
1. SyncLog에서 TARGET_IF 찾기 (Loader 누락)
2. 이름 매칭으로 IF 테이블 추론 (fragile)
3. source_refs에서 PK 추출

**변경:**
1. SyncLog에서 `source_tables`에 요청된 sourceTable이 포함된 매핑 찾기 → **바로 target_tables 확정**
2. target 테이블에서 `execution_id = ?`로 source_refs 조회
3. source_refs에서 PK 추출 (DISTINCT)

이름 매칭, source_refs 샘플링, TARGET_IF 구분 모두 불필요해짐.

### 6. 프론트엔드 / tables API 수정

**현재 tables API 응답:**
```json
[
  {"tableName": "if_rsv_sec_obsvdata", "tableType": "SOURCE", "totalCount": 14093, ...},
  {"tableName": "pm_gd970201", "tableType": "TARGET", "totalCount": 14093, ...}
]
```

**변경:**
```json
[
  {
    "mappingName": "obsvdata",
    "sourceTables": ["if_rsv_sec_obsvdata"],
    "targetTables": ["pm_gd970201", "tm_gd980002", "tm_gd970101"],
    "readCount": 14093,
    "writeCount": 14093,
    "skipCount": 0,
    "failedCount": 0
  }
]
```

프론트엔드도 매핑 단위로 표시하도록 수정 필요.

## 수정 대상 파일 요약

| 영역 | 파일 | 변경 |
|------|------|------|
| YAML | `config/agents/*.yml` (전체 15개) | `table-mappings` 섹션 추가 |
| Entity | `SyncLog.java` | 컬럼 구조 변경 |
| Config | `AgentConfigLoader.java` (bojo, bojo-int) | table-mappings 파싱 |
| Model | 신규 `TableMapping.java` | 매핑 모델 클래스 |
| Step | `SourceToIfStep.java`, `DaejeonLoadStep.java`, `InternalLoadStep.java` | SyncLog 기록 방식 변경 |
| API | `ExecutionDataController.java` | source 필터링, tables API 수정 |
| Frontend | `executions/[id]/page.tsx` | 매핑 단위 표시 |

## 마이그레이션
- DB: sync_log 테이블 ALTER (컬럼 추가/삭제) 또는 새 테이블 생성
- 기존 SyncLog 데이터: 마이그레이션 스크립트 or 버리고 새로 실행
