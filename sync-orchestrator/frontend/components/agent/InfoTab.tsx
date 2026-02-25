'use client';

import { useState, useEffect } from 'react';
import { agentApi, scheduleApi, datasourceApi } from '@/lib/api';
import type { Agent, Zone, Schedule, ScheduleCreateRequest, AgentType, Datasource, DatasourceTable, DatasourceSimple, ExecutionParamResponse } from '@/types';
import StatusBadge from '@/components/StatusBadge';

const ZONE_LABELS: Record<Zone, string> = {
  EXTERNAL: '외부망',
  DMZ: 'DMZ',
  INTERNAL: '내부망',
  INTERNAL_COMMON: '내부공통망',
  INTERNAL_SERVICE: '내부서비스망',
};

const AGENT_TYPE_LABELS: Record<AgentType, string> = {
  RCV: '수신(RCV)',
  SND: '송신(SND)',
  LOADER: 'Loader',
};

/**
 * Spring Cron 표현식 해석 (6자리: 초 분 시 일 월 요일)
 * 지원 패턴:
 * - * : 모든 값
 * - ? : 무시 (일/요일에서 사용)
 * - N : 특정 값
 * - N,M : 여러 값
 * - N-M : 범위
 * - N/M : N부터 M간격
 * - * /M : M간격
 * - L : 마지막 (일/요일)
 * - W : 가장 가까운 평일
 * - # : N번째 요일 (예: 2#3 = 세번째 월요일)
 */
