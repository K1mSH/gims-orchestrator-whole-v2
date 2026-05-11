# KRDS 디자인 시스템 마이그레이션 Phase 2 — 나머지 페이지 마무리

작성일: 2026-05-11
선행: `dev_plan/2026_05/08/krds-frontend-migration.md` (Phase 1 — 셸/대시보드/datasources/agents-id 적용)
목표: **오늘 안에 KRDS 마이그레이션 100% 완료** + globals.css legacy compat alias 제거

---

## 1. 5/11 기준 잔여 인라인 스타일 (총 513개)

| 페이지 | 인라인 | 우선순위 |
|---|---:|:-:|
| `app/login/page.tsx` | 6 | 1 |
| `app/(main)/api-provide/page.tsx` | 9 | 2 |
| `app/(main)/users/page.tsx` | 12 | 3 |
| `app/(main)/users/me/page.tsx` | 14 | 3 |
| `app/(main)/api-provide/[id]/page.tsx` | 12 | 4 |
| `app/(main)/api-collect/[id]/page.tsx` | 14 | 5 |
| `app/(main)/executions/page.tsx` | 19 | 6 |
| `app/(main)/agents/page.tsx` | 58 | 7 |
| `app/(main)/api-provide/new/page.tsx` | 84 | 8 |
| `app/(main)/executions/[id]/page.tsx` | 100 | 9 |
| `app/(main)/api-collect/page.tsx` | 185 | 10 |

> `api-collect/page.tsx` (151→185) 와 `api-provide/new/page.tsx` (57→84) 는 5/8 이후 변경분으로 인라인이 늘었음. 본 사이클에서 흡수.

추가로 각 페이지 내부에서 사용 중인 tab 컴포넌트들도 같이 정리해야 함:
- `api-collect/InfoTab.tsx` (59) / `MappingTab.tsx` (101) / `ScheduleTab.tsx` / `HistoryTab.tsx`
- `api-provide/InfoTab.tsx` / `SpecTab.tsx` / `ParamsTab.tsx` / `ColumnsTab.tsx` / `TestTab.tsx` (38) / `HistoryTab.tsx`

---

## 2. 사용자 결정 사항 (5/11)

| 항목 | 결정 |
|---|---|
| KRDS `krds-input` 사이즈 | **일괄 `.small` 적용** — 관리 UI는 입력/테이블 많음, 기본 large(56px)는 부담 |
| `krds-step-wrap` 파이프라인 시각화 | **미도입** — 현 시각화 유지, KRDS 톤(컬러/폰트/간격)만 적용 |
| `krds-pagination` / `krds-modal` / `krds-info-list` 추가 활용 | **최소한** — 현재 구현 유지, 톤만. 모달도 KRDS 컴포넌트 강제 교체 X |
| 작업 순서 | **쉬운 것 먼저** (1→10) — 점진적 확인, 사용자 피드백 반영 용이 |

---

## 3. 작업 Phase 구성 (12 Phase)

각 Phase 종료 시점:
1. 빌드 확인 (`npx tsc --noEmit`)
2. 사용자 시각 검증 OK 후 다음 Phase 진입

### Phase 0 — 사전 정리 (선결)
- KRDS small 일괄 정책 위해 `globals.css` 에 `.krds-input.small` / `.krds-form-select.small` 디폴트 변수 정렬 확인
- 공용 CSS 누락분 확인 (`.app-section-title` 등 추가 필요한 헬퍼)

### Phase 1 — `/login` (인라인 6)
- KRDS `.krds-input.small` + `.krds-btn` 적용
- 로고/타이틀 톤 정리
- `login.module.css` 신규 (배경 그라데이션 / 카드 중앙정렬)

### Phase 2 — `/api-provide` 목록 (인라인 9)
- `.app-page-header` / `.app-table` / `.krds-badge` (활성/비활성) 적용
- 검색줄 small 적용

### Phase 3 — `/users` + `/users/me` (인라인 12+14)
- `.app-table` (목록) + `.app-card` + `.app-form-grid` (me 마이페이지)
- 권한 배지 KRDS 매핑

### Phase 4 — `/api-provide/[id]` 헤더/탭 (인라인 12)
- KRDS 탭 (`agents/[id]` 와 같은 패턴 — `.krds-tab-area > .tab.line`)
- `TabButton` 재사용 (이미 KRDS 패턴)
- 헤더 액션 버튼 정리

### Phase 5 — `/api-collect/[id]` 헤더/탭 (인라인 14)
- Phase 4 와 동일 패턴 — 탭/헤더 정리
- 자체 `TabButton` → `agents/[id]` 의 KRDS 패턴 재사용

### Phase 6 — `/executions` 목록 (인라인 19)
- `.app-table` + `.krds-badge` (status / trigger)
- 필터줄 small input/select 정리
- 페이지네이션 현재 구현 유지 (KRDS 톤만)

