'use client';

import { useEffect, useState, useRef } from 'react';
import Link from 'next/link';
import type { Agent, ExecutionDetail, StepLog } from '@/types';
import { executionApi } from '@/lib/api';
import StatusBadge from '@/components/StatusBadge';

interface MonitorTabProps {
  agent: Agent;
  onExecutionComplete?: () => void;
}

export default function MonitorTab({ agent, onExecutionComplete }: MonitorTabProps) {
  const [execution, setExecution] = useState<ExecutionDetail | null>(null);
  const [stepLogs, setStepLogs] = useState<StepLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [completed, setCompleted] = useState(false); // 완료 상태 플래그
  const prevStatusRef = useRef<string | null>(null);

  const fetchData = async () => {
    try {
      const executions = await executionApi.getByAgent(agent.agentId);

      if (agent.status === 'RUNNING') {
        const running = executions.find(e => e.status === 'RUNNING');
        if (running) {
          setExecution(running);
          const logs = await executionApi.getStepLogs(running.executionId);
          setStepLogs(logs as unknown as StepLog[]);
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
      fetchData(); // 즉시 1회 실행
      const interval = setInterval(fetchData, 3000); // 이후 3초마다
      return () => clearInterval(interval);
    } else {
      // RUNNING이 아닐 때도 초기 로드 1회
      fetchData();
    }
  }, [agent.status, completed, agent.agentId]);

  // 상태 변경 감지 (RUNNING → 완료)
  useEffect(() => {
    if (prevStatusRef.current === 'RUNNING' && agent.status !== 'RUNNING') {
      // 완료됨 - 마지막 데이터 1회 조회
      const fetchFinal = async () => {
        try {
          const executions = await executionApi.getByAgent(agent.agentId);
          const latest = executions[0];
          if (latest) {
            setExecution(latest);
            const logs = await executionApi.getStepLogs(latest.executionId);
            setStepLogs(logs as unknown as StepLog[]);
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
  }, [agent.status, agent.agentId, onExecutionComplete]);

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

  // 실행 완료 상태""
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
          <p style={{ fontSize: '1.1rem', marginBottom: '1rem' }}>
            {execution.status === 'SUCCESS'
              ? '파이프라인이 성공적으로 완료되었습니다!'
              : '파이프라인 실행 중 오류가 발생했습니다.'}
          </p>
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

  // 실행 중 상태 - 실시간 진행률 표시
  const totalSteps = stepLogs.length > 0 ? (stepLogs[0].totalSteps || stepLogs.length) : 0;
  const completedSteps = stepLogs.filter(s => s.status === 'SUCCESS' || s.status === 'FAILED').length;
  const currentRunningStep = stepLogs.find(s => s.status === 'RUNNING');
  const hasStepLogs = stepLogs.length > 0;

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

        {/* 전체 진행률 */}
        <div style={{ marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
            <strong>전체 진행률</strong>
            <span>{hasStepLogs ? `${completedSteps}/${totalSteps} Steps` : '초기화 중...'}</span>
          </div>
          <div style={{ background: 'var(--gray-200)', borderRadius: '9999px', height: '8px', overflow: 'hidden' }}>
            <div style={{
              background: 'var(--primary)',
              height: '100%',
              width: hasStepLogs && totalSteps > 0 ? `${(completedSteps / totalSteps) * 100}%` : '0%',
              transition: 'width 0.3s'
            }} />
          </div>
        </div>

        {/* 현재 진행중인 Step */}
        {currentRunningStep && (
          <div style={{
            background: 'var(--blue-100)',
            border: '2px solid var(--blue-300)',
            borderRadius: '0.5rem',
            padding: '1rem',
            marginBottom: '1.5rem',
            display: 'flex',
            alignItems: 'center',
            gap: '1rem'
          }}>
            <div className="spinner" style={{ width: '24px', height: '24px' }} />
            <div>
              <div style={{ fontWeight: 600, color: 'var(--blue-700)' }}>
                현재 Step: {currentRunningStep.stepName || currentRunningStep.stepId}
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--blue-600)' }}>
                Step {currentRunningStep.stepOrder}/{totalSteps} 처리 중...
              </div>
            </div>
          </div>
        )}

        {/* Step별 상태 */}
        <div>
          <strong style={{ display: 'block', marginBottom: '1rem' }}>Step 진행 상황</strong>
          {!hasStepLogs ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--gray-500)' }}>
              <div className="spinner" style={{ width: '32px', height: '32px', margin: '0 auto 1rem' }} />
              <p>Step 정보를 기다리는 중...</p>
            </div>
          ) : (
            stepLogs.map((step, index) => (
              <div key={step.stepLogId || `step-${index}`} style={{
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                padding: '1rem',
                background: step.status === 'RUNNING' ? 'var(--blue-100)' : 'white',
                borderRadius: '0.5rem',
                marginBottom: '0.5rem',
                border: step.status === 'RUNNING' ? '2px solid var(--blue-300)' : '1px solid var(--gray-200)',
              }}>
                <div style={{
                  width: '28px', height: '28px', borderRadius: '50%',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: step.status === 'SUCCESS' ? 'var(--green-500)'
                    : step.status === 'FAILED' ? 'var(--red-500)'
                    : step.status === 'RUNNING' ? 'var(--blue-500)'
                    : 'var(--gray-300)',
                  color: 'white', fontSize: '0.8rem', fontWeight: 'bold'
                }}>
                  {step.status === 'SUCCESS' ? '✓' : step.status === 'FAILED' ? '✕' : step.stepOrder || index + 1}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    {step.stepName || step.stepId}
                  </div>
                  {(step.status === 'SUCCESS' || step.status === 'FAILED') && (
                    <div style={{ fontSize: '0.85rem', color: 'var(--gray-500)', marginTop: '0.25rem' }}>
                      {step.readCount != null && `읽기: ${step.readCount}건`}
                      {step.writeCount != null && `, 쓰기: ${step.writeCount}건`}
                      {step.skipCount != null && step.skipCount > 0 && `, 건너뛰기: ${step.skipCount}건`}
                    </div>
                  )}
                  {step.status === 'FAILED' && step.errorMessage && (
                    <div style={{ fontSize: '0.85rem', color: 'var(--red-500)', marginTop: '0.25rem' }}>
                      오류: {step.errorMessage}
                    </div>
                  )}
                </div>
                <StatusBadge status={step.status} />
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
