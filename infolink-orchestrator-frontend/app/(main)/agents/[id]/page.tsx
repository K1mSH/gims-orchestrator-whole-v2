'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { agentApi, scheduleApi, executionHistoryApi, executionApi } from '@/lib/api';
import type { Agent, Schedule, ExecutionHistory, ExecutionCondition, DatasourceTable } from '@/types';
import { CONDITION_OPERATORS } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import TabButton from '@/components/agent/TabButton';
import InfoTab from '@/components/agent/InfoTab';
import HistoryTab from '@/components/agent/HistoryTab';
import styles from './page.module.css';

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

  const [showExecutionOptions, setShowExecutionOptions] = useState(false);
  const [conditions, setConditions] = useState<ExecutionCondition[]>([]);
  const [condTableSelections, setCondTableSelections] = useState<string[]>([]);
  const [sourceTables, setSourceTables] = useState<DatasourceTable[]>([]);

  const fetchData = useCallback(async () => {
    try {
      const agentData = await agentApi.getById(agentId);
      setAgent(agentData);

      if (agentData.agentType !== 'DB_CON_PROXY') {
        const [schedulesData, executionsData] = await Promise.all([
          scheduleApi.getByAgentId(agentId),
          executionHistoryApi.getByAgent(agentId),
        ]);
        setSchedules(schedulesData);
        setExecutions(executionsData);
      }
      try {
        const tables = await agentApi.getSelectTables(agentId);
        setSourceTables(tables.filter((t) => t.columns && t.columns.length > 0));
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
    const validConditions: ExecutionCondition[] = withOptions
      ? conditions
          .map((c, idx) => ({ ...c, tableName: condTableSelections[idx] || undefined }))
          .filter(
            (c) =>
              c.column.trim() &&
              (c.operator === 'IS_NULL' || c.operator === 'IS_NOT_NULL' || (c.value && c.value.trim()))
          )
      : [];

    const parts: string[] = [];
    if (validConditions.length > 0) {
      const opLabels: Record<string, string> = Object.fromEntries(
        CONDITION_OPERATORS.map((o) => [o.value, o.label])
      );
      parts.push(
        `WHERE 조건 ${validConditions.length}개: ${validConditions
          .map(
            (c) =>
              `${c.column} ${opLabels[c.operator] || c.operator} ${c.value || ''}${
                c.value2 ? ` ~ ${c.value2}` : ''
              }`
          )
          .join(', ')}`
      );
    }

    const confirmMsg =
      parts.length > 0
        ? `조건실행:\n${parts.join('\n')}\n\n실행하시겠습니까?`
        : '파이프라인을 실행하시겠습니까? (증분 동기화)';

    if (!confirm(confirmMsg)) return;

    try {
      await executionApi.trigger(agentId, validConditions.length > 0 ? validConditions : undefined);
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
    return <div className="app-loading">로딩중...</div>;
  }

  if (!agent) {
    return <div className="app-empty">Agent를 찾을 수 없습니다</div>;
  }

  const isProxy = agent.agentType === 'DB_CON_PROXY';

  return (
    <div>
      {/* 헤더 */}
      <div className="app-page-header">
        <div className={styles.headerLeft}>
          <h1 className="app-page-header__title">{agent.agentName}</h1>
          <StatusBadge status={agent.status} />
          {isProxy && <span className={styles.proxyBadge}>DB Proxy</span>}
        </div>
        <div className={styles.headerActions}>
          <button type="button" className="krds-btn small primary" onClick={handleHealthCheck}>
            상태확인
          </button>
          {!isProxy && (
            <>
              <button
                type="button"
                className="krds-btn small primary"
                onClick={() => handleTriggerExecution(false)}
                disabled={agent.status !== 'ONLINE'}
              >
                실행
              </button>
              <button
                type="button"
                className="krds-btn small secondary"
                onClick={() => setShowExecutionOptions(!showExecutionOptions)}
                disabled={agent.status !== 'ONLINE'}
              >
                조건실행 ▾
              </button>
            </>
          )}
          <button type="button" className="krds-btn small app-btn-danger" onClick={handleDelete}>
            삭제
          </button>
        </div>
      </div>

      {/* 조건실행 패널 (프록시 Agent 에서는 숨김) */}
      {!isProxy && showExecutionOptions && (
        <div className={styles.optionsPanel}>
          <div>
            <div className={styles.optionsHeader}>
              <span className={styles.optionsLabel}>WHERE 조건</span>
              <button
                type="button"
                className="krds-btn xsmall secondary"
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
              const selectedTable = sourceTables.find((t) => t.tableName === selectedTableName);
              const columns = selectedTable?.columns || [];
              const selectedColumn = columns.find((c) => c.columnName === cond.column);
              const colDataType = selectedColumn?.dataType?.toLowerCase() || '';

              const isDateType = ['date', 'timestamp', 'time'].includes(colDataType);
              const isNumericType = [
                'numeric', 'int4', 'int8', 'serial', 'integer', 'float', 'double',
                'bigint', 'smallint', 'decimal', 'real',
              ].includes(colDataType);
              const isTextType = !isDateType && !isNumericType;

              const allowedOperators = CONDITION_OPERATORS.filter((op) => {
                if (!cond.column || !selectedColumn) return true;
                if (op.value === 'LIKE') return isTextType;
                if (op.value === 'IN') return !isDateType;
                return true;
              });

              const isOperatorAllowed = allowedOperators.some((op) => op.value === cond.operator);

              const inputType =
                colDataType === 'date'
                  ? 'date'
                  : colDataType === 'timestamp'
                  ? 'datetime-local'
                  : colDataType === 'time'
                  ? 'time'
                  : isNumericType
                  ? 'number'
                  : 'text';

              const isDateTimeLocal = inputType === 'datetime-local';

              return (
                <div key={idx} className={styles.conditionRow}>
                  {sourceTables.length > 0 ? (
                    <>
                      <select
                        className={`${styles.conditionInput} ${styles['conditionInput--table']}`}
                        value={selectedTableName}
                        onChange={(e) => {
                          const newSelections = [...condTableSelections];
                          newSelections[idx] = e.target.value;
                          setCondTableSelections(newSelections);
                          const updated = [...conditions];
                          updated[idx] = { ...cond, column: '', operator: 'EQ', value: '', value2: undefined };
                          setConditions(updated);
                        }}
                      >
                        {sourceTables.map((t) => (
                          <option key={t.id} value={t.tableName}>
                            {t.tableAlias ? `${t.tableName} (${t.tableAlias})` : t.tableName}
                          </option>
                        ))}
                      </select>
                      <select
                        className={`${styles.conditionInput} ${styles['conditionInput--column']}`}
                        value={cond.column}
                        onChange={(e) => {
                          const updated = [...conditions];
                          updated[idx] = {
                            ...cond,
                            column: e.target.value,
                            operator: 'EQ',
                            value: '',
                            value2: undefined,
                          };
                          setConditions(updated);
                        }}
                      >
                        <option value="">컬럼 선택</option>
                        {columns.map((c) => (
                          <option key={c.columnName} value={c.columnName}>
                            {c.columnAlias ? `${c.columnName} (${c.columnAlias})` : c.columnName}
                          </option>
                        ))}
                      </select>
                    </>
                  ) : (
                    <input
                      type="text"
                      className={`${styles.conditionInput} ${styles['conditionInput--column']}`}
                      value={cond.column}
                      onChange={(e) => {
                        const updated = [...conditions];
                        updated[idx] = { ...cond, column: e.target.value };
                        setConditions(updated);
                      }}
                      placeholder="컬럼명"
                    />
                  )}
                  <select
                    className={`${styles.conditionInput} ${styles['conditionInput--operator']}`}
                    value={isOperatorAllowed ? cond.operator : 'EQ'}
                    onChange={(e) => {
                      const updated = [...conditions];
                      updated[idx] = {
                        ...cond,
                        operator: e.target.value as ExecutionCondition['operator'],
                        value: '',
                        value2: undefined,
                      };
                      setConditions(updated);
                    }}
                  >
                    {allowedOperators.map((op) => (
                      <option key={op.value} value={op.value}>
                        {op.label}
                      </option>
                    ))}
                  </select>
                  {cond.operator !== 'IS_NULL' && cond.operator !== 'IS_NOT_NULL' && (
                    <input
                      type={cond.operator === 'IN' ? 'text' : inputType}
                      className={`${styles.conditionInput} ${
                        isDateTimeLocal ? styles['conditionInput--valueDate'] : styles['conditionInput--value']
                      }`}
                      value={cond.value || ''}
                      onChange={(e) => {
                        const updated = [...conditions];
                        updated[idx] = { ...cond, value: e.target.value };
                        setConditions(updated);
                      }}
                      placeholder={
                        cond.operator === 'IN' ? '값1,값2,...' : cond.operator === 'LIKE' ? '%값%' : '값'
                      }
                    />
                  )}
                  {cond.operator === 'BETWEEN' && (
                    <input
                      type={inputType}
                      className={`${styles.conditionInput} ${
                        isDateTimeLocal ? styles['conditionInput--valueDate'] : styles['conditionInput--value2']
                      }`}
                      value={cond.value2 || ''}
                      onChange={(e) => {
                        const updated = [...conditions];
                        updated[idx] = { ...cond, value2: e.target.value };
                        setConditions(updated);
                      }}
                      placeholder="종료 값"
                    />
                  )}
                  <button
                    type="button"
                    className="krds-btn xsmall app-btn-danger"
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
              <div className={styles.conditionEmpty}>조건 없음 (기본 증분 동기화)</div>
            )}
          </div>

          <div className={styles.optionsFooter}>
            <button
              type="button"
              className="krds-btn small primary"
              onClick={() => handleTriggerExecution(true)}
            >
              실행
            </button>
            <button
              type="button"
              className="krds-btn small tertiary"
              onClick={() => {
                setShowExecutionOptions(false);
                setConditions([]);
                setCondTableSelections([]);
              }}
            >
              취소
            </button>
          </div>
          <p className={styles.optionsHelp}>* 옵션 미선택 시 전체 데이터 대상 실행</p>
        </div>
      )}

      {/* 탭 네비게이션 */}
      <div className={`krds-tab-area ${styles.tabsWrap}`}>
        <div className="tab line">
          <ul>
            <TabButton active={activeTab === 'info'} onClick={() => setActiveTab('info')}>
              기본정보
            </TabButton>
            {!isProxy && (
              <TabButton active={activeTab === 'history'} onClick={() => setActiveTab('history')}>
                실행이력 ({executions.length})
              </TabButton>
            )}
          </ul>
        </div>
      </div>

      {/* 탭 컨텐츠 */}
      {activeTab === 'info' && <InfoTab agent={agent} schedules={schedules} onUpdate={fetchData} />}
      {activeTab === 'history' && <HistoryTab executions={executions} />}
    </div>
  );
}
