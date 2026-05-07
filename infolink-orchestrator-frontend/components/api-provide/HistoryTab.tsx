'use client';

import { useEffect, useState } from 'react';
import { historyApi } from '@/lib/providerApi';

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

  if (loading) return <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-400)' }}>불러오는 중...</div>;

  const items = history?.content || [];

  return (
    <div className="card">
      <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' }}>
        <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>호출 이력</div>
      </div>

      {items.length === 0 ? (
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-400)' }}>이력 없음</div>
      ) : (
        <>
          <div className="table-container">
            <table>
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
                    <td style={{ fontSize: '0.8rem' }}>{item.calledAt ? new Date(item.calledAt).toLocaleString() : '-'}</td>
                    <td>
                      <span style={{
                        padding: '2px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                        background: item.status === 'SUCCESS' ? '#dcfce7' : '#fee2e2',
                        color: item.status === 'SUCCESS' ? '#166534' : '#991b1b',
                      }}>
                        {item.status}
                      </span>
                    </td>
                    <td>{item.responseCount ?? '-'}</td>
                    <td>{item.durationMs ?? '-'}ms</td>
                    <td style={{ fontSize: '0.8rem' }}>{item.apiKey ? item.apiKey.substring(0, 8) + '...' : '-'}</td>
                    <td style={{ fontSize: '0.8rem' }}>{item.clientIp || '-'}</td>
                    <td style={{ fontSize: '0.8rem', color: '#991b1b', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {item.errorMessage || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {history?.totalPages > 1 && (
            <div style={{ padding: '0.75rem 1rem', display: 'flex', justifyContent: 'center', gap: '0.5rem' }}>
              <button className="btn btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
              <span style={{ fontSize: '0.8rem', color: 'var(--gray-400)', padding: '0.25rem 0.5rem' }}>
                {page + 1} / {history.totalPages}
              </span>
              <button className="btn btn-sm" disabled={page >= history.totalPages - 1} onClick={() => setPage(page + 1)}>다음</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
