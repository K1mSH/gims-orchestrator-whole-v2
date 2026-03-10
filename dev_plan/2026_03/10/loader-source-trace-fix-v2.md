# Loader/SND Source 추적 수정 + tm_gd970101 제거 + 인덱스 보강

## 핵심 원리

모든 Agent에서 동일한 구조:
- **execution_id는 항상 target에만 존재** (source에는 없음 — 외부DB든 IF든 동일)
- source 데이터 조회 흐름: `target WHERE execution_id = ? → source_refs 추출 → source 조회`
- **SyncLog의 sourceTables/targetTables로 매핑 관계를 파악**, source 테이블의 컬럼 구조로 조회 방식 결정

---

## 수정 1: tm_gd970101 Target 제거

| 파일 | 변경 |
|------|------|
| `internal-bojo-loader.yml` | `target: [pm_gd970201, tm_gd970101]` → `target: [pm_gd970201]` |
| `InternalLoadStep.java` | SyncLog targetTables에서 `targetResultTable` 제거 |

---

## 수정 2: `/source` 엔드포인트 — SyncLog 매핑 + 컬럼 기반 분기

### 현재
```
target(execution_id=?) → source_refs → PK 파싱
source WHERE id IN (PK목록)
```
- 외부 source: PK = source의 id → OK
- IF source: PK = 이전 단계 외부 PK ≠ IF의 id → 실패

### 수정
```
1. SyncLog에서 sourceTable에 매핑된 targetTable 조회
2. targetTable WHERE execution_id = ? → source_refs 수집
3. source 테이블에 source_refs 컬럼이 있는가? (hasColumn 체크)
   - 있음 → source WHERE source_refs IN (수집한 source_refs 원본값)
   - 없음 → source WHERE id IN (source_refs에서 파싱한 PK)
```

### 왜 동작하는가
| Agent | source 테이블 | source_refs 컬럼 | 조회 방식 |
|-------|--------------|-----------------|-----------|
| DMZ RCV | sec_jewon_view (외부) | ❌ 없음 | `id IN (PK)` |
| DMZ Loader | if_rsv_sec_jewon | ✅ 있음 | `source_refs IN (값)` |
| DMZ SND | sec_jewon | ✅ 있음 | `source_refs IN (값)` |
| Internal RCV | if_snd_sec_jewon | ✅ 있음 | `source_refs IN (값)` |
| Internal Loader | if_rsv_sec_obsvdata | ✅ 있음 | `source_refs IN (값)` |

- 이름 분기 없음 — 런타임 컬럼 존재 여부로 판단
- 처음부터 맞는 방식으로 조회 (재시도 없음)
- SyncLog 매핑 정보 정당 활용

---

## 수정 3: `/trace` 정방향 — Step 2 datasource 수정

### 현재 문제
- Step 2 (Loader 복사 패턴): source 테이블을 `targetJdbc`로 읽음
- IF와 target이 다른 DB인 경우 (Internal Loader) 실패

### 수정
- Step 2에서 source 테이블 조회 시 Execution.sourceDatasourceId 기반 `sourceJdbc` 사용

---

## 수정 4: execution_id 인덱스 추가 (6개 엔티티)

### 배경
- 모든 추적/조회 쿼리가 `WHERE execution_id = ?`로 1차 필터링
- 관측데이터(obsvdata)는 매일 누적 → 인덱스 없으면 Full Table Scan

### 수정: @Table에 @Index 추가
| 엔티티 | 인덱스명 |
|--------|---------|
| `IfRsvSecJewon.java` | `idx_if_rsv_sec_jewon_exec_id` |
| `IfRsvSecObsvdata.java` | `idx_if_rsv_sec_obsvdata_exec_id` |
| `IfSndSecJewon.java` | `idx_if_snd_sec_jewon_exec_id` |
| `IfSndSecObsvdata.java` | `idx_if_snd_sec_obsvdata_exec_id` |
| `SecJewon.java` | `idx_sec_jewon_exec_id` |
| `SecObsvdata.java` | `idx_sec_obsvdata_exec_id` |

- `ddl-auto: update` → 앱 기동 시 자동 생성

---

## 수정 파일 정리

| 파일 | 변경 | 모듈 |
|------|------|------|
| `ExecutionDataController.java` | `/source` SyncLog+hasColumn 분기, `/trace` Step2 sourceJdbc | common |
| `IfRsvSecJewon.java` | `@Index` execution_id | bojo |
| `IfRsvSecObsvdata.java` | `@Index` execution_id | bojo |
| `IfSndSecJewon.java` | `@Index` execution_id | bojo |
| `IfSndSecObsvdata.java` | `@Index` execution_id | bojo |
| `SecJewon.java` | `@Index` execution_id | bojo |
| `SecObsvdata.java` | `@Index` execution_id | bojo |
| `internal-bojo-loader.yml` | obsvdata target에서 tm_gd970101 제거 | bojo-int |
| `InternalLoadStep.java` | SyncLog targetTables에서 제거 | bojo-int |

---

## 빌드/테스트

1. sync-agent-common 빌드 → JAR 4개 모듈 복사
2. sync-agent-bojo 빌드 (엔티티 인덱스)
3. sync-agent-bojo-int 빌드 (YAML + InternalLoadStep)
4. 앱 기동 → 인덱스 자동 생성 확인
5. 검증: DMZ RCV(회귀), DMZ Loader(IF source 표시), Internal Loader(IF source + tm_gd970101 미표시)
