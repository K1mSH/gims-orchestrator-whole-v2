'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCurrentUser } from '@/lib/useCurrentUser';
import { authApi } from '@/lib/authApi';

export default function AppHeader() {
  const { user, loading, clear } = useCurrentUser();
  const router = useRouter();

  async function onLogout() {
    try {
      await authApi.logout();
    } catch {
      /* idempotent */
    }
    clear();
    router.replace('/login');
  }

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
      {loading ? null : user ? (
        <>
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
        </>
      ) : (
        <Link href="/login" className="btn btn-primary btn-sm" style={{ height: '28px', padding: '0 0.875rem' }}>
          로그인
        </Link>
      )}
    </header>
  );
}
