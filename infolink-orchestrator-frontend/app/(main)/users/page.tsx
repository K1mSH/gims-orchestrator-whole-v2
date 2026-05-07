'use client';

import { useEffect, useState, FormEvent } from 'react';
import Link from 'next/link';
import { authApi, type AuthUser } from '@/lib/authApi';
import { useCurrentUser } from '@/lib/useCurrentUser';

export default function UsersPage() {
  const { user: me } = useCurrentUser();
  const [users, setUsers] = useState<AuthUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const list = await authApi.listUsers();
      setUsers(list);
    } catch (err: any) {
      setError(err.response?.data?.error || 'load failed');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>사용자 관리</h1>
        <button className="btn btn-primary" onClick={() => setShowAdd(true)}>+ 새 사용자 추가</button>
      </div>

      {error && (
        <div style={{ background: '#fef2f2', color: 'var(--error)', padding: '0.75rem', borderRadius: '0.375rem', marginBottom: '1rem' }}>
          {error}
        </div>
      )}

      <div className="card">
        <div className="table-container">
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: 80 }}>ID</th>
                <th>Username</th>
                <th>이름</th>
                <th>등록일</th>
                <th style={{ width: 120 }}>비고</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={5} style={{ textAlign: 'center', padding: '2rem', color: 'var(--gray-500)' }}>로딩 중...</td></tr>
              )}
              {!loading && users.length === 0 && (
                <tr><td colSpan={5} style={{ textAlign: 'center', padding: '2rem', color: 'var(--gray-500)' }}>사용자 없음</td></tr>
              )}
              {!loading && users.map((u) => (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td>{u.authUsersId}</td>
                  <td>{u.name}</td>
                  <td>{u.createdAt?.replace('T', ' ').slice(0, 19)}</td>
                  <td>
                    {me?.id === u.id && (
                      <Link href="/users/me" className="btn btn-secondary btn-sm">내 정보 변경</Link>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {showAdd && (
        <AddUserModal
          onClose={() => setShowAdd(false)}
          onCreated={() => {
            setShowAdd(false);
            load();
          }}
        />
      )}
    </div>
  );
}

function AddUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [authUsersId, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await authApi.addUser(authUsersId, password, name);
      alert(`사용자 [${authUsersId}] 발급 완료. 비밀번호를 별도 채널로 전달하세요.`);
      onCreated();
    } catch (err: any) {
      const code = err.response?.data?.error;
      if (code === 'AUTH_USERS_ID_DUPLICATE') setError('이미 사용 중인 ID 입니다.');
      else if (code === 'PASSWORD_TOO_SHORT') setError('비밀번호는 8자 이상이어야 합니다.');
      else if (code === 'NAME_REQUIRED') setError('이름을 입력하세요.');
      else if (code === 'AUTH_USERS_ID_TOO_LONG') setError('ID 길이가 너무 깁니다 (50자 이하).');
      else setError(code || '실패');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', zIndex: 100,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }} onClick={onClose}>
      <div style={{
        width: 420, background: 'white', borderRadius: '0.5rem', padding: '1.75rem',
      }} onClick={(e) => e.stopPropagation()}>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '1.25rem' }}>새 사용자 추가</h2>

        <form onSubmit={onSubmit}>
          <div className="form-group">
            <label className="form-label">ID</label>
            <input className="form-input" value={authUsersId} onChange={(e) => setUsername(e.target.value)} required disabled={submitting} maxLength={50} autoFocus />
          </div>
          <div className="form-group">
            <label className="form-label">비밀번호 (8자 이상)</label>
            <input className="form-input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required disabled={submitting} minLength={8} />
          </div>
          <div className="form-group">
            <label className="form-label">이름</label>
            <input className="form-input" value={name} onChange={(e) => setName(e.target.value)} required disabled={submitting} maxLength={50} />
          </div>

          {error && (
            <div style={{ background: '#fef2f2', color: 'var(--error)', padding: '0.5rem 0.75rem', borderRadius: '0.375rem', fontSize: '0.8125rem', marginBottom: '1rem' }}>
              {error}
            </div>
          )}

          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={submitting}>취소</button>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? '추가 중...' : '추가'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
