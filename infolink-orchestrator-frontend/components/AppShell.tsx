'use client';

import Sidebar from './Sidebar';
import AppHeader from './AppHeader';

/**
 * sidebar + header 묶음. (main) route group layout 에서만 사용.
 * /login 은 root layout 만 거치므로 본 컴포넌트 통과 X — 구조적 분리.
 */
export default function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="layout">
      <Sidebar />
      <div style={{ flex: 1, marginLeft: '250px', display: 'flex', flexDirection: 'column' }}>
        <AppHeader />
        <main className="main-content" style={{ marginLeft: 0 }}>{children}</main>
      </div>
    </div>
  );
}
