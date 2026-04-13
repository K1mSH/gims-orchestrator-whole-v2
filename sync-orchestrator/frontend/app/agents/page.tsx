'use client';

import { useEffect, useState } from 'react';
import { agentApi, scheduleApi, datasourceApi } from '@/lib/api';
import type { Agent, Zone, ScheduleCreateRequest, DatasourceSimple, DatasourceTable, AgentType, DiscoverAgent } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import Link from 'next/link';

const ZONE_LABELS: Record<Zone, string> = {
  EXTERNAL: '외부망',
  DMZ: 'DMZ',
  INTERNAL_COMMON: '내부공통망',
  INTERNAL_SERVICE: '내부서비스망',
};

const AGENT_TYPE_LABELS: Record<AgentType, string> = {
  RCV: '수신(RCV)',
  SND: '송신(SND)',
  LOADER: 'Loader',
  DB_CON_PROXY: 'DB Proxy',
};

const AGENT_TYPE_COLORS: Record<AgentType, string> = {
  RCV: '#6366f1',      // indigo
  SND: '#f59e0b',      // amber
  LOADER: '#10b981',   // emerald
  DB_CON_PROXY: '#8b5cf6', // violet
};

const AGENT_TYPE_ORDER: AgentType[] = ['RCV', 'LOADER', 'SND', 'DB_CON_PROXY'];

export default function AgentsPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  // 그룹 접힘 상태 (기본: 모두 펼침)
  const [collapsedGroups, setCollapsedGroups] = useState<Set<AgentType>>(new Set());

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

  const handleDelete = async (id: number) => {
    if (!confirm('정말 삭제하시겠습니까?')) return;
    try {
      await agentApi.delete(id);
      fetchAgents();
    } catch (error) {
      console.error('Agent 삭제 실패:', error);
    }
  };

  const handleHealthCheck = async (id: number) => {
    try {
      const result = await agentApi.healthCheck(id);
      const statusLabels: Record<string, string> = {
        ONLINE: '온라인',
        OFFLINE: '오프라인',
        RUNNING: '실행중',
      };
      alert(`상태확인 결과: ${statusLabels[result.status] || result.status}`);
      fetchAgents();
    } catch (error) {
      console.error('상태확인 실패:', error);
      alert('상태확인 실패');
    }
  };

  const toggleGroup = (type: AgentType) => {
    setCollapsedGroups(prev => {
      const next = new Set(prev);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });
  };

  // 타입별 그룹핑
  const groupedAgents = AGENT_TYPE_ORDER.map(type => ({
    type,
    agents: agents.filter(a => a.agentType === type).sort((a, b) => a.agentCode.localeCompare(b.agentCode)),
  }));

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

      {/* 타입별 그룹 표시 */}
      {groupedAgents.map(({ type, agents: groupAgents }) => (
        <div key={type} className="card" style={{ marginBottom: '1rem' }}>
          {/* 그룹 헤더 */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              padding: '0.5rem 0',
            }}
            onClick={() => toggleGroup(type)}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <span
                style={{
                  display: 'inline-block',
                  width: '4px',
                  height: '24px',
                  borderRadius: '2px',
                  backgroundColor: AGENT_TYPE_COLORS[type],
                }}
              />
              <h2 style={{ margin: 0, fontSize: '1.1rem' }}>
                {AGENT_TYPE_LABELS[type]}
              </h2>
              <span style={{
                fontSize: '0.85rem',
                color: 'var(--gray-500)',
                fontWeight: 400,
              }}>
                ({groupAgents.length})
              </span>
            </div>
            <span style={{ color: 'var(--gray-400)', fontSize: '0.9rem' }}>
              {collapsedGroups.has(type) ? '▸' : '▾'}
            </span>
          </div>

          {/* 그룹 내용 */}
          {!collapsedGroups.has(type) && (
            <div className="table-container" style={{ marginTop: '0.5rem' }}>
              <table>
                <thead>
                  <tr>
                    <th>Agent Code</th>
                    <th>이름</th>
                    <th>망구분</th>
                    <th>상태</th>
                    <th>마지막 실행</th>
                    <th>실행 결과</th>
                    <th>작업</th>
                  </tr>
                </thead>
                <tbody>
                  {groupAgents.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="empty-state">
                        등록된 {AGENT_TYPE_LABELS[type]} Agent가 없습니다
                      </td>
                    </tr>
                  ) : (
                    groupAgents.map((agent) => (
                      <tr key={agent.id}>
                        <td>
                          <Link href={`/agents/${agent.id}`}>{agent.agentCode}</Link>
                        </td>
                        <td>{agent.agentName}</td>
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
                            onClick={() => handleHealthCheck(agent.id)}
                            style={{ marginRight: '0.5rem' }}
                          >
                            상태확인
                          </button>
                          <Link href={`/agents/${agent.id}`}>
                            <button className="btn btn-secondary btn-sm" style={{ marginRight: '0.5rem' }}>
                              상세
                            </button>
                          </Link>
                          <button
                            className="btn btn-danger btn-sm"
                            onClick={() => handleDelete(agent.id)}
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
          )}
        </div>
      ))}
    </div>
  );
}

