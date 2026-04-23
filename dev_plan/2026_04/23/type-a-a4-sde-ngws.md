# Type A4 이식 — SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033

> 작성일: 2026-04-23 오후
> 선행: `dev_plan/2026_04/22/provide-source-table-strategy.md`,
>       `dev_plan/2026_04/23/provide-step-integration.md` (오늘 오후 Step 통합 결과)
> 목적: Type A 마지막 남은 한 건(A4, 가뭄119 인허가관정) 이식 + SourceToTargetStep cross-schema 지원 복원

---

## 1. 수정 목적

### 1.1 이식 대상
- API: OPN `selectdroght119` (가뭄119 인허가관정)
- 소스: **`SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033`** (Oracle, SDE 공간 스키마)
- 타겟: `api_prv_wt_dream_permwell_public_21033` (PG 제공 테이블)
- 표준화 매핑 **없음** → 이전 이름 그대로 사용 (전략문서 "원칙 2 — 실운영 스키마 구조 재현")

### 1.2 기술 부채 해소 — SourceToTargetStep cross-schema 지원 복원

**문제**: 오늘 오후 Step 통합(`ProvideLoadStep` 폐기) 과정에서 기존 `tableName.contains(".")` 분기가 사라짐. `SourceToTargetStep`과 유틸(`JdbcTableNameResolver`, `detectSourcePrimaryKey`, `fetchColumnsFromMetadata`) 전부 `schemaPattern=null` 로 호출 → k1m 접속 시 SDE_NGWS 스키마 테이블 조회 불가.

**해결**: `sourceTable` 이 `"SCHEMA.TABLE"` 형식이면 분리해서 `schema` 파라미터를 지정. 없으면 기존 로직 그대로.

---

## 2. 선행 사실 확인

### 2.1 Oracle 스키마 상태
```
SELECT username FROM dba_users WHERE username IN ('SDE_NGWS', 'DBLINKUSR', 'K1M');
→ DBLINKUSR / K1M / SDE_NGWS  ✓ 어제 실행됨
```

### 2.2 레거시 쿼리 구조 (선행 문서 기준)
`WT_DREAM_PERMWELL_PUBLIC` (34컬럼) 와 동일 스키마 + **`OBJECTID`** 추가(= 35컬럼). SDE 관례상 `OBJECTID`가 PK.

### 2.3 엔티티 ApiPrvTmGd112002 재사용 여부 판단
- 이전 계획서(`provide-source-table-strategy.md`)에 "동일 구조 + OBJECTID 추가, ApiPrvTmGd112002 로 공용 가능" 언급 있음
- 하지만 다음 이유로 **별도 엔티티 신설 권장**:
  - 이전 이름 원칙 (표준화 매핑 없음) — `api_prv_wt_dream_permwell_public_21033`
  - source_refs 네임스페이스 분리 (다른 tableId)
  - OBJECTID 컬럼 추가 반영
  - 가뭄119 API 전용으로 향후 분리 운영 가능성

---

## 3. 수정 범위

### 3.1 common — cross-schema 지원 복원

#### 3.1.1 `JdbcTableNameResolver.java` — schema 파싱 추가

```java
public static String resolve(DataSource dataSource, String datasourceId, String logicalName) {
    String cacheKey = datasourceId + ":" + logicalName;
    return tableNameCache.computeIfAbsent(cacheKey, key -> {
        String schema = null;
        String tableName = logicalName;
        if (logicalName.contains(".")) {
            String[] parts = logicalName.split("\\.", 2);
            schema = parts[0];
            tableName = parts[1];
        }
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String[] variants = {tableName, tableName.toLowerCase(), tableName.toUpperCase()};
            String[] schemaVariants = schema != null
                    ? new String[]{schema, schema.toLowerCase(), schema.toUpperCase()}
                    : new String[]{null};

            for (String schemaVar : schemaVariants) {
                for (String tableVar : variants) {
                    try (ResultSet rs = metaData.getTables(catalog, schemaVar, tableVar,
                                                            new String[]{"TABLE","VIEW"})) {
                        if (rs.next()) {
                            String actualName = rs.getString("TABLE_NAME");
                            String actualSchema = rs.getString("TABLE_SCHEM");
                            String result = schema != null
                                    ? (actualSchema != null ? actualSchema : schema) + "." + actualName
                                    : actualName;
                            log.info("Resolved table name: '{}' -> '{}'", logicalName, result);
                            return result;
                        }
                    }
                }
            }
            log.warn("Table not found in metadata: '{}'. Using as-is.", logicalName);
        } catch (Exception e) {
            log.error("Failed to resolve table name '{}': {}", logicalName, e.getMessage());
        }
        return logicalName;
    });
}
```

무인자 버전도 동일 수정.

