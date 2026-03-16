'use client';

interface TabButtonProps {
  label: string;
  active: boolean;
  onClick: () => void;
  disabled?: boolean;
  disabledReason?: string;
}

export default function TabButton({ label, active, onClick, disabled, disabledReason }: TabButtonProps) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={disabled ? disabledReason : undefined}
      style={{
        padding: '0.5rem 1rem',
        border: 'none',
        borderBottom: active ? '2px solid var(--primary)' : '2px solid transparent',
        background: 'none',
        fontWeight: active ? 600 : 400,
        color: disabled ? 'var(--gray-400)' : active ? 'var(--primary)' : 'var(--gray-600)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        fontSize: '0.9rem',
      }}
    >
      {label}
    </button>
  );
}
