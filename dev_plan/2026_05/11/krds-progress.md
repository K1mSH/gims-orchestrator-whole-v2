# KRDS 마이그레이션 전체 진행 상황 — **완료 (2026-05-11)**

> 갱신: 2026-05-11 (전 12 Phase 완료)
> 계획서: `dev_plan/2026_05/08/krds-frontend-migration.md` (Phase 1) + `dev_plan/2026_05/11/krds-frontend-migration-phase2.md` (Phase 2)
> dev_log: `dev_logs/2026_05/2026-05-11.md`

## 전체 페이지 현황 (5/11 도메인 묶음 순서로 재정렬)

| # | 페이지 / 컴포넌트 | 작업일 | 인라인 | 상태 |
|:-:|---|:-:|:-:|:-:|
| — | 셸: `Sidebar` / `AppHeader` / `AppShell` / `StatusBadge` | 5/8 → 5/11 | — | ✅ 완료 (5/11 셸 구조 변경 — 브랜드 헤더로 이동) |
| — | 공용 globals.css (app-layout / app-card / app-table / app-form / app-btn-row / app-alert / krds-btn.app-btn-danger) | 5/8 | — | ✅ |
| 1 | `/login` | 5/11 | 6 → **0** | ✅ |
| 2 | `/` 대시보드 | 5/8 | 0 | ✅ |
| 3 | `/datasources` (메인 + Form + 모달 + 컬럼재수집) | 5/8 | 0 | ✅ |
| 4 | `/agents/[id]` (헤더 + 탭 + 조건실행 + InfoTab + MonitorTab + HistoryTab) | 5/8 | 0 | ✅ |
| **5** | **`/api-provide` 목록** | **5/11** | **9 → 0** | **✅** |
| 6 | `/api-provide/new` (오퍼레이션 등록) | **5/11** | **84 → 0** | ✅ |
| 7 | `/api-provide/[id]` 탭 + Info/Columns/Params/Test/Spec/History 6 탭 | **5/11** | **12+35+42+39+73+11 → 0** | ✅ |
| 8 | `/api-collect` 목록 + 탭 (Info / Mapping / Schedule / History) | — | 185 + tab | 🔄 진입 |
| 9 | `/api-collect/[id]` 탭 | — | 14 | ⏳ |
| 10 | `/agents` 목록 | — | 58 | ⏳ |
| 11 | `/executions` 목록 | — | 19 | ⏳ |
| 12 | `/executions/[id]` (파이프라인 시각화 — 톤만) | — | 100 | ⏳ |
| 13 | `/users` + `/users/me` | — | 12 + 14 | ⏳ |
| 14 | `krds-input.small` 일괄 적용 | — | — | ⏳ |
| 15 | `globals.css` legacy compat 제거 (~183줄) | — | — | ⏳ |

## 별도 발견 / 수정 (Phase 외)
- ✅ **middleware.ts** — matcher 에 `/krds/` 추가. 인증 전(로그인 화면)에서 KRDS CSS/SVG 가 인증 가드에 가로채여 307 → 스타일 미적용 버그 fix (5/11)
- ✅ **셸 구조 변경 (5/11)** — Sidebar 의 GIMS-Link 브랜드 → AppHeader 좌측으로 이동. 헤더를 가로 전체 fixed 로, 사이드바 top:5rem 으로. 가로 라인 5rem 지점에서 정확히 정렬. AppShell/Sidebar/AppHeader 모두 갱신, globals.css `.app-layout` 단순화

## 사용자 결정 사항 (5/11)
- input 사이즈 — **일괄 `.small`** (관리 UI 입력/테이블 많음)
- `krds-step-wrap` 파이프라인 시각화 — **미도입** (현 시각화 유지, KRDS 톤만)
- `krds-pagination` / `krds-modal` / `krds-info-list` 추가 — **최소한**
- 작업 순서 — **쉬운 것 먼저** (login → api-provide → users → api-provide/[id] → api-collect/[id] → executions → agents → api-provide/new → executions/[id] → api-collect 일괄)

## 사용자 피드백 누적

### 5/8 (Phase 1)
- 사이드바 이모지 제거 → 텍스트만
- 헤더 4rem → 5rem (KRDS small 버튼 4rem 정확히 같아 흘러나옴)
- stat 카드 사이드 라인 통일 (다른 카드와 굵기 맞춤)
- agents/[id] Source/Target 박스 4종 통일
- cron input small 적용 (다른 input 은 보류 → 5/11 일괄 결정)
- KRDS 단정 톤 ↔ 한국어 친화 라벨 (Retention → 데이터 보존)

### 5/11 (Phase 2)
- `/login` input 너무 딱딱·성의 없음 → KRDS 적용 안 된 상태였음 (middleware 버그)
- 배경 그라데이션 + 좌측 컬러 라인 → **촌스러워** 제거
- 카드 위쪽 공백 너무 큼 → padding 4rem → 2.8rem → **1.6rem** + brandIcon 4.8rem → 4rem
- `/api-provide` 폰트 (label-small) → 대시보드(.app-table body-small)와 톤 맞춤
- 사이드바·헤더 가로 라인 어긋남 → 셸 구조 변경 (GIMS-Link 헤더 좌측으로 이동)
- 표가 배경 위에 떠 있음 → `.app-card` 로 감쌈

## 다음 진입
**`/api-provide/new`** (오퍼레이션 등록, 인라인 84) — 도메인 묶음 진행