#### 3.1.2 `SourceToTargetStep.fetchColumnsFromMetadata` — schema 지원

기존: `metaData.getColumns(catalog, null, variant, null)`
수정: schema 분리해서 `metaData.getColumns(catalog, schemaPattern, tableName, null)` 형태로 호출.

#### 3.1.3 `SourceToTargetStep.detectSourcePrimaryKey` — schema 지원

기존: `metaData.getPrimaryKeys(catalog, null, variant)`
수정: schema 분리해서 호출.

#### 3.1.4 공통 헬퍼 추출

동일 파싱 로직이 4곳에서 필요하므로 `JdbcTableNameResolver` 에 static util 메서드 추가:

```java
public static class TableRef {
    public final String schema; // nullable
    public final String table;
    public TableRef(String schema, String table) { ... }
}
public static TableRef parse(String logicalName) {
    if (logicalName == null) return new TableRef(null, logicalName);
    if (logicalName.contains(".")) {
        String[] parts = logicalName.split("\\.", 2);
        return new TableRef(parts[0], parts[1]);
    }
    return new TableRef(null, logicalName);
}
```

### 3.2 Oracle DDL (신규)

- `scripts/ddl/internal-oracle/provide-source/WT_DREAM_PERMWELL_PUBLIC_21033.sql`
- 스키마: `SDE_NGWS`
- 컬럼: 112002 구조(34컬럼) + **`OBJECTID NUMBER NOT NULL` PK** + 추적컬럼 5종
- 실행 주체: `SDE_NGWS` 유저 (DDL은 해당 스키마 소유자가 실행)
- 완료 후 k1m 에 권한 부여:
  ```sql
  GRANT SELECT, INSERT, UPDATE, DELETE ON SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 TO K1M;
  ```
- 샘플 데이터 3~5건 (가뭄119 관련 지역 대충 — 제주, 포항, 안동 등)

### 3.3 provide 엔티티 신설

- `ApiPrvWtDreamPermwellPublic21033.java`
- PG 테이블: `api_prv_wt_dream_permwell_public_21033`
- 구조 (오늘 확립한 통일 패턴):
  - `@Id sn IDENTITY`
  - `@UniqueConstraint(columnNames = {"source_refs"})`
  - 자연키(`objectid`)는 일반 컬럼
  - 34개 비즈니스 컬럼 (112002와 동일 구조 복사)
  - 추적 컬럼 (execution_id, source_refs, updated_at)
- 테이블·컬럼 `@Comment` 한글

### 3.4 provide YAML 신설

- `sync-agent-provide/config/agents/provide-wt-dream-permwell-public-21033.yml`

```yaml
agent-code: provide-wt-dream-permwell-public-21033
type: LOADER

steps:
  - id: provide-wt-dream-permwell-public-21033
    name: 가뭄119 인허가관정 복사
    factory-key: source-to-if
    source-table: SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033
    target-table: api_prv_wt_dream_permwell_public_21033
    primary-key: OBJECTID
    conflict-key: source_refs
    target-meta-columns:
      - source_refs
      - execution_id
      - updated_at
```

### 3.5 Orchestrator Agent 등록

기존 A 파일럿과 동일 규격으로 POST /api/agents:
- agentCode: `provide-wt-dream-permwell-public-21033`
- endpointUrl: `http://localhost:8096`
- zone: `INTERNAL_COMMON`
- agentType: `LOADER`
- sourceDatasourceId: `internal`
- targetDatasourceId: `api-provider`
- sourceTableNames: `["SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033"]` (단, datasource_table에 이 이름이 등록돼 있지 않으면 실패 가능 — 확인 필요)

---

## 4. 수정 파일 전체

### 4.1 코드 수정 (common 재배포 필요)
| 파일 | 내용 |
|------|------|
| `sync-agent-common/.../config/JdbcTableNameResolver.java` | schema 파싱 지원 + `TableRef.parse()` 유틸 |
| `sync-agent-common/.../step/SourceToTargetStep.java` | `fetchColumnsFromMetadata`, `detectSourcePrimaryKey` 에 schema 전달 |

### 4.2 DDL 신규
| 파일 | 내용 |
|------|------|
| `scripts/ddl/internal-oracle/provide-source/WT_DREAM_PERMWELL_PUBLIC_21033.sql` | SDE_NGWS 스키마에 테이블 + PK_OBJECTID + 추적컬럼 + 주석 + 샘플 + k1m 권한 |

### 4.3 provide 엔티티·YAML 신규
| 파일 | 내용 |
|------|------|
| `sync-agent-provide/.../entity/target/ApiPrvWtDreamPermwellPublic21033.java` | @Id sn IDENTITY + source_refs UK + 34+1컬럼 + @Comment |
| `sync-agent-provide/config/agents/provide-wt-dream-permwell-public-21033.yml` | source-to-if, SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 |

