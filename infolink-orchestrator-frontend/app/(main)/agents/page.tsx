'use client';

import { useEffect, useState } from 'react';
import { agentApi, scheduleApi, datasourceApi } from '@/lib/api';
import type { Agent, Zone, ScheduleCreateRequest, DatasourceSimple, DatasourceTable, AgentType, DiscoverAgent } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import Link from 'next/link';
import styles from './page.module.css';

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

const AGENT_TYPE_COLOR_CLASS: Record<AgentType, string> = {
  RCV: styles['groupColorBar--rcv'],
  SND: styles['groupColorBar--snd'],
  LOADER: styles['groupColorBar--loader'],
  DB_CON_PROXY: styles['groupColorBar--proxy'],
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
    return <div className="app-loading">로딩중...</div>;
  }

  return (
    <div>
      <div className="app-page-header">
        <h1 className="app-page-header__title">Agent 관리</h1>
        <button className="krds-btn small" onClick={() => setShowForm(!showForm)}>
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
        <div key={type} className="app-card">
          {/* 그룹 헤더 */}
          <div className={styles.groupHeader} onClick={() => toggleGroup(type)}>
            <div className={styles.groupHeaderLeft}>
              <span className={`${styles.groupColorBar} ${AGENT_TYPE_COLOR_CLASS[type]}`} />
              <h2 className={styles.groupTitle}>{AGENT_TYPE_LABELS[type]}</h2>
              <span className={styles.groupCount}>({groupAgents.length})</span>
            </div>
            <span className={styles.groupToggle}>
              {collapsedGroups.has(type) ? '▸' : '▾'}
            </span>
          </div>

          {/* 그룹 내용 */}
          {!collapsedGroups.has(type) && (
            <div className={styles.tableWrap}>
              <table className={`app-table ${styles.agentTable}`}>
                <thead>
                  <tr>
                    <th>Agent Code</th>
                    <th>이름</th>
                    <th>망구분</th>
                    <th>상태</th>
                    <th>마지막 실행</th>
                    <th>실행 결과</th>
                  </tr>
                </thead>
                <tbody>
                  {groupAgents.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="app-empty">
                        등록된 {AGENT_TYPE_LABELS[type]} Agent가 없습니다
                      </td>
                    </tr>
                  ) : (
                    groupAgents.map((agent) => (
                      <tr key={agent.id}>
                        <td>
                          <Link href={`/agents/${agent.id}`} className={styles.agentLink}>{agent.agentCode}</Link>
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
    sourceDatasourceId: '',
    targetDatasourceId: '',
    historyDatasourceId: '',  // agent /health 자동 추출 (readonly 표시)
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
        setFormData(prev => ({
          ...prev,
          zone: result.zone || '',
          historyDatasourceId: result.historyDatasourceId || '',  // agent /health 자동 추출
        }));
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
        sourceDatasourceId: formData.sourceDatasourceId || undefined,
        targetDatasourceId: formData.targetDatasourceId || undefined,
        historyDatasourceId: formData.historyDatasourceId || undefined,  // agent /health 자동
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
    <div className="app-card">
      <div className="app-card__header">
        <h2 className="app-card__title">Agent 등록</h2>
        {selectedAgentCode && (
          <button type="button" className="krds-btn small" onClick={handleSubmit} disabled={submitting}>
            {submitting ? '등록중...' : '등록'}
          </button>
        )}
      </div>
      <form onSubmit={handleSubmit}>
        {/* Step 1: Agent 프로세스 조회 */}
        <div className={styles.panel}>
          <div className={styles.stepTitle}>1. Agent 프로세스 조회</div>
          <div className={styles.discoverRow}>
            <div className={`app-form-field ${styles.discoverInput}`}>
              <label className="app-form-label">Endpoint URL</label>
              <input
                type="url"
                className="krds-input small"
                value={endpointUrl}
                onChange={(e) => setEndpointUrl(e.target.value)}
                placeholder="http://localhost:8081"
                required
              />
            </div>
            <button
              type="button"
              className="krds-btn small"
              onClick={handleDiscover}
              disabled={discovering || !endpointUrl}
            >
              {discovering ? '조회중...' : '에이전트 조회'}
            </button>
          </div>

          {discoverError && (
            <div className={`app-alert app-alert--danger ${styles.discoverErrorBox}`}>
              {discoverError}
            </div>
          )}

          {discoveredAgents.length > 0 && (
            <div className={styles.discoveredWrap}>
              <div className={styles.discoveredMeta}>
                Zone: <strong>{discoveredZone}</strong> | 검색된 Agent: <strong>{discoveredAgents.length}</strong>개
              </div>
              <div className={styles.chipRow}>
                {discoveredAgents.map(agent => {
                  const isSelected = selectedAgentCode === agent.agentCode;
                  const dotClass = AGENT_TYPE_COLOR_CLASS[agent.type]?.replace('groupColorBar', 'agentChipDot')
                    ?? styles['agentChipDot--rcv'];
                  return (
                    <button
                      key={agent.agentCode}
                      type="button"
                      onClick={() => handleSelectAgent(agent)}
                      disabled={agent.registered}
                      className={`${styles.agentChip} ${isSelected ? styles.agentChipSelected : ''} ${agent.registered ? styles.agentChipDisabled : ''}`}
                    >
                      <span className={`${styles.agentChipDot} ${
                        agent.type === 'RCV' ? styles['agentChipDot--rcv'] :
                        agent.type === 'SND' ? styles['agentChipDot--snd'] :
                        agent.type === 'LOADER' ? styles['agentChipDot--loader'] :
                        styles['agentChipDot--proxy']
                      }`} />
                      <span>{agent.agentCode}</span>
                      <span className={styles.agentChipType}>({AGENT_TYPE_LABELS[agent.type]})</span>
                      {agent.registered && <span className={styles.agentChipRegistered}>등록됨</span>}
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* [DEV] 폼 미리보기 버튼 */}
        {!selectedAgentCode && (
          <div className={`${styles.section} ${styles.topGap}`}>
            <button
              type="button"
              className="krds-btn small secondary"
              onClick={() => { setSelectedAgentCode('preview-test'); setSelectedAgentType('RCV'); setFormData(prev => ({ ...prev, agentName: '미리보기 테스트' })); }}
            >
              [DEV] 폼 미리보기
            </button>
          </div>
        )}

        {/* Step 2: 선택된 Agent 정보 + 추가 설정 */}
        {selectedAgentCode && (
          <>
            <div className={`${styles.section} ${styles.topGap}`}>
              <div className={styles.gridForm}>
                <div className="app-form-field">
                  <label className="app-form-label">Agent Code</label>
                  <input type="text" className={`krds-input small ${styles.disabledInput}`} value={selectedAgentCode} disabled />
                </div>
                <div className="app-form-field">
                  <label className="app-form-label">Agent 타입</label>
                  <input type="text" className={`krds-input small ${styles.disabledInput}`}
                    value={selectedAgentType ? AGENT_TYPE_LABELS[selectedAgentType] : ''} disabled />
                </div>
                <div className="app-form-field">
                  <label className="app-form-label">이름</label>
                  <input type="text" className="krds-input small" value={formData.agentName}
                    onChange={(e) => setFormData({ ...formData, agentName: e.target.value })}
                    placeholder="주문 추출 Agent" required />
                </div>
                <div className="app-form-field">
                  <label className="app-form-label">망구분</label>
                  <select className="krds-input small" value={formData.zone} onChange={(e) => setFormData({ ...formData, zone: e.target.value })}>
                    <option value="">선택하세요</option>
                    <option value="EXTERNAL">외부망</option>
                    <option value="DMZ">DMZ</option>
                    <option value="INTERNAL_COMMON">내부공통망</option>
                    <option value="INTERNAL_SERVICE">내부서비스망</option>
                  </select>
                </div>
                <div className="app-form-field">
                  <label className="app-form-label">이력 DB <span className="app-form-label__hint">(자동)</span></label>
                  <input type="text" className="krds-input small" value={formData.historyDatasourceId}
                    readOnly tabIndex={-1}
                    placeholder="Agent 조회 시 자동" />
                </div>
                <div className={`app-form-field ${styles.fullSpan}`}>
                  <label className="app-form-label">설명 (선택)</label>
                  <input type="text" className="krds-input small" value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    placeholder="외부망에서 주문 데이터를 추출하는 Agent" />
                </div>
              </div>
            </div>

            {/* Datasource & 파이프라인 설정 (프록시 Agent는 제외) */}
            {selectedAgentType !== 'DB_CON_PROXY' && (
              <div className={`${styles.panel} ${styles.topGap}`}>
                <div className={styles.stepTitle}>Datasource & 테이블 설정</div>

                {/* 파이프라인 구성 표시 (auto-discover) */}
                {selectedAgentInfo && (
                  <div className={styles.pipelinePanel}>
                    <div className={styles.pipelinePanelLabel}>파이프라인 구성 (YAML 기반)</div>
                    {(selectedAgentInfo.steps || []).map((step: any, idx: number) => (
                      <div key={idx} className={styles.pipelineStep}>
                        <div className={styles.pipelineStepName}>[{step.stepId}] {step.stepName}</div>
                        <div className={styles.pipelineStepLine}>
                          <span className={styles.pipelineSourceLabel}>Source:</span> {(step.sourceTables || []).join(', ') || '-'}
                        </div>
                        <div className={styles.pipelineStepLine}>
                          <span className={styles.pipelineTargetLabel}>Target:</span> {(step.targetTables || []).join(', ') || '-'}
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                <div className={styles.dsSubGrid}>
                  {/* Source Datasource */}
                  <div className="app-form-field">
                    <label className="app-form-label">Source Datasource</label>
                    <select className="krds-input small" value={formData.sourceDatasourceId}
                      onChange={(e) => setFormData({ ...formData, sourceDatasourceId: e.target.value })}>
                      <option value="">선택하세요</option>
                      {datasources.map(ds => (
                        <option key={ds.datasourceId} value={ds.datasourceId}>{ds.datasourceName} ({ds.dbType})</option>
                      ))}
                    </select>
                    {selectedAgentInfo && formData.sourceDatasourceId && Object.keys(sourceTableVerify).length > 0 && (
                      <div className={styles.verifyBox}>
                        {Object.entries(sourceTableVerify).map(([table, found]) => (
                          <div key={table} className={styles.verifyRow}>
                            <span className={found ? styles.verifyOk : styles.verifyFail}>{found ? '✓' : '✗'}</span>
                            <span>{table}</span>
                            {!found && <span className={styles.verifyMissing}>(미발견)</span>}
                          </div>
                        ))}
                      </div>
                    )}
                    {!selectedAgentInfo && sourceTables.length > 0 && (
                      <>
                        <label className={`app-form-label ${styles.tableSelectLabel}`}>Source 테이블 선택 ({selectedSourceTableIds.length}/{sourceTables.length})</label>
                        <div className={styles.tableCheckList}>
                          {sourceTables.map(table => (
                            <div key={table.id} className={styles.tableCheckItem}>
                              <div className="krds-form-check medium">
                                <input type="checkbox" id={`src-tbl-${table.id}`}
                                  checked={selectedSourceTableIds.includes(table.id)}
                                  onChange={() => toggleTableSelection(table.id, 'source')} />
                                <label htmlFor={`src-tbl-${table.id}`} aria-label={`${table.tableName} 선택`}></label>
                              </div>
                              <label htmlFor={`src-tbl-${table.id}`}>{table.tableName}</label>
                              <span className={styles.tableCheckCount}>({table.columns.length}컬럼)</span>
                            </div>
                          ))}
                        </div>
                      </>
                    )}
                  </div>

                  {/* Target Datasource */}
                  <div className="app-form-field">
                    <label className="app-form-label">Target Datasource</label>
                    <select className="krds-input small" value={formData.targetDatasourceId}
                      onChange={(e) => setFormData({ ...formData, targetDatasourceId: e.target.value })}>
                      <option value="">선택하세요</option>
                      {datasources.map(ds => (
                        <option key={ds.datasourceId} value={ds.datasourceId}>{ds.datasourceName} ({ds.dbType})</option>
                      ))}
                    </select>
                    {selectedAgentInfo && formData.targetDatasourceId && Object.keys(targetTableVerify).length > 0 && (
                      <div className={styles.verifyBox}>
                        {Object.entries(targetTableVerify).map(([table, found]) => (
                          <div key={table} className={styles.verifyRow}>
                            <span className={found ? styles.verifyOk : styles.verifyFail}>{found ? '✓' : '✗'}</span>
                            <span>{table}</span>
                            {!found && <span className={styles.verifyMissing}>(미발견)</span>}
                          </div>
                        ))}
                      </div>
                    )}
                    {!selectedAgentInfo && targetTables.length > 0 && (
                      <>
                        <label className={`app-form-label ${styles.tableSelectLabel}`}>Target 테이블 선택 ({selectedTargetTableIds.length}/{targetTables.length})</label>
                        <div className={styles.tableCheckList}>
                          {targetTables.map(table => (
                            <div key={table.id} className={styles.tableCheckItem}>
                              <div className="krds-form-check medium">
                                <input type="checkbox" id={`tgt-tbl-${table.id}`}
                                  checked={selectedTargetTableIds.includes(table.id)}
                                  onChange={() => toggleTableSelection(table.id, 'target')} />
                                <label htmlFor={`tgt-tbl-${table.id}`} aria-label={`${table.tableName} 선택`}></label>
                              </div>
                              <label htmlFor={`tgt-tbl-${table.id}`}>{table.tableName}</label>
                              <span className={styles.tableCheckCount}>({table.columns.length}컬럼)</span>
                            </div>
                          ))}
                        </div>
                      </>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* 스케줄 설정 섹션 (프록시 Agent는 제외) */}
            {selectedAgentType !== 'DB_CON_PROXY' && (
              <div className={`${styles.panel} ${styles.topGap}`}>
                <div className={styles.stepTitle}>스케줄 설정</div>
                <div className={styles.dsSubGrid}>
                  <div className="app-form-field">
                    <label className="app-form-label">Cron 표현식</label>
                    <input type="text" className="krds-input small" value={scheduleData.cronExpression}
                      onChange={(e) => setScheduleData({ ...scheduleData, cronExpression: e.target.value })}
                      placeholder="0 0 * * * *" required />
                    <div className={styles.formHelp}>형식: 초 분 시 일 월 요일 (예: 매시 정각 = 0 0 * * * *)</div>
                  </div>
                  <div className="app-form-field">
                    <label className="app-form-label">활성화 여부</label>
                    <label className={styles.scheduleActiveLabel}>
                      <div className="krds-form-check medium">
                        <input type="checkbox" id="schedule-enabled"
                          checked={scheduleData.isEnabled}
                          onChange={(e) => setScheduleData({ ...scheduleData, isEnabled: e.target.checked })} />
                        <label htmlFor="schedule-enabled" aria-label="활성화"></label>
                      </div>
                      <label htmlFor="schedule-enabled">등록 즉시 활성화</label>
                    </label>
                  </div>
                </div>
              </div>
            )}
          </>
        )}
      </form>
    </div>
  );
}