### Phase 7 — `/agents` 목록 (인라인 58)
- 대시보드와 같은 톤 (`.app-table` + Agent 타입 배지 `.krds-badge.bg-light-*` 매핑)
- Zone 컬럼 색상 토큰 정리

### Phase 8 — `/api-provide/new` (인라인 84)
- 폼 페이지 — `.app-form-grid` + `.krds-input.small` + 컬럼 추가/제거 UI
- 다단계 폼이면 KRDS Stepper 톤 검토 (구조 변경은 최소)

### Phase 9 — `/executions/[id]` (인라인 100)
- 파이프라인 시각화 **현 구조 유지** + KRDS 톤만
- 처리현황 표 `.app-table` 적용 (이미 일부 적용됨, 보강)
- 행 클릭 펼침 영역 톤 정리

### Phase 10 — `/api-collect` 목록 + 탭 컴포넌트 (인라인 185 + tab 인라인 합산)
- 가장 무거움. 목록 + InfoTab + MappingTab + ScheduleTab + HistoryTab 일괄 정리
- `MappingTab.tsx` 의 101개 인라인 동시 처리

### Phase 11 — `krds-input.small` 일괄 적용 (보류 결정 → 일괄)
- 모든 페이지의 input/select 에 `.small` 클래스 보강
- 전역 검색 + 추가

### Phase 12 — `globals.css` legacy compat alias 제거
- `.card` / `.card-header` / `.card-title` / `.empty-state` / `.loading` / `.page-header` / `.page-title`
- `.btn` / `.btn-primary` / `.btn-secondary` / `.btn-danger` / `.btn-sm`
- `.form-group` / `.form-label` / `.form-input` / `.form-select` / `.table-container` / `.status-badge` / `.trigger-badge`
- 242~425 줄 영역 (약 183 줄 삭감)
- `.zone-*` 는 도메인 토큰이라 유지

각 Phase 종료 시 빌드 + 시각 검증 OK 후 다음 진입.

---

## 4. 영향 범위 / 검토 사항

### 4.1 공용 CSS 추가 후보 (필요 시 globals.css 보강)
- `.app-section-title` — 카드 내부 섹션 타이틀
- `.app-search-bar` — 검색 input + 버튼 묶음
- `.app-pager` — 페이지네이션 wrapper (KRDS 컴포넌트 미도입이지만 톤 통일용)
- `.krds-input.small` / `.krds-form-select.small` 정렬 검증

### 4.2 회귀 방지
- **기능 변경 없음**. 디자인 톤(색상/간격/타이포)만 교체
- 모든 기존 props/state/handler 그대로
- 모달/팝업 동작 동일 (현재 구현 유지)

### 4.3 미정 / 보류
- `krds-pagination` 도입은 본 사이클 외 (관리 UI 페이지 다수 검토 필요)
- `krds-modal` 일괄 도입은 본 사이클 외
- 다크/하이콘트라스트 테마는 도입 X (라이트만)

---

## 5. 산출물 (예상)

### 5.1 새 module.css (페이지별)
- `login/login.module.css` (또는 page.module.css)
- `api-provide/api-provide.module.css` + `api-provide-detail.module.css` + `api-provide-new.module.css`
- `users/users.module.css` + `users-me.module.css`
- `api-collect/api-collect.module.css` + `api-collect-detail.module.css`
- `executions/executions.module.css` + `executions-detail.module.css`
- `agents/agents.module.css`

### 5.2 tab 컴포넌트 module.css (필요시)
- `components/api-collect/{Info,Mapping,Schedule,History}Tab.module.css`
- `components/api-provide/{Info,Spec,Params,Columns,Test,History}Tab.module.css`

### 5.3 globals.css 변경
- legacy compat alias 제거 (~183줄 삭감)
- `.app-section-title` / `.app-search-bar` / `.app-pager` 추가 (필요 시)
- `.krds-input.small` 디폴트 정책 명시 주석

---

## 6. 검증 절차

각 Phase 종료마다:
1. `cd infolink-orchestrator-frontend && npx tsc --noEmit` — 타입 OK
2. 사용자 화면 검증 — 브라우저로 해당 페이지 확인
3. 인라인 스타일 개수 카운트 (`grep -c "style="`) — 0 또는 명시적 사유 (예: 동적 width)
4. 사용자 OK → 다음 Phase

최종 (Phase 12 종료):
- 전체 인라인 스타일 합계 ~0
- legacy compat alias 0
- 모든 페이지 KRDS 톤 일관성 확인

---

## 7. 일정

- Phase 1~6 (쉬운 페이지 6개): 오전
- Phase 7~10 (무거운 페이지 + 탭): 오후
- Phase 11~12 (정리): 저녁

각 Phase 사용자 OK 시점에 commit 단위 묶기 검토.
