'use client';

import { useState, useEffect, useCallback } from 'react';
import { agentApi, scheduleApi, datasourceApi } from '@/lib/api';
import type { Agent, Zone, Schedule, ScheduleCreateRequest, AgentType, Datasource, DatasourceTable, DatasourceSimple } from '@/types';
import StatusBadge from '@/components/StatusBadge';

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
  const [dsChangeMode, setDsChangeMode] = useState(false);
  const [sourceTableVerify, setSourceTableVerify] = useState<Record<string, boolean>>({});
  const [targetTableVerify, setTargetTableVerify] = useState<Record<string, boolean>>({});
  // YAML 기반 파이프라인 테이블 목록 (검증 대상)
  const [pipelineSourceTableNames, setPipelineSourceTableNames] = useState<string[]>([]);
  const [pipelineTargetTableNames, setPipelineTargetTableNames] = useState<string[]>([]);
  // 읽기 모드용 pass/fail
  const [viewSourceVerify, setViewSourceVerify] = useState<Record<string, boolean>>({});
  const [viewTargetVerify, setViewTargetVerify] = useState<Record<string, boolean>>({});
  const [syncing, setSyncing] = useState(false);

  // 조건실행

  // Retention 설정
  type RetentionTarget = { table: string; dateColumn: string; retentionDays: number };
  type RetentionState = { enabled: boolean; targetDatasourceId?: string; targets: RetentionTarget[] };
  const [retentionConfig, setRetentionConfig] = useState<RetentionState | null>(null);
  const [retentionEditMode, setRetentionEditMode] = useState(false);
  const [retentionForm, setRetentionForm] = useState<RetentionState>({ enabled: false, targets: [] });
  const [retentionSaving, setRetentionSaving] = useState(false);
  const [retentionTables, setRetentionTables] = useState<DatasourceTable[]>([]);

  const fetchRetention = useCallback(async () => {
    if (agent.agentType === 'DB_CON_PROXY') return;
    try {
      const config = await agentApi.getRetentionConfig(agent.id);
      setRetentionConfig(config);
    } catch {
      setRetentionConfig(null);
    }
    // retention select용: target datasource의 전체 등록 테이블
    const dsId = agent.targetDatasourceId;
    if (dsId) {
      try {
        const tables = await datasourceApi.getRegisteredTables(dsId);
        setRetentionTables(tables);
      } catch { /* ignore */ }
    }
  }, [agent.id, agent.agentType, agent.targetDatasourceId]);

  useEffect(() => {
    fetchRetention();
  }, [fetchRetention]);

  // 읽기 모드: pipeline/info 조회 → pass/fail
  const loadPipelineVerify = useCallback(async () => {
    if (agent.agentType === 'DB_CON_PROXY' || !agent.endpointUrl) return;
    try {
      const result = await agentApi.discover(agent.endpointUrl);
      const infoList = (result as any).agentInfo || [];
      const info = infoList.find((a: any) => a.agentCode === agent.agentCode);
      if (!info) return;

      const srcNames: string[] = (info.steps || []).flatMap((s: any) => s.sourceTables || []);
      const tgtNames: string[] = (info.steps || []).flatMap((s: any) => s.targetTables || []);
      const uniqueSrc = Array.from(new Set(srcNames));
      const uniqueTgt = Array.from(new Set(tgtNames));

      // source 검증
      if (agent.sourceDatasourceId) {
        const tables = await datasourceApi.getRegisteredTables(agent.sourceDatasourceId);
        const registered = new Set(tables.map(t => t.tableName.toUpperCase()));
        const v: Record<string, boolean> = {};
        uniqueSrc.forEach(name => { v[name] = registered.has(name.toUpperCase()); });
        setViewSourceVerify(v);
      }
      // target 검증
      if (agent.targetDatasourceId) {
        const tables = await datasourceApi.getRegisteredTables(agent.targetDatasourceId);
        const registered = new Set(tables.map(t => t.tableName.toUpperCase()));
        const v: Record<string, boolean> = {};
        uniqueTgt.forEach(name => { v[name] = registered.has(name.toUpperCase()); });
        setViewTargetVerify(v);
      }
    } catch {
      // Agent OFFLINE — 무시
    }
  }, [agent.agentCode, agent.endpointUrl, agent.agentType, agent.sourceDatasourceId, agent.targetDatasourceId]);

  useEffect(() => {
    loadPipelineVerify();
  }, [loadPipelineVerify]);

  const handleSyncTables = async () => {
    setSyncing(true);
    try {
      await agentApi.syncTables(agent.id);
      await loadPipelineVerify();
      onUpdate();
    } catch (error) {
      console.error('테이블 갱신 실패:', error);
      alert('테이블 갱신에 실패했습니다. Agent가 실행 중인지 확인하세요.');
    } finally {
      setSyncing(false);
    }
  };

  const handleRetentionEdit = () => {
    setRetentionForm(retentionConfig
      ? { ...retentionConfig, targets: retentionConfig.targets.map(t => ({ ...t })) }
      : { enabled: true, targetDatasourceId: agent.targetDatasourceId || '', targets: [] });
    setRetentionEditMode(true);
  };

  const handleRetentionSave = async () => {
    setRetentionSaving(true);
    try {
      await agentApi.updateRetentionConfig(agent.id, retentionForm);
      setRetentionEditMode(false);
      fetchRetention();
    } catch (error) {
      console.error('Retention 설정 저장 실패:', error);
      alert('저장에 실패했습니다.');
    } finally {
      setRetentionSaving(false);
    }
  };

  const handleRetentionAddTarget = () => {
    const usedTables = retentionForm.targets.map(t => t.table);
    const firstAvailable = retentionTables.find(tb => !usedTables.includes(tb.tableName));
    setRetentionForm(prev => ({
      ...prev,
      targets: [...prev.targets, {
        table: firstAvailable?.tableName || '',
        dateColumn: firstAvailable?.columns[0]?.columnName || '',
        retentionDays: 365
      }]
    }));
  };

  const handleRetentionRemoveTarget = (index: number) => {
    setRetentionForm(prev => ({
      ...prev,
      targets: prev.targets.filter((_, i) => i !== index)
    }));
  };

  const handleRetentionTargetChange = (index: number, field: keyof RetentionTarget, value: string | number) => {
    setRetentionForm(prev => ({
      ...prev,
      targets: prev.targets.map((t, i) => {
        if (i !== index) return t;
        if (field === 'table') {
          const selected = retentionTables.find(tb => tb.tableName === value);
          return { ...t, table: value as string, dateColumn: selected?.columns[0]?.columnName || '' };
        }
        return { ...t, [field]: value };
      })
    }));
  };

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
          setSourceTables(tables.filter(t => agent.sourceTableIds?.includes(t.id)).sort((a, b) => a.tableName.localeCompare(b.tableName)));
        } catch (e) { console.error('Source datasource 조회 실패:', e); }
      }
      if (agent.targetDatasourceId) {
        try {
          const [ds, tables] = await Promise.all([
            datasourceApi.getById(agent.targetDatasourceId),
            datasourceApi.getRegisteredTables(agent.targetDatasourceId),
          ]);
          setTargetDatasource(ds);
          setTargetTables(tables.filter(t => agent.targetTableIds?.includes(t.id)).sort((a, b) => a.tableName.localeCompare(b.tableName)));
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

  // Source Datasource 변경 시 테이블 목록 로드 + YAML 기반 검증
  useEffect(() => {
    if (editMode && agentForm.sourceDatasourceId && pipelineSourceTableNames.length > 0) {
      datasourceApi.getRegisteredTables(agentForm.sourceDatasourceId)
        .then(tables => {
          setAvailableSourceTables(tables.sort((a, b) => a.tableName.localeCompare(b.tableName)));
          const registeredNames = new Set(tables.map(t => t.tableName.toUpperCase()));
          const verify: Record<string, boolean> = {};
          pipelineSourceTableNames.forEach(name => {
            verify[name] = registeredNames.has(name.toUpperCase());
          });
          setSourceTableVerify(verify);
        })
        .catch(console.error);
    } else if (editMode && agentForm.sourceDatasourceId) {
      datasourceApi.getRegisteredTables(agentForm.sourceDatasourceId)
        .then(tables => setAvailableSourceTables(tables.sort((a, b) => a.tableName.localeCompare(b.tableName))))
        .catch(console.error);
    } else {
      setAvailableSourceTables([]);
      setSourceTableVerify({});
    }
  }, [editMode, agentForm.sourceDatasourceId, pipelineSourceTableNames]);

  // Target Datasource 변경 시 테이블 목록 로드 + YAML 기반 검증
  useEffect(() => {
    if (editMode && agentForm.targetDatasourceId && pipelineTargetTableNames.length > 0) {
      datasourceApi.getRegisteredTables(agentForm.targetDatasourceId)
        .then(tables => {
          setAvailableTargetTables(tables.sort((a, b) => a.tableName.localeCompare(b.tableName)));
          const registeredNames = new Set(tables.map(t => t.tableName.toUpperCase()));
          const verify: Record<string, boolean> = {};
          pipelineTargetTableNames.forEach(name => {
            verify[name] = registeredNames.has(name.toUpperCase());
          });
          setTargetTableVerify(verify);
        })
        .catch(console.error);
    } else if (editMode && agentForm.targetDatasourceId) {
      datasourceApi.getRegisteredTables(agentForm.targetDatasourceId)
        .then(tables => setAvailableTargetTables(tables.sort((a, b) => a.tableName.localeCompare(b.tableName))))
        .catch(console.error);
    } else {
      setAvailableTargetTables([]);
      setTargetTableVerify({});
    }
  }, [editMode, agentForm.targetDatasourceId, pipelineTargetTableNames]);

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
    setDsChangeMode(true);
    // YAML 기반 파이프라인 테이블 목록 조회
    agentApi.discover(agent.endpointUrl).then(result => {
      const infoList = (result as any).agentInfo || [];
      const info = infoList.find((a: any) => a.agentCode === agent.agentCode);
      if (info) {
        const srcTables = (info.steps || []).flatMap((s: any) => s.sourceTables || []);
        const tgtTables = (info.steps || []).flatMap((s: any) => s.targetTables || []);
        setPipelineSourceTableNames(Array.from(new Set(srcTables as string[])));
        setPipelineTargetTableNames(Array.from(new Set(tgtTables as string[])));
      }
    }).catch(() => { /* Agent 미기동 시 무시 */ });
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

  const handleAgentUpdate = async () => {
    // datasource 변경 시 테이블 검증 실패 차단
    if (dsChangeMode) {
      const hasFail = Object.values(sourceTableVerify).some(v => !v)
                   || Object.values(targetTableVerify).some(v => !v);
      if (hasFail) {
        alert('미발견 테이블이 있는 Datasource로 변경할 수 없습니다.');
        return;
      }
    }
    setSubmitting(true);
    try {
      await agentApi.update(agent.id, agentForm);
      setEditMode(false);
      setDsChangeMode(false);
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

            {/* Datasource & 테이블 설정 (프록시 Agent 제외) */}
            {agent.agentType !== 'DB_CON_PROXY' && (
            <div style={{ marginTop: '1.5rem', padding: '1rem', background: 'var(--gray-50)', borderRadius: '0.5rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h4 style={{ fontSize: '0.9rem', fontWeight: 600, margin: 0 }}>Datasource & 테이블 설정</h4>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={handleSyncTables}
                  disabled={syncing}
                >
                  {syncing ? '갱신중...' : '테이블 갱신'}
                </button>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
                {/* Source */}
                <div>
                  <div className="form-group">
                    <label className="form-label" style={{ color: 'var(--primary-color, #1976d2)' }}>Source Datasource</label>
                    {dsChangeMode ? (
                      <select
                        className="form-select"
                        value={agentForm.sourceDatasourceId}
                        onChange={(e) => setAgentForm({ ...agentForm, sourceDatasourceId: e.target.value })}
                      >
                        <option value="">선택 안함</option>
                        {datasources.map(ds => (
                          <option key={ds.datasourceId} value={ds.datasourceId}>
                            {ds.datasourceName} ({ds.dbType})
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input type="text" className="form-input" value={sourceDatasource ? `${sourceDatasource.datasourceName} (${sourceDatasource.dbType})` : agentForm.sourceDatasourceId || '-'} disabled style={{ background: 'var(--gray-100)' }} />
                    )}
                  </div>
                  {/* 테이블 검증 */}
                  {Object.keys(sourceTableVerify).length > 0 && (
                    <div style={{ fontSize: '0.8rem', padding: '0.5rem', background: 'white', borderRadius: '0.25rem', border: '1px solid var(--border-color)', marginTop: '0.5rem' }}>
                      {Object.entries(sourceTableVerify).map(([table, found]) => {
                        const tableInfo = availableSourceTables.find(t => t.tableName.toUpperCase() === table.toUpperCase());
                        return (
                          <div key={table} style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', padding: '0.125rem 0' }}>
                            <span style={{ color: found ? '#16a34a' : '#dc2626' }}>{found ? '✓' : '✗'}</span>
                            <span>{table}</span>
                            {tableInfo?.description && <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{tableInfo.description}</span>}
                            {!found && <span style={{ color: '#dc2626', fontSize: '0.7rem' }}>(미발견)</span>}
                          </div>
                        );
                      })}
                    </div>
                  )}
                  {/* 테이블 목록 (검증 없을 때만 표시) */}
                  {Object.keys(sourceTableVerify).length === 0 && sourceTables.length > 0 && (
                    <div className="form-group" style={{ marginTop: '0.75rem' }}>
                      <label className="form-label">Source 테이블 ({sourceTables.length}개)</label>
                      <div style={{ fontSize: '0.85rem', padding: '0.5rem', background: 'white', borderRadius: '0.375rem', border: '1px solid var(--border-color)' }}>
                        {sourceTables.map(t => (
                          <div key={t.id} style={{ padding: '0.125rem 0', color: 'var(--text-primary)' }}>
                            {t.tableName}
                            {t.description && <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: '0.5rem' }}>{t.description}</span>}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                {/* Target */}
                <div>
                  <div className="form-group">
                    <label className="form-label" style={{ color: 'var(--success-color, #28a745)' }}>Target Datasource</label>
                    {dsChangeMode ? (
                      <select
                        className="form-select"
                        value={agentForm.targetDatasourceId}
                        onChange={(e) => setAgentForm({ ...agentForm, targetDatasourceId: e.target.value })}
                      >
                        <option value="">선택 안함</option>
                        {datasources.map(ds => (
                          <option key={ds.datasourceId} value={ds.datasourceId}>
                            {ds.datasourceName} ({ds.dbType})
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input type="text" className="form-input" value={targetDatasource ? `${targetDatasource.datasourceName} (${targetDatasource.dbType})` : agentForm.targetDatasourceId || '-'} disabled style={{ background: 'var(--gray-100)' }} />
                    )}
                  </div>
                  {/* 테이블 검증 */}
                  {Object.keys(targetTableVerify).length > 0 && (
                    <div style={{ fontSize: '0.8rem', padding: '0.5rem', background: 'white', borderRadius: '0.25rem', border: '1px solid var(--border-color)', marginTop: '0.5rem' }}>
                      {Object.entries(targetTableVerify).map(([table, found]) => {
                        const tableInfo = availableTargetTables.find(t => t.tableName.toUpperCase() === table.toUpperCase());
                        return (
                          <div key={table} style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', padding: '0.125rem 0' }}>
                            <span style={{ color: found ? '#16a34a' : '#dc2626' }}>{found ? '✓' : '✗'}</span>
                            <span>{table}</span>
                            {tableInfo?.description && <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{tableInfo.description}</span>}
                            {!found && <span style={{ color: '#dc2626', fontSize: '0.7rem' }}>(미발견)</span>}
                          </div>
                        );
                      })}
                    </div>
                  )}
                  {/* 테이블 목록 (검증 없을 때만 표시) */}
                  {Object.keys(targetTableVerify).length === 0 && targetTables.length > 0 && (
                    <div className="form-group" style={{ marginTop: '0.75rem' }}>
                      <label className="form-label">Target 테이블 ({targetTables.length}개)</label>
                      <div style={{ fontSize: '0.85rem', padding: '0.5rem', background: 'white', borderRadius: '0.375rem', border: '1px solid var(--border-color)' }}>
                        {targetTables.map(t => (
                          <div key={t.id} style={{ padding: '0.125rem 0', color: 'var(--text-primary)' }}>
                            {t.tableName}
                            {t.description && <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: '0.5rem' }}>{t.description}</span>}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
            )}
          </div>
        )}
      </div>

      {/* Datasource & 테이블 정보 (프록시 Agent 제외) */}
      {agent.agentType !== 'DB_CON_PROXY' && (sourceDatasource || targetDatasource) && (
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
                  {Object.keys(viewSourceVerify).length > 0 ? (
                    <div style={{ border: '1px solid var(--border-color)', borderRadius: '0.375rem', overflow: 'hidden' }}>
                      {Object.entries(viewSourceVerify).map(([table, found], idx) => {
                        const tableInfo = sourceTables.find(t => t.tableName.toUpperCase() === table.toUpperCase());
                        return (
                          <div key={table} style={{ padding: '0.5rem 0.75rem', borderBottom: idx < Object.keys(viewSourceVerify).length - 1 ? '1px solid var(--border-color)' : 'none', background: idx % 2 === 0 ? 'var(--gray-50)' : 'white' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                              <span style={{ color: found ? '#16a34a' : '#dc2626' }}>{found ? '✓' : '✗'}</span>
                              <span style={{ fontWeight: 500 }}>{table}</span>
                              {tableInfo?.description && <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{tableInfo.description}</span>}
                              {tableInfo?.columns && <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>({tableInfo.columns.length}컬럼)</span>}
                              {!found && <span style={{ color: '#dc2626', fontSize: '0.75rem' }}>(미등록)</span>}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ) : sourceTables.length > 0 ? (
                    <div style={{ border: '1px solid var(--border-color)', borderRadius: '0.375rem', overflow: 'hidden' }}>
                      {sourceTables.map((table, idx) => (
                        <div key={table.id} style={{ padding: '0.5rem 0.75rem', borderBottom: idx < sourceTables.length - 1 ? '1px solid var(--border-color)' : 'none', background: idx % 2 === 0 ? 'var(--gray-50)' : 'white' }}>
                          <div style={{ fontWeight: 500 }}>
                            {table.tableName}
                            {table.description && <span style={{ fontWeight: 400, fontSize: '0.8rem', color: 'var(--text-muted)', marginLeft: '0.5rem' }}> - {table.description}</span>}
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: '0.5rem' }}>({table.columns.length}컬럼)</span>
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
                  {Object.keys(viewTargetVerify).length > 0 ? (
                    <div style={{ border: '1px solid var(--border-color)', borderRadius: '0.375rem', overflow: 'hidden' }}>
                      {Object.entries(viewTargetVerify).map(([table, found], idx) => {
                        const tableInfo = targetTables.find(t => t.tableName.toUpperCase() === table.toUpperCase());
                        return (
                          <div key={table} style={{ padding: '0.5rem 0.75rem', borderBottom: idx < Object.keys(viewTargetVerify).length - 1 ? '1px solid var(--border-color)' : 'none', background: idx % 2 === 0 ? 'var(--gray-50)' : 'white' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                              <span style={{ color: found ? '#16a34a' : '#dc2626' }}>{found ? '✓' : '✗'}</span>
                              <span style={{ fontWeight: 500 }}>{table}</span>
                              {tableInfo?.description && <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{tableInfo.description}</span>}
                              {tableInfo?.columns && <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>({tableInfo.columns.length}컬럼)</span>}
                              {!found && <span style={{ color: '#dc2626', fontSize: '0.75rem' }}>(미등록)</span>}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ) : targetTables.length > 0 ? (
                    <div style={{ border: '1px solid var(--border-color)', borderRadius: '0.375rem', overflow: 'hidden' }}>
                      {targetTables.map((table, idx) => (
                        <div key={table.id} style={{ padding: '0.5rem 0.75rem', borderBottom: idx < targetTables.length - 1 ? '1px solid var(--border-color)' : 'none', background: idx % 2 === 0 ? 'var(--gray-50)' : 'white' }}>
                          <div style={{ fontWeight: 500 }}>
                            {table.tableName}
                            {table.description && <span style={{ fontWeight: 400, fontSize: '0.8rem', color: 'var(--text-muted)', marginLeft: '0.5rem' }}> - {table.description}</span>}
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: '0.5rem' }}>({table.columns.length}컬럼)</span>
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

      {/* Retention 설정 (프록시 Agent 제외) */}
      {agent.agentType !== 'DB_CON_PROXY' && (
        <div className="card" style={{ marginTop: '1.5rem' }}>
          <div className="card-header">
            <h2 className="card-title">데이터 보존 (Retention)</h2>
            {!retentionEditMode ? (
              <button className="btn btn-secondary btn-sm" onClick={handleRetentionEdit}>설정</button>
            ) : (
              <div>
                <button className="btn btn-primary btn-sm" onClick={handleRetentionSave} disabled={retentionSaving} style={{ marginRight: '0.5rem' }}>
                  {retentionSaving ? '저장중...' : '저장'}
                </button>
                <button className="btn btn-secondary btn-sm" onClick={() => setRetentionEditMode(false)}>취소</button>
              </div>
            )}
          </div>

          {retentionEditMode ? (
            <div style={{ marginTop: '1rem' }}>
              <div style={{ marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                  <input type="checkbox" checked={retentionForm.enabled} onChange={(e) => setRetentionForm(prev => ({ ...prev, enabled: e.target.checked }))} />
                  활성화
                </label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <label className="form-label" style={{ margin: 0, fontSize: '0.875rem' }}>Target Datasource ID:</label>
                  <input type="text" className="form-input" value={retentionForm.targetDatasourceId || ''} onChange={(e) => setRetentionForm(prev => ({ ...prev, targetDatasourceId: e.target.value }))} style={{ width: '150px', padding: '0.25rem 0.5rem', fontSize: '0.875rem' }} placeholder={agent.targetDatasourceId || ''} />
                </div>
              </div>
              <div className="table-container">
                <table>
                  <thead>
                    <tr>
                      <th>테이블</th>
                      <th>기준 날짜 컬럼</th>
                      <th>보존 기간 (일)</th>
                      <th style={{ width: '60px' }}>삭제</th>
                    </tr>
                  </thead>
                  <tbody>
                    {retentionForm.targets.map((t, i) => {
                      const selectedTable = retentionTables.find(tb => tb.tableName === t.table);
                      return (
                        <tr key={i}>
                          <td>
                            <select className="form-input" value={t.table} onChange={(e) => handleRetentionTargetChange(i, 'table', e.target.value)} style={{ padding: '0.25rem 0.5rem', fontSize: '0.875rem' }}>
                              <option value="">-- 테이블 선택 --</option>
                              {retentionTables.map(tb => (
                                <option key={tb.id} value={tb.tableName}>{tb.tableName}{tb.description ? ` (${tb.description})` : ''}</option>
                              ))}
                            </select>
                          </td>
                          <td>
                            <select className="form-input" value={t.dateColumn} onChange={(e) => handleRetentionTargetChange(i, 'dateColumn', e.target.value)} style={{ padding: '0.25rem 0.5rem', fontSize: '0.875rem' }}>
                              <option value="">-- 컬럼 선택 --</option>
                              {selectedTable?.columns.map(c => (
                                <option key={c.id} value={c.columnName}>{c.columnName}{c.dataType ? ` (${c.dataType})` : ''}</option>
                              ))}
                            </select>
                          </td>
                          <td><input type="number" className="form-input" min={1} value={t.retentionDays} onChange={(e) => handleRetentionTargetChange(i, 'retentionDays', Math.max(1, parseInt(e.target.value) || 1))} style={{ width: '100px', padding: '0.25rem 0.5rem', fontSize: '0.875rem' }} /></td>
                          <td><button className="btn btn-danger btn-sm" onClick={() => handleRetentionRemoveTarget(i)}>X</button></td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <button className="btn btn-secondary btn-sm" onClick={handleRetentionAddTarget} style={{ marginTop: '0.5rem' }}>+ 테이블 추가</button>
            </div>
          ) : retentionConfig && retentionConfig.enabled && retentionConfig.targets.length > 0 ? (
            <div>
              <div className="table-container" style={{ marginTop: '1rem' }}>
                <table>
                  <thead>
                    <tr>
                      <th>테이블</th>
                      <th>기준 날짜 컬럼</th>
                      <th>보존 기간</th>
                    </tr>
                  </thead>
                  <tbody>
                    {retentionConfig.targets.map((t, i) => (
                      <tr key={i}>
                        <td><code>{t.table}</code></td>
                        <td><code>{t.dateColumn}</code></td>
                        <td>{t.retentionDays}일 ({Math.floor(t.retentionDays / 365) > 0 ? `${Math.floor(t.retentionDays / 365)}년` : `${t.retentionDays}일`})</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                * 기준 날짜 컬럼 기준으로 보존 기간 이전 데이터가 자동 삭제됩니다.
              </p>
            </div>
          ) : (
            <div className="empty-state" style={{ marginTop: '1rem', padding: '1.5rem', textAlign: 'center' }}>
              설정된 보존 정책이 없습니다. &quot;설정&quot; 버튼을 눌러 추가하세요.
            </div>
          )}
        </div>
      )}

      {/* 스케줄 (프록시 Agent 제외) */}
      {agent.agentType !== 'DB_CON_PROXY' && (
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
      )}
    </>
  );
}