function parseCronExpression(cron: string): string {
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 6) return '잘못된 형식';

  const [sec, min, hour, day, month, dow] = parts;

  // 요일 맵
  const dowMap: Record<string, string> = {
    '0': '일', '1': '월', '2': '화', '3': '수', '4': '목', '5': '금', '6': '토', '7': '일',
    'SUN': '일', 'MON': '월', 'TUE': '화', 'WED': '수', 'THU': '목', 'FRI': '금', 'SAT': '토',
  };

  // 요일 해석
  const parseDow = (d: string): string => {
    const upper = d.toUpperCase();
    if (upper === '*' || upper === '?') return '';

    // N#M 패턴 (N번째 M요일) - 예: 2#3 = 세번째 월요일
    if (upper.includes('#')) {
      const [dowPart, nth] = upper.split('#');
      const dowName = dowMap[dowPart] || dowPart;
      const ordinal = ['첫', '두', '세', '네', '다섯'][parseInt(nth) - 1] || nth;
      return `${ordinal}번째 ${dowName}요일`;
    }

    // L 패턴 (마지막 요일) - 예: 5L = 마지막 금요일
    if (upper.endsWith('L')) {
      const dowPart = upper.slice(0, -1);
      if (dowPart) {
        const dowName = dowMap[dowPart] || dowPart;
        return `마지막 ${dowName}요일`;
      }
      return '마지막 요일';
    }

    // 평일/주말 패턴
    if (upper === 'MON-FRI' || upper === '1-5') return '평일';
    if (upper === 'SAT,SUN' || upper === '0,6' || upper === '6,0' || upper === 'SAT-SUN' || upper === '6-7' || upper === '0,7') return '주말';

    // 범위 패턴
    if (upper.includes('-')) {
      const [from, to] = upper.split('-');
      return `${dowMap[from] || from}~${dowMap[to] || to}요일`;
    }

    // 목록 패턴
    if (upper.includes(',')) {
      return d.split(',').map(x => dowMap[x.toUpperCase()] || x).join(',') + '요일';
    }

    return (dowMap[upper] || d) + '요일';
  };

  const dowStr = parseDow(dow);

  // 간격 패턴 해석 (*/N 또는 N/M)
  const parseInterval = (val: string, unit: string): string | null => {
    // */N 패턴
    if (val.startsWith('*/')) {
      const interval = val.slice(2);
      return `${interval}${unit}마다`;
    }
    // N/M 패턴 (N부터 M간격)
    if (val.includes('/') && !val.startsWith('*')) {
      const [start, interval] = val.split('/');
      if (start.includes('-')) {
        // N-M/I 패턴 (범위 내 간격)
        const [from, to] = start.split('-');
        return `${from}~${to}${unit} 중 ${interval}${unit}마다`;
      }
      // 0부터 시작하면 간단히 표시
      if (start === '0') {
        return `${interval}${unit}마다`;
      }
      return `${start}${unit}부터 ${interval}${unit}마다`;
    }
    return null;
  };

  // 시간 해석
  const parseTime = (): string => {
    // 초 간격
    const secInterval = parseInterval(sec, '초');
    if (secInterval) return secInterval;

    // 분 간격
    if (sec === '0') {
      const minInterval = parseInterval(min, '분');
      if (minInterval) return minInterval;
    }

    // 시간 간격
    if (sec === '0' && min === '0') {
      const hourInterval = parseInterval(hour, '시간');
      if (hourInterval) return hourInterval;
    }

    // 시간 범위 (예: 8-17)
    if (hour.includes('-') && !hour.includes('/')) {
      const [from, to] = hour.split('-');
      if (min === '0' && sec === '0') {
        return `${from}시~${to}시 매시 정각`;
      }
      if (min !== '*' && sec === '0') {
        return `${from}시~${to}시 매시 ${min}분`;
      }
      return `${from}시~${to}시`;
    }

    // 여러 시간 (예: 9,12,18)
    if (hour.includes(',')) {
      const hours = hour.split(',').join(', ');
      if (min !== '*' && sec === '0') {
        return `${hours}시 ${min}분`;
      }
      return `${hours}시`;
    }

    // 특정 시간
    if (hour !== '*' && min !== '*' && sec === '0') {
      const h = hour.padStart(2, '0');
      const m = min.padStart(2, '0');
      if (day === '*' && month === '*' && (dow === '*' || dow === '?')) {
        return `매일 ${h}:${m}`;
      }
      return `${h}:${m}`;
    }

    // 매시 N분
    if (hour === '*' && min !== '*' && sec === '0') {
      if (min.includes(',')) {
        return `매시 ${min.split(',').join(', ')}분`;
      }
      return min === '0' ? '매시 정각' : `매시 ${min}분`;
    }

    // 기본
    if (hour === '*' && min === '*' && sec === '0') return '매분';
    if (hour === '*' && min === '*' && sec === '*') return '매초';

    return '';
  };

  const timeStr = parseTime();

  // 일/월 해석
  const parseDayMonth = (): string => {
    const parts: string[] = [];

    // 월 해석
    if (month !== '*') {
      if (month.includes(',')) {
        parts.push(`${month.split(',').join(', ')}월`);
      } else if (month.includes('-')) {
        const [from, to] = month.split('-');
        parts.push(`${from}~${to}월`);
      } else {
        parts.push(`${month}월`);
      }
    }

    // 일 해석
    if (day !== '*' && day !== '?') {
      // L 패턴 (마지막 날)
      if (day.toUpperCase() === 'L') {
        parts.push('마지막 날');
      }
      // LW 패턴 (마지막 평일)
      else if (day.toUpperCase() === 'LW') {
        parts.push('마지막 평일');
      }
      // NW 패턴 (N일에 가장 가까운 평일)
      else if (day.toUpperCase().endsWith('W')) {
        const dayNum = day.slice(0, -1);
        parts.push(`${dayNum}일에 가장 가까운 평일`);
      }
      // 범위
      else if (day.includes('-')) {
        const [from, to] = day.split('-');
        parts.push(`${from}~${to}일`);
      }
      // 목록
      else if (day.includes(',')) {
        parts.push(`${day.split(',').join(', ')}일`);
      }
      // 단일 값
      else {
        parts.push(month === '*' ? `매월 ${day}일` : `${day}일`);
      }
    }

    return parts.join(' ');
  };

  const dayMonthStr = parseDayMonth();

  // 조합
  const resultParts = [dayMonthStr, dowStr, timeStr].filter(Boolean);
  return resultParts.length > 0 ? resultParts.join(' ') : cron;
}

interface InfoTabProps {
  agent: Agent;
  schedules: Schedule[];
  onUpdate: () => void;
}

