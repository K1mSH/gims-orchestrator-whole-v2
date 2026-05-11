'use client';

import { useCallback, useEffect, useState } from 'react';
import { scheduleApi } from '@/lib/collectorApi';
import { ApiScheduleItem } from '@/types/api-collect';
import styles from './ScheduleTab.module.css';

interface ScheduleTabProps {
  endpointId: number;
}

const CRON_PRESETS = [
  { label: '매 1분', cron: '0 */1 * * * *' },
  { label: '매 5분', cron: '0 */5 * * * *' },
  { label: '매 10분', cron: '0 */10 * * * *' },
  { label: '매 30분', cron: '0 */30 * * * *' },
  { label: '매 1시간', cron: '0 0 * * * *' },
  { label: '매일 00시', cron: '0 0 0 * * *' },
  { label: '매일 06시', cron: '0 0 6 * * *' },
  { label: '매일 12시', cron: '0 0 12 * * *' },
];

export default function ScheduleTab({ endpointId }: ScheduleTabProps) {
  const [schedules, setSchedules] = useState<ApiScheduleItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [newCron, setNewCron] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editCron, setEditCron] = useState('');

  const fetchSchedules = useCallback(async () => {
    try {
      setLoading(true);
      const data = await scheduleApi.getAll(endpointId);
      setSchedules(data);
    } catch (e) {
      console.error('스케줄 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  }, [endpointId]);

  useEffect(() => {
    fetchSchedules();
  }, [fetchSchedules]);

  const handleCreate = async () => {
    const cron = newCron.trim();
    if (!cron) return;
    try {
      await scheduleApi.create(endpointId, cron);
      setNewCron('');
      fetchSchedules();
    } catch (e) {
      console.error('스케줄 생성 실패:', e);
      alert('스케줄 생성에 실패했습니다. Cron 표현식을 확인하세요.');
    }
  };

  const handleToggle = async (id: number) => {
    try {
      await scheduleApi.toggle(id);
      fetchSchedules();
    } catch (e) {
      console.error('토글 실패:', e);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('이 스케줄을 삭제하시겠습니까?')) return;
    try {
      await scheduleApi.delete(id);
      fetchSchedules();
    } catch (e) {
      console.error('삭제 실패:', e);
    }
  };

  const handleUpdate = async (id: number) => {
    const cron = editCron.trim();
    if (!cron) return;
    try {
      await scheduleApi.update(id, cron);
      setEditingId(null);
      fetchSchedules();
    } catch (e) {
      console.error('수정 실패:', e);
      alert('스케줄 수정에 실패했습니다.');
    }
  };

  if (loading) {
    return <div className="app-loading">로딩 중...</div>;
  }

  return (
    <div className="app-card">
      <div className="app-card__header">
        <h2 className="app-card__title">스케줄 관리</h2>
      </div>

      {/* 새 스케줄 추가 */}
      <div className={styles.addRow}>
        <input
          type="text"
          value={newCron}
          onChange={e => setNewCron(e.target.value)}
          placeholder="Cron 표현식 (예: 0 */10 * * * *)"
          className={`krds-input small ${styles.cronInput}`}
        />
        <button
          type="button"
          className="krds-btn small"
          onClick={handleCreate}
          disabled={!newCron.trim()}
        >
          추가
        </button>
      </div>
      <div className={styles.presetRow}>
        {CRON_PRESETS.map(p => (
          <button
            key={p.cron}
            type="button"
            className="krds-btn xsmall secondary"
            onClick={() => setNewCron(p.cron)}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* 스케줄 목록 */}
      {schedules.length === 0 ? (
        <div className="app-empty">등록된 스케줄이 없습니다.</div>
      ) : (
        <table className="app-table">
          <thead>
            <tr>
              <th>Cron 표현식</th>
              <th>상태</th>
              <th>등록일</th>
              <th>동작</th>
            </tr>
          </thead>
          <tbody>
            {schedules.map(s => (
              <tr key={s.id}>
                <td>
                  {editingId === s.id ? (
                    <div className={styles.editRow}>
                      <input
                        type="text"
                        value={editCron}
                        onChange={e => setEditCron(e.target.value)}
                        className={`krds-input small ${styles.editInput}`}
                      />
                      <button type="button" className="krds-btn small" onClick={() => handleUpdate(s.id)}>저장</button>
                      <button type="button" className="krds-btn small secondary" onClick={() => setEditingId(null)}>취소</button>
                    </div>
                  ) : (
                    <code
                      className={styles.cronCode}
                      onClick={() => { setEditingId(s.id); setEditCron(s.cronExpression); }}
                      title="클릭하여 수정"
                    >
                      {s.cronExpression}
                    </code>
                  )}
                </td>
                <td>
                  <button
                    type="button"
                    onClick={() => handleToggle(s.id)}
                    className={`${styles.toggleBadge} krds-badge ${s.isEnabled ? 'bg-light-success' : 'bg-light-gray'}`}
                  >
                    {s.isEnabled ? '활성' : '비활성'}
                  </button>
                </td>
                <td className={styles.mutedCell}>{new Date(s.createdAt).toLocaleString('ko-KR')}</td>
                <td>
                  <button
                    type="button"
                    className="krds-btn small app-btn-danger"
                    onClick={() => handleDelete(s.id)}
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* 도움말 */}
      <div className={styles.helpFoot}>
        Spring Cron 6자리: 초 분 시 일 월 요일 | 예: <code>0 */5 * * * *</code> = 매 5분
      </div>
    </div>
  );
}
