'use client';

import { useState, FormEvent } from 'react';
import { authApi } from '@/lib/authApi';
import { useCurrentUser } from '@/lib/useCurrentUser';
import styles from './me.module.css';

export default function MyPage() {
  const { user, loading, clear } = useCurrentUser();

  if (loading) return <div className="app-loading">로딩 중...</div>;
  if (!user) return null;

  return (
    <div className={styles.pageWrap}>
      <div className="app-page-header">
        <h1 className="app-page-header__title">내 정보</h1>
      </div>

      <div className="app-card">
        <div className={styles.infoGrid}>
          <div className={styles.infoLabel}>ID</div><div className={styles.infoValue}>{user.authUsersId}</div>
          <div className={styles.infoLabel}>이름</div><div className={styles.infoValue}>{user.name}</div>
          <div className={styles.infoLabel}>등록일</div><div className={styles.infoValue}>{user.createdAt?.replace('T', ' ').slice(0, 19)}</div>
        </div>
      </div>

      <ChangePasswordCard
        onSuccess={async () => {
          alert('비밀번호가 변경되었습니다. 다시 로그인해주세요.');
          try { await authApi.logout(); } catch {}
          clear();
          window.location.href = '/login';
        }}
      />

      <DeleteMeCard
        onSuccess={async () => {
          try { await authApi.logout(); } catch {}
          clear();
          window.location.href = '/login';
        }}
      />
    </div>
  );
}

function ChangePasswordCard({ onSuccess }: { onSuccess: () => void }) {
  const [cur, setCur] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (next !== confirm) {
      setError('새 비밀번호 확인이 일치하지 않습니다.');
      return;
    }
    if (next.length < 8) {
      setError('새 비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    setSubmitting(true);
    try {
      await authApi.changeMyPassword(cur, next);
      onSuccess();
    } catch (err: any) {
      const code = err.response?.data?.error;
      if (code === 'CURRENT_PASSWORD_MISMATCH') setError('현재 비밀번호가 일치하지 않습니다.');
      else if (code === 'PASSWORD_TOO_SHORT') setError('새 비밀번호는 8자 이상이어야 합니다.');
      else setError(code || '실패');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="app-card">
      <div className="app-card__header">
        <h2 className="app-card__title">비밀번호 변경</h2>
      </div>
      <form onSubmit={onSubmit}>
        <div className={styles.cardFields}>
          <div className="app-form-field">
            <label className="app-form-label">현재 비밀번호</label>
            <input className="krds-input small" type="password" value={cur} onChange={(e) => setCur(e.target.value)} required disabled={submitting} />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">새 비밀번호 (8자 이상)</label>
            <input className="krds-input small" type="password" value={next} onChange={(e) => setNext(e.target.value)} required disabled={submitting} minLength={8} />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">새 비밀번호 확인</label>
            <input className="krds-input small" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required disabled={submitting} minLength={8} />
          </div>
        </div>

        {error && <div className={`app-alert app-alert--danger ${styles.errorBox}`}>{error}</div>}

        <button type="submit" className="krds-btn small" disabled={submitting}>
          {submitting ? '변경 중...' : '비밀번호 변경'}
        </button>
      </form>
    </div>
  );
}

function DeleteMeCard({ onSuccess }: { onSuccess: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onClick() {
    if (!confirm('정말 탈퇴하시겠습니까? 모든 디바이스에서 로그아웃됩니다.')) return;
    setError(null);
    setSubmitting(true);
    try {
      await authApi.deleteMe();
      onSuccess();
    } catch (err: any) {
      const code = err.response?.data?.error;
      if (code === 'LAST_USER_CANNOT_DELETE') setError('마지막 사용자는 탈퇴할 수 없습니다.');
      else setError(code || '실패');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="app-card">
      <div className="app-card__header">
        <h2 className={`app-card__title ${styles.deleteTitle}`}>탈퇴</h2>
      </div>
      <p className={styles.deleteDesc}>
        탈퇴 시 모든 디바이스에서 즉시 로그아웃됩니다. 마지막 1명은 탈퇴할 수 없습니다.
      </p>
      {error && <div className={`app-alert app-alert--danger ${styles.errorBox}`}>{error}</div>}
      <button type="button" onClick={onClick} className="krds-btn small app-btn-danger" disabled={submitting}>
        {submitting ? '처리 중...' : '탈퇴'}
      </button>
    </div>
  );
}
