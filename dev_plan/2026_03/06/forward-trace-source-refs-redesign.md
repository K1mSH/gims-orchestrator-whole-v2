# Forward Trace 로직 source_refs 기반 재설계

## 수정 목적
Internal Loader(if_rsv_sec_obsvdata → pm_gd970201) forward trace 시 다른 source PK의 레코드가 섞여 5건 반환되는 문제 해결.
현재 로직이 테이블 **이름 매칭**에 의존하여 pm_gd970201 같은 비규칙 이름에서 실패.

## 수정 대상 파일
- `sync-agent-common/.../controller/ExecutionDataController.java`

## 변경 내용

### 1. `traceBySourcePk()` 메서드 본문 교체 (기존 line 635~836)

**기존 로직 (4단계, 이름 기반):**
1. sourceTable 이름에서 베이스 추출 → SyncLog에서 이름 매칭으로 target 테이블 찾기
2. IF prefix 제거 후 재시도 (Loader 대응)
3. 이름 매칭 실패 시 source_refs probe (LIMIT 5 + early return)
4. target 테이블 결정 후: PK 직접 조회 → source_refs LIKE → business key → agent-scoped fallback

**신규 로직 (3단계, source_refs 기반):**
- 테이블 이름 매칭 없이 SyncLog에서 TARGET/IF 타입 **전체** 목록을 가져와 순회

```
Step 1: source_refs에 PK가 임베딩된 경우
─────────────────────────────────────────
RCV, SND, Internal RCV, Internal Loader에서 target이 source_refs를 새로 생성하는 패턴.
모든 target 테이블 순회:
  Pattern A (정밀): WHERE source_refs LIKE '%:{sourceTable}:{pkValue}"]' AND execution_id = ?
  Pattern B (범용): WHERE source_refs LIKE '%:{pkValue}"]' AND execution_id = ?
  A 먼저 시도 → 없으면 B → 찾으면 break

Step 2: source_refs 값 일치 (Loader 복사 패턴)
─────────────────────────────────────────
Loader는 IF의 source_refs를 target에 그대로 복사.
  1. targetJdbc로 sourceTable에서 source record 읽기 (Loader는 source/target 같은 DB)
  2. source_refs 값 추출
  3. 각 target 테이블: WHERE source_refs = {동일값} AND execution_id = ?
     (sourceTable 자기 자신은 skip)

Step 3: 직접 PK fallback (최후 수단)
─────────────────────────────────────────
  WHERE {pkColumn} = {pkValue} AND execution_id = ?
  try-catch로 감싸서 컬럼 없으면 skip
```

### 2. `traceByBusinessKey()` 삭제 (기존 line 1181~1272)
- Step 2(source_refs 값 일치)로 대체됨

### 3. `extractAgentCode()` 삭제 (기존 line 1494~1498)
- agent-scoped fallback 제거로 불필요

## 삭제 대상 정리

| 삭제 | 이유 |
|------|------|
| sourceBase 추출 + 이름 contains 매칭 | Step 1에서 source_refs로 직접 검색 |
| IF prefix stripping (if_rsv_, if_snd_) | 불필요 |
| `LIMIT 5` probe + early return | 불필요 (LIMIT 없이 전체 반환) |
| `traceByBusinessKey()` | Step 2로 대체 |
| `extractAgentCode()` + agent-scoped fallback | 제거 |
| `fallbackMode`, `FOUND_IN_IF` 상태 | 제거 |

## 유지 대상 (reverse trace 등에서 사용)

| 유지 | 사용처 |
|------|--------|
| `parseSourceRefsPks()` | traceToSource에서 사용 |
| `parseSourceRefsTableName()` | traceToSource에서 사용 |
| `getUniqueKeyColumns()` | traceSourceBySndBusinessKey에서 사용 |
| `traceToSource()` (reverse trace 전체) | 수정 안함 |
| `findValueIgnoreCase()`, `typedValue()`, `qi()` 등 공통 헬퍼 | 여러 곳에서 사용 |

## 파이프라인별 매칭 경로

| 단계 | source → target | target source_refs 예시 | 매칭 Step |
|------|----------------|------------------------|-----------|
| RCV | sec_jewon_view → if_rsv_sec_jewon | `["E:3:12:677"]` | Step 1B |
| DMZ Loader | if_rsv_sec_jewon → sec_jewon | `["E:3:12:677"]` (복사) | Step 2 |
| SND | sec_jewon → if_snd_sec_jewon | `["D:1:34:123"]` | Step 1B |
| Internal RCV | if_snd → if_rsv (internal) | `["D:1018:52:124307"]` | Step 1B |
| Internal Loader | if_rsv → pm_gd970201 | `["I:internal:if_rsv_sec_obsvdata:41072"]` | Step 1A |

## 영향 범위
- forward trace API (`GET /{executionId}/trace`) 만 변경
- reverse trace (`trace-source`), 데이터 조회 API 등은 변경 없음

## 현재 상태
⚠️ **코드는 이미 수정 완료된 상태** (빌드 성공, JAR 4개 모듈 복사 완료)
- 확인 후 문제 있으면 git checkout으로 되돌릴 수 있음
