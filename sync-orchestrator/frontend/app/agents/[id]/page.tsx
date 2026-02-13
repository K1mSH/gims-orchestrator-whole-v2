'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { agentApi, scheduleApi, executionHistoryApi, executionApi } from '@/lib/api';
import type { Agent, Schedule, ExecutionHistory, ExecutionParamResponse, ExecutionFilter } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import TabButton from '@/components/agent/TabButton';
import InfoTab from '@/components/agent/InfoTab';
import MonitorTab from '@/components/agent/MonitorTab';
import HistoryTab from '@/components/agent/HistoryTab';

const STATUS_LABELS: Record<string, string> = {
  ONLINE: '온라인',
  OFFLINE: '오프라인',
  RUNNING: '실행중',
};

type TabType = 'info' | 'monitor' | 'history';

export default function AgentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const agentId = Number(params.id);

  const [activeTab, setActiveTab] = useState<TabType>('info');
  const [agent, setAgent] = useState<Agent | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [executions, setExecutions] = useState<ExecutionHistory[]>([]);
  const [loading, setLoading] = useState(true);

  // 실행 옵션 패널 상태
  const [showExecutionOptions, setShowExecutionOptions] = useState(false);
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [useTimeRange, setUseTimeRange] = useState(false);
  // 데이터 필터 상태
  const [executionParams, setExecutionParams] = useState<ExecutionParamResponse[]>([]);
  const [activeFilters, setActiveFilters] = useState<Record<string, { enabled: boolean; value: string }>>({});

  const fetchData = useCallback(async () => {
    try {
      const [agentData, schedulesData, executionsData] = await Promise.all([
        agentApi.getById(agentId),
        scheduleApi.getByAgentId(agentId),
        executionHistoryApi.getByAgent(agentId),
      ]);
      setAgent(agentData);
      setSchedules(schedulesData);
      setExecutions(executionsData);
      // 실행 파라미터 설정 (Agent 응답에 포함된 경우)
      if (agentData.executionParams && agentData.executionParams.length > 0) {
        const enabledParams = agentData.executionParams.filter(p => p.isEnabled);
        setExecutionParams(enabledParams);
      }
    } catch (error) {
      console.error('데이터 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [agentId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // 실행중일 때 주기적으로 Agent 상태 확인
  useEffect(() => {
    if (agent?.status === 'RUNNING') {
      const interval = setInterval(async () => {
        const updatedAgent = await agentApi.getById(agentId);
        if (updatedAgent.status !== 'RUNNING') {
          fetchData();
        } else {
          setAgent(updatedAgent);
        }
      }, 30000);
      return () => clearInterval(interval);
    }
  }, [agent?.status, agentId, fetchData]);

  const handleHealthCheck = async () => {
    try {
      const result = await agentApi.healthCheck(agentId);
      alert(`상태확인 결과: ${STATUS_LABELS[result.status] || result.status}\n${result.message}`);
      fetchData();
    } catch (error) {
      console.error('상태확인 실패:', error);
      alert('상태확인 실패');
    }
  };

  const handleTriggerExecution = async (withOptions: boolean = false) => {
    const hasTimeRange = withOptions && useTimeRange && (startTime || endTime);
    const filters: ExecutionFilter[] = [];
    if (withOptions) {
      Object.entries(activeFilters).forEach(([paramId, f]) => {
        if (f.enabled && f.value.trim()) {
          filters.push({ paramId, value: f.value.trim() });
        }
      });
    }

    const parts: string[] = [];
    if (hasTimeRange) parts.push(`시간 범위: ${startTime || '(기본값)'} ~ ${endTime || '(현재시간)'}`);
    if (filters.length > 0) parts.push(`필터 ${filters.length}개: ${filters.map(f => `${f.paramId}=${f.value}`).join(', ')}`);

    const confirmMsg = parts.length > 0
      ? `실행 옵션:\n${parts.join('\n')}\n\n실행하시겠습니까?`
      : '파이프라인을 실행하시겠습니까? (기본 lookback 사용)';

    if (!confirm(confirmMsg)) return;

    try {
      await executionApi.trigger(
        agentId,
        hasTimeRange && startTime ? startTime : undefined,
        hasTimeRange && endTime ? endTime : undefined,
        filters.length > 0 ? filters : undefined
      );
      setActiveTab('monitor');
      setShowExecutionOptions(false);
      setStartTime('');
      setEndTime('');
      setUseTimeRange(false);
      setActiveFilters({});
      fetchData();
    } catch (error) {
      console.error('실행 실패:', error);
      alert('실행에 실패했습니다.');
    }
  };

  const handleGenerateTestData = async () => {
    const countStr = prompt('생성할 테스트 데이터 수를 입력하세요 (기본: 1000)', '1000');
    if (countStr === null) return;

    const count = parseInt(countStr) || 1000;
    try {
      const result = await agentApi.generateTestData(agentId, count);
      alert(`테스트 데이터 생성 완료!\n생성된 건수: ${result.created}\n시간 범위: ${result.timeRange.from} ~ ${result.timeRange.to}`);
    } catch (error) {
      console.error('테스트 데이터 생성 실패:', error);
      alert('테스트 데이터 생성에 실패했습니다.');
    }
  };

  const handleClearTestData = async () => {
    if (!confirm('테스트 데이터(REMARK=TEST_DATA)를 모두 삭제하시겠습니까?')) return;
    try {
      const result = await agentApi.clearTestData(agentId);
      alert(`테스트 데이터 삭제 완료!\n삭제된 건수: ${result.deleted}`);
    } catch (error) {
      console.error('테스트 데이터 삭제 실패:', error);
      alert('테스트 데이터 삭제에 실패했습니다.');
    }
  };

  const handleDelete = async () => {
    if (!confirm('정말 삭제하시겠습니까? 연결된 스케줄도 함께 삭제됩니다.')) return;
    try {
      await agentApi.delete(agentId);
      router.push('/agents');
    } catch (error) {
      console.error('Agent 삭제 실패:', error);
    }
  };

  if (loading) {
    return <div className="loading">로딩중...</div>;
  }

  if (!agent) {
    return <div className="empty-state">Agent를 찾을 수 없습니다</div>;
  }

  return (
    <div>
      {/* 헤더 */}
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <h1 className="page-title">{agent.agentName}</h1>
          <StatusBadge status={agent.status} />
        </div>
        <div>
          <button className="btn btn-secondary" onClick={() => router.push('/agents')} style={{ marginRight: '0.5rem' }}>
            목록
          </button>
          <button className="btn btn-primary" onClick={handleHealthCheck} style={{ marginRight: '0.5rem' }}>
            상태확인
          </button>
          <button className="btn btn-secondary" onClick={handleGenerateTestData} disabled={agent.status === 'OFFLINE'} style={{ marginRight: '0.25rem' }}>
            테스트 생성
          </button>
          <button className="btn btn-secondary" onClick={handleClearTestData} disabled={agent.status === 'OFFLINE'} style={{ marginRight: '0.5rem' }}>
            테스트 삭제
          </button>
          <button className="btn btn-primary" onClick={() => handleTriggerExecution(false)} disabled={agent.status !== 'ONLINE'} style={{ marginRight: '0.25rem' }}>
            실행
          </button>
          <button className="btn btn-secondary" onClick={() => setShowExecutionOptions(!showExecutionOptions)} disabled={agent.status !== 'ONLINE'} style={{ marginRight: '0.5rem' }}>
            실행옵션 ▾
          </button>
          <button className="btn btn-danger" onClick={handleDelete}>
            삭제
          </button>
        </div>
      </div>

      {/* 실행 옵션 패널 */}
      {showExecutionOptions && (
        <div className="card" style={{ marginBottom: '1rem', padding: '1rem', background: 'var(--gray-50)' }}>
          {/* 시간 범위 */}
          <div style={{ marginBottom: executionParams.length > 0 ? '1rem' : 0 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', fontWeight: 500 }}>
              <input type="checkbox" checked={useTimeRange} onChange={(e) => setUseTimeRange(e.target.checked)} />
              시간 범위 지정
            </label>
            {useTimeRange && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginLeft: '1.5rem' }}>
                <div>
                  <label className="form-label" style={{ marginBottom: '0.25rem', fontSize: '0.8rem' }}>시작</label>
                  <input type="datetime-local" className="form-input" value={startTime} onChange={(e) => setStartTime(e.target.value)} style={{ width: '220px' }} />
                </div>
                <div>
                  <label className="form-label" style={{ marginBottom: '0.25rem', fontSize: '0.8rem' }}>종료</label>
                  <input type="datetime-local" className="form-input" value={endTime} onChange={(e) => setEndTime(e.target.value)} style={{ width: '220px' }} />
                </div>
              </div>
            )}
          </div>

          {/* 데이터 필터 */}
          {executionParams.length > 0 && (
            <div>
              <div style={{ fontWeight: 500, marginBottom: '0.5rem', paddingTop: '0.5rem', borderTop: '1px solid var(--border-color)' }}>
                데이터 필터
              </div>
              {executionParams.map(param => {
                const filterState = activeFilters[param.paramId] || { enabled: false, value: '' };
                return (
                  <div key={param.paramId} style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem', marginLeft: '0.5rem' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', minWidth: '120px', cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={filterState.enabled}
                        onChange={(e) => setActiveFilters(prev => ({
                          ...prev,
                          [param.paramId]: { ...filterState, enabled: e.target.checked }
                        }))}
                      />
                      {param.label}
                    </label>
                    {filterState.enabled && (
                      <input
                        type="text"
                        className="form-input"
                        value={filterState.value}
                        onChange={(e) => setActiveFilters(prev => ({
                          ...prev,
                          [param.paramId]: { ...filterState, value: e.target.value }
                        }))}
                        placeholder={param.description || param.paramId}
                        style={{ width: '250px', padding: '0.25rem 0.5rem', fontSize: '0.875rem' }}
                      />
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* 버튼 */}
          <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem', paddingTop: '0.75rem', borderTop: '1px solid var(--border-color)' }}>
            <button className="btn btn-primary" onClick={() => handleTriggerExecution(true)}>
              실행
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => {
                setShowExecutionOptions(false);
                setStartTime('');
                setEndTime('');
                setUseTimeRange(false);
                setActiveFilters({});
              }}
            >
              취소
            </button>
          </div>
          <p style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--gray-500)' }}>
            * 옵션 미선택 시 전체 데이터 대상 실행
          </p>
        </div>
      )}

      {/* 탭 네비게이션 */}
      <div style={{ display: 'flex', gap: '0', marginBottom: '1.5rem', borderBottom: '2px solid var(--gray-200)' }}>
        <TabButton active={activeTab === 'info'} onClick={() => setActiveTab('info')}>
          기본정보
        </TabButton>
        <TabButton active={activeTab === 'monitor'} onClick={() => setActiveTab('monitor')} highlight={agent.status === 'RUNNING'}>
          모니터링 {agent.status === 'RUNNING' && '●'}
        </TabButton>
        <TabButton active={activeTab === 'history'} onClick={() => setActiveTab('history')}>
          실행이력 ({executions.length})
        </TabButton>
      </div>

      {/* 탭 컨텐츠 */}
      {activeTab === 'info' && (
        <InfoTab agent={agent} schedules={schedules} onUpdate={fetchData} />
      )}
      {activeTab === 'monitor' && (
        <MonitorTab agent={agent} onExecutionComplete={fetchData} />
      )}
      {activeTab === 'history' && (
        <HistoryTab executions={executions} />
      )}
    </div>
  );
}
