'use client';

import Sidebar from './Sidebar';
import AppHeader from './AppHeader';

/**
 * sidebar + header 묶음. (main) route group layout 에서만 사용.
 * /login 은 root layout 만 거치므로 본 컴포넌트 통과 X — 구조적 분리.
 *
 * 레이아웃:
 *   ┌─────────────────────────────────────┐
 *   │ AppHeader (5rem, 가로 전체, fixed)  │
 *   ├─────────────┬───────────────────────┤
 *   │ Sidebar     │ main                  │
 *   │ (24rem,     │ (margin-left 24rem,   │
 *   │  top 5rem)  │  margin-top 5rem)     │
 *   └─────────────┴───────────────────────┘
 */
export default function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="app-layout">
      <AppHeader />
      <Sidebar />
      <main className="app-layout__main">{children}</main>
    </div>
  );
}
