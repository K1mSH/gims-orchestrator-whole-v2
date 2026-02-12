'use client';

import Link from 'next/link';
import type { ExecutionHistory } from '@/types';
import StatusBadge from '@/components/StatusBadge';

interface HistoryTabProps {
  executions: ExecutionHistory[];
}

export default function HistoryTab({ executions }: HistoryTabProps) {
  if (executions.length === 0) {
    return (
      <div className="card">
        <div className="empty-state" style={{ padding: '3rem', textAlign: 'center' }}>
          <p style={{ fontSize: '1.2rem', color: 'var(--gray-500)' }}>실행 이력이 없습니다</p>
        </div>
      </div>
    );
  }

  return (
    <div className="card">
      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>상태</th>
              <th>읽기/쓰기/스킵</th>
              <th>소요시간</th>
              <th>트리거</th>
              <th>시작시간</th>
              <th>상세</th>
            </tr>
          </thead>
          <tbody>
            {executions.map((execution) => (
              <tr key={execution.executionId}>
                <td><StatusBadge status={execution.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} /></td>
                <td>
                  {execution.status === 'RUNNING' ? (
                    <span style={{ color: 'var(--gray-400)' }}>-</span>
                  ) : (
                    <span>
                      {execution.totalReadCount ?? 0} / {execution.totalWriteCount ?? 0} / {execution.totalSkipCount ?? 0}
                    </span>
                  )}
                </td>
                <td>
                  {execution.durationMs != null ? `${(execution.durationMs / 1000).toFixed(1)}s` : '-'}
                </td>
                <td>
                  <span className={`trigger-badge trigger-${execution.triggeredBy.toLowerCase()}`}>
                    {execution.triggeredBy}
                  </span>
                </td>
                <td>{new Date(execution.startedAt).toLocaleString('ko-KR')}</td>
                <td>
                  <Link href={`/executions/${encodeURIComponent(execution.executionId)}`}>
                    <button className="btn btn-secondary btn-sm">상세보기</button>
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
