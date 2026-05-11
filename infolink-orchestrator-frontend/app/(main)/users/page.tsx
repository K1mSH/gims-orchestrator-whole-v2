'use client';

import { useEffect, useState, FormEvent } from 'react';
import Link from 'next/link';
import { authApi, type AuthUser } from '@/lib/authApi';
import { useCurrentUser } from '@/lib/useCurrentUser';
import styles from './users.module.css';

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
      <div className="app-page-header">
        <h1 className="app-page-header__title">사용자 관리</h1>
        <button className="krds-btn small" onClick={() => setShowAdd(true)}>+ 새 사용자 추가</button>
      </div>

      {error && <div className={`app-alert app-alert--danger ${styles.errorBox}`}>{error}</div>}

      <div className="app-card">
        <table className="app-table">
          <thead>
            <tr>
              <th className={styles.idCol}>ID</th>
              <th>Username</th>
              <th>이름</th>
              <th>등록일</th>
              <th className={styles.bigoCol}>비고</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={5} className="app-empty">로딩 중...</td></tr>
            )}
            {!loading && users.length === 0 && (
              <tr><td colSpan={5} className="app-empty">사용자 없음</td></tr>
            )}
            {!loading && users.map((u) => (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td>{u.authUsersId}</td>
                <td>{u.name}</td>
                <td>{u.createdAt?.replace('T', ' ').slice(0, 19)}</td>
                <td>
                  {me?.id === u.id && (
                    <Link href="/users/me" className="krds-btn small secondary">내 정보 변경</Link>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modalBox} onClick={(e) => e.stopPropagation()}>
        <h2 className={styles.modalTitle}>새 사용자 추가</h2>

        <form onSubmit={onSubmit}>
          <div className={styles.modalFields}>
            <div className="app-form-field">
              <label className="app-form-label">ID</label>
              <input className="krds-input small" value={authUsersId} onChange={(e) => setUsername(e.target.value)} required disabled={submitting} maxLength={50} autoFocus />
            </div>
            <div className="app-form-field">
              <label className="app-form-label">비밀번호 (8자 이상)</label>
              <input className="krds-input small" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required disabled={submitting} minLength={8} />
            </div>
            <div className="app-form-field">
              <label className="app-form-label">이름</label>
              <input className="krds-input small" value={name} onChange={(e) => setName(e.target.value)} required disabled={submitting} maxLength={50} />
            </div>
          </div>

          {error && <div className={`app-alert app-alert--danger ${styles.errorBox}`}>{error}</div>}

          <div className={styles.modalActions}>
            <button type="button" className="krds-btn small secondary" onClick={onClose} disabled={submitting}>취소</button>
            <button type="submit" className="krds-btn small" disabled={submitting}>
              {submitting ? '추가 중...' : '추가'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
