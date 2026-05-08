'use client';

import { useState, useEffect, useCallback } from 'react';
import { agentApi, scheduleApi, datasourceApi } from '@/lib/api';
import type {
  Agent,
  Zone,
  Schedule,
  ScheduleCreateRequest,
  AgentType,
  Datasource,
  DatasourceTable,
  DatasourceSimple,
} from '@/types';
import StatusBadge from '@/components/StatusBadge';
import styles from './InfoTab.module.css';

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

const AGENT_TYPE_BADGE_CLASS: Record<string, string> = {
  RCV: styles['agentTypeBadge--rcv'],
  SND: styles['agentTypeBadge--snd'],
  LOADER: styles['agentTypeBadge--loader'],
  DB_CON_PROXY: styles['agentTypeBadge--proxy'],
};

/**
 * Spring Cron 표현식 해석 (6자리: 초 분 시 일 월 요일)
 */
function parseCronExpression(cron: string): string {
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 6) return '잘못된 형식';

  const [sec, min, hour, day, month, dow] = parts;

  const dowMap: Record<string, string> = {
    '0': '일', '1': '월', '2': '화', '3': '수', '4': '목', '5': '금', '6': '토', '7': '일',
    'SUN': '일', 'MON': '월', 'TUE': '화', 'WED': '수', 'THU': '목', 'FRI': '금', 'SAT': '토',
  };

  const parseDow = (d: string): string => {
    const upper = d.toUpperCase();
    if (upper === '*' || upper === '?') return '';
    if (upper.includes('#')) {
      const [dowPart, nth] = upper.split('#');
      const dowName = dowMap[dowPart] || dowPart;
      const ordinal = ['첫', '두', '세', '네', '다섯'][parseInt(nth) - 1] || nth;
      return `${ordinal}번째 ${dowName}요일`;
    }
    if (upper.endsWith('L')) {
      const dowPart = upper.slice(0, -1);
      if (dowPart) {
        const dowName = dowMap[dowPart] || dowPart;
        return `마지막 ${dowName}요일`;
      }
      return '마지막 요일';
    }
    if (upper === 'MON-FRI' || upper === '1-5') return '평일';
    if (upper === 'SAT,SUN' || upper === '0,6' || upper === '6,0' || upper === 'SAT-SUN' || upper === '6-7' || upper === '0,7') return '주말';
    if (upper.includes('-')) {
      const [from, to] = upper.split('-');
      return `${dowMap[from] || from}~${dowMap[to] || to}요일`;
    }
    if (upper.includes(',')) {
      return d.split(',').map((x) => dowMap[x.toUpperCase()] || x).join(',') + '요일';
    }
    return (dowMap[upper] || d) + '요일';
  };

  const dowStr = parseDow(dow);

  const parseInterval = (val: string, unit: string): string | null => {
    if (val.startsWith('*/')) {
      const interval = val.slice(2);
      return `${interval}${unit}마다`;
    }
    if (val.includes('/') && !val.startsWith('*')) {
      const [start, interval] = val.split('/');
      if (start.includes('-')) {
        const [from, to] = start.split('-');
        return `${from}~${to}${unit} 중 ${interval}${unit}마다`;
      }
      if (start === '0') return `${interval}${unit}마다`;
      return `${start}${unit}부터 ${interval}${unit}마다`;
    }
    return null;
  };

  const parseTime = (): string => {
    const secInterval = parseInterval(sec, '초');
    if (secInterval) return secInterval;

    if (sec === '0') {
      const minInterval = parseInterval(min, '분');
      if (minInterval) return minInterval;
    }

    if (sec === '0' && min === '0') {
      const hourInterval = parseInterval(hour, '시간');
      if (hourInterval) return hourInterval;
    }

    if (hour.includes('-') && !hour.includes('/')) {
      const [from, to] = hour.split('-');
      if (min === '0' && sec === '0') return `${from}시~${to}시 매시 정각`;
      if (min !== '*' && sec === '0') return `${from}시~${to}시 매시 ${min}분`;
      return `${from}시~${to}시`;
    }

    if (hour.includes(',')) {
      const hours = hour.split(',').join(', ');
      if (min !== '*' && sec === '0') return `${hours}시 ${min}분`;
      return `${hours}시`;
    }

    if (hour !== '*' && min !== '*' && sec === '0') {
      const h = hour.padStart(2, '0');
      const m = min.padStart(2, '0');
      if (day === '*' && month === '*' && (dow === '*' || dow === '?')) {
        return `매일 ${h}:${m}`;
      }
      return `${h}:${m}`;
    }

    if (hour === '*' && min !== '*' && sec === '0') {
      if (min.includes(',')) return `매시 ${min.split(',').join(', ')}분`;
      return min === '0' ? '매시 정각' : `매시 ${min}분`;
    }

    if (hour === '*' && min === '*' && sec === '0') return '매분';
    if (hour === '*' && min === '*' && sec === '*') return '매초';

    return '';
  };

  const timeStr = parseTime();

  const parseDayMonth = (): string => {
    const parts: string[] = [];
    if (month !== '*') {
      if (month.includes(',')) parts.push(`${month.split(',').join(', ')}월`);
      else if (month.includes('-')) {
        const [from, to] = month.split('-');
        parts.push(`${from}~${to}월`);
      } else parts.push(`${month}월`);
    }
    if (day !== '*' && day !== '?') {
      if (day.toUpperCase() === 'L') parts.push('마지막 날');
      else if (day.toUpperCase() === 'LW') parts.push('마지막 평일');
      else if (day.toUpperCase().endsWith('W')) {
        const dayNum = day.slice(0, -1);
        parts.push(`${dayNum}일에 가장 가까운 평일`);
      } else if (day.includes('-')) {
        const [from, to] = day.split('-');
        parts.push(`${from}~${to}일`);
      } else if (day.includes(',')) {
        parts.push(`${day.split(',').join(', ')}일`);
      } else {
        parts.push(month === '*' ? `매월 ${day}일` : `${day}일`);
      }
    }
    return parts.join(' ');
  };

  const dayMonthStr = parseDayMonth();
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

  const [editingScheduleId, setEditingScheduleId] = useState<number | null>(null);
  const [editScheduleForm, setEditScheduleForm] = useState({ cronExpression: '' });

  const [sourceDatasource, setSourceDatasource] = useState<Datasource | null>(null);
  const [targetDatasource, setTargetDatasource] = useState<Datasource | null>(null);
  const [sourceTables, setSourceTables] = useState<DatasourceTable[]>([]);
  const [targetTables, setTargetTables] = useState<DatasourceTable[]>([]);

  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  const [availableSourceTables, setAvailableSourceTables] = useState<DatasourceTable[]>([]);
  const [availableTargetTables, setAvailableTargetTables] = useState<DatasourceTable[]>([]);
  const [dsChangeMode, setDsChangeMode] = useState(false);
  const [sourceTableVerify, setSourceTableVerify] = useState<Record<string, boolean>>({});
  const [targetTableVerify, setTargetTableVerify] = useState<Record<string, boolean>>({});
  const [pipelineSourceTableNames, setPipelineSourceTableNames] = useState<string[]>([]);
  const [pipelineTargetTableNames, setPipelineTargetTableNames] = useState<string[]>([]);
  const [viewSourceVerify, setViewSourceVerify] = useState<Record<string, boolean>>({});
  const [viewTargetVerify, setViewTargetVerify] = useState<Record<string, boolean>>({});
  const [syncing, setSyncing] = useState(false);

  type RetentionTarget = { table: string; dateColumn: string; retentionDays: number };
  type RetentionState = { enabled: boolean; targetDatasourceId?: string; targets: RetentionTarget[] };
  const [retentionConfig, setRetentionConfig] = useState<RetentionState | null>(null);
  const [retentionEditMode, setRetentionEditMode] = useState(false);
  const [retentionForm, setRetentionForm] = useState<RetentionState>({ enabled: false, targets: [] });
  const [retentionSaving, setRetentionSaving] = useState(false);
  type RetentionCandidate = { table: string; dateColumn: string; description?: string };
  const [retentionCandidates, setRetentionCandidates] = useState<RetentionCandidate[]>([]);

  const fetchRetention = useCallback(async () => {
    if (agent.agentType === 'DB_CON_PROXY') return;
    try {
      const config = await agentApi.getRetentionConfig(agent.id);
      setRetentionConfig(config);
    } catch {
      setRetentionConfig(null);
    }
    try {
      const candidates = await agentApi.getRetentionCandidates(agent.id);
      setRetentionCandidates(candidates);
    } catch {
      setRetentionCandidates([]);
    }
  }, [agent.id, agent.agentType]);

  useEffect(() => {
    fetchRetention();
  }, [fetchRetention]);

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

      if (agent.sourceDatasourceId) {
        const tables = await datasourceApi.getRegisteredTables(agent.sourceDatasourceId);
        const registered = new Set(tables.map((t) => t.tableName.toUpperCase()));
        const v: Record<string, boolean> = {};
        uniqueSrc.forEach((name) => { v[name] = registered.has(name.toUpperCase()); });
        setViewSourceVerify(v);
      }
      if (agent.targetDatasourceId) {
        const tables = await datasourceApi.getRegisteredTables(agent.targetDatasourceId);
        const registered = new Set(tables.map((t) => t.tableName.toUpperCase()));
        const v: Record<string, boolean> = {};
        uniqueTgt.forEach((name) => { v[name] = registered.has(name.toUpperCase()); });
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
      ? { ...retentionConfig, targets: retentionConfig.targets.map((t) => ({ ...t })) }
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
    const usedTables = retentionForm.targets.map((t) => t.table);
    const firstAvailable = retentionCandidates.find((c) => !usedTables.includes(c.table));
    setRetentionForm((prev) => ({
      ...prev,
      enabled: true,
      targets: [...prev.targets, {
        table: firstAvailable?.table || '',
        dateColumn: firstAvailable?.dateColumn || '',
        retentionDays: 365,
      }],
    }));
  };

  const handleRetentionRemoveTarget = (index: number) => {
    setRetentionForm((prev) => ({
      ...prev,
      targets: prev.targets.filter((_, i) => i !== index),
    }));
  };

  const handleRetentionTargetChange = (index: number, field: keyof RetentionTarget, value: string | number) => {
    setRetentionForm((prev) => ({
      ...prev,
      targets: prev.targets.map((t, i) => {
        if (i !== index) return t;
        if (field === 'table') {
          const selected = retentionCandidates.find((c) => c.table === value);
          return { ...t, table: value as string, dateColumn: selected?.dateColumn || '' };
        }
        return { ...t, [field]: value };
      }),
    }));
  };

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
          setSourceTables(
            tables
              .filter((t) => agent.sourceTableIds?.includes(t.id))
              .sort((a, b) => a.tableName.localeCompare(b.tableName))
          );
        } catch (e) { console.error('Source datasource 조회 실패:', e); }
      }
      if (agent.targetDatasourceId) {
        try {
          const [ds, tables] = await Promise.all([
            datasourceApi.getById(agent.targetDatasourceId),
            datasourceApi.getRegisteredTables(agent.targetDatasourceId),
          ]);
          setTargetDatasource(ds);
          setTargetTables(
            tables
              .filter((t) => agent.targetTableIds?.includes(t.id))
              .sort((a, b) => a.tableName.localeCompare(b.tableName))
          );
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

  useEffect(() => {
    if (editMode && agentForm.sourceDatasourceId && pipelineSourceTableNames.length > 0) {
      datasourceApi.getRegisteredTables(agentForm.sourceDatasourceId)
        .then((tables) => {
          setAvailableSourceTables(tables.sort((a, b) => a.tableName.localeCompare(b.tableName)));
          const registeredNames = new Set(tables.map((t) => t.tableName.toUpperCase()));
          const verify: Record<string, boolean> = {};
          pipelineSourceTableNames.forEach((name) => {
            verify[name] = registeredNames.has(name.toUpperCase());
          });
          setSourceTableVerify(verify);
        })
        .catch(console.error);
    } else if (editMode && agentForm.sourceDatasourceId) {
      datasourceApi.getRegisteredTables(agentForm.sourceDatasourceId)
        .then((tables) => setAvailableSourceTables(tables.sort((a, b) => a.tableName.localeCompare(b.tableName))))
        .catch(console.error);
    } else {
      setAvailableSourceTables([]);
      setSourceTableVerify({});
    }
  }, [editMode, agentForm.sourceDatasourceId, pipelineSourceTableNames]);

  useEffect(() => {
    if (editMode && agentForm.targetDatasourceId && pipelineTargetTableNames.length > 0) {
      datasourceApi.getRegisteredTables(agentForm.targetDatasourceId)
        .then((tables) => {
          setAvailableTargetTables(tables.sort((a, b) => a.tableName.localeCompare(b.tableName)));
          const registeredNames = new Set(tables.map((t) => t.tableName.toUpperCase()));
          const verify: Record<string, boolean> = {};
          pipelineTargetTableNames.forEach((name) => {
            verify[name] = registeredNames.has(name.toUpperCase());
          });
          setTargetTableVerify(verify);
        })
        .catch(console.error);
    } else if (editMode && agentForm.targetDatasourceId) {
      datasourceApi.getRegisteredTables(agentForm.targetDatasourceId)
        .then((tables) => setAvailableTargetTables(tables.sort((a, b) => a.tableName.localeCompare(b.tableName))))
        .catch(console.error);
    } else {
      setAvailableTargetTables([]);
      setTargetTableVerify({});
    }
  }, [editMode, agentForm.targetDatasourceId, pipelineTargetTableNames]);

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
    agentApi.discover(agent.endpointUrl).then((result) => {
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

  const [newSchedule, setNewSchedule] = useState({
    cronExpression: '0 0 * * * *',
    isEnabled: true,
    useFilters: false,
    filters: {} as Record<string, { value: string }>,
  });

  const handleAgentUpdate = async () => {
    if (dsChangeMode) {
      const hasFail = Object.values(sourceTableVerify).some((v) => !v)
        || Object.values(targetTableVerify).some((v) => !v);
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

  /**
   * 테이블 박스 — 검증(verify) + 단순 목록(simple) 둘 다 같은 톤으로 렌더.
   * verify 가 주어지면 ✓/✗ 마크 + 미발견 표시, 아니면 단순 이름+description+컬럼수.
   */
  const renderTableBox = (
    tables: DatasourceTable[],
    options?: {
      verify?: Record<string, boolean>;
      missingLabel?: string;
      showColumnCount?: boolean;
    }
  ) => {
    const { verify, missingLabel = '(미발견)', showColumnCount = false } = options ?? {};

    if (verify) {
      return (
        <div className={styles.tableBox}>
          {Object.entries(verify).map(([table, found]) => {
            const tableInfo = tables.find(
              (t) => t.tableName.toUpperCase() === table.toUpperCase()
            );
            return (
              <div key={table} className={styles.tableBox__row}>
                <span className={found ? styles['verifyMark--ok'] : styles['verifyMark--fail']}>
                  {found ? '✓' : '✗'}
                </span>
                <span className={styles.tableBox__name}>{table}</span>
                {tableInfo?.description && (
                  <span className={styles.tableBox__description}>{tableInfo.description}</span>
                )}
                {showColumnCount && tableInfo?.columns && (
                  <span className={styles.tableBox__columnCount}>
                    ({tableInfo.columns.length}컬럼)
                  </span>
                )}
                {!found && <span className={styles.verifyMissing}>{missingLabel}</span>}
              </div>
            );
          })}
        </div>
      );
    }

    return (
      <div className={styles.tableBox}>
        {tables.map((t) => (
          <div key={t.id} className={styles.tableBox__row}>
            <span className={styles.tableBox__name}>{t.tableName}</span>
            {t.description && (
              <span className={styles.tableBox__description}>{t.description}</span>
            )}
            {showColumnCount && (
              <span className={styles.tableBox__columnCount}>({t.columns.length}컬럼)</span>
            )}
          </div>
        ))}
      </div>
    );
  };

  return (
    <>
      {/* Agent 정보 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">Agent 정보</h2>
          {!editMode ? (
            <button type="button" className="krds-btn small secondary" onClick={handleEditMode}>
              수정
            </button>
          ) : (
            <div className={styles.actionGroup}>
              <button
                type="button"
                className="krds-btn small primary"
                onClick={handleAgentUpdate}
                disabled={submitting}
              >
                {submitting ? '저장중...' : '저장'}
              </button>
              <button
                type="button"
                className="krds-btn small tertiary"
                onClick={() => setEditMode(false)}
              >
                취소
              </button>
            </div>
          )}
        </div>

        {!editMode ? (
          <div className={styles.infoGrid}>
            <div className={styles.infoGrid__row}>
              <span className={styles.infoGrid__label}>Agent Code:</span>
              {agent.agentCode}
            </div>
            <div className={styles.infoGrid__row}>
              <span className={styles.infoGrid__label}>이름:</span>
              {agent.agentName}
            </div>
            <div className={styles.infoGrid__row}>
              <span className={styles.infoGrid__label}>Agent 타입:</span>
              <span
                className={`${styles.agentTypeBadge} ${
                  AGENT_TYPE_BADGE_CLASS[agent.agentType ?? ''] ?? styles['agentTypeBadge--proxy']
                }`}
              >
                {AGENT_TYPE_LABELS[agent.agentType as AgentType] || agent.agentType}
              </span>
            </div>
            <div className={styles.infoGrid__row}>
              <span className={styles.infoGrid__label}>망구분:</span>
              <span className={`zone-${agent.zone.toLowerCase().replace('_', '-')}`}>
                {ZONE_LABELS[agent.zone as Zone]}
              </span>
            </div>
            <div className={styles.infoGrid__row}>
              <span className={styles.infoGrid__label}>Endpoint URL:</span>
              {agent.endpointUrl}
            </div>
            <div className={styles.infoGrid__row}>
              <span className={styles.infoGrid__label}>활성화:</span>
              {agent.isActive ? '예' : '아니오'}
            </div>
            <div className={styles.infoGrid__row}>
              <span className={styles.infoGrid__label}>등록일:</span>
              {agent.createdAt ? new Date(agent.createdAt).toLocaleString('ko-KR') : '-'}
            </div>
            {agent.description && (
              <div className={`${styles.infoGrid__row} ${styles['infoGrid__row--full']}`}>
                <span className={styles.infoGrid__label}>설명:</span>
                {agent.description}
              </div>
            )}
          </div>
        ) : (
          <div style={{ marginTop: '1.6rem' }}>
            {/* 기본 정보 */}
            <div className="app-form-grid">
              <div className="app-form-field">
                <label className="app-form-label">이름</label>
                <input
                  type="text"
                  className="krds-input"
                  value={agentForm.agentName}
                  onChange={(e) => setAgentForm({ ...agentForm, agentName: e.target.value })}
                />
              </div>
              <div className="app-form-field">
                <label className="app-form-label">Agent 타입</label>
                <select
                  className="krds-form-select"
                  value={agentForm.agentType}
                  onChange={(e) => setAgentForm({ ...agentForm, agentType: e.target.value as AgentType })}
                >
                  <option value="RCV">수신(RCV) - 외부 Source → IF</option>
                  <option value="LOADER">Loader - IF → Target 적재</option>
                  <option value="SND">송신(SND) - 내부 Target → IF</option>
                </select>
              </div>
              <div className="app-form-field">
                <label className="app-form-label">망구분</label>
                <select
                  className="krds-form-select"
                  value={agentForm.zone}
                  onChange={(e) => setAgentForm({ ...agentForm, zone: e.target.value })}
                >
                  <option value="EXTERNAL">외부망</option>
                  <option value="DMZ">DMZ</option>
                  <option value="INTERNAL_COMMON">내부공통망</option>
                  <option value="INTERNAL_SERVICE">내부서비스망</option>
                </select>
              </div>
              <div className="app-form-field">
                <label className="app-form-label">Endpoint URL</label>
                <input
                  type="url"
                  className="krds-input"
                  value={agentForm.endpointUrl}
                  onChange={(e) => setAgentForm({ ...agentForm, endpointUrl: e.target.value })}
                />
              </div>
              <div className="app-form-field" style={{ gridColumn: '1 / -1' }}>
                <label className="app-form-label">설명</label>
                <input
                  type="text"
                  className="krds-input"
                  value={agentForm.description}
                  onChange={(e) => setAgentForm({ ...agentForm, description: e.target.value })}
                />
              </div>
            </div>

            {/* Datasource & 테이블 설정 (프록시 Agent 제외) */}
            {agent.agentType !== 'DB_CON_PROXY' && (
              <div className={styles.dsSection}>
                <div className={styles.dsSectionHeader}>
                  <h4 className={styles.dsSectionTitle}>Datasource & 테이블 설정</h4>
                  <button
                    type="button"
                    className="krds-btn small secondary"
                    onClick={handleSyncTables}
                    disabled={syncing}
                  >
                    {syncing ? '갱신중...' : '테이블 갱신'}
                  </button>
                </div>
                <div className="app-form-grid">
                  {/* Source */}
                  <div>
                    <div className="app-form-field">
                      <label className={`app-form-label ${styles.sourceLabel}`}>Source Datasource</label>
                      {dsChangeMode ? (
                        <select
                          className="krds-form-select"
                          value={agentForm.sourceDatasourceId}
                          onChange={(e) => setAgentForm({ ...agentForm, sourceDatasourceId: e.target.value })}
                        >
                          <option value="">선택 안함</option>
                          {datasources.map((ds) => (
                            <option key={ds.datasourceId} value={ds.datasourceId}>
                              {ds.datasourceName} ({ds.dbType})
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input
                          type="text"
                          className={`krds-input ${styles.inputDisabled}`}
                          value={
                            sourceDatasource
                              ? `${sourceDatasource.datasourceName} (${sourceDatasource.dbType})`
                              : agentForm.sourceDatasourceId || '-'
                          }
                          disabled
                        />
                      )}
                    </div>
                    {Object.keys(sourceTableVerify).length > 0 &&
                      renderTableBox(availableSourceTables, { verify: sourceTableVerify })}
                    {Object.keys(sourceTableVerify).length === 0 && sourceTables.length > 0 && (
                      <div className="app-form-field" style={{ marginTop: '1rem' }}>
                        <label className="app-form-label">Source 테이블 ({sourceTables.length}개)</label>
                        {renderTableBox(sourceTables)}
                      </div>
                    )}
                  </div>

                  {/* Target */}
                  <div>
                    <div className="app-form-field">
                      <label className={`app-form-label ${styles.targetLabel}`}>Target Datasource</label>
                      {dsChangeMode ? (
                        <select
                          className="krds-form-select"
                          value={agentForm.targetDatasourceId}
                          onChange={(e) => setAgentForm({ ...agentForm, targetDatasourceId: e.target.value })}
                        >
                          <option value="">선택 안함</option>
                          {datasources.map((ds) => (
                            <option key={ds.datasourceId} value={ds.datasourceId}>
                              {ds.datasourceName} ({ds.dbType})
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input
                          type="text"
                          className={`krds-input ${styles.inputDisabled}`}
                          value={
                            targetDatasource
                              ? `${targetDatasource.datasourceName} (${targetDatasource.dbType})`
                              : agentForm.targetDatasourceId || '-'
                          }
                          disabled
                        />
                      )}
                    </div>
                    {Object.keys(targetTableVerify).length > 0 &&
                      renderTableBox(availableTargetTables, { verify: targetTableVerify })}
                    {Object.keys(targetTableVerify).length === 0 && targetTables.length > 0 && (
                      <div className="app-form-field" style={{ marginTop: '1rem' }}>
                        <label className="app-form-label">Target 테이블 ({targetTables.length}개)</label>
                        {renderTableBox(targetTables)}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Datasource & 테이블 정보 (보기 모드 카드) */}
      {agent.agentType !== 'DB_CON_PROXY' && (sourceDatasource || targetDatasource) && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">Datasource & 테이블</h2>
          </div>
          <div className={styles.dsViewGrid}>
            {/* Source */}
            <div>
              <h4 className={`${styles.dsViewSubtitle} ${styles['dsViewSubtitle--source']}`}>Source</h4>
              {sourceDatasource ? (
                <>
                  <div className={styles.dsViewMeta}>
                    <span className={styles.dsViewMeta__name}>{sourceDatasource.datasourceName}</span>
                    <span className={styles.dsViewMeta__sub}>
                      ({sourceDatasource.dbType}) {sourceDatasource.host}:{sourceDatasource.port}
                    </span>
                  </div>
                  {Object.keys(viewSourceVerify).length > 0
                    ? renderTableBox(sourceTables, {
                        verify: viewSourceVerify,
                        missingLabel: '(미등록)',
                        showColumnCount: true,
                      })
                    : sourceTables.length > 0
                    ? renderTableBox(sourceTables, { showColumnCount: true })
                    : (
                      <div className={styles.dsViewEmpty}>선택된 테이블 없음</div>
                    )}
                </>
              ) : (
                <div className={styles.dsViewEmpty}>설정되지 않음</div>
              )}
            </div>

            {/* Target */}
            <div>
              <h4 className={`${styles.dsViewSubtitle} ${styles['dsViewSubtitle--target']}`}>Target</h4>
              {targetDatasource ? (
                <>
                  <div className={styles.dsViewMeta}>
                    <span className={styles.dsViewMeta__name}>{targetDatasource.datasourceName}</span>
                    <span className={styles.dsViewMeta__sub}>
                      ({targetDatasource.dbType}) {targetDatasource.host}:{targetDatasource.port}
                    </span>
                  </div>
                  {Object.keys(viewTargetVerify).length > 0
                    ? renderTableBox(targetTables, {
                        verify: viewTargetVerify,
                        missingLabel: '(미등록)',
                        showColumnCount: true,
                      })
                    : targetTables.length > 0
                    ? renderTableBox(targetTables, { showColumnCount: true })
                    : (
                      <div className={styles.dsViewEmpty}>선택된 테이블 없음</div>
                    )}
                </>
              ) : (
                <div className={styles.dsViewEmpty}>설정되지 않음</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Retention 설정 (프록시 Agent 제외) */}
      {agent.agentType !== 'DB_CON_PROXY' && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">데이터 보존</h2>
            {retentionCandidates.length === 0 ? (
              <span className={styles.retentionMutedHeader}>
                비대상 (마스터 / Link / 메타 데이터)
              </span>
            ) : !retentionEditMode ? (
              <button type="button" className="krds-btn small secondary" onClick={handleRetentionEdit}>
                설정
              </button>
            ) : (
              <div className={styles.actionGroup}>
                <button
                  type="button"
                  className="krds-btn small primary"
                  onClick={handleRetentionSave}
                  disabled={retentionSaving}
                >
                  {retentionSaving ? '저장중...' : '저장'}
                </button>
                <button
                  type="button"
                  className="krds-btn small tertiary"
                  onClick={() => setRetentionEditMode(false)}
                >
                  취소
                </button>
              </div>
            )}
          </div>

          {retentionEditMode ? (
            <div style={{ marginTop: '1.6rem' }}>
              <div className={styles.retentionToolbar}>
                <span className={styles.retentionToolbar__label}>Target Datasource ID:</span>
                <input
                  type="text"
                  className={`krds-input ${styles.retentionToolbar__input}`}
                  value={retentionForm.targetDatasourceId || ''}
                  onChange={(e) =>
                    setRetentionForm((prev) => ({ ...prev, targetDatasourceId: e.target.value }))
                  }
                  placeholder={agent.targetDatasourceId || ''}
                />
              </div>
              <table className="app-table">
                <thead>
                  <tr>
                    <th>테이블</th>
                    <th>기준 날짜 컬럼</th>
                    <th>보존 기간 (일)</th>
                    <th style={{ width: '8rem' }}>삭제</th>
                  </tr>
                </thead>
                <tbody>
                  {retentionForm.targets.map((t, i) => {
                    const selectedCandidate = retentionCandidates.find((c) => c.table === t.table);
                    return (
                      <tr key={i}>
                        <td>
                          <select
                            className="krds-form-select"
                            value={t.table}
                            onChange={(e) => handleRetentionTargetChange(i, 'table', e.target.value)}
                          >
                            <option value="">-- 테이블 선택 --</option>
                            {retentionCandidates.map((c) => (
                              <option key={c.table} value={c.table}>
                                {c.table}
                                {c.description ? ` (${c.description})` : ''}
                              </option>
                            ))}
                          </select>
                        </td>
                        <td>
                          <input
                            type="text"
                            className={`krds-input ${styles.retentionDateColumnReadonly}`}
                            value={selectedCandidate?.dateColumn || t.dateColumn}
                            readOnly
                            title="yml retention-candidates 에서 자동 결정"
                          />
                        </td>
                        <td>
                          <input
                            type="number"
                            className="krds-input"
                            min={1}
                            value={t.retentionDays}
                            onChange={(e) =>
                              handleRetentionTargetChange(
                                i,
                                'retentionDays',
                                Math.max(1, parseInt(e.target.value) || 1)
                              )
                            }
                          />
                        </td>
                        <td>
                          <button
                            type="button"
                            className="krds-btn xsmall app-btn-danger"
                            onClick={() => handleRetentionRemoveTarget(i)}
                          >
                            X
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              <button
                type="button"
                className={`krds-btn small secondary ${styles.retentionAddBtn}`}
                onClick={handleRetentionAddTarget}
              >
                + 테이블 추가
              </button>
            </div>
          ) : retentionConfig && retentionConfig.targets.length > 0 ? (
            <div>
              <table className="app-table" style={{ marginTop: '1.6rem' }}>
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
                      <td>
                        {t.retentionDays}일 (
                        {Math.floor(t.retentionDays / 365) > 0
                          ? `${Math.floor(t.retentionDays / 365)}년`
                          : `${t.retentionDays}일`}
                        )
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className={styles.retentionHelp}>
                * 기준 날짜 컬럼 기준으로 보존 기간 이전 데이터가 자동 삭제됩니다. 제거하려면 행 삭제 후 저장하세요.
              </p>
            </div>
          ) : (
            <div className="app-empty">
              설정된 보존 정책이 없습니다. &quot;설정&quot; 버튼을 눌러 추가하세요.
            </div>
          )}
        </div>
      )}

      {/* 스케줄 (프록시 Agent 제외) */}
      {agent.agentType !== 'DB_CON_PROXY' && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">스케줄</h2>
            {!showAddSchedule && (
              <button
                type="button"
                className="krds-btn small primary"
                onClick={() => setShowAddSchedule(true)}
              >
                스케줄 추가
              </button>
            )}
          </div>

          {showAddSchedule && (
            <div className={styles.scheduleAddPanel}>
              <div className="app-form-grid">
                <div className="app-form-field">
                  <label className="app-form-label">Cron 표현식</label>
                  <input
                    type="text"
                    className={`krds-input small ${styles.cronInputAdd}`}
                    value={newSchedule.cronExpression}
                    onChange={(e) => setNewSchedule({ ...newSchedule, cronExpression: e.target.value })}
                  />
                  <small className="app-form-help">형식: 초 분 시 일 월 요일</small>
                  {newSchedule.cronExpression && (
                    <div className={styles.cronPreview}>
                      → {parseCronExpression(newSchedule.cronExpression)}
                    </div>
                  )}
                </div>
                <div className="app-form-field">
                  <label className="app-form-label">활성화 여부</label>
                  <label className={styles.checkboxLabel}>
                    <input
                      type="checkbox"
                      checked={newSchedule.isEnabled}
                      onChange={(e) => setNewSchedule({ ...newSchedule, isEnabled: e.target.checked })}
                    />
                    등록 즉시 활성화
                  </label>
                </div>
              </div>
              <div className="app-btn-row">
                <button
                  type="button"
                  className="krds-btn small primary"
                  onClick={handleAddSchedule}
                  disabled={submitting}
                >
                  {submitting ? '등록중...' : '등록'}
                </button>
                <button
                  type="button"
                  className="krds-btn small tertiary"
                  onClick={() => setShowAddSchedule(false)}
                >
                  취소
                </button>
              </div>
            </div>
          )}

          {schedules.length === 0 ? (
            <div className="app-empty">등록된 스케줄이 없습니다</div>
          ) : (
            <table className="app-table" style={{ marginTop: '1.6rem' }}>
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
                        <div className={styles.cronInputCell}>
                          <input
                            type="text"
                            className={`krds-input ${styles.cronInputCell__input}`}
                            value={editScheduleForm.cronExpression}
                            onChange={(e) => setEditScheduleForm({ cronExpression: e.target.value })}
                            placeholder="0 0 * * * *"
                          />
                          <div className={styles.cronArrow}>
                            → {parseCronExpression(editScheduleForm.cronExpression)}
                          </div>
                        </div>
                      ) : (
                        <div>
                          <code>{schedule.cronExpression}</code>
                          <div className={styles.muted}>
                            {parseCronExpression(schedule.cronExpression)}
                          </div>
                        </div>
                      )}
                    </td>
                    <td>
                      {schedule.executionOptions
                        ? (() => {
                            try {
                              const opts = JSON.parse(schedule.executionOptions);
                              const filters = opts.filters || [];
                              if (filters.length === 0) return <span className={styles.muted}>-</span>;
                              return (
                                <span>
                                  {filters.map((f: { paramId?: string; value?: string }, i: number) => (
                                    <span key={i} className={styles.filterChip}>
                                      {f.paramId}={f.value}
                                    </span>
                                  ))}
                                </span>
                              );
                            } catch {
                              return <span className={styles.muted}>-</span>;
                            }
                          })()
                        : <span className={styles.muted}>-</span>}
                    </td>
                    <td>
                      <StatusBadge status={schedule.isEnabled ? 'ONLINE' : 'OFFLINE'} />
                    </td>
                    <td>
                      <div className={styles.actionGroup}>
                        {editingScheduleId === schedule.scheduleId ? (
                          <>
                            <button
                              type="button"
                              className="krds-btn xsmall primary"
                              onClick={() => handleScheduleUpdate(schedule.scheduleId)}
                              disabled={submitting}
                            >
                              {submitting ? '저장중...' : '저장'}
                            </button>
                            <button
                              type="button"
                              className="krds-btn xsmall tertiary"
                              onClick={handleScheduleEditCancel}
                              disabled={submitting}
                            >
                              취소
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              type="button"
                              className="krds-btn xsmall secondary"
                              onClick={() => handleScheduleEditStart(schedule)}
                            >
                              수정
                            </button>
                            <button
                              type="button"
                              className="krds-btn xsmall secondary"
                              onClick={() => handleScheduleToggle(schedule.scheduleId)}
                            >
                              {schedule.isEnabled ? '비활성화' : '활성화'}
                            </button>
                            <button
                              type="button"
                              className="krds-btn xsmall app-btn-danger"
                              onClick={() => handleScheduleDelete(schedule.scheduleId)}
                            >
                              삭제
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </>
  );
}
