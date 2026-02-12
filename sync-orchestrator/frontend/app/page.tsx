'use client';

import { useEffect, useState, useCallback } from 'react';
import { executionApi, executionHistoryApi, AgentExecutionSummary } from '@/lib/api';
import type { ExecutionHistory, ExecutionDashboardStats } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import Link from 'next/link';

export default function DashboardPage() {
  const [execStats, setExecStats] = useState<ExecutionDashboardStats | null>(null);
  const [agentStatuses, setAgentStatuses] = useState<AgentExecutionSummary[]>([]);
  const [recentHistory, setRecentHistory] = useState<ExecutionHistory[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    try {
      const [execStatsData, statusesData, historyData] = await Promise.all([
        executionHistoryApi.getDashboardStats(),
        executionApi.getAgentStatuses(),
        executionHistoryApi.getRecent(),
      ]);
      setExecStats(execStatsData);
      setAgentStatuses(statusesData);
      setRecentHistory(historyData);
    } catch (error) {
      console.error('대시보드 데이터 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();

    // 10초마다 자동 갱신
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, [fetchData]);

  if (loading) {
    return <div className="loading">로딩중...</div>;
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">대시보드</h1>
      </div>

      {execStats && (
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-label">전체 Agent</div>
            <div className="stat-value">{execStats.totalAgents}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">온라인 Agent</div>
            <div className="stat-value success">{execStats.onlineAgents}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">오프라인 Agent</div>
            <div className="stat-value error">{execStats.totalAgents - execStats.onlineAgents}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">현재 실행 중</div>
            <div className="stat-value" style={{ color: execStats.currentlyRunning > 0 ? 'var(--primary)' : undefined }}>
              {execStats.currentlyRunning}
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">오늘 실행</div>
            <div className="stat-value">{execStats.todayExecutions}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">오늘 실패</div>
            <div className="stat-value error">{execStats.todayFailed}</div>
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Agent 상태</h2>
          <Link href="/agents" className="btn btn-secondary btn-sm">
            전체보기
          </Link>
        </div>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Agent</th>
                <th>Zone</th>
                <th>상태</th>
                <th>마지막 실행 결과</th>
                <th>마지막 실행 시간</th>
              </tr>
            </thead>
            <tbody>
              {agentStatuses.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty-state">
                    등록된 Agent가 없습니다
                  </td>
                </tr>
              ) : (
                agentStatuses.map((agent) => (
                  <tr key={agent.agentId}>
                    <td>
                      <Link href={`/agents/${agent.agentId}`}>
                        {agent.agentName}
                      </Link>
                    </td>
                    <td>{agent.zone}</td>
                    <td>
                      <StatusBadge status={agent.agentStatus as 'ONLINE' | 'OFFLINE' | 'RUNNING'} />
                    </td>
                    <td>
                      {agent.lastExecutionStatus ? (
                        <StatusBadge status={agent.lastExecutionStatus as 'SUCCESS' | 'FAILED' | 'RUNNING'} />
                      ) : (
                        <span style={{ color: 'var(--gray-400)' }}>-</span>
                      )}
                    </td>
                    <td>
                      {agent.lastRunAt ? new Date(agent.lastRunAt).toLocaleString('ko-KR') : '-'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">최근 실행 이력</h2>
          <span style={{ color: 'var(--gray-400)', fontSize: '12px' }}>10초마다 자동 갱신</span>
        </div>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Agent</th>
                <th>타입</th>
                <th>상태</th>
                <th>읽기/쓰기/스킵</th>
                <th>소요시간</th>
                <th>트리거</th>
                <th>시작 시간</th>
              </tr>
            </thead>
            <tbody>
              {recentHistory.length === 0 ? (
                <tr>
                  <td colSpan={7} className="empty-state">
                    실행 이력이 없습니다
                  </td>
                </tr>
              ) : (
                recentHistory.slice(0, 20).map((history) => (
                  <tr key={history.executionId}>
                    <td>
                      <Link href={`/executions/${encodeURIComponent(history.executionId)}`} style={{ fontWeight: 500 }}>
                        {history.agentName}
                      </Link>
                    </td>
                    <td>
                      <span className={`agent-type-badge agent-type-${history.agentType?.toLowerCase().replace('_', '-') || 'unknown'}`}>
                        {history.agentType === 'RELAY' ? 'Relay' :
                         history.agentType === 'LOADER_CUSTOM' ? 'Loader' :
                         history.agentType === 'LOADER_STANDARD' ? 'Loader' :
                         history.agentType || '-'}
                      </span>
                    </td>
                    <td>
                      <StatusBadge status={history.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} />
                    </td>
                    <td>
                      {history.status === 'RUNNING' ? (
                        <span style={{ color: 'var(--gray-400)' }}>-</span>
                      ) : (
                        <span>
                          {history.totalReadCount ?? 0} / {history.totalWriteCount ?? 0} / {history.totalSkipCount ?? 0}
                        </span>
                      )}
                    </td>
                    <td>
                      {history.durationMs != null ? `${(history.durationMs / 1000).toFixed(1)}s` : '-'}
                    </td>
                    <td>
                      <span className={`trigger-badge trigger-${history.triggeredBy.toLowerCase()}`}>
                        {history.triggeredBy}
                      </span>
                    </td>
                    <td>{new Date(history.startedAt).toLocaleString('ko-KR')}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
