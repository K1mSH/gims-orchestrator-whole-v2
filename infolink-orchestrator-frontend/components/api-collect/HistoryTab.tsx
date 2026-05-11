'use client';

import { useCallback, useEffect, useState } from 'react';
import { historyApi } from '@/lib/collectorApi';
import { ApiExecutionHistoryItem } from '@/types/api-collect';
import styles from './HistoryTab.module.css';

interface HistoryTabProps {
  endpointId: number;
}

export default function HistoryTab({ endpointId }: HistoryTabProps) {
  const [history, setHistory] = useState<ApiExecutionHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [appliedStart, setAppliedStart] = useState<string | undefined>();
  const [appliedEnd, setAppliedEnd] = useState<string | undefined>();

  const [selectedError, setSelectedError] = useState<string | null>(null);

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

  const statusClass = (status: string) => {
    switch (status) {
      case 'SUCCESS': return styles.statusSuccess;
      case 'FAILED': return styles.statusFailed;
      case 'RUNNING': return styles.statusRunning;
      default: return styles.statusOther;
    }
  };

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
    return <div className="app-loading">로딩 중...</div>;
  }

  return (
    <div className="app-card">
      <div className="app-card__header">
        <h2 className="app-card__title">실행 이력</h2>
        <div className={styles.searchRow}>
          <input
            type="date"
            className={`krds-input small ${styles.dateInput}`}
            value={startDate}
            onChange={e => setStartDate(e.target.value)}
          />
          <span className={styles.dateSep}>~</span>
          <input
            type="date"
            className={`krds-input small ${styles.dateInput}`}
            value={endDate}
            onChange={e => setEndDate(e.target.value)}
          />
          <button type="button" className="krds-btn small" onClick={handleSearch}>검색</button>
          {(appliedStart || appliedEnd) && (
            <button type="button" className="krds-btn small secondary" onClick={handleReset}>초기화</button>
          )}
          <button type="button" className="krds-btn small secondary" onClick={fetchHistory}>새로고침</button>
        </div>
      </div>

      {history.length === 0 ? (
        <div className="app-empty">실행 이력이 없습니다.</div>
      ) : (
        <>
          <div className={styles.tableWrap}>
            <table className={`app-table ${styles.historyTable}`}>
              <thead>
                <tr>
                  <th>상태</th>
                  <th>HTTP</th>
                  <th>파싱</th>
                  <th>신규</th>
                  <th>갱신</th>
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
                      <span className={`${styles.statusBadge} ${statusClass(h.status)}`}>{h.status}</span>
                    </td>
                    <td>{h.httpStatusCode || '-'}</td>
                    <td>{h.responseCount ?? '-'}</td>
                    <td className={styles.boldCell}>{h.insertCount ?? '-'}</td>
                    <td>{h.updateCount ?? '-'}</td>
                    <td>{h.skipCount ?? '-'}</td>
                    <td>{h.durationMs != null ? `${h.durationMs}ms` : '-'}</td>
                    <td>
                      <span className={`${styles.triggerBadge} ${h.triggeredBy === 'SCHEDULE' ? styles.triggerSchedule : styles.triggerManual}`}>
                        {h.triggeredBy}
                      </span>
                    </td>
                    <td>{h.startedAt ? new Date(h.startedAt).toLocaleString('ko-KR') : '-'}</td>
                    <td className={styles.errorCell}>
                      {h.errorMessage ? (
                        <button
                          type="button"
                          className={styles.errorIconBtn}
                          onClick={() => setSelectedError(h.errorMessage || '')}
                          title="에러 메시지 보기"
                        >
                          !
                        </button>
                      ) : (
                        <span className={styles.errorNone}>-</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className={styles.pagerFoot}>
            <span className={styles.pagerTotal}>총 {totalElements}건</span>
            {totalPages > 1 && (
              <div className={styles.pager}>
                <button type="button" className="krds-btn xsmall secondary" disabled={page === 0} onClick={() => setPage(0)}>&laquo;</button>
                <button type="button" className="krds-btn xsmall secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>&lsaquo;</button>
                {getPageNumbers().map(p => (
                  <button
                    key={p}
                    type="button"
                    className={`krds-btn xsmall ${p === page ? '' : 'secondary'} ${styles.pageBtn}`}
                    onClick={() => setPage(p)}
                  >
                    {p + 1}
                  </button>
                ))}
                <button type="button" className="krds-btn xsmall secondary" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>&rsaquo;</button>
                <button type="button" className="krds-btn xsmall secondary" disabled={page >= totalPages - 1} onClick={() => setPage(totalPages - 1)}>&raquo;</button>
              </div>
            )}
          </div>
        </>
      )}

      {/* 에러 상세 모달 */}
      {selectedError !== null && (
        <div className={styles.modalOverlay} onClick={() => setSelectedError(null)}>
          <div className={styles.modalBox} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>실행 에러</h3>
              <button type="button" className={styles.modalCloseBtn} onClick={() => setSelectedError(null)} aria-label="닫기">×</button>
            </div>
            <div className={styles.modalBody}>{selectedError}</div>
            <div className={styles.modalFooter}>
              <button type="button" className="krds-btn small secondary" onClick={() => setSelectedError(null)}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
