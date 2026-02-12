'use client';

import { useEffect, useState } from 'react';
import { agentApi, scheduleApi, datasourceApi } from '@/lib/api';
import type { Agent, Zone, ScheduleCreateRequest, DatasourceSimple, DatasourceTable, AgentType, ExecutionParamDefinition, ExecutionParamInput } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import Link from 'next/link';

const ZONE_LABELS: Record<Zone, string> = {
  EXTERNAL: '외부망',
  DMZ: 'DMZ',
  INTERNAL_COMMON: '내부공통망',
  INTERNAL_SERVICE: '내부서비스망',
};

const AGENT_TYPE_LABELS: Record<AgentType, string> = {
  RELAY: 'Relay',
  LOADER_STANDARD: 'Loader(표준)',
  LOADER_CUSTOM: 'Loader(커스텀)',
};

const AGENT_TYPE_COLORS: Record<AgentType, string> = {
  RELAY: '#6366f1',      // indigo
  LOADER_STANDARD: '#10b981', // emerald
  LOADER_CUSTOM: '#f59e0b',   // amber
};

export default function AgentsPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    fetchAgents();
  }, []);

  const fetchAgents = async () => {
    try {
      const data = await agentApi.getAll();
      setAgents(data);
    } catch (error) {
      console.error('Agent 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (agentId: string) => {
    if (!confirm('정말 삭제하시겠습니까?')) return;
    try {
      await agentApi.delete(agentId);
      fetchAgents();
    } catch (error) {
      console.error('Agent 삭제 실패:', error);
    }
  };

  const handleHealthCheck = async (agentId: string) => {
    try {
      const result = await agentApi.healthCheck(agentId);
      const statusLabels: Record<string, string> = {
        ONLINE: '온라인',
        OFFLINE: '오프라인',
        RUNNING: '실행중',
      };
      alert(`상태확인 결과: ${statusLabels[result.status] || result.status}`);
      fetchAgents(); // 상태 업데이트 반영
    } catch (error) {
      console.error('상태확인 실패:', error);
      alert('상태확인 실패');
    }
  };

  if (loading) {
    return <div className="loading">로딩중...</div>;
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Agent 관리</h1>
        <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? '취소' : 'Agent 등록'}
        </button>
      </div>

      {showForm && (
        <AgentForm
          onSuccess={() => {
            setShowForm(false);
            fetchAgents();
          }}
        />
      )}

      <div className="card">
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Agent ID</th>
                <th>타입</th>
                <th>망구분</th>
                <th>상태</th>
                <th>마지막 실행</th>
                <th>실행 결과</th>
                <th>작업</th>
              </tr>
            </thead>
            <tbody>
              {agents.length === 0 ? (
                <tr>
                  <td colSpan={7} className="empty-state">
                    등록된 Agent가 없습니다
                  </td>
                </tr>
              ) : (
                agents.map((agent) => (
                  <tr key={agent.agentId}>
                    <td>
                      <Link href={`/agents/${agent.agentId}`}>{agent.agentId}</Link>
                    </td>
                    <td>
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '0.25rem 0.5rem',
                          borderRadius: '0.25rem',
                          fontSize: '0.75rem',
                          fontWeight: 500,
                          backgroundColor: `${AGENT_TYPE_COLORS[agent.agentType as AgentType] || '#6b7280'}20`,
                          color: AGENT_TYPE_COLORS[agent.agentType as AgentType] || '#6b7280',
                        }}
                      >
                        {AGENT_TYPE_LABELS[agent.agentType as AgentType] || agent.agentType}
                      </span>
                    </td>
                    <td>
                      <span className={`zone-${agent.zone.toLowerCase().replace('_', '-')}`}>
                        {ZONE_LABELS[agent.zone as Zone]}
                      </span>
                    </td>
                    <td>
                      <StatusBadge status={agent.status} />
                    </td>
                    <td>
                      {agent.lastExecutedAt
                        ? new Date(agent.lastExecutedAt).toLocaleString('ko-KR')
                        : '-'}
                    </td>
                    <td>
                      {agent.lastExecutionStatus ? (
                        <StatusBadge status={agent.lastExecutionStatus as 'SUCCESS' | 'FAILED' | 'RUNNING'} />
                      ) : '-'}
                    </td>
                    <td>
                      <button
                        className="btn btn-primary btn-sm"
                        onClick={() => handleHealthCheck(agent.agentId)}
                        style={{ marginRight: '0.5rem' }}
                      >
                        상태확인
                      </button>
                      <Link href={`/agents/${agent.agentId}`}>
                        <button className="btn btn-secondary btn-sm" style={{ marginRight: '0.5rem' }}>
                          상세
                        </button>
                      </Link>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDelete(agent.agentId)}
                      >
                        삭제
                      </button>
                    </td>
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

function AgentForm({ onSuccess }: { onSuccess: () => void }) {
  const [formData, setFormData] = useState({
    agentId: '',
    agentName: '',
    zone: 'DMZ',
    agentType: 'LOADER_CUSTOM' as AgentType,
    endpointUrl: '',
    description: '',
    sourceDatasourceId: '',
    targetDatasourceId: '',
  });
  const [scheduleData, setScheduleData] = useState({
    cronExpression: '0 0 * * * *',
    isEnabled: true,
  });
  const [submitting, setSubmitting] = useState(false);

  // Datasource 관련
  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  const [sourceTables, setSourceTables] = useState<DatasourceTable[]>([]);
  const [targetTables, setTargetTables] = useState<DatasourceTable[]>([]);
  const [selectedSourceTableIds, setSelectedSourceTableIds] = useState<number[]>([]);
  const [selectedTargetTableIds, setSelectedTargetTableIds] = useState<number[]>([]);

  // 실행 옵션
  const [fetchedParams, setFetchedParams] = useState<ExecutionParamDefinition[]>([]);
  const [selectedParamIds, setSelectedParamIds] = useState<Set<string>>(new Set());
  const [fetchingParams, setFetchingParams] = useState(false);

  // Datasource 목록 로드
  useEffect(() => {
    const loadDatasources = async () => {
      try {
        const data = await datasourceApi.getSimple();
        setDatasources(data);
      } catch (error) {
        console.error('Datasource 목록 조회 실패:', error);
      }
    };
    loadDatasources();
  }, []);

  // Source Datasource 변경 시 테이블 로드
  useEffect(() => {
    if (!formData.sourceDatasourceId) {
      setSourceTables([]);
      setSelectedSourceTableIds([]);
      return;
    }
    const loadTables = async () => {
      try {
        const tables = await datasourceApi.getRegisteredTables(formData.sourceDatasourceId);
        setSourceTables(tables);
        setSelectedSourceTableIds([]);
      } catch (error) {
        console.error('Source 테이블 조회 실패:', error);
      }
    };
    loadTables();
  }, [formData.sourceDatasourceId]);

  // Target Datasource 변경 시 테이블 로드
  useEffect(() => {
    if (!formData.targetDatasourceId) {
      setTargetTables([]);
      setSelectedTargetTableIds([]);
      return;
    }
    const loadTables = async () => {
      try {
        const tables = await datasourceApi.getRegisteredTables(formData.targetDatasourceId);
        setTargetTables(tables);
        setSelectedTargetTableIds([]);
      } catch (error) {
        console.error('Target 테이블 조회 실패:', error);
      }
    };
    loadTables();
  }, [formData.targetDatasourceId]);

  const handleFetchExecutionParams = async () => {
    if (!formData.endpointUrl) {
      alert('Endpoint URL을 먼저 입력하세요.');
      return;
    }
    if (!formData.agentId) {
      alert('Agent ID를 먼저 입력하세요.');
      return;
    }
    setFetchingParams(true);
    try {
      // Agent가 아직 등록되지 않았으므로 직접 Agent URL 호출
      const response = await fetch(`${formData.endpointUrl}/api/pipeline/execution-params`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const params: ExecutionParamDefinition[] = await response.json();
      setFetchedParams(params);
      // 기본적으로 모두 선택
      setSelectedParamIds(new Set(params.map(p => p.paramId)));
    } catch (error) {
      console.error('실행 옵션 가져오기 실패:', error);
      alert('실행 옵션을 가져오지 못했습니다. Agent URL을 확인하세요.');
    } finally {
      setFetchingParams(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      // 선택된 실행 옵션 변환
      const executionParams: ExecutionParamInput[] = fetchedParams
        .filter(p => selectedParamIds.has(p.paramId))
        .map(p => ({
          paramId: p.paramId,
          label: p.label,
          description: p.description,
          dataType: p.dataType,
          defaultValue: p.defaultValue || undefined,
          isEnabled: true,
          displayOrder: p.displayOrder,
        }));

      // 1. Agent 등록
      await agentApi.create({
        ...formData,
        sourceTableIds: selectedSourceTableIds,
        targetTableIds: selectedTargetTableIds,
        executionParams: executionParams.length > 0 ? executionParams : undefined,
      });

      // 2. 스케줄 등록 (필수)
      const scheduleReq: ScheduleCreateRequest = {
        agentId: formData.agentId,
        cronExpression: scheduleData.cronExpression,
        isEnabled: scheduleData.isEnabled,
      };
      await scheduleApi.create(scheduleReq);

      onSuccess();
    } catch (error) {
      console.error('Agent 등록 실패:', error);
      alert('등록에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleTableSelection = (tableId: number, type: 'source' | 'target') => {
    if (type === 'source') {
      setSelectedSourceTableIds(prev =>
        prev.includes(tableId) ? prev.filter(id => id !== tableId) : [...prev, tableId]
      );
    } else {
      setSelectedTargetTableIds(prev =>
        prev.includes(tableId) ? prev.filter(id => id !== tableId) : [...prev, tableId]
      );
    }
  };

  return (
    <div className="card">
      <h3 className="card-title" style={{ marginBottom: '1rem' }}>
        Agent 등록
      </h3>
      <form onSubmit={handleSubmit}>
        {/* 기본 정보 */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Agent ID</label>
            <input
              type="text"
              className="form-input"
              value={formData.agentId}
              onChange={(e) => setFormData({ ...formData, agentId: e.target.value })}
              placeholder="agent-a"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">이름</label>
            <input
              type="text"
              className="form-input"
              value={formData.agentName}
              onChange={(e) => setFormData({ ...formData, agentName: e.target.value })}
              placeholder="주문 추출 Agent"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Agent 타입</label>
            <select
              className="form-select"
              value={formData.agentType}
              onChange={(e) => setFormData({ ...formData, agentType: e.target.value as AgentType })}
            >
              <option value="RELAY">Relay (추출 전달)</option>
              <option value="LOADER_STANDARD">Loader 표준 (컬럼 매핑)</option>
              <option value="LOADER_CUSTOM">Loader 커스텀 (로직 구현)</option>
            </select>
            <small style={{ color: 'var(--gray-500)' }}>
              {formData.agentType === 'RELAY' ? 'source → IF 추출 전달 (별도 프로세스)' :
               formData.agentType === 'LOADER_STANDARD' ? '1:N 컬럼 매핑 기반 데이터 미러링' :
               '직접 구현한 동기화 로직 사용'}
            </small>
          </div>
          <div className="form-group">
            <label className="form-label">망구분</label>
            <select
              className="form-select"
              value={formData.zone}
              onChange={(e) => setFormData({ ...formData, zone: e.target.value })}
            >
              <option value="EXTERNAL">외부망</option>
              <option value="DMZ">DMZ</option>
              <option value="INTERNAL_COMMON">내부공통망</option>
              <option value="INTERNAL_SERVICE">내부서비스망</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Endpoint URL</label>
            <input
              type="url"
              className="form-input"
              value={formData.endpointUrl}
              onChange={(e) => setFormData({ ...formData, endpointUrl: e.target.value })}
              placeholder="http://localhost:8081"
              required
            />
          </div>
          <div className="form-group" style={{ gridColumn: '1 / -1' }}>
            <label className="form-label">설명 (선택)</label>
            <input
              type="text"
              className="form-input"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              placeholder="외부망에서 주문 데이터를 추출하는 Agent"
            />
          </div>
        </div>

        {/* Datasource 및 테이블 설정 */}
        <div style={{ marginTop: '1.5rem', padding: '1rem', background: 'var(--gray-50)', borderRadius: '0.5rem' }}>
          <strong style={{ marginBottom: '1rem', display: 'block' }}>Datasource 및 테이블 설정</strong>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            {/* Source */}
            <div>
              <div className="form-group">
                <label className="form-label">Source Datasource</label>
                <select
                  className="form-select"
                  value={formData.sourceDatasourceId}
                  onChange={(e) => setFormData({ ...formData, sourceDatasourceId: e.target.value })}
                >
                  <option value="">선택하세요</option>
                  {datasources.map(ds => (
                    <option key={ds.datasourceId} value={ds.datasourceId}>
                      {ds.datasourceName} ({ds.dbType})
                    </option>
                  ))}
                </select>
              </div>
              {sourceTables.length > 0 && (
                <div className="form-group">
                  <label className="form-label">Source 테이블 선택 ({selectedSourceTableIds.length}/{sourceTables.length})</label>
                  <div style={{ maxHeight: '150px', overflow: 'auto', border: '1px solid var(--border-color)', borderRadius: '0.375rem', padding: '0.5rem' }}>
                    {sourceTables.map(table => (
                      <label key={table.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.25rem 0', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={selectedSourceTableIds.includes(table.id)}
                          onChange={() => toggleTableSelection(table.id, 'source')}
                        />
                        <span>{table.tableName}</span>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>({table.columns.length}컬럼)</span>
                      </label>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Target */}
            <div>
              <div className="form-group">
                <label className="form-label">Target Datasource</label>
                <select
                  className="form-select"
                  value={formData.targetDatasourceId}
                  onChange={(e) => setFormData({ ...formData, targetDatasourceId: e.target.value })}
                >
                  <option value="">선택하세요</option>
                  {datasources.map(ds => (
                    <option key={ds.datasourceId} value={ds.datasourceId}>
                      {ds.datasourceName} ({ds.dbType})
                    </option>
                  ))}
                </select>
              </div>
              {targetTables.length > 0 && (
                <div className="form-group">
                  <label className="form-label">Target 테이블 선택 ({selectedTargetTableIds.length}/{targetTables.length})</label>
                  <div style={{ maxHeight: '150px', overflow: 'auto', border: '1px solid var(--border-color)', borderRadius: '0.375rem', padding: '0.5rem' }}>
                    {targetTables.map(table => (
                      <label key={table.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.25rem 0', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={selectedTargetTableIds.includes(table.id)}
                          onChange={() => toggleTableSelection(table.id, 'target')}
                        />
                        <span>{table.tableName}</span>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>({table.columns.length}컬럼)</span>
                      </label>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 실행 옵션 섹션 */}
        <div style={{ marginTop: '1.5rem', padding: '1rem', background: 'var(--gray-50)', borderRadius: '0.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <strong>실행 옵션</strong>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={handleFetchExecutionParams}
              disabled={fetchingParams || !formData.endpointUrl}
            >
              {fetchingParams ? '가져오는 중...' : '실행 옵션 가져오기'}
            </button>
          </div>
          {fetchedParams.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '1rem', color: 'var(--text-muted)', fontSize: '0.875rem' }}>
              Agent URL 입력 후 &quot;실행 옵션 가져오기&quot; 버튼을 클릭하세요.
            </div>
          ) : (
            <div style={{ border: '1px solid var(--border-color)', borderRadius: '0.375rem', padding: '0.5rem' }}>
              {fetchedParams.map(param => (
                <label key={param.paramId} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.375rem 0', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={selectedParamIds.has(param.paramId)}
                    onChange={(e) => {
                      setSelectedParamIds(prev => {
                        const next = new Set(prev);
                        if (e.target.checked) next.add(param.paramId);
                        else next.delete(param.paramId);
                        return next;
                      });
                    }}
                  />
                  <span style={{ fontWeight: 500 }}>{param.label}</span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>({param.paramId})</span>
                  <code style={{ fontSize: '0.75rem' }}>{param.dataType}</code>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: 'auto' }}>{param.description}</span>
                </label>
              ))}
            </div>
          )}
        </div>

        {/* 스케줄 설정 섹션 (필수) */}
        <div style={{ marginTop: '1.5rem', padding: '1rem', background: 'var(--gray-50)', borderRadius: '0.5rem' }}>
          <strong style={{ marginBottom: '1rem', display: 'block' }}>스케줄 설정</strong>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Cron 표현식</label>
              <input
                type="text"
                className="form-input"
                value={scheduleData.cronExpression}
                onChange={(e) => setScheduleData({ ...scheduleData, cronExpression: e.target.value })}
                placeholder="0 0 * * * *"
                required
              />
              <small style={{ color: 'var(--gray-500)' }}>
                형식: 초 분 시 일 월 요일 (예: 매시 정각 = 0 0 * * * *)
              </small>
            </div>
            <div className="form-group">
              <label className="form-label">활성화 여부</label>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <input
                  type="checkbox"
                  checked={scheduleData.isEnabled}
                  onChange={(e) => setScheduleData({ ...scheduleData, isEnabled: e.target.checked })}
                />
                등록 즉시 활성화
              </label>
            </div>
          </div>
        </div>

        <button type="submit" className="btn btn-primary" disabled={submitting} style={{ marginTop: '1rem' }}>
          {submitting ? '등록중...' : '등록'}
        </button>
      </form>
    </div>
  );
}
