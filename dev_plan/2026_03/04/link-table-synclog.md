# Link 테이블 SyncLog 기록 + 처리 현황 표시

## 목적
Link 테이블(`link_ngwis`, `tm_gd980002`)의 처리 결과를 실행 상세 페이지의 테이블별 처리 현황에 "LINK" 타입으로 표시.
드릴다운(데이터 조회)은 불필요, 건수만 보여줌.

## 수정 파일 및 내용

### 1. LinkTableUpdateStep.java (sync-agent-bojo)
- `SyncLogRepository` 의존성 추가 (생성자 파라미터)
- `saveSyncLogSummary()` 메서드 추가
- execute() 완료 후 SyncLog 저장: `tableName=linkTable`, `tableType="LINK"`, `successCount=updateCount`

### 2. RcvPipelineConfig.java (sync-agent-bojo)
- LinkTableUpdateStep 생성자에 `syncLogRepository` 전달 (이미 필드로 보유)

### 3. InternalLoadStep.java (sync-agent-bojo-int)
- Phase 3 link UPSERT(라인 252) 후 `saveSyncLogSummary()` 호출 추가
- `tableName=targetLinkTable`, `tableType="LINK"`, `successCount=linkUpdated`

### 4. Frontend - types/index.ts
- `TableStats.tableType`에 `'LINK'` 추가
```ts
tableType: 'SOURCE' | 'TARGET_IF' | 'TARGET' | 'LINK';
```

### 5. Frontend - executions/[id]/page.tsx
- `typeOrder`에 `LINK: 3` 추가 (TARGET 다음에 위치)
- `typeConfig`에 LINK 스타일 추가 (보라색 계열)
- LINK 타입 행 클릭 비활성화 (cursor: default, 선택 안 됨, 건수만 표시)

## 예상 결과
```
[SOURCE] if_rsv_sec_obsvdata  — 9,940건    ← 클릭 가능
[TARGET] pm_gd970201          — 9,940건    ← 클릭 가능
[LINK]   tm_gd980002          — 402건      ← 건수만 표시, 클릭 불가
```

## 빌드 순서
1. sync-agent-common → bojo, bojo-int, proxy-dmz, proxy-internal libs/ 복사
   (이번엔 common 수정 없으므로 생략 가능)
2. sync-agent-bojo 빌드
3. sync-agent-bojo-int 빌드
4. frontend 타입체크 (`npx tsc --noEmit`)
