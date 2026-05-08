'use client';

import Link from 'next/link';
import { useCurrentUser } from '@/lib/useCurrentUser';
import { authApi } from '@/lib/authApi';
import styles from './AppHeader.module.css';

export default function AppHeader() {
  const { user, loading, clear } = useCurrentUser();

  async function onLogout() {
    try {
      await authApi.logout();
    } catch {
      /* idempotent */
    }
    clear();
    window.location.href = '/login';
  }

  if (loading || !user) {
    return <header className={styles.headerEmpty} />;
  }

  return (
    <header className={styles.header}>
      <span className={styles.user}>{user.name}</span>
      <Link href="/users" title="사용자 목록" className={styles.navLink}>
        사용자 목록
      </Link>
      <Link href="/users/me" title="내 정보" className={styles.navLink}>
        내 정보
      </Link>
      <button
        type="button"
        onClick={onLogout}
        className={`krds-btn small secondary ${styles.logoutBtn}`}
      >
        로그아웃
      </button>
    </header>
  );
}
