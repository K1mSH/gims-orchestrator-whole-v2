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
  const [totalElements, setTotalElements] = useState(0);

  // 날짜 검색
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [appliedStart, setAppliedStart] = useState<string | undefined>();
  const [appliedEnd, setAppliedEnd] = useState<string | undefined>();

  const fetchHistory = useCallback(async () => {
    try {
      setLoading(true);
      const data = await historyApi.get(endpointId, page, 15, appliedStart, appliedEnd);
      setHistory(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (e) {
      console.error('이력 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  }, [endpointId, page, appliedStart, appliedEnd]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const handleSearch = () => {
    setPage(0);
    setAppliedStart(startDate || undefined);
    setAppliedEnd(endDate || undefined);
  };

  const handleReset = () => {
    setStartDate('');
    setEndDate('');
    setPage(0);
    setAppliedStart(undefined);
    setAppliedEnd(undefined);
  };

  const statusStyle = (status: string) => {
    switch (status) {
      case 'SUCCESS': return { background: '#dcfce7', color: '#166534' };
      case 'FAILED': return { background: '#fee2e2', color: '#991b1b' };
      case 'RUNNING': return { background: '#dbeafe', color: '#1d4ed8' };
      default: return { background: 'var(--gray-100)', color: 'var(--gray-600)' };
    }
  };

  // 페이지 번호 목록 생성 (최대 5개, 현재 페이지 중심)
  const getPageNumbers = () => {
    const maxVisible = 5;
    let start = Math.max(0, page - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible);
    if (end - start < maxVisible) {
      start = Math.max(0, end - maxVisible);
    }
    const pages: number[] = [];
    for (let i = start; i < end; i++) pages.push(i);
    return pages;
  };

  if (loading && history.length === 0) {
    return <div style={{ padding: '2rem', textAlign: 'center' }}>로딩 중...</div>;
  }

  return (
    <div className="card">
      <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
        <h3 className="card-title" style={{ margin: 0 }}>실행 이력</h3>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
          <input type="date" className="form-input" value={startDate}
            onChange={e => setStartDate(e.target.value)}
            style={{ fontSize: '0.8rem', padding: '3px 6px', width: '130px' }} />
          <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)' }}>~</span>
          <input type="date" className="form-input" value={endDate}
            onChange={e => setEndDate(e.target.value)}
            style={{ fontSize: '0.8rem', padding: '3px 6px', width: '130px' }} />
          <button className="btn btn-sm btn-primary" onClick={handleSearch}
            style={{ fontSize: '0.75rem', padding: '3px 10px' }}>검색</button>
          {(appliedStart || appliedEnd) && (
            <button className="btn btn-sm" onClick={handleReset}
              style={{ fontSize: '0.75rem', padding: '3px 8px', background: 'var(--gray-200)' }}>초기화</button>
          )}
          <button className="btn btn-sm" style={{ background: 'var(--gray-200)', fontSize: '0.75rem', padding: '3px 8px' }} onClick={fetchHistory}>새로고침</button>
        </div>
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

          {/* 페이징 */}
          <div style={{ padding: '0.75rem 1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)' }}>
              총 {totalElements}건
            </span>
            {totalPages > 1 && (
              <div style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                <button className="btn btn-sm" disabled={page === 0}
                  onClick={() => setPage(0)}
                  style={{ fontSize: '0.75rem', padding: '2px 6px' }}>&laquo;</button>
                <button className="btn btn-sm" disabled={page === 0}
                  onClick={() => setPage(p => p - 1)}
                  style={{ fontSize: '0.75rem', padding: '2px 6px' }}>&lsaquo;</button>
                {getPageNumbers().map(p => (
                  <button key={p} className="btn btn-sm"
                    onClick={() => setPage(p)}
                    style={{
                      fontSize: '0.75rem', padding: '2px 8px', minWidth: '28px',
                      background: p === page ? 'var(--primary)' : 'var(--gray-100)',
                      color: p === page ? '#fff' : 'var(--gray-700)',
                      fontWeight: p === page ? 600 : 400,
                    }}>
                    {p + 1}
                  </button>
                ))}
                <button className="btn btn-sm" disabled={page >= totalPages - 1}
                  onClick={() => setPage(p => p + 1)}
                  style={{ fontSize: '0.75rem', padding: '2px 6px' }}>&rsaquo;</button>
                <button className="btn btn-sm" disabled={page >= totalPages - 1}
                  onClick={() => setPage(totalPages - 1)}
                  style={{ fontSize: '0.75rem', padding: '2px 6px' }}>&raquo;</button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
