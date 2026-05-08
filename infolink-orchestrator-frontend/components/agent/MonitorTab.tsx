'use client';

import { useEffect, useState, useRef } from 'react';
import Link from 'next/link';
import type { Agent, ExecutionDetail } from '@/types';
import { executionApi } from '@/lib/api';
import StatusBadge from '@/components/StatusBadge';
import styles from './MonitorTab.module.css';

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
        const running = executions.find((e) => e.status === 'RUNNING');
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

  useEffect(() => {
    if (agent.status === 'RUNNING' && !completed) {
      fetchData();
      const interval = setInterval(fetchData, 3000);
      return () => clearInterval(interval);
    } else {
      fetchData();
    }
  }, [agent.status, completed, agent.id]);

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
      <div className="app-card">
        <div className={styles.loadingBody}>
          <span className={`krds-spinner ${styles.spinnerInline}`} />
          <p className={styles.emptyTitle}>로딩중...</p>
        </div>
      </div>
    );
  }

  // 실행 완료 상태
  if (completed && execution) {
    const isSuccess = execution.status === 'SUCCESS';
    return (
      <div
        className={`app-card ${styles.statusCard} ${
          isSuccess ? styles['statusCard--success'] : styles['statusCard--failed']
        }`}
      >
        <div className="app-card__header">
          <h2
            className={`app-card__title ${
              isSuccess ? styles['statusTitle--success'] : styles['statusTitle--failed']
            }`}
          >
            실행 완료
          </h2>
          <StatusBadge status={execution.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} />
        </div>
        <div className={styles.completeBody}>
          <p className={styles.completeMessage}>
            {isSuccess
              ? '파이프라인이 성공적으로 완료되었습니다.'
              : '파이프라인 실행 중 오류가 발생했습니다.'}
          </p>
          {isSuccess && (
            <p className={styles.completeStats}>
              읽기: {execution.totalReadCount ?? 0}건, 쓰기: {execution.totalWriteCount ?? 0}건
              {execution.totalSkipCount ? `, 스킵: ${execution.totalSkipCount}건` : ''}
              {execution.durationMs != null
                ? ` (${(execution.durationMs / 1000).toFixed(1)}s)`
                : ''}
            </p>
          )}
          <Link
            href={`/executions/${execution.executionId}`}
            className="krds-btn small primary"
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
      <div className="app-card">
        <div className="app-empty">
          <p className={styles.emptyTitle}>현재 실행 중인 작업이 없습니다</p>
          <p className={styles.emptyHint}>
            파이프라인을 실행하면 여기서 진행 상황을 확인할 수 있습니다
          </p>
        </div>
      </div>
    );
  }

  // 실행 중 상태
  return (
    <div className={`app-card ${styles.statusCard} ${styles['statusCard--running']}`}>
      <div className="app-card__header">
        <h2 className={`app-card__title ${styles['statusTitle--running']}`}>실행 중</h2>
        <StatusBadge status="RUNNING" />
      </div>

      <div className={styles.runningBody}>
        <div className={styles.runningMeta}>
          <div>
            <span className={styles.runningMeta__label}>실행 ID:</span>
            <code>{execution.executionId.substring(0, 8)}...</code>
          </div>
          <div>
            <span className={styles.runningMeta__label}>시작 시간:</span>
            {new Date(execution.startedAt).toLocaleString('ko-KR')}
          </div>
        </div>

        <div className={styles.runningProgress}>
          <span className={`krds-spinner ${styles.spinnerInline}`} />
          <div>
            <div className={styles.runningProgress__title}>파이프라인 처리 중...</div>
            <div className={styles.runningProgress__sub}>
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
