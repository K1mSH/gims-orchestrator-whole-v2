'use client';

import { useState, FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { authApi } from '@/lib/authApi';
import { useCurrentUser } from '@/lib/useCurrentUser';

export default function MyPage() {
  const router = useRouter();
  const { user, loading, clear } = useCurrentUser();

  if (loading) return <div>로딩 중...</div>;
  if (!user) return null;

  return (
    <div style={{ maxWidth: 520 }}>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem' }}>내 정보</h1>

      <div className="card" style={{ padding: '1.25rem 1.5rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '90px 1fr', rowGap: '0.5rem', fontSize: '0.875rem' }}>
          <div style={{ color: 'var(--gray-500)' }}>ID</div><div>{user.username}</div>
          <div style={{ color: 'var(--gray-500)' }}>이름</div><div>{user.name}</div>
          <div style={{ color: 'var(--gray-500)' }}>등록일</div><div>{user.createdAt?.replace('T', ' ').slice(0, 19)}</div>
        </div>
      </div>

      <ChangePasswordCard
        onSuccess={() => {
          alert('비밀번호가 변경되었습니다. 다시 로그인해주세요.');
          clear();
          router.replace('/login');
        }}
      />

      <DeleteMeCard
        onSuccess={() => {
          clear();
          router.replace('/login');
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
    <div className="card" style={{ padding: '1.25rem 1.5rem', marginBottom: '1.5rem' }}>
      <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>비밀번호 변경</h2>
      <form onSubmit={onSubmit}>
        <div className="form-group">
          <label className="form-label">현재 비밀번호</label>
          <input className="form-input" type="password" value={cur} onChange={(e) => setCur(e.target.value)} required disabled={submitting} />
        </div>
        <div className="form-group">
          <label className="form-label">새 비밀번호 (8자 이상)</label>
          <input className="form-input" type="password" value={next} onChange={(e) => setNext(e.target.value)} required disabled={submitting} minLength={8} />
        </div>
        <div className="form-group">
          <label className="form-label">새 비밀번호 확인</label>
          <input className="form-input" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required disabled={submitting} minLength={8} />
        </div>

        {error && (
          <div style={{ background: '#fef2f2', color: 'var(--error)', padding: '0.5rem 0.75rem', borderRadius: '0.375rem', fontSize: '0.8125rem', marginBottom: '1rem' }}>
            {error}
          </div>
        )}

        <button type="submit" className="btn btn-primary" disabled={submitting}>
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
    <div className="card" style={{ padding: '1.25rem 1.5rem', borderColor: 'var(--gray-200)' }}>
      <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.5rem', color: 'var(--error)' }}>탈퇴</h2>
      <p style={{ color: 'var(--gray-500)', fontSize: '0.8125rem', marginBottom: '1rem' }}>
        탈퇴 시 모든 디바이스에서 즉시 로그아웃됩니다. 마지막 1명은 탈퇴할 수 없습니다.
      </p>
      {error && (
        <div style={{ background: '#fef2f2', color: 'var(--error)', padding: '0.5rem 0.75rem', borderRadius: '0.375rem', fontSize: '0.8125rem', marginBottom: '1rem' }}>
          {error}
        </div>
      )}
      <button onClick={onClick} className="btn btn-danger" disabled={submitting}>
        {submitting ? '처리 중...' : '탈퇴'}
      </button>
    </div>
  );
}
