'use client';

import { useCallback, useEffect, useState } from 'react';
import { scheduleApi } from '@/lib/collectorApi';
import { ApiScheduleItem } from '@/types/api-collect';

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
    return <div style={{ padding: '2rem', textAlign: 'center' }}>로딩 중...</div>;
  }

  return (
    <div className="card">
      <div className="card-header">
        <h3 className="card-title">스케줄 관리</h3>
      </div>

      {/* 새 스케줄 추가 */}
      <div style={{ padding: '1rem', borderBottom: '1px solid var(--gray-200)' }}>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.5rem' }}>
          <input
            type="text"
            value={newCron}
            onChange={e => setNewCron(e.target.value)}
            placeholder="Cron 표현식 (예: 0 */10 * * * *)"
            style={{ flex: 1, padding: '6px 10px', border: '1px solid var(--gray-300)', borderRadius: '4px', fontSize: '0.85rem' }}
          />
          <button className="btn btn-sm btn-primary" onClick={handleCreate}
            disabled={!newCron.trim()}>
            추가
          </button>
        </div>
        <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
          {CRON_PRESETS.map(p => (
            <button key={p.cron} className="btn btn-sm"
              style={{ background: 'var(--gray-100)', fontSize: '0.75rem', padding: '2px 8px' }}
              onClick={() => setNewCron(p.cron)}>
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {/* 스케줄 목록 */}
      {schedules.length === 0 ? (
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-500)' }}>
          등록된 스케줄이 없습니다.
        </div>
      ) : (
        <div className="table-container">
          <table>
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
                      <div style={{ display: 'flex', gap: '0.25rem' }}>
                        <input
                          type="text"
                          value={editCron}
                          onChange={e => setEditCron(e.target.value)}
                          style={{ flex: 1, padding: '4px 8px', border: '1px solid var(--gray-300)', borderRadius: '4px', fontSize: '0.85rem' }}
                        />
                        <button className="btn btn-sm btn-primary" onClick={() => handleUpdate(s.id)}>저장</button>
                        <button className="btn btn-sm" style={{ background: 'var(--gray-200)' }}
                          onClick={() => setEditingId(null)}>취소</button>
                      </div>
                    ) : (
                      <code style={{ fontSize: '0.85rem', cursor: 'pointer' }}
                        onClick={() => { setEditingId(s.id); setEditCron(s.cronExpression); }}
                        title="클릭하여 수정">
                        {s.cronExpression}
                      </code>
                    )}
                  </td>
                  <td>
                    <button
                      className="btn btn-sm"
                      onClick={() => handleToggle(s.id)}
                      style={{
                        background: s.isEnabled ? '#dcfce7' : 'var(--gray-100)',
                        color: s.isEnabled ? '#166534' : 'var(--gray-500)',
                        border: 'none',
                        fontWeight: 600,
                        fontSize: '0.75rem',
                        padding: '2px 10px',
                      }}>
                      {s.isEnabled ? 'ON' : 'OFF'}
                    </button>
                  </td>
                  <td style={{ fontSize: '0.8rem' }}>
                    {new Date(s.createdAt).toLocaleString('ko-KR')}
                  </td>
                  <td>
                    <button className="btn btn-sm"
                      style={{ background: '#fee2e2', color: '#991b1b', fontSize: '0.75rem' }}
                      onClick={() => handleDelete(s.id)}>
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 도움말 */}
      <div style={{ padding: '0.75rem 1rem', fontSize: '0.75rem', color: 'var(--gray-500)', borderTop: '1px solid var(--gray-200)' }}>
        Spring Cron 6자리: 초 분 시 일 월 요일 | 예: <code>0 */5 * * * *</code> = 매 5분
      </div>
    </div>
  );
}