export default function InfoTab({ agent, schedules, onUpdate }: InfoTabProps) {
  const [editMode, setEditMode] = useState(false);
  const [showAddSchedule, setShowAddSchedule] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 스케줄 수정용 상태
  const [editingScheduleId, setEditingScheduleId] = useState<number | null>(null);
  const [editScheduleForm, setEditScheduleForm] = useState({ cronExpression: '' });

  // Datasource & Table 정보 (보기용)
  const [sourceDatasource, setSourceDatasource] = useState<Datasource | null>(null);
  const [targetDatasource, setTargetDatasource] = useState<Datasource | null>(null);
  const [sourceTables, setSourceTables] = useState<DatasourceTable[]>([]);
  const [targetTables, setTargetTables] = useState<DatasourceTable[]>([]);

  // 수정용 Datasource & Table 목록
  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  const [availableSourceTables, setAvailableSourceTables] = useState<DatasourceTable[]>([]);
  const [availableTargetTables, setAvailableTargetTables] = useState<DatasourceTable[]>([]);

  // 실행 옵션
  const [executionParams, setExecutionParams] = useState<ExecutionParamResponse[]>(agent.executionParams || []);
  const [refreshingParams, setRefreshingParams] = useState(false);

  // Datasource 목록 로드
  useEffect(() => {
    if (editMode) {
      datasourceApi.getSimple().then(setDatasources).catch(console.error);
    }
  }, [editMode]);

  useEffect(() => {
    const fetchDatasourceInfo = async () => {
      if (agent.sourceDatasourceId) {
        try {
          const [ds, tables] = await Promise.all([
            datasourceApi.getById(agent.sourceDatasourceId),
            datasourceApi.getRegisteredTables(agent.sourceDatasourceId),
          ]);
          setSourceDatasource(ds);
          // Agent에 설정된 테이블만 필터링
          setSourceTables(tables.filter(t => agent.sourceTableIds?.includes(t.id)));
        } catch (e) { console.error('Source datasource 조회 실패:', e); }
      }
      if (agent.targetDatasourceId) {
        try {
          const [ds, tables] = await Promise.all([
            datasourceApi.getById(agent.targetDatasourceId),
            datasourceApi.getRegisteredTables(agent.targetDatasourceId),
          ]);
          setTargetDatasource(ds);
          setTargetTables(tables.filter(t => agent.targetTableIds?.includes(t.id)));
        } catch (e) { console.error('Target datasource 조회 실패:', e); }
      }
    };
    fetchDatasourceInfo();
  }, [agent.sourceDatasourceId, agent.targetDatasourceId, agent.sourceTableIds, agent.targetTableIds]);

  const [agentForm, setAgentForm] = useState({
    agentName: agent.agentName,
    zone: agent.zone,
    agentType: agent.agentType || 'LOADER' as AgentType,
    endpointUrl: agent.endpointUrl,
    description: agent.description || '',
    sourceDatasourceId: agent.sourceDatasourceId || '',
    targetDatasourceId: agent.targetDatasourceId || '',
    sourceTableIds: agent.sourceTableIds || [] as number[],
    targetTableIds: agent.targetTableIds || [] as number[],
  });

  // Source Datasource 변경 시 테이블 목록 로드
  useEffect(() => {
    if (editMode && agentForm.sourceDatasourceId) {
      datasourceApi.getRegisteredTables(agentForm.sourceDatasourceId)
        .then(setAvailableSourceTables)
        .catch(console.error);
    } else {
      setAvailableSourceTables([]);
    }
  }, [editMode, agentForm.sourceDatasourceId]);

  // Target Datasource 변경 시 테이블 목록 로드
  useEffect(() => {
    if (editMode && agentForm.targetDatasourceId) {
      datasourceApi.getRegisteredTables(agentForm.targetDatasourceId)
        .then(setAvailableTargetTables)
        .catch(console.error);
    } else {
      setAvailableTargetTables([]);
    }
  }, [editMode, agentForm.targetDatasourceId]);

  // 수정 모드 진입 시 form 초기화
  const handleEditMode = () => {
    setAgentForm({
      agentName: agent.agentName,
      zone: agent.zone,
      agentType: agent.agentType || 'LOADER' as AgentType,
      endpointUrl: agent.endpointUrl,
      description: agent.description || '',
      sourceDatasourceId: agent.sourceDatasourceId || '',
      targetDatasourceId: agent.targetDatasourceId || '',
      sourceTableIds: agent.sourceTableIds || [],
      targetTableIds: agent.targetTableIds || [],
    });
    setEditMode(true);
  };

  const handleSourceTableToggle = (tableId: number) => {
    setAgentForm(prev => ({
      ...prev,
      sourceTableIds: prev.sourceTableIds.includes(tableId)
        ? prev.sourceTableIds.filter(id => id !== tableId)
        : [...prev.sourceTableIds, tableId]
    }));
  };

  const handleTargetTableToggle = (tableId: number) => {
    setAgentForm(prev => ({
      ...prev,
      targetTableIds: prev.targetTableIds.includes(tableId)
        ? prev.targetTableIds.filter(id => id !== tableId)
        : [...prev.targetTableIds, tableId]
    }));
  };

  const [newSchedule, setNewSchedule] = useState({
    cronExpression: '0 0 * * * *',
    isEnabled: true,
    useFilters: false,
    filters: {} as Record<string, { value: string }>,
  });

  const handleRefreshExecutionParams = async () => {
    setRefreshingParams(true);
    try {
      const params = await agentApi.refreshExecutionParams(agent.id);
      setExecutionParams(params);
      onUpdate();
    } catch (error) {
      console.error('실행 옵션 갱신 실패:', error);
      alert('실행 옵션 갱신에 실패했습니다. Agent가 온라인 상태인지 확인하세요.');
    } finally {
      setRefreshingParams(false);
    }
  };

  const handleAgentUpdate = async () => {
    setSubmitting(true);
    try {
      await agentApi.update(agent.id, agentForm);
      setEditMode(false);
      onUpdate();
    } catch (error) {
      console.error('Agent 수정 실패:', error);
      alert('수정에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleAddSchedule = async () => {
    setSubmitting(true);
    try {
      // 필터 설정이 있으면 executionOptions JSON 생성
      let executionOptions: string | undefined;
      if (newSchedule.useFilters) {
        const filters = Object.entries(newSchedule.filters)
          .filter(([, f]) => f.value.trim())
          .map(([paramId, f]) => ({ paramId, value: f.value.trim() }));
        if (filters.length > 0) {
          executionOptions = JSON.stringify({ filters });
        }
      }

      const createData: ScheduleCreateRequest = {
        agentId: agent.id,
        cronExpression: newSchedule.cronExpression,
        isEnabled: newSchedule.isEnabled,
        executionOptions,
      };
      await scheduleApi.create(createData);
      setShowAddSchedule(false);
      setNewSchedule({ cronExpression: '0 0 * * * *', isEnabled: true, useFilters: false, filters: {} });
      onUpdate();
    } catch (error) {
      console.error('스케줄 등록 실패:', error);
      alert('스케줄 등록에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleScheduleToggle = async (scheduleId: number) => {
    try {
      await scheduleApi.toggle(scheduleId);
      onUpdate();
    } catch (error) {
      console.error('스케줄 상태 변경 실패:', error);
    }
  };

  const handleScheduleDelete = async (scheduleId: number) => {
    if (!confirm('이 스케줄을 삭제하시겠습니까?')) return;
    try {
      await scheduleApi.delete(scheduleId);
      onUpdate();
    } catch (error) {
      console.error('스케줄 삭제 실패:', error);
    }
  };

  const handleScheduleEditStart = (schedule: Schedule) => {
    setEditingScheduleId(schedule.scheduleId);
    setEditScheduleForm({ cronExpression: schedule.cronExpression });
  };

  const handleScheduleEditCancel = () => {
    setEditingScheduleId(null);
    setEditScheduleForm({ cronExpression: '' });
  };

  const handleScheduleUpdate = async (scheduleId: number) => {
    if (!editScheduleForm.cronExpression.trim()) {
      alert('Cron 표현식을 입력하세요.');
      return;
    }
    setSubmitting(true);
    try {
      await scheduleApi.update(scheduleId, { cronExpression: editScheduleForm.cronExpression });
      setEditingScheduleId(null);
      setEditScheduleForm({ cronExpression: '' });
      onUpdate();
    } catch (error) {
      console.error('스케줄 수정 실패:', error);
      alert('스케줄 수정에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      {/* Agent 정보 */}
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Agent 정보</h2>
          {!editMode ? (
            <button className="btn btn-secondary btn-sm" onClick={handleEditMode}>수정</button>
          ) : (
            <div>
              <button className="btn btn-primary btn-sm" onClick={handleAgentUpdate} disabled={submitting} style={{ marginRight: '0.5rem' }}>
                {submitting ? '저장중...' : '저장'}
              </button>
              <button className="btn btn-secondary btn-sm" onClick={() => setEditMode(false)}>취소</button>
            </div>
          )}
        </div>

        {!editMode ? (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginTop: '1rem' }}>
            <div><strong>Agent Code:</strong> {agent.agentCode}</div>
            <div><strong>이름:</strong> {agent.agentName}</div>
            <div><strong>Agent 타입:</strong> <span style={{ padding: '0.25rem 0.5rem', background: agent.agentType === 'RCV' ? '#ede9fe' : agent.agentType === 'LOADER' ? '#d1fae5' : '#fef3c7', borderRadius: '0.25rem', fontSize: '0.875rem' }}>{AGENT_TYPE_LABELS[agent.agentType as AgentType] || agent.agentType}</span></div>
            <div><strong>망구분:</strong> <span className={`zone-${agent.zone.toLowerCase().replace('_', '-')}`}>{ZONE_LABELS[agent.zone as Zone]}</span></div>
            <div><strong>Endpoint URL:</strong> {agent.endpointUrl}</div>
            <div><strong>활성화:</strong> {agent.isActive ? '예' : '아니오'}</div>
            <div><strong>등록일:</strong> {agent.createdAt ? new Date(agent.createdAt).toLocaleString('ko-KR') : '-'}</div>
            {agent.description && <div style={{ gridColumn: '1 / -1' }}><strong>설명:</strong> {agent.description}</div>}
          </div>
        ) : (
          <div style={{ marginTop: '1rem' }}>
            {/* 기본 정보 */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label">이름</label>
                <input type="text" className="form-input" value={agentForm.agentName} onChange={(e) => setAgentForm({ ...agentForm, agentName: e.target.value })} />
              </div>
              <div className="form-group">
                <label className="form-label">Agent 타입</label>
                <select className="form-select" value={agentForm.agentType} onChange={(e) => setAgentForm({ ...agentForm, agentType: e.target.value as AgentType })}>
                  <option value="RCV">수신(RCV) - 외부 Source → IF</option>
                  <option value="LOADER">Loader - IF → Target 적재</option>
                  <option value="SND">송신(SND) - 내부 Target → IF</option>
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">망구분</label>
                <select className="form-select" value={agentForm.zone} onChange={(e) => setAgentForm({ ...agentForm, zone: e.target.value })}>
                  <option value="EXTERNAL">외부망</option>
                  <option value="DMZ">DMZ</option>
                  <option value="INTERNAL_COMMON">내부공통망</option>
                  <option value="INTERNAL_SERVICE">내부서비스망</option>
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Endpoint URL</label>
                <input type="url" className="form-input" value={agentForm.endpointUrl} onChange={(e) => setAgentForm({ ...agentForm, endpointUrl: e.target.value })} />
              </div>
              <div className="form-group" style={{ gridColumn: '1 / -1' }}>
                <label className="form-label">설명</label>
                <input type="text" className="form-input" value={agentForm.description} onChange={(e) => setAgentForm({ ...agentForm, description: e.target.value })} />
              </div>
            </div>

            {/* Datasource & 테이블 설정 */}
            <div style={{ marginTop: '1.5rem', padding: '1rem', background: 'var(--gray-50)', borderRadius: '0.5rem' }}>
              <h4 style={{ marginBottom: '1rem', fontSize: '0.9rem', fontWeight: 600 }}>Datasource & 테이블 설정</h4>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
                {/* Source */}
                <div>
                  <div className="form-group">
                    <label className="form-label" style={{ color: 'var(--primary-color, #1976d2)' }}>Source Datasource</label>
                    <select
                      className="form-select"
                      value={agentForm.sourceDatasourceId}
                      onChange={(e) => setAgentForm({ ...agentForm, sourceDatasourceId: e.target.value, sourceTableIds: [] })}
                    >
                      <option value="">선택 안함</option>
                      {datasources.map(ds => (
                        <option key={ds.datasourceId} value={ds.datasourceId}>
                          {ds.datasourceName} ({ds.dbType})
                        </option>
                      ))}
                    </select>
                  </div>
                  {availableSourceTables.length > 0 && (
                    <div className="form-group" style={{ marginTop: '0.75rem' }}>
                      <label className="form-label">Source 테이블</label>
                      <div style={{ maxHeight: '150px', overflow: 'auto', border: '1px solid var(--border-color)', borderRadius: '0.375rem', padding: '0.5rem' }}>
                        {availableSourceTables.map(table => (
                          <label key={table.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.25rem 0', cursor: 'pointer' }}>
                            <input
                              type="checkbox"
                              checked={agentForm.sourceTableIds.includes(table.id)}
                              onChange={() => handleSourceTableToggle(table.id)}
                            />
                            <span style={{ fontSize: '0.875rem' }}>{table.tableName}</span>
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
                    <label className="form-label" style={{ color: 'var(--success-color, #28a745)' }}>Target Datasource</label>
                    <select
                      className="form-select"
                      value={agentForm.targetDatasourceId}
                      onChange={(e) => setAgentForm({ ...agentForm, targetDatasourceId: e.target.value, targetTableIds: [] })}
                    >
                      <option value="">선택 안함</option>
                      {datasources.map(ds => (
                        <option key={ds.datasourceId} value={ds.datasourceId}>
                          {ds.datasourceName} ({ds.dbType})
                        </option>
                      ))}
                    </select>
                  </div>
                  {availableTargetTables.length > 0 && (
                    <div className="form-group" style={{ marginTop: '0.75rem' }}>
                      <label className="form-label">Target 테이블</label>
                      <div style={{ maxHeight: '150px', overflow: 'auto', border: '1px solid var(--border-color)', borderRadius: '0.375rem', padding: '0.5rem' }}>
                        {availableTargetTables.map(table => (
                          <label key={table.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.25rem 0', cursor: 'pointer' }}>
                            <input
                              type="checkbox"
                              checked={agentForm.targetTableIds.includes(table.id)}
                              onChange={() => handleTargetTableToggle(table.id)}
                            />
                            <span style={{ fontSize: '0.875rem' }}>{table.tableName}</span>
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>({table.columns.length}컬럼)</span>
                          </label>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Datasource & 테이블 정보 */}
      {(sourceDatasource || targetDatasource) && (
        <div className="card" style={{ marginTop: '1.5rem' }}>
          <div className="card-header">
            <h2 className="card-title">Datasource & 테이블</h2>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginTop: '1rem' }}>
            {/* Source */}
            <div>
              <h4 style={{ marginBottom: '0.75rem', color: 'var(--primary-color, #1976d2)' }}>Source</h4>
              {sourceDatasource ? (
                <>
                  <div style={{ marginBottom: '0.5rem' }}>
                    <strong>{sourceDatasource.datasourceName}</strong>
                    <span style={{ marginLeft: '0.5rem', fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                      ({sourceDatasource.dbType}) {sourceDatasource.host}:{sourceDatasource.port}
                    </span>
                  </div>
                  {sourceTables.length > 0 ? (
                    <div style={{ border: '1px solid var(--border-color)', borderRadius: '0.375rem', overflow: 'hidden' }}>
                      {sourceTables.map((table, idx) => (
                        <div key={table.id} style={{ padding: '0.5rem 0.75rem', borderBottom: idx < sourceTables.length - 1 ? '1px solid var(--border-color)' : 'none', background: idx % 2 === 0 ? 'var(--gray-50)' : 'white' }}>
                          <div style={{ fontWeight: 500 }}>{table.tableName}</div>
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                            {table.columns.map(c => c.columnName).join(', ')}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>선택된 테이블 없음</div>
                  )}
                </>
              ) : (
                <div style={{ color: 'var(--text-muted)' }}>설정되지 않음</div>
              )}
            </div>

            {/* Target */}
            <div>
              <h4 style={{ marginBottom: '0.75rem', color: 'var(--success-color, #28a745)' }}>Target</h4>
              {targetDatasource ? (
                <>
                  <div style={{ marginBottom: '0.5rem' }}>
                    <strong>{targetDatasource.datasourceName}</strong>
                    <span style={{ marginLeft: '0.5rem', fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                      ({targetDatasource.dbType}) {targetDatasource.host}:{targetDatasource.port}
                    </span>
                  </div>
                  {targetTables.length > 0 ? (
                    <div style={{ border: '1px solid var(--border-color)', borderRadius: '0.375rem', overflow: 'hidden' }}>
                      {targetTables.map((table, idx) => (
                        <div key={table.id} style={{ padding: '0.5rem 0.75rem', borderBottom: idx < targetTables.length - 1 ? '1px solid var(--border-color)' : 'none', background: idx % 2 === 0 ? 'var(--gray-50)' : 'white' }}>
                          <div style={{ fontWeight: 500 }}>{table.tableName}</div>
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                            {table.columns.map(c => c.columnName).join(', ')}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>선택된 테이블 없음</div>
                  )}
                </>
              ) : (
                <div style={{ color: 'var(--text-muted)' }}>설정되지 않음</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 실행 옵션 */}
      <div className="card" style={{ marginTop: '1.5rem' }}>
        <div className="card-header">
          <h2 className="card-title">실행 옵션</h2>
          <button
            className="btn btn-secondary btn-sm"
            onClick={handleRefreshExecutionParams}
            disabled={refreshingParams || agent.status === 'OFFLINE'}
          >
            {refreshingParams ? '갱신중...' : 'Agent에서 가져오기'}
          </button>
        </div>
        {executionParams.length === 0 ? (
          <div className="empty-state" style={{ marginTop: '1rem', padding: '2rem', textAlign: 'center' }}>
            등록된 실행 옵션이 없습니다.
            <br />
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Agent가 실행 옵션을 정의하고 있다면 &quot;Agent에서 가져오기&quot; 버튼을 클릭하세요.
            </span>
          </div>
        ) : (
          <div className="table-container" style={{ marginTop: '1rem' }}>
            <table>
              <thead>
                <tr>
                  <th>상태</th>
                  <th>이름</th>
                  <th>타입</th>
                  <th>기본값</th>
                  <th>설명</th>
                </tr>
              </thead>
              <tbody>
                {executionParams.map(param => (
                  <tr key={param.id}>
                    <td>{param.isEnabled ? '✅' : '⬜'}</td>
                    <td><strong>{param.label}</strong> <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>({param.paramId})</span></td>
                    <td><code>{param.dataType}</code></td>
                    <td style={{ fontSize: '0.875rem' }}>{param.defaultValue || '-'}</td>
                    <td style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>{param.description || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 스케줄 */}
      <div className="card" style={{ marginTop: '1.5rem' }}>
        <div className="card-header">
          <h2 className="card-title">스케줄</h2>
          {!showAddSchedule && <button className="btn btn-primary btn-sm" onClick={() => setShowAddSchedule(true)}>스케줄 추가</button>}
        </div>

        {showAddSchedule && (
          <div style={{ marginTop: '1rem', padding: '1rem', background: 'var(--gray-50)', borderRadius: '0.5rem' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label">Cron 표현식</label>
                <input type="text" className="form-input" value={newSchedule.cronExpression} onChange={(e) => setNewSchedule({ ...newSchedule, cronExpression: e.target.value })} />
                <small style={{ color: 'var(--gray-500)' }}>형식: 초 분 시 일 월 요일</small>
                {newSchedule.cronExpression && (
                  <div style={{ marginTop: '0.5rem', padding: '0.5rem', background: 'var(--gray-100)', borderRadius: '0.25rem', fontSize: '0.875rem' }}>
                    → {parseCronExpression(newSchedule.cronExpression)}
                  </div>
                )}
              </div>
              <div className="form-group">
                <label className="form-label">활성화 여부</label>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <input type="checkbox" checked={newSchedule.isEnabled} onChange={(e) => setNewSchedule({ ...newSchedule, isEnabled: e.target.checked })} />
                  등록 즉시 활성화
                </label>
              </div>
            </div>
            {/* 스케줄 실행 필터 */}
            {executionParams.filter(p => p.isEnabled).length > 0 && (
              <div style={{ marginTop: '1rem', padding: '0.75rem', background: 'var(--gray-100)', borderRadius: '0.375rem' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', fontWeight: 500, fontSize: '0.875rem' }}>
                  <input
                    type="checkbox"
                    checked={newSchedule.useFilters}
                    onChange={(e) => setNewSchedule({ ...newSchedule, useFilters: e.target.checked })}
                  />
                  실행 필터 설정
                </label>
                {newSchedule.useFilters && executionParams.filter(p => p.isEnabled).map(param => (
                  <div key={param.paramId} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginLeft: '1.5rem', marginBottom: '0.5rem' }}>
                    <span style={{ minWidth: '80px', fontSize: '0.875rem' }}>{param.label}</span>
                    <input
                      type="text"
                      className="form-input"
                      value={newSchedule.filters[param.paramId]?.value || ''}
                      onChange={(e) => setNewSchedule(prev => ({
                        ...prev,
                        filters: { ...prev.filters, [param.paramId]: { value: e.target.value } }
                      }))}
                      placeholder={param.description || ''}
                      style={{ width: '220px', padding: '0.2rem 0.4rem', fontSize: '0.8rem' }}
                    />
                  </div>
                ))}
              </div>
            )}
            <div style={{ marginTop: '1rem' }}>
              <button className="btn btn-primary btn-sm" onClick={handleAddSchedule} disabled={submitting} style={{ marginRight: '0.5rem' }}>
                {submitting ? '등록중...' : '등록'}
              </button>
              <button className="btn btn-secondary btn-sm" onClick={() => setShowAddSchedule(false)}>취소</button>
            </div>
          </div>
        )}

        {schedules.length === 0 ? (
          <div className="empty-state" style={{ marginTop: '1rem', padding: '2rem', textAlign: 'center' }}>등록된 스케줄이 없습니다</div>
        ) : (
          <div className="table-container" style={{ marginTop: '1rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Cron 표현식</th>
                  <th>필터</th>
                  <th>상태</th>
                  <th>작업</th>
                </tr>
              </thead>
              <tbody>
                {schedules.map((schedule) => (
                  <tr key={schedule.scheduleId}>
                    <td>
                      {editingScheduleId === schedule.scheduleId ? (
                        <div>
                          <input
                            type="text"
                            className="form-input"
                            value={editScheduleForm.cronExpression}
                            onChange={(e) => setEditScheduleForm({ cronExpression: e.target.value })}
                            style={{ width: '200px', padding: '0.25rem 0.5rem', fontSize: '0.875rem' }}
                            placeholder="0 0 * * * *"
                          />
                          <div style={{ fontSize: '0.75rem', color: 'var(--primary)', marginTop: '0.25rem' }}>
                            → {parseCronExpression(editScheduleForm.cronExpression)}
                          </div>
                        </div>
                      ) : (
                        <div>
                          <code>{schedule.cronExpression}</code>
                          <div style={{ fontSize: '0.75rem', color: 'var(--gray-500)', marginTop: '0.25rem' }}>
                            {parseCronExpression(schedule.cronExpression)}
                          </div>
                        </div>
                      )}
                    </td>
                    <td>
                      {schedule.executionOptions ? (() => {
                        try {
                          const opts = JSON.parse(schedule.executionOptions);
                          const filters = opts.filters || [];
                          if (filters.length === 0) return <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>-</span>;
                          return (
                            <span style={{ fontSize: '0.75rem' }}>
                              {filters.map((f: { paramId?: string; value?: string }, i: number) => (
                                <span key={i} style={{ display: 'inline-block', padding: '0.125rem 0.375rem', background: '#e3f2fd', borderRadius: '0.25rem', marginRight: '0.25rem', marginBottom: '0.125rem' }}>
                                  {f.paramId}={f.value}
                                </span>
                              ))}
                            </span>
                          );
                        } catch { return <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>-</span>; }
                      })() : <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>-</span>}
                    </td>
                    <td><StatusBadge status={schedule.isEnabled ? 'ONLINE' : 'OFFLINE'} /></td>
                    <td>
                      {editingScheduleId === schedule.scheduleId ? (
                        <>
                          <button
                            className="btn btn-primary btn-sm"
                            onClick={() => handleScheduleUpdate(schedule.scheduleId)}
                            disabled={submitting}
                            style={{ marginRight: '0.25rem' }}
                          >
                            {submitting ? '저장중...' : '저장'}
                          </button>
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={handleScheduleEditCancel}
                            disabled={submitting}
                          >
                            취소
                          </button>
                        </>
                      ) : (
                        <>
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => handleScheduleEditStart(schedule)}
                            style={{ marginRight: '0.25rem' }}
                          >
                            수정
                          </button>
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => handleScheduleToggle(schedule.scheduleId)}
                            style={{ marginRight: '0.25rem' }}
                          >
                            {schedule.isEnabled ? '비활성화' : '활성화'}
                          </button>
                          <button className="btn btn-danger btn-sm" onClick={() => handleScheduleDelete(schedule.scheduleId)}>삭제</button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
