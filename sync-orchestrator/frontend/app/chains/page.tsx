'use client';

import { useEffect, useState } from 'react';
import { chainApi, agentApi } from '@/lib/api';
import type { AgentChain, Agent, Zone } from '@/types';
import StatusBadge from '@/components/StatusBadge';

const ZONE_LABELS: Record<string, string> = {
  EXTERNAL: '외부망',
  DMZ: 'DMZ',
  INTERNAL_COMMON: '내부공통망',
  INTERNAL_SERVICE: '내부서비스망',
};

export default function ChainsPage() {
  const [chains, setChains] = useState<AgentChain[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [chainsData, agentsData] = await Promise.all([
        chainApi.getAll(),
        agentApi.getAll(),
      ]);
      setChains(chainsData);
      setAgents(agentsData);
    } catch (error) {
      console.error('데이터 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleExecute = async (id: number) => {
    if (!confirm('체인 실행을 시작하시겠습니까?')) return;
    try {
      await chainApi.execute(id);
      alert('체인 실행이 시작되었습니다');
    } catch (error) {
      console.error('체인 실행 실패:', error);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('정말 삭제하시겠습니까?')) return;
    try {
      await chainApi.delete(id);
      fetchData();
    } catch (error) {
      console.error('체인 삭제 실패:', error);
    }
  };

  if (loading) {
    return <div className="loading">로딩중...</div>;
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Agent 체인</h1>
        <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? '취소' : '체인 등록'}
        </button>
      </div>

      {showForm && (
        <ChainForm
          agents={agents}
          onSuccess={() => {
            setShowForm(false);
            fetchData();
          }}
        />
      )}

      {chains.length === 0 ? (
        <div className="card">
          <div className="empty-state">등록된 체인이 없습니다</div>
        </div>
      ) : (
        chains.map((chain) => (
          <div key={chain.id} className="card">
            <div className="card-header">
              <div>
                <h2 className="card-title">{chain.name}</h2>
                {chain.description && (
                  <p style={{ color: 'var(--gray-500)', marginTop: '0.25rem' }}>
                    {chain.description}
                  </p>
                )}
              </div>
              <div>
                <StatusBadge status={chain.isActive ? 'ONLINE' : 'OFFLINE'} />
              </div>
            </div>

            <div className="chain-flow">
              {chain.members
                .sort((a, b) => a.sequence - b.sequence)
                .map((member, index) => (
                  <div key={member.id} style={{ display: 'flex', alignItems: 'center' }}>
                    {index > 0 && <span className="chain-arrow">→</span>}
                    <div className="chain-node">
                      <strong>{member.agent?.agentName || `Agent ${member.agentId}`}</strong>
                      <span
                        className={`zone-${member.agent?.zone?.toLowerCase().replace('_', '-') || ''}`}
                        style={{ fontSize: '0.75rem' }}
                      >
                        {member.agent ? ZONE_LABELS[member.agent.zone] || member.agent.zone : '-'}
                      </span>
                      <span style={{ fontSize: '0.75rem', color: 'var(--gray-500)' }}>
                        순서 #{member.sequence}
                      </span>
                    </div>
                  </div>
                ))}
            </div>

            <div style={{ marginTop: '1rem', display: 'flex', gap: '0.5rem' }}>
              <button
                className="btn btn-primary btn-sm"
                onClick={() => handleExecute(chain.id)}
              >
                체인 실행
              </button>
              <button
                className="btn btn-danger btn-sm"
                onClick={() => handleDelete(chain.id)}
              >
                삭제
              </button>
            </div>
          </div>
        ))
      )}
    </div>
  );
}

function ChainForm({
  agents,
  onSuccess,
}: {
  agents: Agent[];
  onSuccess: () => void;
}) {
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    isActive: true,
    memberAgentIds: [] as string[],
  });
  const [submitting, setSubmitting] = useState(false);

  const handleAgentToggle = (agentId: string) => {
    setFormData((prev) => {
      const ids = prev.memberAgentIds.includes(agentId)
        ? prev.memberAgentIds.filter((id) => id !== agentId)
        : [...prev.memberAgentIds, agentId];
      return { ...prev, memberAgentIds: ids };
    });
  };

  const moveAgent = (index: number, direction: 'up' | 'down') => {
    const newIds = [...formData.memberAgentIds];
    const newIndex = direction === 'up' ? index - 1 : index + 1;
    if (newIndex < 0 || newIndex >= newIds.length) return;
    [newIds[index], newIds[newIndex]] = [newIds[newIndex], newIds[index]];
    setFormData({ ...formData, memberAgentIds: newIds });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (formData.memberAgentIds.length < 2) {
      alert('최소 2개 이상의 Agent를 선택해주세요.');
      return;
    }
    setSubmitting(true);
    try {
      await chainApi.create(formData);
      onSuccess();
    } catch (error) {
      console.error('체인 등록 실패:', error);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="card">
      <h3 className="card-title" style={{ marginBottom: '1rem' }}>
        체인 등록
      </h3>
      <form onSubmit={handleSubmit}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">체인명</label>
            <input
              type="text"
              className="form-input"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder="주문 동기화 체인"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">설명</label>
            <input
              type="text"
              className="form-input"
              value={formData.description}
              onChange={(e) =>
                setFormData({ ...formData, description: e.target.value })
              }
              placeholder="외부→DMZ→내부 순차 처리"
            />
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">Agent 선택 (실행 순서대로)</label>
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
            {agents.map((agent) => (
              <label
                key={agent.agentId}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  padding: '0.5rem',
                  border: '1px solid var(--gray-200)',
                  borderRadius: '0.375rem',
                  cursor: 'pointer',
                }}
              >
                <input
                  type="checkbox"
                  checked={formData.memberAgentIds.includes(agent.agentId)}
                  onChange={() => handleAgentToggle(agent.agentId)}
                />
                {agent.agentName}
                <span
                  className={`zone-${agent.zone.toLowerCase().replace('_', '-')}`}
                  style={{ fontSize: '0.75rem' }}
                >
                  ({ZONE_LABELS[agent.zone] || agent.zone})
                </span>
              </label>
            ))}
          </div>
        </div>

        {formData.memberAgentIds.length > 0 && (
          <div className="form-group">
            <label className="form-label">실행 순서</label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              {formData.memberAgentIds.map((agentId, index) => {
                const agent = agents.find((a) => a.agentId === agentId);
                return (
                  <div
                    key={agentId}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.5rem',
                      padding: '0.5rem',
                      background: 'var(--gray-50)',
                      borderRadius: '0.375rem',
                    }}
                  >
                    <span style={{ width: '2rem' }}>{index + 1}.</span>
                    <span style={{ flex: 1 }}>{agent?.agentName}</span>
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() => moveAgent(index, 'up')}
                      disabled={index === 0}
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() => moveAgent(index, 'down')}
                      disabled={index === formData.memberAgentIds.length - 1}
                    >
                      ↓
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? '등록중...' : '등록'}
        </button>
      </form>
    </div>
  );
}
