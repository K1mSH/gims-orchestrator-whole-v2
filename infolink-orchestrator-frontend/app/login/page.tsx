'use client';

import { useState, FormEvent } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { authApi } from '@/lib/authApi';
import { useCurrentUser } from '@/lib/useCurrentUser';

export default function LoginPage() {
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get('next') || '/';
  const { mutate } = useCurrentUser();

  const [authUsersId, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await authApi.login(authUsersId, password);
      // 로그인 직전 미인증 상태에서 /auth/me 가 401 받아 cached=null 로 박혀있음.
      // mutate() 로 캐시 비우고 새 fetch 강제 — AppHeader 가 즉시 사용자 이름 표시.
      await mutate();
      router.replace(next);
      router.refresh();
    } catch (err: any) {
      const status = err.response?.status;
      const code = err.response?.data?.error;
      if (status === 401 && code === 'INVALID_CREDENTIALS') {
        setError('ID 또는 비밀번호가 일치하지 않습니다.');
      } else if (status === 423 || code === 'ACCOUNT_LOCKED') {
        setError('계정이 잠겼습니다. 30분 후 다시 시도해주세요.');
      } else if (status === 503) {
        setError('인증 서버가 응답하지 않습니다. 잠시 후 다시 시도해주세요.');
      } else {
        setError('로그인 실패: ' + (code || status || 'UNKNOWN'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--gray-50)',
    }}>
      <div style={{
        width: '380px',
        background: 'white',
        padding: '2.5rem',
        borderRadius: '0.75rem',
        boxShadow: '0 4px 6px rgba(0,0,0,0.05)',
      }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '0.25rem' }}>
          Sync Orchestrator
        </h1>
        <p style={{ color: 'var(--gray-500)', marginBottom: '1.75rem', fontSize: '0.875rem' }}>
          운영자 로그인
        </p>

        <form onSubmit={onSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="authUsersId">ID</label>
            <input
              id="authUsersId"
              className="form-input"
              type="text"
              autoComplete="username"
              value={authUsersId}
              onChange={(e) => setUsername(e.target.value)}
              required
              disabled={submitting}
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="password">비밀번호</label>
            <input
              id="password"
              className="form-input"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={submitting}
            />
          </div>

          {error && (
            <div style={{
              background: '#fef2f2',
              color: 'var(--error)',
              padding: '0.625rem 0.75rem',
              borderRadius: '0.375rem',
              fontSize: '0.8125rem',
              marginBottom: '1rem',
            }}>
              {error}
            </div>
          )}

          <button
            type="submit"
            className="btn btn-primary"
            disabled={submitting}
            style={{ width: '100%' }}
          >
            {submitting ? '로그인 중...' : '로그인'}
          </button>
        </form>
      </div>
    </div>
  );
}
