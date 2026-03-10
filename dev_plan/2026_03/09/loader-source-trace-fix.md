# Loader/SND Source 추적 수정 + tm_gd970101 제거

## 배경

### 문제 1: Loader/SND에서 Source(IF) 데이터 조회 불가
- Loader의 source = IF 테이블 (if_rsv_sec_jewon, if_rsv_sec_obsvdata)
- IF 테이블의 `execution_id`는 **RCV가 기록한 값**
- Loader의 source 엔드포인트는 target(sec_jewon)의 `source_refs`에서 PK를 추출하여 source DB에서 조회
- 그런데 Loader의 source DB = IF 테이블이 있는 DB인데, 현재 로직은 `sourceDatasourceId`로 접근 → **IF 테이블이 target DB에 있으므로 datasource 불일치**
- 결과: source 데이터 0건 반환

### 문제 2: tm_gd970101이 Target으로 표시됨
- Internal Loader의 YAML table-mappings에서 obsvdata target에 `[pm_gd970201, tm_gd970101]` 포함
- InternalLoadStep의 SyncLog에서도 targetTables에 tm_gd970101 포함
- tm_gd970101은 link 테이블 성격 (최종관측시각 관리)이므로 Target이 아님

## 수정 계획

### 1. tm_gd970101 Target 제거

#### 수정 파일
- `sync-agent-bojo-int/src/main/resources/config/agents/internal-bojo-loader.yml`
  - table-mappings의 obsvdata target에서 `tm_gd970101` 제거
  - `[pm_gd970201, tm_gd970101]` → `[pm_gd970201]`

- `sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/loader/step/InternalLoadStep.java`
  - `saveSyncLogMapping()`에서 targetTables JSON 배열에서 `targetResultTable` 제거
  - `["pm_gd970201", "tm_gd970101"]` → `["pm_gd970201"]`

### 2. Loader/SND Source(IF) 추적 수정

#### 현재 로직 (ExecutionDataController `/source` 엔드포인트)
```
1. Execution에서 sourceDatasourceId 가져옴
2. getSourcePksFromIfTable()로 target 테이블의 source_refs에서 PK 추출
3. sourceDatasourceId의 DB에서 sourceTable WHERE id IN (추출된 PK)
```

#### 문제
- Loader: source=IF테이블(target DB에 있음), sourceDatasourceId=target DB
  - IF 테이블에는 execution_id가 RCV것이라 source_refs 추출까지는 OK
  - 하지만 IF 테이블의 PK(id)는 자동생성 ID이고, source_refs의 PK는 **외부 source의 PK**
  - 즉 source_refs에서 추출한 PK로 IF 테이블을 조회해도 의미 없음

#### 해결 방안: Loader source는 execution_id 기반으로 직접 조회
- IF 테이블(Loader의 source)은 target DB에 존재
- Loader가 읽은 IF 데이터를 식별하는 방법: **target 테이블에서 Loader execution_id로 기록된 레코드의 source_refs → IF 테이블의 source_refs와 매칭**
- 하지만 이건 너무 복잡. 더 간단한 접근:

**실제 접근**: IF 테이블에는 `link_status` 컬럼이 있고 Loader가 처리하면 `SUCCESS`로 업데이트함. 하지만 이것도 여러 Loader 실행에서 구분이 안됨.

**최종 접근**: source 뷰에서 IF 테이블이 sourceTable인 경우, execution_id 필터 없이 **target 테이블의 execution_id로 기록된 레코드의 source_refs 전체를 가져와서**, 그 source_refs 값을 가진 IF 레코드를 조회
→ 즉: `SELECT * FROM if_rsv_sec_jewon WHERE source_refs IN (SELECT source_refs FROM sec_jewon WHERE execution_id = ?)`

#### 수정 파일
- `sync-agent-common/src/main/java/com/sync/agent/common/controller/ExecutionDataController.java`
  - `/source` 엔드포인트에서 IF 테이블이 source인 경우 (테이블명이 `if_`로 시작) 별도 로직 적용:
    1. SyncLog에서 해당 source에 매핑된 target 테이블명 조회
    2. target 테이블에서 `execution_id = ?`인 레코드의 `source_refs` 목록 조회
    3. IF(source) 테이블에서 `source_refs IN (...)` 조회 (targetJdbc 사용)
  - sourceDatasourceId가 아닌 **targetDatasourceId**의 JdbcTemplate 사용 (IF는 target DB에 있으므로)

### 3. 영향 범위

| 구간 | 영향 |
|------|------|
| RCV (Source→IF) | 없음. source_refs 기반 추적은 기존과 동일 |
| Loader (IF→Target) | source 뷰 수정. target/trace는 기존과 동일 |
| SND (Target→IF_SND) | Loader와 동일 패턴, 같이 수정됨 |
| Internal RCV | SND와 동일 |
| Internal Loader | tm_gd970101 제거 + source 뷰 수정 |
| Frontend | 변경 없음 (이미 이전 형식 복원 완료) |

### 4. 빌드/테스트
- sync-agent-common 빌드 → JAR 4개 모듈 복사
- sync-agent-bojo-int 빌드 (YAML + InternalLoadStep 변경)
- Loader 실행 후 source 뷰에서 IF 데이터 조회 확인
- Internal Loader 실행 후 tm_gd970101이 테이블 현황에서 제거 확인
