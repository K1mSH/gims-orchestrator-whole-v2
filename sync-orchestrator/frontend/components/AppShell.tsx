'use client';

import { usePathname } from 'next/navigation';
import Sidebar from './Sidebar';
import AppHeader from './AppHeader';

/**
 * /login 페이지는 sidebar/header 둘 다 숨김 (전체화면 form).
 * 그 외 모든 화면은 sidebar + header + main 구조.
 */
export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isAuthPage = pathname === '/login';

  if (isAuthPage) {
    return <>{children}</>;
  }

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
