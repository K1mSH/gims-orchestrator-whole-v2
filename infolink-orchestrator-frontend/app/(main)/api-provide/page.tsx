'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { operationApi } from '@/lib/providerApi';
import { ApiPrvOperation } from '@/types/api-provide';
import styles from './api-provide.module.css';

export default function ApiProvidePage() {
  const router = useRouter();
  const [operations, setOperations] = useState<ApiPrvOperation[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchOperations = async () => {
    try {
      const data = await operationApi.getAll();
      setOperations(data);
    } catch (e: any) {
      console.error('오퍼레이션 목록 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchOperations(); }, []);

  const handleTogglePublish = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await operationApi.togglePublish(id);
      fetchOperations();
    } catch (e: any) {
      alert('상태 변경 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  return (
    <div>
      <div className="app-page-header">
        <h1 className="app-page-header__title">API 제공 관리</h1>
      </div>

      {loading ? (
        <div className="app-loading">불러오는 중...</div>
      ) : (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">오퍼레이션 목록</h2>
            <button className="krds-btn small" onClick={() => router.push('/api-provide/new')}>
              + 오퍼레이션 등록
            </button>
          </div>
          <table className={`app-table ${styles.opTable}`}>
            <thead>
              <tr>
                <th>이름</th>
                <th>오퍼레이션 ID</th>
                <th>타입</th>
                <th>활성</th>
                <th>등록일</th>
              </tr>
            </thead>
            <tbody>
              {operations.length === 0 ? (
                <tr>
                  <td colSpan={5} className="app-empty">등록된 오퍼레이션이 없습니다.</td>
                </tr>
              ) : (
                [...operations]
                  .sort((a, b) => (a.operationName ?? '').localeCompare(b.operationName ?? '', 'ko'))
                  .map(op => {
                    const isCustom = op.operationType === 'CUSTOM';
                    return (
                      <tr
                        key={op.id}
                        onClick={() => router.push(`/api-provide/${op.id}`)}
                        className={styles.clickableRow}
                      >
                        <td>{op.operationName}</td>
                        <td><code className={styles.codeText}>{op.operationId}</code></td>
                        <td>
                          <span
                            title={isCustom ? '원본 DB 직접 조회 — 관할 외 운영 시스템 데이터 (수정 불가)' : '메타등록형 — 자체 PG 적재본 SELECT'}
                            className={`krds-badge ${isCustom ? 'bg-light-warning' : 'bg-light-information'} ${styles.typeBadge}`}
                          >
                            {isCustom && <span aria-hidden>🔒</span>}
                            {isCustom ? 'CUSTOM' : 'META'}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`krds-badge ${op.isPublished ? 'bg-light-success' : 'bg-light-gray'} ${styles.publishBadge}`}
                            onClick={(e) => handleTogglePublish(op.id, e)}
                          >
                            {op.isPublished ? '활성중' : '미활성'}
                          </span>
                        </td>
                        <td className={styles.muted}>
                          {op.createdAt ? new Date(op.createdAt).toLocaleDateString() : '-'}
                        </td>
                      </tr>
                    );
                  })
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
