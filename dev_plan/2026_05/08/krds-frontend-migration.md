# KRDS 디자인 시스템 적용 계획 (Frontend 전면 교체)

작성일: 2026-05-08
대상: `infolink-orchestrator-frontend` (Next.js 14 App Router)
원본 키트: `D:\dev\claude\GIMS\krds-uiux\` (한국 정부 KRDS UI/UX 키트)

---

## 1. 목적

기존 자체 설계 CSS(`globals.css` 449줄 + 인라인 1,037건)를 KRDS 디자인 시스템 기반으로 전면 교체하여
- 정부 표준 디자인 일관성 확보
- 인라인 스타일 최소화 → 유지보수성 향상
- 토큰 기반 설계로 향후 테마 변경 용이성 확보

## 2. 적용 방향 (사용자 결정 사항)

| 항목 | 결정 |
|------|------|
| 적용 범위 | **전면 교체 (한 번에)**. 기존 `globals.css` 폐기, 모든 페이지 KRDS 기반으로 |
| 인라인 스타일 | **최소화**. 공용 CSS 우선, 페이지별 특수 스타일은 `page.module.css`로 분리 |
| KRDS JS | **React 컴포넌트로 재구현**. `ui-script.js`는 사용 안 함 |
| 테마 | **라이트 모드만**. `--krds-light-*` 토큰만 노출. 하이콘트라스트/다크 미도입 |

## 3. 현재 프론트 인벤토리

### 3.1 페이지 (12개)
- `app/login/page.tsx`
- `app/(main)/page.tsx` (대시보드)
- `app/(main)/agents/page.tsx` + `[id]/page.tsx`
- `app/(main)/api-collect/page.tsx` + `[id]/page.tsx`
- `app/(main)/api-provide/page.tsx` + `[id]/page.tsx` + `new/page.tsx`
- `app/(main)/datasources/page.tsx`
- `app/(main)/executions/page.tsx` + `[id]/page.tsx`
- `app/(main)/users/page.tsx` + `me/page.tsx`

### 3.2 컴포넌트 (22개)
- 공통: `AppShell` / `AppHeader` / `Sidebar` / `StatusBadge`
- agent: `InfoTab` / `MonitorTab` / `HistoryTab` / `TabButton`
- api-collect: `InfoTab` / `MappingTab` / `ScheduleTab` / `HistoryTab` / `TabButton`
- api-provide: `InfoTab` / `SpecTab` / `ParamsTab` / `ColumnsTab` / `TestTab` / `HistoryTab` / `TabButton`

### 3.3 인라인 스타일 분포 (Top 10 hotspot)
```
api-collect/page.tsx              151
agent/InfoTab.tsx                 108
api-collect/MappingTab.tsx        101
executions/[id]/page.tsx          100
agents/page.tsx                    58
api-collect/InfoTab.tsx            59
api-provide/new/page.tsx           57
datasources/page.tsx               63
api-provide/TestTab.tsx            38
api-provide/ParamsTab.tsx          37
```
총 1,037개 — 페이지별 평균 30~40개. 전면 정리 필요.

### 3.4 자체 정의 클래스 (`globals.css`에서 폐기 대상)
- 레이아웃: `.layout` / `.sidebar` / `.sidebar-nav` / `.main-content` / `.page-header` / `.page-title`
- 카드/통계: `.card` / `.card-header` / `.stats-grid` / `.stat-card` / `.stat-label` / `.stat-value`
- 표: `.table-container` / `table` / `th` / `td` 디폴트
- 버튼: `.btn` / `.btn-primary` / `.btn-secondary` / `.btn-danger` / `.btn-sm`
- 배지: `.status-badge.online/offline/error/running/cancelled` / `.trigger-badge` / `.agent-type-badge`
- 폼: `.form-group` / `.form-label` / `.form-input` / `.form-select`
- 기타: `.chain-flow` / `.chain-node` / `.spinner` / `.empty-state` / `.zone-*`

## 4. KRDS 키트 활용 자원

### 4.1 직접 도입할 자원
- `css/token/krds_tokens.css` — Light 테마 토큰만 사용 (790줄)
- `css/common/common.css` — 리셋, 타이포, 레이아웃 베이스 (1,417줄)
- `css/component/component.css` — 컴포넌트 스타일 (11,799줄)
- `fonts/PretendardGOV-*.woff2` — 정부 표준 한글 웹폰트
- `img/component/icon/*.svg` — 아이콘 88종 (필요분만 선별)

### 4.2 KRDS 컴포넌트 클래스 매핑 (현재 → KRDS)
| 현재 | KRDS | 비고 |
|------|------|------|
| `.btn-primary` | `.krds-btn.large` (또는 medium) | 사이즈 5단계 |
| `.btn-danger` | `.krds-btn.danger` | warning/danger 색상 토큰 |
| `.card` | `.krds-help-panel` 또는 자체 wrapper + 토큰 | KRDS에 일반 card는 명시 없음 |
| `.stat-card` | KRDS 컴포넌트 없음 → `page.module.css`로 자체 작성, 단 토큰만 사용 | |
| `.status-badge` | `.krds-badge` (8000~8200줄대) | semantic 색상 토큰 활용 |
| `.form-input` | `.krds-input` (9193줄~) | |
| `.form-select` | `.krds-form-select` (9491줄~) | |
| 사이드바 | `.krds-side-navigation` (5757줄~) | |
| 헤더 | `.krds-main-menu` (3856줄~) — 단순화 필요 | |
| 페이지네이션 | `.krds-pagination` (7271줄~) | |
| 탭 (TabButton) | `.krds-tab-area` (1140줄~) | |
| 표 | `.krds-table-wrap` (1398줄~) | |
| 모달 | `.krds-modal` (1621줄~) — React 재구현 필요 | |
| 아코디언 | `.krds-accordion` — React 재구현 필요 | |
| 토글 스위치 | `.krds-form-toggle-switch` | |
| 체크박스/라디오 | `.krds-form-check` | |
| 스피너 | `.krds-spinner` (2490줄~) | |
| 브레드크럼 | `.krds-breadcrumb-wrap` (7498줄~) | 신규 도입 가능 |

### 4.3 KRDS에 없어 자체 작성 필요
- 통계 카드 (대시보드의 stat-card) — 토큰만 차용해 `dashboard.module.css`
- 체인 플로우 다이어그램 (`.chain-flow`, `.chain-node`) — `chains.module.css`
- 일부 specialized badge (`.trigger-badge`, `.agent-type-badge`) — `badge` 모듈에서 자체 변형

## 5. 작업 단계

### Phase 0 — 사전 준비
1. `infolink-orchestrator-frontend/public/krds/` 디렉터리 생성, 키트 복사
   - `public/krds/css/token/krds_tokens.css`
   - `public/krds/css/common/common.css`
   - `public/krds/css/component/component.css`
   - `public/krds/fonts/PretendardGOV-*.woff2`
   - `public/krds/img/icon/*.svg` (필요분 선별)
2. `app/layout.tsx`에서 KRDS CSS import 순서 결정 (token → common → component → globals)
3. base font-size 확인: KRDS 토큰이 `rem` 단위라 `:root { font-size: 62.5% }` (1rem=10px) 베이스 필요한지 사전 확인

### Phase 1 — 토큰/베이스 교체
1. `globals.css` 슬림화 — KRDS 토큰을 alias하는 호환 레이어만 유지하거나 폐기
2. PretendardGOV 폰트 `@font-face` 등록
3. body / html 베이스 KRDS 룰로 전환

### Phase 2 — 공통 레이아웃 (Sidebar / AppHeader / AppShell)
1. `Sidebar.tsx` → `.krds-side-navigation` 구조로 마크업 교체
2. `AppHeader.tsx` → `.krds-main-menu` 구조로 (단, 우리 시스템 단순하므로 축약)
3. `AppShell.tsx` → KRDS layout 룰 차용
4. 인라인 스타일 제거 → 공용 CSS / 모듈 CSS로 이동

### Phase 3 — 공용 컴포넌트 (Button / Badge / Spinner / TabButton / StatusBadge)
1. 공용 React 컴포넌트 신규 생성: `components/ui/Button.tsx`, `Badge.tsx`, `Spinner.tsx`, `Tabs.tsx`, `Modal.tsx`, `Input.tsx`, `Select.tsx`
   - 모두 KRDS 클래스를 props로 매핑 (size/variant)
   - `.krds-btn.large.danger` 같은 조합을 `<Button size="large" variant="danger">`로
2. 각 도메인의 `TabButton.tsx` 3개 → 공용 `Tabs.tsx`로 통합
3. `StatusBadge.tsx` → `.krds-badge` 기반 재작성

### Phase 4 — 페이지 단위 적용 (Top-down, hotspot 우선)
순서: 인라인 많은 곳부터
1. `app/(main)/page.tsx` (대시보드) — stat-card는 `dashboard.module.css`
2. `app/(main)/agents/page.tsx` + `[id]/page.tsx` (`agent.module.css`)
3. `app/(main)/api-collect/*` (151개 인라인) (`api-collect.module.css`)
4. `app/(main)/api-provide/*` (`api-provide.module.css`)
5. `app/(main)/executions/*` (`executions.module.css`)
6. `app/(main)/datasources/page.tsx`
7. `app/(main)/users/*`
8. `app/login/page.tsx`

각 페이지 작업 패턴:
- 인라인 `style={{...}}` → 의미 단위 클래스로 추출
- 재사용 가능 패턴 → 공용 클래스 (`.app-page-header`, `.app-form-row` 등)
- 페이지 전용 → `page.module.css`

### Phase 5 — 검증
1. `npx tsc --noEmit` (타입체크)
2. `npm run dev` 기동 → 모든 페이지 시각 확인 (12개 페이지)
3. 골든패스: 로그인 → 대시보드 → 각 메뉴 → 상세 페이지
4. 인라인 스타일 잔존 카운트 확인 (`grep -r "style={{"`) — 목표 100건 이하

## 6. 영향 범위

### 6.1 변경 파일
- 추가: `infolink-orchestrator-frontend/public/krds/**` (정적 자산), `components/ui/**` (공용 컴포넌트), `app/**/*.module.css` (페이지 모듈)
- 수정: 모든 `*.tsx` (35개) — 클래스명 변경 + 인라인 제거
- 축소: `app/globals.css` (449줄 → ~50줄, KRDS alias만)

### 6.2 백엔드 영향
- **없음**. 순수 프론트 작업. API/스펙 변경 없음.

### 6.3 회귀 위험
- 라이트 모드만이라 다크/하이콘트라스트는 무관
- 인라인 스타일 정리하면서 의도치 않은 시각적 변화 가능성 있음 → Phase 5 시각 확인 필수
- KRDS의 `rem` 베이스가 우리 기존 px 기반과 충돌하면 폰트 사이즈 어긋날 가능성 — Phase 0에서 사전 확인

## 7. 산정 (러프)

| Phase | 예상 분량 | 비고 |
|-------|-----------|------|
| 0. 사전 준비 | 0.5d | 키트 복사 + base 확인 |
| 1. 토큰/베이스 | 0.5d | globals.css 정리 |
| 2. 공통 레이아웃 | 1.0d | Sidebar/Header/Shell |
| 3. 공용 컴포넌트 | 1.5d | UI primitive 7~8개 + Modal/Accordion React 재구현 |
| 4. 페이지 적용 | 4~5d | 페이지 12개 + 1,037개 인라인 정리 |
| 5. 검증 | 0.5d | 시각 + 타입체크 |
| **합계** | **8~9d** | |

## 8. 진행 전 확인 사항

1. **모달/아코디언 React 재구현**의 우선순위 — 현재 시스템에 모달이 얼마나 쓰이는지에 따라 Phase 3에 포함 vs Phase 4 페이지 작업하면서 발견되는 대로
2. **PretendardGOV 폰트 라이선스** — 정부 공개 폰트라 OFL 라이선스로 자유 사용 가능한지 사용자 확인
3. **체인 플로우 다이어그램** — 현재 자체 디자인인데, KRDS 톤에 맞춰 재디자인할지 vs 기능적 형태 유지할지
4. **`page.module.css` vs `globals.css`의 경계** — 한 페이지에서만 쓰는 건 모듈, 2개 이상이면 글로벌? 명확한 룰 합의

---

**다음 단계**: 사용자 승인 후 Phase 0부터 진행. Phase 별로 PR 단위 분리 vs 단일 큰 변경으로 갈지 추가 합의 필요.