function AgentForm({ onSuccess }: { onSuccess: () => void }) {
  // Step 1: Endpoint URL로 Agent 조회
  const [endpointUrl, setEndpointUrl] = useState('');
  const [discovering, setDiscovering] = useState(false);
  const [discoveredAgents, setDiscoveredAgents] = useState<DiscoverAgent[]>([]);
  const [discoveredZone, setDiscoveredZone] = useState('');
  const [discoverError, setDiscoverError] = useState('');

  // Step 2: 선택된 agentCode + 파이프라인 정보
  const [selectedAgentCode, setSelectedAgentCode] = useState('');
  const [selectedAgentType, setSelectedAgentType] = useState<AgentType | ''>('');
  const [agentInfoMap, setAgentInfoMap] = useState<Record<string, any>>({});
  const [selectedAgentInfo, setSelectedAgentInfo] = useState<any>(null);

  // 테이블 검증 상태
  const [sourceTableVerify, setSourceTableVerify] = useState<Record<string, boolean>>({});
  const [targetTableVerify, setTargetTableVerify] = useState<Record<string, boolean>>({});

  // Step 3: 나머지 폼 데이터
  const [formData, setFormData] = useState({
    agentName: '',
    zone: '' as string,
    description: '',
    datasourceTag: '',
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

  // Source Datasource 변경 시 테이블 로드 + 검증
  useEffect(() => {
    if (!formData.sourceDatasourceId) {
      setSourceTables([]);
      setSelectedSourceTableIds([]);
      setSourceTableVerify({});
      return;
    }
    const loadTables = async () => {
      try {
        const tables = await datasourceApi.getRegisteredTables(formData.sourceDatasourceId);
        setSourceTables(tables);
        setSelectedSourceTableIds([]);
        // auto-discover 테이블 검증
        if (selectedAgentInfo) {
          const registeredNames = new Set(tables.map((t: any) => t.tableName.toUpperCase()));
          const verify: Record<string, boolean> = {};
          for (const step of selectedAgentInfo.steps || []) {
            for (const t of step.sourceTables || []) {
              verify[t] = registeredNames.has(t.toUpperCase());
            }
          }
          setSourceTableVerify(verify);
        }
      } catch (error) {
        console.error('Source 테이블 조회 실패:', error);
      }
    };
    loadTables();
  }, [formData.sourceDatasourceId, selectedAgentInfo]);

  // Target Datasource 변경 시 테이블 로드 + 검증
  useEffect(() => {
    if (!formData.targetDatasourceId) {
      setTargetTables([]);
      setSelectedTargetTableIds([]);
      setTargetTableVerify({});
      return;
    }
    const loadTables = async () => {
      try {
        const tables = await datasourceApi.getRegisteredTables(formData.targetDatasourceId);
        setTargetTables(tables);
        setSelectedTargetTableIds([]);
        // auto-discover 테이블 검증
        if (selectedAgentInfo) {
          const registeredNames = new Set(tables.map((t: any) => t.tableName.toUpperCase()));
          const verify: Record<string, boolean> = {};
          for (const step of selectedAgentInfo.steps || []) {
            for (const t of step.targetTables || []) {
              verify[t] = registeredNames.has(t.toUpperCase());
            }
          }
          setTargetTableVerify(verify);
        }
      } catch (error) {
        console.error('Target 테이블 조회 실패:', error);
      }
    };
    loadTables();
  }, [formData.targetDatasourceId, selectedAgentInfo]);

  // Agent 조회
  const handleDiscover = async () => {
    if (!endpointUrl) {
      alert('Endpoint URL을 입력하세요.');
      return;
    }
    setDiscovering(true);
    setDiscoverError('');
    setDiscoveredAgents([]);
    setSelectedAgentCode('');
    setSelectedAgentType('');
    try {
      const result = await agentApi.discover(endpointUrl);
      if (result.error) {
        setDiscoverError(result.error);
      } else {
        setDiscoveredAgents(result.agents || []);
        setDiscoveredZone(result.zone || '');
        setFormData(prev => ({ ...prev, zone: result.zone || '' }));
        // agentInfo를 agentCode 기준 Map으로 저장
        const infoList = result.agentInfo || [];
        const infoMap: Record<string, any> = {};
        for (const info of infoList) {
          infoMap[info.agentCode] = info;
        }
        setAgentInfoMap(infoMap);
      }
    } catch (error) {
      console.error('Agent 조회 실패:', error);
      setDiscoverError('Agent 프로세스에 연결할 수 없습니다. URL을 확인하세요.');
    } finally {
      setDiscovering(false);
    }
  };

  // agentCode 선택 시 타입 자동 설정
  const handleSelectAgent = (agent: DiscoverAgent) => {
    if (agent.registered) return;
    setSelectedAgentCode(agent.agentCode);
    setSelectedAgentType(agent.type);
    setSelectedAgentInfo(agentInfoMap[agent.agentCode] || null);
    setFormData(prev => ({ ...prev, agentName: agent.agentCode }));
    setSourceTableVerify({});
    setTargetTableVerify({});
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAgentCode || !selectedAgentType) {
      alert('Agent를 선택하세요.');
      return;
    }
    if (!formData.zone) {
      alert('망구분을 선택하세요.');
      return;
    }
    setSubmitting(true);
    try {
      // 1. Agent 등록 (auto-discover 테이블명 또는 기존 ID 방식)
      const sourceTableNames: string[] = [];
      const targetTableNames: string[] = [];
      if (selectedAgentInfo) {
        for (const step of selectedAgentInfo.steps || []) {
          for (const t of step.sourceTables || []) {
            if (!sourceTableNames.includes(t)) sourceTableNames.push(t);
          }
          for (const t of step.targetTables || []) {
            if (!targetTableNames.includes(t)) targetTableNames.push(t);
          }
        }
      }

      const createdAgent = await agentApi.create({
        agentCode: selectedAgentCode,
        agentName: formData.agentName,
        zone: formData.zone || discoveredZone || 'DMZ',
        agentType: selectedAgentType,
        endpointUrl: endpointUrl,
        description: formData.description || undefined,
        datasourceTag: formData.datasourceTag || undefined,
        sourceDatasourceId: formData.sourceDatasourceId || undefined,
        targetDatasourceId: formData.targetDatasourceId || undefined,
        sourceTableIds: sourceTableNames.length > 0 ? undefined : selectedSourceTableIds,
        targetTableIds: targetTableNames.length > 0 ? undefined : selectedTargetTableIds,
        sourceTableNames: sourceTableNames.length > 0 ? sourceTableNames : undefined,
        targetTableNames: targetTableNames.length > 0 ? targetTableNames : undefined,
      });

      // 2. 스케줄 등록 (프록시 Agent는 스케줄 불필요)
      if (selectedAgentType !== 'DB_CON_PROXY') {
        const scheduleReq: ScheduleCreateRequest = {
          agentId: createdAgent.id,
          cronExpression: scheduleData.cronExpression,
          isEnabled: scheduleData.isEnabled,
        };
        await scheduleApi.create(scheduleReq);
      }

      onSuccess();
    } catch (error: unknown) {
      console.error('Agent 등록 실패:', error);
      const msg = error instanceof Error ? error.message : '등록에 실패했습니다.';
      alert(msg);
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
        {/* Step 1: Agent 프로세스 조회 */}
        <div style={{ padding: '1rem', background: 'var(--gray-50)', borderRadius: '0.5rem', marginBottom: '1.5rem' }}>
          <strong style={{ marginBottom: '0.75rem', display: 'block' }}>1. Agent 프로세스 조회</strong>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-end' }}>
            <div className="form-group" style={{ flex: 1, marginBottom: 0 }}>
              <label className="form-label">Endpoint URL</label>
              <input
                type="url"
                className="form-input"
                value={endpointUrl}
                onChange={(e) => setEndpointUrl(e.target.value)}
                placeholder="http://localhost:8081"
                required
              />
            </div>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleDiscover}
              disabled={discovering || !endpointUrl}
              style={{ height: '38px', whiteSpace: 'nowrap' }}
            >
              {discovering ? '조회중...' : '에이전트 조회'}
            </button>
          </div>

          {discoverError && (
            <div style={{ marginTop: '0.75rem', padding: '0.5rem 0.75rem', background: '#fef2f2', color: '#dc2626', borderRadius: '0.375rem', fontSize: '0.875rem' }}>
              {discoverError}
            </div>
          )}

          {discoveredAgents.length > 0 && (
            <div style={{ marginTop: '0.75rem' }}>
              <div style={{ fontSize: '0.85rem', color: 'var(--gray-500)', marginBottom: '0.5rem' }}>
                Zone: <strong>{discoveredZone}</strong> | 검색된 Agent: <strong>{discoveredAgents.length}</strong>개
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
                {discoveredAgents.map(agent => {
                  const isSelected = selectedAgentCode === agent.agentCode;
                  const typeColor = AGENT_TYPE_COLORS[agent.type] || '#6b7280';
                  return (
                    <button
                      key={agent.agentCode}
                      type="button"
                      onClick={() => handleSelectAgent(agent)}
                      disabled={agent.registered}
                      style={{
                        padding: '0.375rem 0.75rem',
                        borderRadius: '0.375rem',
                        border: isSelected ? `2px solid ${typeColor}` : '1px solid var(--border-color)',
                        background: agent.registered ? 'var(--gray-100)' : isSelected ? `${typeColor}10` : 'white',
                        color: agent.registered ? 'var(--gray-400)' : 'var(--text-primary)',
                        cursor: agent.registered ? 'not-allowed' : 'pointer',
                        fontSize: '0.85rem',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.375rem',
                        opacity: agent.registered ? 0.6 : 1,
                      }}
                    >
                      <span style={{
                        display: 'inline-block',
                        width: '8px',
                        height: '8px',
                        borderRadius: '50%',
                        backgroundColor: typeColor,
                      }} />
                      <span>{agent.agentCode}</span>
                      <span style={{ fontSize: '0.7rem', color: 'var(--gray-500)' }}>
                        ({AGENT_TYPE_LABELS[agent.type]})
                      </span>
                      {agent.registered && (
                        <span style={{ fontSize: '0.7rem', color: 'var(--gray-400)' }}>등록됨</span>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* 임시: 폼 미리보기 버튼 */}
        {!selectedAgentCode && (
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            style={{ marginBottom: '1rem' }}
            onClick={() => { setSelectedAgentCode('preview-test'); setSelectedAgentType('RCV'); setFormData(prev => ({ ...prev, agentName: '미리보기 테스트' })); }}
          >
            [DEV] 폼 미리보기
          </button>
        )}

        {/* Step 2: 선택된 Agent 정보 + 추가 설정 */}
        {selectedAgentCode && (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label">Agent Code</label>
                <input
                  type="text"
                  className="form-input"
                  value={selectedAgentCode}
                  disabled
                  style={{ background: 'var(--gray-100)' }}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Agent 타입</label>
                <input
                  type="text"
                  className="form-input"
                  value={selectedAgentType ? AGENT_TYPE_LABELS[selectedAgentType] : ''}
                  disabled
                  style={{ background: 'var(--gray-100)' }}
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
                <label className="form-label">망구분</label>
                <select className="form-select" value={formData.zone} onChange={(e) => setFormData({ ...formData, zone: e.target.value })}>
                  <option value="">선택하세요</option>
                  <option value="EXTERNAL">외부망</option>
                  <option value="DMZ">DMZ</option>
                  <option value="INTERNAL_COMMON">내부공통망</option>
                  <option value="INTERNAL_SERVICE">내부서비스망</option>
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Datasource Tag (선택)</label>
                <input
                  type="text"
                  className="form-input"
                  value={formData.datasourceTag}
                  onChange={(e) => setFormData({ ...formData, datasourceTag: e.target.value })}
                  placeholder="gims_ngw"
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

            {/* Datasource & 파이프라인 설정 (프록시 Agent는 제외) */}
            {selectedAgentType !== 'DB_CON_PROXY' && (
            <div style={{ marginTop: '1.5rem', padding: '1rem', background: 'var(--gray-50)', borderRadius: '0.5rem' }}>
              <strong style={{ marginBottom: '1rem', display: 'block' }}>Datasource & 테이블 설정</strong>

              {/* 파이프라인 구성 표시 (auto-discover) */}
              {selectedAgentInfo && (
                <div style={{ marginBottom: '1rem', padding: '0.75rem', background: 'white', borderRadius: '0.375rem', border: '1px solid var(--border-color)' }}>
                  <div style={{ fontSize: '0.8rem', color: 'var(--gray-500)', marginBottom: '0.5rem' }}>파이프라인 구성 (YAML 기반)</div>
                  {(selectedAgentInfo.steps || []).map((step: any, idx: number) => (
                    <div key={idx} style={{ marginBottom: idx < selectedAgentInfo.steps.length - 1 ? '0.75rem' : 0, padding: '0.5rem', background: 'var(--gray-50)', borderRadius: '0.25rem' }}>
                      <div style={{ fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.25rem' }}>
                        [{step.stepId}] {step.stepName}
                      </div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--gray-600)' }}>
                        <span style={{ color: '#2563eb' }}>Source:</span> {(step.sourceTables || []).join(', ') || '-'}
                      </div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--gray-600)' }}>
                        <span style={{ color: '#16a34a' }}>Target:</span> {(step.targetTables || []).join(', ') || '-'}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                {/* Source Datasource */}
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
                  {/* 테이블 검증 표시 (auto-discover) */}
                  {selectedAgentInfo && formData.sourceDatasourceId && Object.keys(sourceTableVerify).length > 0 && (
                    <div style={{ fontSize: '0.8rem', padding: '0.5rem', background: 'white', borderRadius: '0.25rem', border: '1px solid var(--border-color)' }}>
                      {Object.entries(sourceTableVerify).map(([table, found]) => (
                        <div key={table} style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', padding: '0.125rem 0' }}>
                          <span style={{ color: found ? '#16a34a' : '#dc2626' }}>{found ? '✓' : '✗'}</span>
                          <span>{table}</span>
                          {!found && <span style={{ color: '#dc2626', fontSize: '0.7rem' }}>(미발견)</span>}
                        </div>
                      ))}
                    </div>
                  )}
                  {/* 기존 수동 선택 (agentInfo 없을 때만) */}
                  {!selectedAgentInfo && sourceTables.length > 0 && (
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

                {/* Target Datasource */}
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
                  {/* 테이블 검증 표시 (auto-discover) */}
                  {selectedAgentInfo && formData.targetDatasourceId && Object.keys(targetTableVerify).length > 0 && (
                    <div style={{ fontSize: '0.8rem', padding: '0.5rem', background: 'white', borderRadius: '0.25rem', border: '1px solid var(--border-color)' }}>
                      {Object.entries(targetTableVerify).map(([table, found]) => (
                        <div key={table} style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', padding: '0.125rem 0' }}>
                          <span style={{ color: found ? '#16a34a' : '#dc2626' }}>{found ? '✓' : '✗'}</span>
                          <span>{table}</span>
                          {!found && <span style={{ color: '#dc2626', fontSize: '0.7rem' }}>(미발견)</span>}
                        </div>
                      ))}
                    </div>
                  )}
                  {/* 기존 수동 선택 (agentInfo 없을 때만) */}
                  {!selectedAgentInfo && targetTables.length > 0 && (
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

            )}

            {/* 스케줄 설정 섹션 (프록시 Agent는 제외) */}
            {selectedAgentType !== 'DB_CON_PROXY' && (
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
            )}

            <button type="submit" className="btn btn-primary" disabled={submitting} style={{ marginTop: '1rem' }}>
              {submitting ? '등록중...' : '등록'}
            </button>
          </>
        )}
      </form>
    </div>
  );
}
