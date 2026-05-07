'use client';

import { useEffect, useState, useCallback } from 'react';
import { executionApi, executionHistoryApi, AgentExecutionSummary } from '@/lib/api';
import type { ExecutionHistory, ExecutionDashboardStats, AgentType, Zone } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import Link from 'next/link';

const AGENT_TYPE_LABELS: Record<AgentType, string> = {
  RCV: '수신(RCV)',
  SND: '송신(SND)',
  LOADER: 'Loader',
  DB_CON_PROXY: 'DB Proxy',
};

const ZONE_LABELS: Record<Zone, string> = {
  EXTERNAL: '외부망',
  DMZ: 'DMZ',
  INTERNAL_COMMON: '내부공통망',
  INTERNAL_SERVICE: '내부서비스망',
};

type ActiveCard = 'total' | 'online' | 'offline' | 'running' | 'todayExec' | 'todayFailed' | null;

export default function DashboardPage() {
  const [execStats, setExecStats] = useState<ExecutionDashboardStats | null>(null);
  const [agentStatuses, setAgentStatuses] = useState<AgentExecutionSummary[]>([]);
  const [recentHistory, setRecentHistory] = useState<ExecutionHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeCard, setActiveCard] = useState<ActiveCard>(null);

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
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const handleCardClick = (card: ActiveCard) => {
    setActiveCard(prev => prev === card ? null : card);
  };

  const offlineCount = execStats ? execStats.totalAgents - execStats.onlineAgents : 0;
  const today = new Date().toISOString().slice(0, 10); // yyyy-MM-dd

  // 필터된 Agent 목록
  const filteredAgents = (() => {
    if (activeCard === null || activeCard === 'total') return agentStatuses;
    if (activeCard === 'online') return agentStatuses.filter(a => a.agentStatus === 'ONLINE' || a.agentStatus === 'RUNNING');
    if (activeCard === 'offline') return agentStatuses.filter(a => a.agentStatus === 'OFFLINE');
    return [];
  })();

  // 필터된 실행 이력
  const filteredHistory = (() => {
    if (activeCard === 'running') return recentHistory.filter(h => h.status === 'RUNNING');
    if (activeCard === 'todayExec') return recentHistory;
    if (activeCard === 'todayFailed') return recentHistory.filter(h => h.status === 'FAILED');
    return [];
  })();

  if (loading) {
    return <div className="loading">로딩중...</div>;
  }

  const cardClass = (card: ActiveCard, alertCondition?: boolean) => {
    const classes = ['stat-card', 'stat-card-clickable'];
    if (activeCard === card) classes.push('stat-card-active');
    if (alertCondition) classes.push('stat-card-alert');
    return classes.join(' ');
  };

  const showAgentTable = activeCard === null || activeCard === 'total' || activeCard === 'online' || activeCard === 'offline';
  const showHistoryTable = activeCard === 'running' || activeCard === 'todayExec' || activeCard === 'todayFailed';

  const cardTitle: Record<string, string> = {
    total: '전체 Agent',
    online: '온라인 Agent',
    offline: '오프라인 Agent',
    running: '현재 실행 중',
    todayExec: '오늘 실행 이력',
    todayFailed: '오늘 실패 이력',
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">대시보드</h1>
        <span style={{ color: 'var(--gray-400)', fontSize: '12px' }}>10초마다 자동 갱신</span>
      </div>

      {execStats && (
        <div className="stats-grid">
          <div className={cardClass('total')} onClick={() => handleCardClick('total')}>
            <div className="stat-label">전체 Agent</div>
            <div className="stat-value">{execStats.totalAgents}</div>
          </div>
          <div className={cardClass('online')} onClick={() => handleCardClick('online')}>
            <div className="stat-label">온라인</div>
            <div className="stat-value success">{execStats.onlineAgents}</div>
          </div>
          <div className={cardClass('offline', offlineCount > 0)} onClick={() => handleCardClick('offline')}>
            <div className="stat-label">오프라인</div>
            <div className="stat-value error">{offlineCount}</div>
          </div>
          <Link href={`/executions?status=RUNNING&startDate=${today}`} style={{ textDecoration: 'none' }}>
            <div className={cardClass('running')}>
              <div className="stat-label">실행 중</div>
              <div className="stat-value" style={{ color: execStats.currentlyRunning > 0 ? 'var(--primary)' : undefined }}>
                {execStats.currentlyRunning}
              </div>
            </div>
          </Link>
          <Link href={`/executions?startDate=${today}`} style={{ textDecoration: 'none' }}>
            <div className={cardClass('todayExec')}>
              <div className="stat-label">오늘 실행</div>
              <div className="stat-value">{execStats.todayExecutions}</div>
            </div>
          </Link>
          <Link href={`/executions?status=FAILED&startDate=${today}`} style={{ textDecoration: 'none' }}>
            <div className={cardClass('todayFailed', execStats.todayFailed > 0)}>
              <div className="stat-label">오늘 실패</div>
              <div className="stat-value error">{execStats.todayFailed}</div>
            </div>
          </Link>
        </div>
      )}

      {/* Agent 테이블 (기본 뷰 + 카드 클릭) */}
      {showAgentTable && (
        <div className="card">
          <div className="card-header">
            <h2 className="card-title">{activeCard ? cardTitle[activeCard] : 'Agent 상태'}</h2>
            <Link href="/agents" className="btn btn-secondary btn-sm">전체보기</Link>
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
                {filteredAgents.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="empty-state">해당 Agent가 없습니다</td>
                  </tr>
                ) : (
                  filteredAgents.map(agent => (
                    <tr key={agent.agentId}>
                      <td>
                        <Link href={`/agents/${agent.agentId}`}>{agent.agentName}</Link>
                        <span style={{ marginLeft: '0.5rem', fontSize: '0.75rem', color: 'var(--gray-400)' }}>
                          {agent.agentCode}
                        </span>
                      </td>
                      <td>
                        <span className={`zone-${agent.zone.toLowerCase().replace('_', '-')}`}>
                          {ZONE_LABELS[agent.zone as Zone] || agent.zone}
                        </span>
                      </td>
                      <td><StatusBadge status={agent.agentStatus as 'ONLINE' | 'OFFLINE' | 'RUNNING'} /></td>
                      <td>
                        {agent.lastExecutionStatus ? (
                          <StatusBadge status={agent.lastExecutionStatus as 'SUCCESS' | 'FAILED' | 'RUNNING'} />
                        ) : <span style={{ color: 'var(--gray-400)' }}>-</span>}
                      </td>
                      <td>{agent.lastRunAt ? new Date(agent.lastRunAt).toLocaleString('ko-KR') : '-'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 카드 클릭 시: 실행 이력 테이블 */}
      {showHistoryTable && (
        <div className="card">
          <div className="card-header">
            <h2 className="card-title">{cardTitle[activeCard!]}</h2>
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
                {filteredHistory.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="empty-state">해당 이력이 없습니다</td>
                  </tr>
                ) : (
                  filteredHistory.map(history => (
                    <tr key={history.executionId}>
                      <td>
                        <Link href={`/executions/${encodeURIComponent(history.executionId)}`} style={{ fontWeight: 500 }}>
                          {history.agentName}
                        </Link>
                      </td>
                      <td>
                        <span className={`agent-type-badge agent-type-${history.agentType?.toLowerCase() || 'unknown'}`}>
                          {history.agentType ? (AGENT_TYPE_LABELS[history.agentType] || history.agentType) : '-'}
                        </span>
                      </td>
                      <td><StatusBadge status={history.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} /></td>
                      <td>
                        {history.status === 'RUNNING' ? (
                          <span style={{ color: 'var(--gray-400)' }}>-</span>
                        ) : (
                          <span>
                            {history.totalReadCount ?? 0} / {history.totalWriteCount ?? 0} / {history.totalSkipCount ?? 0}
                          </span>
                        )}
                      </td>
                      <td>{history.durationMs != null ? `${(history.durationMs / 1000).toFixed(1)}s` : '-'}</td>
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
      )}
    </div>
  );
}
