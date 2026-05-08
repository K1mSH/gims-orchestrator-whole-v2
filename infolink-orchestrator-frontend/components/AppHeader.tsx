'use client';

import Link from 'next/link';
import { useCurrentUser } from '@/lib/useCurrentUser';
import { authApi } from '@/lib/authApi';

export default function AppHeader() {
  const { user, loading, clear } = useCurrentUser();

  async function onLogout() {
    try {
      await authApi.logout();
    } catch {
      /* idempotent */
    }
    clear();
    // full reload — cookie 만료 후 middleware 가 새 검증해서 /login 로
    window.location.href = '/login';
  }

  // 미인증 상태에서는 (main) 영역 진입 자체가 middleware 에 막혀야 함.
  // user 가 없으면 헤더 자체를 빈 채로 둠 — "로그인" 버튼 같은 분기 X.
  if (loading || !user) return (
    <header style={{
      height: '40px',
      background: 'white',
      borderBottom: '1px solid var(--gray-200)',
      position: 'sticky',
      top: 0,
      zIndex: 50,
    }} />
  );

  return (
    <header style={{
      height: '40px',
      background: 'white',
      borderBottom: '1px solid var(--gray-200)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'flex-end',
      padding: '0 1.25rem',
      position: 'sticky',
      top: 0,
      zIndex: 50,
      gap: '0.75rem',
      fontSize: '0.8125rem',
    }}>
      <span style={{ color: 'var(--gray-500)' }}>{user.name}</span>
      <Link
        href="/users"
        title="사용자 목록"
        style={{ color: 'var(--gray-600)', textDecoration: 'none' }}
      >
        👥 사용자 목록
      </Link>
      <Link
        href="/users/me"
        title="내 정보"
        style={{ color: 'var(--gray-600)', textDecoration: 'none' }}
      >
        내 정보
      </Link>
      <button
        onClick={onLogout}
        className="btn btn-secondary btn-sm"
        style={{ height: '28px', padding: '0 0.75rem' }}
      >
        로그아웃
      </button>
    </header>
  );
}
