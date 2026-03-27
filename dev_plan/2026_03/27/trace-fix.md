# 추적 기능 버그 수정 계획

## 이슈 1: obsvdata source 0건 (대량 IN절 초과)

### 현상
- DMZ Loader/SND에서 obsvdata source 조회 시 0건
- jewon(4,006건)은 정상, obsvdata(151,095건)만 실패

### 원인
- `buildSourceFilter()`: source_refs 불일치 → PK 파싱 fallback
- 15만건 `WHERE id IN (?, ?, ...)` → PG 파라미터 제한 초과 → 예외 → catch에서 null

### 수정
- `ExecutionDataController.buildSourceFilter()` (398행 부근)
- IN절을 chunk 단위로 분할 (1,000건씩)
- 또는 대량일 때 서브쿼리 방식 전환: `WHERE id IN (SELECT ... FROM target WHERE execution_id = ?)`

### 대상 파일
- `sync-agent-common/.../controller/ExecutionDataController.java`

---

## 이슈 2: Internal RCV target/source 전부 0건

### 현상
- Internal RCV 실행 후 추적 API에서 target, source 모두 0건

### 원인 (2건)
1. **Oracle LIMIT 문법**: 344행 `SELECT COUNT(*) FROM ... LIMIT 1` → Oracle 미지원
   - Oracle: `FETCH FIRST 1 ROWS ONLY` 또는 `ROWNUM` 사용
2. **Internal Agent → DMZ DB 접근**: source=dmz(PG 29001)인데 Internal Agent(8092)에서 접근 가능 여부 확인 필요

### 수정
1. `buildSourceFilter()` 344행: Oracle 분기 추가 (LIMIT → FETCH FIRST)
2. `getSourceData()` 전체에서 `LIMIT` 사용하는 곳 모두 Oracle 분기
3. Internal Agent가 dmz datasource에 접근 가능한지 확인 → 불가능하면 Proxy 경유 로직 필요

### 대상 파일
- `sync-agent-common/.../controller/ExecutionDataController.java`

---

## 수정 순서
1. LIMIT → Oracle 호환 수정 (전체 파일 검색)
2. IN절 chunking 적용
3. 빌드 (common → JAR 복사 → bojo-int)
4. 테스트
