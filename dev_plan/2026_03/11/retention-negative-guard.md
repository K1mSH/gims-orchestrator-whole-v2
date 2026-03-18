# retentionDays 음수 방어 + 프론트 UI 테스트

## 목적
- retentionDays에 음수/0 입력 시 `minusDays(음수)` → 미래 날짜 → **현재 데이터 전체 삭제** 버그 방어
- Schedule/Retention 프론트 UI 동작 확인

---

## 1. retentionDays 음수 방어

### 영향 범위 (4개 계층)

| 계층 | 파일 | 수정 내용 |
|------|------|----------|
| 프론트엔드 | `InfoTab.tsx` (Line 910) | `<input>` min=1 속성 + onChange에서 1 미만 방어 |
| Orchestrator API | `AgentService.java` (Line 550) | `updateRetentionConfig()` 저장 전 검증 (1 이상) |
| Agent 공통 설정 | `DataRetentionController.java` (Line 95) | `parseFromBody()` 파싱 시 1 미만이면 예외 |
| Agent 삭제 로직 | `DataRetentionService.java` (Line 41) | `executeCleanup()` 실행 전 최종 방어 |

### 수정 상세

#### 1-1. 프론트엔드 — `InfoTab.tsx`
```tsx
// 기존
<input type="number" value={t.retentionDays} onChange={...} />

// 수정: min 속성 + onChange 방어
<input type="number" min={1} value={t.retentionDays}
  onChange={(e) => {
    const v = parseInt(e.target.value) || 1;
    handleRetentionTargetChange(i, 'retentionDays', Math.max(1, v));
  }} />
```

#### 1-2. Orchestrator — `AgentService.java`
```java
// updateRetentionConfig() 내 JSON 파싱 후 검증
// retentionDays < 1 이면 IllegalArgumentException throw
```

#### 1-3. Agent 공통 — `DataRetentionController.java`
```java
// parseFromBody() 내
int days = ((Number) t.get("retentionDays")).intValue();
if (days < 1) throw new IllegalArgumentException("retentionDays must be >= 1");
```

#### 1-4. Agent 공통 — `DataRetentionService.java`
```java
// executeCleanup() 내 최종 방어
if (target.getRetentionDays() < 1) {
    log.warn("Invalid retentionDays: {}, skipping table: {}", ...);
    continue; // skip
}
```

---

## 2. 프론트 UI 테스트 (Schedule/Retention)

### 테스트 항목

| # | 섹션 | 테스트 | 확인 사항 |
|---|------|--------|----------|
| 1 | Schedule | 스케줄 추가 | cron 입력 + 활성화 체크 + 저장 |
| 2 | Schedule | 한글 파싱 | cron → 한글 설명 정상 표시 |
| 3 | Schedule | 토글 | 활성화 ↔ 비활성화 전환 |
| 4 | Schedule | 수정 | cron 변경 후 저장 |
| 5 | Schedule | 삭제 | 스케줄 삭제 |
| 6 | Retention | 설정 조회 | 기존 설정 정상 로드 |
| 7 | Retention | 테이블 추가 | + 버튼 → 테이블/컬럼/일수 입력 |
| 8 | Retention | 음수 방어 | 음수/0 입력 시 1로 보정 확인 |
| 9 | Retention | 저장 | 설정 저장 + 재조회 확인 |
| 10 | Retention | 년/일 표시 | 365 → "1년", 30 → "30일" 표시 |

→ 이 테스트는 코드 수정 완료 후 앱 기동해서 브라우저로 확인

---

## 빌드 순서
1. `sync-agent-common` → `./gradlew clean build -x test` → JAR 복사
2. `sync-orchestrator/backend` → `./gradlew clean build -x test`
3. `sync-orchestrator/frontend` → `npx tsc --noEmit` (타입체크)
