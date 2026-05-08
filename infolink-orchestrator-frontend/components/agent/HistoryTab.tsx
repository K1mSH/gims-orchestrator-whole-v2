'use client';

import Link from 'next/link';
import type { ExecutionHistory } from '@/types';
import StatusBadge from '@/components/StatusBadge';

interface HistoryTabProps {
  executions: ExecutionHistory[];
}

const TRIGGER_BADGE_VARIANT: Record<string, string> = {
  MANUAL: 'bg-light-gray',
  SCHEDULE: 'bg-light-information',
  CHAIN: 'bg-light-warning',
  API: 'bg-light-information',
};

export default function HistoryTab({ executions }: HistoryTabProps) {
  if (executions.length === 0) {
    return (
      <div className="app-card">
        <div className="app-empty">실행 이력이 없습니다</div>
      </div>
    );
  }

  return (
    <div className="app-card">
      <table className="app-table">
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
              <td>
                <StatusBadge status={execution.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} />
              </td>
              <td>
                {execution.status === 'RUNNING' ? (
                  <span style={{ color: 'var(--krds-light-color-text-subtle)' }}>-</span>
                ) : (
                  <span>
                    {execution.totalReadCount ?? 0} / {execution.totalWriteCount ?? 0} /{' '}
                    {execution.totalSkipCount ?? 0}
                  </span>
                )}
              </td>
              <td>
                {execution.durationMs != null ? `${(execution.durationMs / 1000).toFixed(1)}s` : '-'}
              </td>
              <td>
                <span
                  className={`krds-badge ${
                    TRIGGER_BADGE_VARIANT[execution.triggeredBy] ?? 'bg-light-gray'
                  }`}
                >
                  {execution.triggeredBy}
                </span>
              </td>
              <td>{new Date(execution.startedAt).toLocaleString('ko-KR')}</td>
              <td>
                <Link
                  href={`/executions/${encodeURIComponent(execution.executionId)}`}
                  className="krds-btn xsmall secondary"
                >
                  상세보기
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
