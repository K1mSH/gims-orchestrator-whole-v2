'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { agentApi, scheduleApi, executionHistoryApi, executionApi } from '@/lib/api';
import type { Agent, Schedule, ExecutionHistory, ExecutionCondition, DatasourceTable, DatasourceColumn, AgentType } from '@/types';
import { CONDITION_OPERATORS } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import TabButton from '@/components/agent/TabButton';
import InfoTab from '@/components/agent/InfoTab';
import HistoryTab from '@/components/agent/HistoryTab';

const STATUS_LABELS: Record<string, string> = {
  ONLINE: '온라인',
  OFFLINE: '오프라인',
  RUNNING: '실행중',
};

type TabType = 'info' | 'history';

export default function AgentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const searchParams = useSearchParams();
  const agentId = Number(params.id);

  const [activeTab, setActiveTab] = useState<TabType>(
    searchParams.get('tab') === 'history' ? 'history' : 'info'
  );
  const [agent, setAgent] = useState<Agent | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [executions, setExecutions] = useState<ExecutionHistory[]>([]);
  const [loading, setLoading] = useState(true);

  // 조건실행 패널 상태
  const [showExecutionOptions, setShowExecutionOptions] = useState(false);
  // 동적 WHERE 조건 상태
  const [conditions, setConditions] = useState<ExecutionCondition[]>([]);
  const [condTableSelections, setCondTableSelections] = useState<string[]>([]); // 조건별 테이블 선택 (UI용)
  // 소스 테이블/컬럼 정보 (WHERE 조건 드롭다운용)
  const [sourceTables, setSourceTables] = useState<DatasourceTable[]>([]);

  const fetchData = useCallback(async () => {
    try {
      const agentData = await agentApi.getById(agentId);
      setAgent(agentData);

      // 프록시 Agent는 스케줄/실행이력 불필요
      if (agentData.agentType !== 'DB_CON_PROXY') {
        const [schedulesData, executionsData] = await Promise.all([
          scheduleApi.getByAgentId(agentId),
          executionHistoryApi.getByAgent(agentId),
        ]);
        setSchedules(schedulesData);
        setExecutions(executionsData);
      }
      // WHERE 조건 대상 테이블 로드 (Agent YML의 select-tables 기반)
      try {
        const tables = await agentApi.getSelectTables(agentId);
        setSourceTables(tables.filter(t => t.columns && t.columns.length > 0));
      } catch {
        // 테이블 정보 로드 실패 시 무시 (수동 입력 fallback)
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
    // 동적 WHERE 조건: 유효한 조건만 필터링
    const validConditions: ExecutionCondition[] = withOptions
      ? conditions
          .map((c, idx) => ({ ...c, tableName: condTableSelections[idx] || undefined }))
          .filter(c => c.column.trim() && (
            c.operator === 'IS_NULL' || c.operator === 'IS_NOT_NULL' || (c.value && c.value.trim())
          ))
      : [];

    const parts: string[] = [];
    if (validConditions.length > 0) {
      const opLabels: Record<string, string> = Object.fromEntries(CONDITION_OPERATORS.map(o => [o.value, o.label]));
      parts.push(`WHERE 조건 ${validConditions.length}개: ${validConditions.map(c => `${c.column} ${opLabels[c.operator] || c.operator} ${c.value || ''}${c.value2 ? ` ~ ${c.value2}` : ''}`).join(', ')}`);
    }

    const confirmMsg = parts.length > 0
      ? `조건실행:\n${parts.join('\n')}\n\n실행하시겠습니까?`
      : '파이프라인을 실행하시겠습니까? (증분 동기화)';

    if (!confirm(confirmMsg)) return;

    try {
      await executionApi.trigger(
        agentId,
        validConditions.length > 0 ? validConditions : undefined
      );
      setActiveTab('history');
      setShowExecutionOptions(false);
      setConditions([]);
      setCondTableSelections([]);
      fetchData();
    } catch (error) {
      console.error('실행 실패:', error);
      alert('실행에 실패했습니다.');
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

  const isProxy = agent.agentType === 'DB_CON_PROXY';

  return (
    <div>
      {/* 헤더 */}
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <h1 className="page-title">{agent.agentName}</h1>
          <StatusBadge status={agent.status} />
          {isProxy && (
            <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', borderRadius: '4px', background: '#8b5cf620', color: '#8b5cf6', fontWeight: 500 }}>
              DB Proxy
            </span>
          )}
        </div>
        <div>
          <button className="btn btn-primary" onClick={handleHealthCheck} style={{ marginRight: '0.5rem' }}>
            상태확인
          </button>
          {!isProxy && (
            <>
              <button className="btn btn-primary" onClick={() => handleTriggerExecution(false)} disabled={agent.status !== 'ONLINE'} style={{ marginRight: '0.25rem' }}>
                실행
              </button>
              <button className="btn btn-secondary" onClick={() => setShowExecutionOptions(!showExecutionOptions)} disabled={agent.status !== 'ONLINE'} style={{ marginRight: '0.5rem' }}>
                조건실행 ▾
              </button>
            </>
          )}
          <button className="btn btn-danger" onClick={handleDelete}>
            삭제
          </button>
        </div>
      </div>

      {/* 조건실행 패널 (프록시 Agent에서는 숨김) */}
      {!isProxy && showExecutionOptions && (
        <div className="card" style={{ marginBottom: '1rem', padding: '1rem', background: 'var(--gray-50)' }}>
          {/* 동적 WHERE 조건 */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
              <span style={{ fontWeight: 500 }}>WHERE 조건</span>
              <button
                className="btn btn-secondary"
                style={{ padding: '0.15rem 0.5rem', fontSize: '0.75rem' }}
                onClick={() => {
                  setConditions([...conditions, { column: '', operator: 'EQ', value: '' }]);
                  setCondTableSelections([...condTableSelections, sourceTables[0]?.tableName || '']);
                }}
              >
                + 조건 추가
              </button>
            </div>
            {conditions.map((cond, idx) => {
              const selectedTableName = condTableSelections[idx] || '';
              const selectedTable = sourceTables.find(t => t.tableName === selectedTableName);
              const columns = selectedTable?.columns || [];
              const selectedColumn = columns.find(c => c.columnName === cond.column);
              const colDataType = selectedColumn?.dataType?.toLowerCase() || '';

              // dataType 카테고리 분류
              const isDateType = ['date', 'timestamp', 'time'].includes(colDataType);
              const isNumericType = ['numeric', 'int4', 'int8', 'serial', 'integer', 'float', 'double', 'bigint', 'smallint', 'decimal', 'real'].includes(colDataType);
              const isTextType = !isDateType && !isNumericType;

              // dataType별 허용 연산자 필터링
              const allowedOperators = CONDITION_OPERATORS.filter(op => {
                if (!cond.column || !selectedColumn) return true; // 컬럼 미선택 시 전체
                if (op.value === 'LIKE') return isTextType;
                if (op.value === 'IN') return !isDateType;
                return true;
              });

              // 현재 연산자가 허용 목록에 없으면 EQ로 리셋
              const isOperatorAllowed = allowedOperators.some(op => op.value === cond.operator);

              // input type 결정
              const inputType = colDataType === 'date' ? 'date'
                : colDataType === 'timestamp' ? 'datetime-local'
                : colDataType === 'time' ? 'time'
                : isNumericType ? 'number'
                : 'text';

              return (
                <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem', marginLeft: '0.5rem', flexWrap: 'wrap' }}>
                  {sourceTables.length > 0 ? (
                    <>
                      <select
                        className="form-input"
                        value={selectedTableName}
                        onChange={(e) => {
                          const newSelections = [...condTableSelections];
                          newSelections[idx] = e.target.value;
                          setCondTableSelections(newSelections);
                          const updated = [...conditions];
                          updated[idx] = { ...cond, column: '', operator: 'EQ', value: '', value2: undefined };
                          setConditions(updated);
                        }}
                        style={{ width: '180px', padding: '0.25rem 0.3rem', fontSize: '0.8rem' }}
                      >
                        {sourceTables.map(t => (
                          <option key={t.id} value={t.tableName}>
                            {t.tableAlias ? `${t.tableName} (${t.tableAlias})` : t.tableName}
                          </option>
                        ))}
                      </select>
                      <select
                        className="form-input"
                        value={cond.column}
                        onChange={(e) => {
                          const updated = [...conditions];
                          updated[idx] = { ...cond, column: e.target.value, operator: 'EQ', value: '', value2: undefined };
                          setConditions(updated);
                        }}
                        style={{ width: '160px', padding: '0.25rem 0.3rem', fontSize: '0.8rem' }}
                      >
                        <option value="">컬럼 선택</option>
                        {columns.map(c => (
                          <option key={c.columnName} value={c.columnName}>
                            {c.columnAlias ? `${c.columnName} (${c.columnAlias})` : c.columnName}
                          </option>
                        ))}
                      </select>
                    </>
                  ) : (
                    <input
                      type="text"
                      className="form-input"
                      value={cond.column}
                      onChange={(e) => {
                        const updated = [...conditions];
                        updated[idx] = { ...cond, column: e.target.value };
                        setConditions(updated);
                      }}
                      placeholder="컬럼명"
                      style={{ width: '150px', padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
                    />
                  )}
                  <select
                    className="form-input"
                    value={isOperatorAllowed ? cond.operator : 'EQ'}
                    onChange={(e) => {
                      const updated = [...conditions];
                      updated[idx] = { ...cond, operator: e.target.value as ExecutionCondition['operator'], value: '', value2: undefined };
                      setConditions(updated);
                    }}
                    style={{ width: '110px', padding: '0.25rem 0.3rem', fontSize: '0.8rem' }}
                  >
                    {allowedOperators.map(op => (
                      <option key={op.value} value={op.value}>{op.label}</option>
                    ))}
                  </select>
                  {cond.operator !== 'IS_NULL' && cond.operator !== 'IS_NOT_NULL' && (
                    <input
                      type={cond.operator === 'IN' ? 'text' : inputType}
                      className="form-input"
                      value={cond.value || ''}
                      onChange={(e) => {
                        const updated = [...conditions];
                        updated[idx] = { ...cond, value: e.target.value };
                        setConditions(updated);
                      }}
                      placeholder={cond.operator === 'IN' ? '값1,값2,...' : cond.operator === 'LIKE' ? '%값%' : '값'}
                      style={{ width: inputType === 'datetime-local' ? '220px' : '180px', padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
                    />
                  )}
                  {cond.operator === 'BETWEEN' && (
                    <input
                      type={inputType}
                      className="form-input"
                      value={cond.value2 || ''}
                      onChange={(e) => {
                        const updated = [...conditions];
                        updated[idx] = { ...cond, value2: e.target.value };
                        setConditions(updated);
                      }}
                      placeholder="종료 값"
                      style={{ width: inputType === 'datetime-local' ? '220px' : '150px', padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
                    />
                  )}
                  <button
                    className="btn btn-danger"
                    style={{ padding: '0.15rem 0.4rem', fontSize: '0.75rem' }}
                    onClick={() => {
                      setConditions(conditions.filter((_, i) => i !== idx));
                      setCondTableSelections(condTableSelections.filter((_, i) => i !== idx));
                    }}
                  >
                    X
                  </button>
                </div>
              );
            })}
            {conditions.length === 0 && (
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginLeft: '0.5rem' }}>
                조건 없음 (기본 증분 동기화)
              </div>
            )}
          </div>

          {/* 버튼 */}
          <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem', paddingTop: '0.75rem', borderTop: '1px solid var(--border-color)' }}>
            <button className="btn btn-primary" onClick={() => handleTriggerExecution(true)}>
              실행
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => {
                setShowExecutionOptions(false);
                setConditions([]);
                setCondTableSelections([]);
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
        {!isProxy && (
          <TabButton active={activeTab === 'history'} onClick={() => setActiveTab('history')}>
            실행이력 ({executions.length})
          </TabButton>
        )}
      </div>

      {/* 탭 컨텐츠 */}
      {activeTab === 'info' && (
        <InfoTab agent={agent} schedules={schedules} onUpdate={fetchData} />
      )}
      {activeTab === 'history' && (
        <HistoryTab executions={executions} />
      )}
    </div>
  );
}