### 4.4 영향 없음 (동작 불변)
- 기존 20개 YAML: 모두 `source-table`에 `.` 없으므로 `TableRef.parse()` → schema=null → 기존 동작 동일
- 기존 Agent 실행 로직: 변경 없음

---

## 5. Phase 순서

| Phase | 내용 | 완료 조건 |
|:---:|------|---------|
| 1 | `JdbcTableNameResolver.TableRef.parse` + `resolve()` schema 지원 | 빌드 성공 |
| 2 | `SourceToTargetStep.fetchColumnsFromMetadata`, `detectSourcePrimaryKey` schema 전달 | 빌드 성공 |
| 3 | common JAR 재빌드 + 6개 모듈 배포 | 배포 완료 |
| 4 | **회귀 테스트**: 기존 4개 provide Agent (000203/130001/112002/120001) 재실행 | 전부 SUCCESS + trace-source FOUND |
| 5 | Oracle DDL 작성 + SDE_NGWS 유저로 실행 + k1m 권한 | 샘플 3~5건 PENDING 확인 |
| 6 | 엔티티 신설 + provide 재빌드 + 재기동 | PG `api_prv_wt_dream_permwell_public_21033` 생성 확인 |
| 7 | YAML 등록 (provide 재시작 시 자동 로드) | 6 → 7개 Agent 설정 로드 |
| 8 | Orchestrator 에 Agent 등록 + health-check + 실행 | RUNNING → SUCCESS |
| 9 | trace-source 검증 (OBJECTID 기반 조회) | FOUND |
| 10 | dev_logs 업데이트 | 오늘 일지에 추가 |

---

## 6. 회귀 체크리스트

### 6.1 common 코드 변경 무손실 확인
- [ ] 기존 20개 YAML (source-table에 "." 없음) → `TableRef.parse()` 결과 schema=null → 기존 동작과 동일 경로
- [ ] `JdbcTableNameResolver.resolve()` 캐시 키에 schema 반영 (캐시 충돌 방지)

### 6.2 기존 4개 provide Agent (Phase 4)
- [ ] provide-tm-gd000203 read=5 write=5, trace-source OK
- [ ] provide-tm-gd130001 read=6 write=6, trace-source OK
- [ ] provide-tm-gd112002 read=8 write=6, trace-source OK
- [ ] provide-tm-gd120001 read=4 write=4, trace-source OK

### 6.3 신규 A4 (Phase 8~9)
- [ ] PK 탐지: detectSourcePrimaryKey 반환 = `[OBJECTID]`
- [ ] source_refs 포맷: `IC:1019:{tbId}:{objectid}`
- [ ] target 적재 건수 = source PENDING 건수
- [ ] Oracle LINK_STATUS → SUCCESS
- [ ] /trace-source 정상 (OBJECTID로 역조회)

---

## 7. 리스크 / 롤백

| 리스크 | 완화 |
|--------|------|
| common 재수정 → 기존 Agent 회귀 | Phase 4에서 즉시 확인, 문제 시 직전 JAR 복원 |
| SDE_NGWS 유저에게 DDL 권한 범위 불확실 | 스크립트 상단에 CONNECT/RESOURCE 권한 요구 기술, 없으면 system 계정으로 GRANT 후 재실행 |
| OBJECTID 타입 충돌 (NUMBER vs BIGINT in PG) | provide 엔티티 `Long` 사용, DDL NUMBER(10) — 호환 |
| 테이블명 resolve 캐시에 이전 결과 남아있어 schema 전 실패했던 것 유지 | 기동 시 캐시 초기화 or 재시작으로 자연 리셋 (재시작할 예정) |

### 롤백
- common JAR 이전 버전으로 복원 (오늘 여러 번 해본 절차)
- provide 엔티티/YAML 삭제 (git checkout)
- PG `api_prv_wt_dream_permwell_public_21033` drop
- Oracle SDE_NGWS 테이블은 그대로 두어도 다른 영향 없음

---

## 8. 사용자 확인 포인트

1. **엔티티 신설 vs 112002 재사용** — 제 권장: **신설** (이전 이름 원칙, 네임스페이스 분리) >> a4가 112002 이 테이블을 써?
2. **DDL을 내가 생성 후 `SDE_NGWS` 유저로 실행** OK? (sqlplus `sde_ngws/1111@...`) >>3번과 동일 답변
3. **k1m GRANT 은 system 계정으로 실행** OK? (또는 SDE_NGWS 유저 권한으로 GRANT 가능한지 확인) >> 같은 계정으로 진행해줘
4. **A7(TMP_MEGOKR_API)는 계속 보류** — 동의 여부 >> 저거 왜 보류 했었는지 기억하는지?

승인 주시면 Phase 1부터 착수합니다.
