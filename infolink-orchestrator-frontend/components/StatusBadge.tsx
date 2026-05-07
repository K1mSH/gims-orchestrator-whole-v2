import type { AgentStatus, ExecutionStatus } from '@/types';

type Status = AgentStatus | ExecutionStatus;

const STATUS_CONFIG: Record<string, { label: string; className: string }> = {
  // Agent 상태 (ONLINE, OFFLINE, RUNNING)
  ONLINE: { label: '온라인', className: 'online' },
  OFFLINE: { label: '오프라인', className: 'offline' },
  // 실행 상태 (RUNNING, SUCCESS, FAILED)
  RUNNING: { label: '실행중', className: 'running' },
  SUCCESS: { label: '성공', className: 'success' },
  FAILED: { label: '실패', className: 'failed' },
};

interface StatusBadgeProps {
  status: Status;
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const config = STATUS_CONFIG[status] || { label: status, className: 'offline' };

  return (
    <span className={`status-badge ${config.className}`}>
      {config.label}
    </span>
  );
}
