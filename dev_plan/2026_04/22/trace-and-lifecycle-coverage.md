# ExecutionDataController — IF 전제 의존 제거 + /target-if와 /target 통합

> 작성일: 2026-04-22
> 선행 원칙: `feedback_no_regression_organic.md` — 기존 기능 영향 없어야 함 (대전제)

---

## 1. 배경

provide Agent 도입으로 드러난 두 문제:
- **`/target-if`의 link_status 무조건 필터** → provide target에 link_status 없어서 에러
- **`/trace-source` 분기 3(else)의 단방향 contains 매칭** → provide 네이밍 방향이 반대라 매칭 실패

분석 결과:
- 내부망 target 테이블(bojo-int 포함)도 link_status 없음 → 기존 잠재 버그
- `/target-if`와 `/target`은 link_status 처리 방식만 다름 → 역할 중복
- `/trace-source`의 prefix 분기(1, 2)는 **실제 기능** (bojo/bojo-int 경로에서 사용 중)
- 분기 3(else)만 내부 매칭이 불완전

---

## 2. 수정 범위 (3개)

### 2.1 `/target-if` 엔드포인트 삭제 → `/target`으로 통합

`/target-if`는 link_status 무조건 필터 빼면 `/target`과 100% 동일 로직. `/target`이 이미 `hasLinkStatus` 체크로 컬럼 유무 대응하므로 통합 후 link_status 없는 테이블 자동 지원.

- 백엔드: `getTargetIfData()` 메서드(`/{id}/target-if` 매핑) 삭제
- 프론트: `getTargetIfData` API 래퍼 삭제, 호출처를 `getTargetData`로 교체

### 2.2 `/trace-source` 분기 3(else) 내부 매칭 개선

prefix 분기(1, 2)는 **그대로 유지**. else 분기만 개선:

```java
// 기존: 단방향 contains
.filter(t -> t.toLowerCase().contains(lowerTable))

// 개선:
//   1순위: target_tables 정확 매칭 → 해당 매핑의 source_tables 반환
//   2순위: 양방향 contains (bojo-int, provide 모두 대응)
//   3순위: source_refs 파싱 fallback (기존)
```

### 2.3 프론트 TARGET_IF 레거시 정리

죽은 코드 제거 (flatTableStats에서 TARGET_IF 설정 안 되므로 아래는 모두 도달 불가):
- `isIfTable` 변수
- `typeOrder.TARGET_IF`
- `TARGET_IF` 라벨 정의 (line 545, 647)
- `case 'TARGET_IF':` 분기 (line 149, fetchTableData)
- `default: getTargetIfData` fallback (line 157)

---

## 3. 변경 지점별 기존 Agent 영향

### `/trace-source` 분기 매트릭스

| 상황 | sourceTable | 타는 분기 | 기존 동작 | 수정 후 |
|------|------------|:--------:|----------|---------|
| bojo Loader, SOURCE 행 클릭 | if_rsv_sec_jewon | 분기 1 | 정상 | **동일** |
| bojo Loader, TARGET 행 클릭 | if_snd_sec_jewon | 분기 1 + isSndRelay | 정상 | **동일** |
| bojo-int, SOURCE 행 클릭 | if_rsv_sec_jewon | 분기 1 | 정상 | **동일** |
| bojo-int, TARGET 행 클릭 | sec_jewon | 분기 3 | 정상 (2순위 contains) | **동일 결과** (1순위 target_tables 매칭 먼저 타거나 2순위에서 동일) |
| **provide, SOURCE 행 클릭** | TM_GD000203 | 분기 3 | **매칭 실패** | **정상화** (1순위) |
| **provide, TARGET 행 클릭** | api_prv_tm_gd000203 | 분기 3 | **매칭 실패** | **정상화** (1순위) |

### `/target-if` → `/target` 통합

| Agent | 영향 |
|-------|------|
| bojo | 정방향 추적에서 `/target` 호출. IF_SND 테이블은 link_status 있어 hasLinkStatus 체크 타고 **동일 결과** |
| bojo-int | 정방향 추적에서 `/target` 호출. SEC_JEWON 등 실테이블 link_status 없어도 정상 (hasLinkStatus 체크) — **잠재 에러 복구** |
| provide | 정방향 추적에서 `/target` 호출 → 정상화 |

**모든 기존 Agent의 동작이 같거나 개선**. 리그레션 없음.

---

## 4. 구현 순서

1. **백엔드**: `ExecutionDataController`
   - `getTargetIfData` 메서드 삭제
   - `traceToSource` 분기 3(else) 내부 매칭 확장

2. **공통 JAR 빌드 + 배포** (Proxy, Agent 전체)

3. **Proxy Internal 재기동**

4. **프론트**
   - `lib/api.ts`: `getTargetIfData` 래퍼 삭제
   - `executions/[id]/page.tsx`:
     - 정방향 추적 line 230: `getTargetIfData` → `getTargetData`
     - `case 'TARGET_IF':` / `default: getTargetIfData` 분기 제거
     - `TARGET_IF` 관련 변수/라벨/typeOrder 제거 (죽은 코드 정리)

5. **E2E 회귀 검증**
   - provide 실행: SOURCE 행 클릭 (정방향) + TARGET 행 클릭 (역방향)
   - bojo-int 실행이 있으면 동일 추적 기능 회귀 확인
   - bojo 실행이 있으면 IF_RSV / IF_SND 탭 정상 동작 확인

6. 문제 없으면 **오늘 전체 작업(헤더 라우팅 + 이번 정리) 묶어서 커밋**

---

## 5. 명시적으로 빼는 범위 (별도 작업)

- `/trace-source`의 prefix 분기(1, 2) 자체를 SyncLog 메타데이터 기반으로 재작성 — 회귀 리스크 크고 현재 동작함. 별도 리팩토링 이슈로.
- Agent의 `IF` 개념 자체에 대한 타입 시스템 도입 (AgentTable.TableType에 IF 추가 등) — 현재 필요성 낮음.

---

## 6. 참고 파일

| 파일 | 역할 |
|------|------|
| `sync-agent-common/.../controller/ExecutionDataController.java` | /target-if 삭제 + /trace-source 분기 3 수정 |
| `sync-orchestrator/frontend/lib/api.ts` | getTargetIfData 래퍼 삭제 |
| `sync-orchestrator/frontend/app/executions/[id]/page.tsx` | 호출처 교체 + TARGET_IF 레거시 정리 |
| `feedback_no_regression_organic.md` | 기존 기능 영향 없어야 대전제 |
