'use client';

import { useEffect, useState, useRef } from 'react';
import Link from 'next/link';
import type { Agent, ExecutionDetail } from '@/types';
import { executionApi } from '@/lib/api';
import StatusBadge from '@/components/StatusBadge';

interface MonitorTabProps {
  agent: Agent;
  onExecutionComplete?: () => void;
}

export default function MonitorTab({ agent, onExecutionComplete }: MonitorTabProps) {
  const [execution, setExecution] = useState<ExecutionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [completed, setCompleted] = useState(false);
  const prevStatusRef = useRef<string | null>(null);

  const fetchData = async () => {
    try {
      const executions = await executionApi.getByAgent(agent.id);

      if (agent.status === 'RUNNING') {
        const running = executions.find(e => e.status === 'RUNNING');
        if (running) {
          setExecution(running);
        }
      }
    } catch (error) {
      console.error('실행 상태 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  // 실행 중일 때만 폴링 (진입 시 즉시 1회 + 이후 3초마다)
  useEffect(() => {
    if (agent.status === 'RUNNING' && !completed) {
      fetchData();
      const interval = setInterval(fetchData, 3000);
      return () => clearInterval(interval);
    } else {
      fetchData();
    }
  }, [agent.status, completed, agent.id]);

  // 상태 변경 감지 (RUNNING → 완료)
  useEffect(() => {
    if (prevStatusRef.current === 'RUNNING' && agent.status !== 'RUNNING') {
      const fetchFinal = async () => {
        try {
          const executions = await executionApi.getByAgent(agent.id);
          const latest = executions[0];
          if (latest) {
            setExecution(latest);
          }
          setCompleted(true);
          onExecutionComplete?.();
        } catch (error) {
          console.error('완료 데이터 조회 실패:', error);
        }
      };
      fetchFinal();
    }
    prevStatusRef.current = agent.status;
  }, [agent.status, agent.id, onExecutionComplete]);

  if (loading) {
    return (
      <div className="card">
        <div className="empty-state" style={{ padding: '3rem', textAlign: 'center' }}>
          <div className="spinner" style={{ width: '32px', height: '32px', margin: '0 auto 1rem' }} />
          <p style={{ color: 'var(--gray-500)' }}>로딩중...</p>
        </div>
      </div>
    );
  }

  // 실행 완료 상태
  if (completed && execution) {
    return (
      <div className="card" style={{
        background: execution.status === 'SUCCESS' ? 'var(--green-50)' : 'var(--red-50)',
        border: `2px solid ${execution.status === 'SUCCESS' ? 'var(--green-200)' : 'var(--red-200)'}`
      }}>
        <div className="card-header">
          <h2 className="card-title" style={{
            color: execution.status === 'SUCCESS' ? 'var(--green-700)' : 'var(--red-700)'
          }}>
            실행 완료
          </h2>
          <StatusBadge status={execution.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} />
        </div>
        <div style={{ marginTop: '1.5rem', textAlign: 'center' }}>
          <p style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>
            {execution.status === 'SUCCESS'
              ? '파이프라인이 성공적으로 완료되었습니다!'
              : '파이프라인 실행 중 오류가 발생했습니다.'}
          </p>
          {execution.status === 'SUCCESS' && (
            <p style={{ fontSize: '0.9rem', color: 'var(--gray-600)', marginBottom: '1rem' }}>
              읽기: {execution.totalReadCount ?? 0}건, 쓰기: {execution.totalWriteCount ?? 0}건
              {execution.totalSkipCount ? `, 스킵: ${execution.totalSkipCount}건` : ''}
              {execution.durationMs != null ? ` (${(execution.durationMs / 1000).toFixed(1)}s)` : ''}
            </p>
          )}
          <Link
            href={`/executions/${execution.executionId}`}
            className="btn btn-primary"
          >
            실행 결과 보기
          </Link>
        </div>
      </div>
    );
  }

  // 실행 중이 아니고 완료 직후도 아님
  if (agent.status !== 'RUNNING' || !execution) {
    return (
      <div className="card">
        <div className="empty-state" style={{ padding: '3rem', textAlign: 'center' }}>
          <p style={{ fontSize: '1.2rem', color: 'var(--gray-500)' }}>현재 실행 중인 작업이 없습니다</p>
          <p style={{ color: 'var(--gray-400)', marginTop: '0.5rem' }}>파이프라인을 실행하면 여기서 진행 상황을 확인할 수 있습니다</p>
        </div>
      </div>
    );
  }

  // 실행 중 상태
  return (
    <div className="card" style={{ background: 'var(--blue-50)', border: '2px solid var(--blue-200)' }}>
      <div className="card-header">
        <h2 className="card-title" style={{ color: 'var(--blue-700)' }}>실행 중</h2>
        <StatusBadge status="RUNNING" />
      </div>

      <div style={{ marginTop: '1.5rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.5rem' }}>
          <div><strong>실행 ID:</strong> <code>{execution.executionId.substring(0, 8)}...</code></div>
          <div><strong>시작 시간:</strong> {new Date(execution.startedAt).toLocaleString('ko-KR')}</div>
        </div>

        {/* 진행 중 애니메이션 */}
        <div style={{
          background: 'var(--blue-100)',
          border: '2px solid var(--blue-300)',
          borderRadius: '0.5rem',
          padding: '1.5rem',
          display: 'flex',
          alignItems: 'center',
          gap: '1rem',
          justifyContent: 'center',
        }}>
          <div className="spinner" style={{ width: '24px', height: '24px' }} />
          <div>
            <div style={{ fontWeight: 600, color: 'var(--blue-700)' }}>
              파이프라인 처리 중...
            </div>
            <div style={{ fontSize: '0.85rem', color: 'var(--blue-600)', marginTop: '0.25rem' }}>
              {execution.totalReadCount != null
                ? `읽기: ${execution.totalReadCount}건, 쓰기: ${execution.totalWriteCount ?? 0}건`
                : '데이터 처리 대기 중...'}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
