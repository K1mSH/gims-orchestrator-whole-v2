import type { AgentStatus, ExecutionStatus } from '@/types';

type Status = AgentStatus | ExecutionStatus;

const STATUS_CONFIG: Record<string, { label: string; variant: string }> = {
  // Agent 상태 (ONLINE, OFFLINE, RUNNING)
  ONLINE: { label: '온라인', variant: 'bg-light-success' },
  OFFLINE: { label: '오프라인', variant: 'bg-light-gray' },
  // 실행 상태 (RUNNING, SUCCESS, FAILED)
  RUNNING: { label: '실행중', variant: 'bg-light-information' },
  SUCCESS: { label: '성공', variant: 'bg-light-success' },
  FAILED: { label: '실패', variant: 'bg-light-danger' },
};

interface StatusBadgeProps {
  status: Status;
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const config = STATUS_CONFIG[status] || { label: status, variant: 'bg-light-gray' };

  return <span className={`krds-badge ${config.variant}`}>{config.label}</span>;
}
