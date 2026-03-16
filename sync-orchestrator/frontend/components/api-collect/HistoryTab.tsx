'use client';

import { useCallback, useEffect, useState } from 'react';
import { historyApi } from '@/lib/collectorApi';
import { ApiExecutionHistoryItem } from '@/types/api-collect';

interface HistoryTabProps {
  endpointId: number;
}

export default function HistoryTab({ endpointId }: HistoryTabProps) {
  const [history, setHistory] = useState<ApiExecutionHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchHistory = useCallback(async () => {
    try {
      setLoading(true);
      const data = await historyApi.get(endpointId, page, 15);
      setHistory(data.content);
      setTotalPages(data.totalPages);
    } catch (e) {
      console.error('이력 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  }, [endpointId, page]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const statusStyle = (status: string) => {
    switch (status) {
      case 'SUCCESS': return { background: '#dcfce7', color: '#166534' };
      case 'FAILED': return { background: '#fee2e2', color: '#991b1b' };
      case 'RUNNING': return { background: '#dbeafe', color: '#1d4ed8' };
      default: return { background: 'var(--gray-100)', color: 'var(--gray-600)' };
    }
  };

  if (loading) {
    return <div style={{ padding: '2rem', textAlign: 'center' }}>로딩 중...</div>;
  }

  return (
    <div className="card">
      <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 className="card-title">실행 이력</h3>
        <button className="btn btn-sm" style={{ background: 'var(--gray-200)' }} onClick={fetchHistory}>새로고침</button>
      </div>
      {history.length === 0 ? (
        <div className="empty-state" style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-500)' }}>
          실행 이력이 없습니다.
        </div>
      ) : (
        <>
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>상태</th>
                  <th>HTTP</th>
                  <th>파싱</th>
                  <th>적재</th>
                  <th>스킵</th>
                  <th>소요시간</th>
                  <th>트리거</th>
                  <th>시작 시각</th>
                  <th>에러</th>
                </tr>
              </thead>
              <tbody>
                {history.map(h => (
                  <tr key={h.id}>
                    <td>
                      <span style={{ ...statusStyle(h.status), padding: '2px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600 }}>
                        {h.status}
                      </span>
                    </td>
                    <td>{h.httpStatusCode || '-'}</td>
                    <td>{h.responseCount ?? '-'}</td>
                    <td style={{ fontWeight: 600 }}>{h.insertCount ?? '-'}</td>
                    <td>{h.skipCount ?? '-'}</td>
                    <td>{h.durationMs != null ? `${h.durationMs}ms` : '-'}</td>
                    <td>
                      <span style={{ fontSize: '0.75rem', padding: '1px 6px', borderRadius: '3px', background: h.triggeredBy === 'SCHEDULE' ? '#e0e7ff' : 'var(--gray-100)' }}>
                        {h.triggeredBy}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.8rem' }}>
                      {h.startedAt ? new Date(h.startedAt).toLocaleString('ko-KR') : '-'}
                    </td>
                    <td style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--error)', fontSize: '0.8rem' }}
                      title={h.errorMessage || ''}>
                      {h.errorMessage || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <div style={{ padding: '0.75rem 1rem', display: 'flex', justifyContent: 'center', gap: '0.5rem' }}>
              <button className="btn btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>이전</button>
              <span style={{ padding: '0.25rem 0.5rem', fontSize: '0.85rem' }}>{page + 1} / {totalPages}</span>
              <button className="btn btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>다음</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
