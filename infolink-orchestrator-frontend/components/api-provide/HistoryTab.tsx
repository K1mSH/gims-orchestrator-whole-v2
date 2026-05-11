'use client';

import { useEffect, useState } from 'react';
import { historyApi } from '@/lib/providerApi';
import styles from './HistoryTab.module.css';

interface Props {
  operationId: number;
}

export default function HistoryTab({ operationId }: Props) {
  const [history, setHistory] = useState<any>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  const fetchHistory = async () => {
    setLoading(true);
    try {
      const data = await historyApi.get(operationId, page, 20);
      setHistory(data);
    } catch (e: any) {
      console.error('이력 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchHistory(); }, [page]);

  if (loading) return <div className="app-loading">불러오는 중...</div>;

  const items = history?.content || [];

  return (
    <div className="app-card">
      <div className="app-card__header">
        <h2 className="app-card__title">호출 이력</h2>
      </div>

      {items.length === 0 ? (
        <div className="app-empty">이력 없음</div>
      ) : (
        <>
          <div className={styles.tableWrap}>
            <table className="app-table">
              <thead>
                <tr>
                  <th>시각</th>
                  <th>상태</th>
                  <th>응답건수</th>
                  <th>소요시간</th>
                  <th>API Key</th>
                  <th>IP</th>
                  <th>에러</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item: any) => (
                  <tr key={item.id}>
                    <td>{item.calledAt ? new Date(item.calledAt).toLocaleString() : '-'}</td>
                    <td>
                      <span className={`krds-badge ${item.status === 'SUCCESS' ? 'bg-light-success' : 'bg-light-danger'}`}>
                        {item.status}
                      </span>
                    </td>
                    <td>{item.responseCount ?? '-'}</td>
                    <td>{item.durationMs ?? '-'}ms</td>
                    <td className={styles.apiKeyCell}>{item.apiKey ? item.apiKey.substring(0, 8) + '...' : '-'}</td>
                    <td className={styles.muted}>{item.clientIp || '-'}</td>
                    <td className={styles.errorCell}>{item.errorMessage || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {history?.totalPages > 1 && (
            <div className={styles.pager}>
              <button
                type="button"
                className="krds-btn small secondary"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                이전
              </button>
              <span className={styles.pagerLabel}>{page + 1} / {history.totalPages}</span>
              <button
                type="button"
                className="krds-btn small secondary"
                disabled={page >= history.totalPages - 1}
                onClick={() => setPage(page + 1)}
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
